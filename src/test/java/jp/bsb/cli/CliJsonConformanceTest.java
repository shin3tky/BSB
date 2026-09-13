package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.FileOffset;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.format.FormatResult;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ProgramRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CliJsonConformanceTest {
  @TempDir Path temporaryDirectory;

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void matchesEveryExpectedJsonByteForByte(CliJsonConformanceData.CaseSpec spec)
      throws IOException {
    Invocation invocation = invoke(spec);

    assertEquals(spec.exitCode(), invocation.exitCode(), spec.id());
    assertEquals(0, invocation.stderr().length, spec.id() + ": stderr");
    assertPhysicalJson(invocation.stdout(), spec.id());
    Object expected = parse(spec.expectedBytes());
    Object actual = parse(invocation.stdout());
    validateDocument(asObject(expected, "expected document"));
    validateDocument(asObject(actual, "actual document"));
    assertEquals(expected, actual, spec.id() + ": parsed JSON");
    assertArrayEquals(spec.expectedBytes(), invocation.stdout(), spec.id() + ": UTF-8 bytes");
    String output = new String(invocation.stdout(), StandardCharsets.UTF_8);
    assertFalse(output.contains("Exception"), spec.id() + ": host exception type");
    assertFalse(output.contains("java."), spec.id() + ": internal Java name");
    assertFalse(output.contains("/Users/"), spec.id() + ": unexpected absolute path");
  }

  @Test
  void roundTripsQuotesAndBackslashesInTheUserSuppliedPath() throws IOException {
    Path source = temporaryDirectory.resolve("引用\"逆\\斜線.bsb");
    Files.copy(Path.of("tests/conformance/language-core/sources/CORE-N001.bsb"), source);

    Invocation invocation = invoke(new BsbCli(), source.toString());
    Map<String, Object> document = asObject(parse(invocation.stdout()), "document");

    assertEquals(0, invocation.exitCode());
    assertEquals(source.toString(), document.get("source"));
    String output = new String(invocation.stdout(), StandardCharsets.UTF_8);
    assertTrue(output.contains("\\\""));
    assertTrue(output.contains("\\\\"));
  }

  @Test
  void capsAndOrdersOneHundredDiagnosticsDeterministically() throws IOException {
    var collector = new DiagnosticCollector();
    for (int index = 0; index < 101; index++) {
      collector.add(
          Diagnostic.builder(
                  DiagnosticCode.W_PARTICLE_POSITION,
                  Severity.WARNING,
                  DiagnosticStage.TYPE_AND_STACK,
                  "many.bsb",
                  new SourcePosition(index, 1, index + 1))
              .expected("本体先頭以外")
              .actual("を")
              .fix("先頭のをを削除してください")
              .build());
    }
    AnalysisResult result =
        new AnalysisResult(Optional.empty(), collector.diagnostics(), Optional.empty());
    BsbCli cli = new BsbCli(backendReturning(result), DiagnosticMessageCatalog.loadDefault());

    Invocation first = invoke(cli, "many.bsb");
    Invocation second = invoke(cli, "many.bsb");
    Map<String, Object> document = asObject(parse(first.stdout()), "document");
    List<Object> diagnostics = asArray(document.get("diagnostics"), "diagnostics");

    assertEquals(8, first.exitCode());
    assertEquals(100, diagnostics.size());
    assertEquals(
        "W_PARTICLE_POSITION", asObject(diagnostics.getFirst(), "first diagnostic").get("code"));
    assertEquals(
        "E_DIAGNOSTIC_LIMIT", asObject(diagnostics.getLast(), "last diagnostic").get("code"));
    assertArrayEquals(first.stdout(), second.stdout());
    assertEquals(0, first.stderr().length);
  }

  @Test
  void strictParserRejectsDuplicatesTrailingDataAndInvalidTypes() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> StrictJsonParser.parse("{\"a\":1,\"a\":2}"));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> StrictJsonParser.parse("{} trailing"));
    org.junit.jupiter.api.Assertions.assertThrows(
        AssertionError.class,
        () -> validateDocument(asObject(StrictJsonParser.parse("{}"), "document")));
  }

  static Stream<CliJsonConformanceData.CaseSpec> cases() throws IOException {
    return CliJsonConformanceData.load().stream();
  }

  private static Invocation invoke(CliJsonConformanceData.CaseSpec spec) throws IOException {
    return switch (spec.kind()) {
      case "file", "missing" -> invoke(new BsbCli(), spec.source());
      case "usage" -> invoke(new BsbCli(), null);
      case "synthetic-utf8" ->
          invoke(
              new BsbCli(
                  backendReturning(syntheticUtf8(spec.source())),
                  DiagnosticMessageCatalog.loadDefault()),
              spec.source());
      case "synthetic-offset" ->
          invoke(
              new BsbCli(
                  backendReturning(syntheticOffset(spec.source())),
                  DiagnosticMessageCatalog.loadDefault()),
              spec.source());
      case "synthetic-internal" ->
          invoke(
              new BsbCli(throwingBackend(), DiagnosticMessageCatalog.loadDefault()), spec.source());
      case "synthetic-output-limit" -> {
        byte[] validSource = "メインとは （--）\nこと。\n".getBytes(StandardCharsets.UTF_8);
        AnalysisResult result = new SourceChecker().check(spec.source(), validSource);
        BsbCli cli =
            new BsbCli(
                backendReturning(result),
                DiagnosticMessageCatalog.loadDefault(),
                new CliJsonRenderer(256));
        yield invoke(cli, spec.source().repeat(20));
      }
      default -> throw new IllegalStateException("unknown case kind: " + spec.kind());
    };
  }

  private static Invocation invoke(BsbCli cli, String source) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    String[] arguments =
        source == null
            ? new String[] {"check", "--json"}
            : new String[] {"check", "--json", source};
    int exitCode = cli.run(arguments, stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private static AnalysisResult syntheticUtf8(String source) {
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_INVALID_UTF8,
                Severity.ERROR,
                DiagnosticStage.UTF8,
                source,
                new SourcePosition(0, 1, 1))
            .expected("UTF-8")
            .actual("C3 28")
            .fix("UTF-8で保存し直してください")
            .build();
    return new AnalysisResult(Optional.empty(), List.of(diagnostic), Optional.empty());
  }

  private static AnalysisResult syntheticOffset(String source) {
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_SOURCE_SIZE_LIMIT,
                Severity.ERROR,
                DiagnosticStage.UTF8,
                source,
                new FileOffset(33_554_432))
            .limit("sourceBytes", 33_554_432, 33_554_433)
            .build();
    return new AnalysisResult(Optional.empty(), List.of(diagnostic), Optional.empty());
  }

  private static CliBackend backendReturning(AnalysisResult result) {
    return new CliBackend() {
      @Override
      public AnalysisResult check(Path path) {
        return result;
      }

      @Override
      public ProgramRunResult run(Path path, ExecutionContext context) {
        throw new UnsupportedOperationException();
      }

      @Override
      public FormatResult format(Path path) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static CliBackend throwingBackend() {
    return new CliBackend() {
      @Override
      public AnalysisResult check(Path path) {
        throw new IllegalStateException("host exception must remain secret");
      }

      @Override
      public ProgramRunResult run(Path path, ExecutionContext context) {
        throw new UnsupportedOperationException();
      }

      @Override
      public FormatResult format(Path path) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static Object parse(byte[] bytes) {
    return StrictJsonParser.parse(bytes);
  }

  private static void assertPhysicalJson(byte[] bytes, String label) {
    assertTrue(bytes.length > 1, label);
    assertFalse(
        bytes.length >= 3
            && bytes[0] == (byte) 0xEF
            && bytes[1] == (byte) 0xBB
            && bytes[2] == (byte) 0xBF,
        label + ": BOM");
    assertEquals('\n', bytes[bytes.length - 1], label + ": trailing LF");
    for (int index = 0; index < bytes.length - 1; index++) {
      assertFalse(bytes[index] == '\n' || bytes[index] == '\r', label + ": one line");
    }
  }

  private static void validateDocument(Map<String, Object> document) {
    validateKeyOrder(
        document,
        List.of(
            "schemaVersion", "command", "source", "success", "exitCode", "diagnostics", "problem"),
        Set.of("schemaVersion", "command", "success", "exitCode", "diagnostics"));
    assertEquals(new BigDecimal("1"), document.get("schemaVersion"));
    assertEquals("check", document.get("command"));
    optionalString(document, "source");
    boolean success = asBoolean(document.get("success"), "success");
    int exitCode = asNumber(document.get("exitCode"), "exitCode").intValueExact();
    assertEquals(exitCode == 0, success);
    List<Object> diagnostics = asArray(document.get("diagnostics"), "diagnostics");
    diagnostics.forEach(value -> validateDiagnostic(asObject(value, "diagnostic")));
    if (document.containsKey("problem")) {
      assertTrue(diagnostics.isEmpty());
      validateProblem(asObject(document.get("problem"), "problem"));
      assertTrue(exitCode == 2 || exitCode == 3 || exitCode == 70);
    } else {
      assertTrue(exitCode == 0 || exitCode == 8 || exitCode == 9);
    }
  }

  private static void validateDiagnostic(Map<String, Object> diagnostic) {
    validateKeyOrder(
        diagnostic,
        List.of(
            "code",
            "severity",
            "stage",
            "message",
            "sourcePath",
            "span",
            "point",
            "offset",
            "fields",
            "expected",
            "actual",
            "relatedLocations",
            "fixes",
            "resourceLimit"),
        Set.of(
            "code",
            "severity",
            "stage",
            "message",
            "sourcePath",
            "fields",
            "relatedLocations",
            "fixes"));
    requireString(diagnostic, "code");
    assertTrue(Set.of("error", "warning").contains(requireString(diagnostic, "severity")));
    assertTrue(
        Set.of("utf8", "lexical", "syntax", "name", "typeAndStack", "ir", "runtime")
            .contains(requireString(diagnostic, "stage")));
    requireString(diagnostic, "message");
    requireString(diagnostic, "sourcePath");
    int locations =
        (diagnostic.containsKey("span") ? 1 : 0)
            + (diagnostic.containsKey("point") ? 1 : 0)
            + (diagnostic.containsKey("offset") ? 1 : 0);
    assertEquals(1, locations);
    if (diagnostic.containsKey("span")) {
      validateSpan(asObject(diagnostic.get("span"), "span"));
    }
    if (diagnostic.containsKey("point")) {
      validatePoint(asObject(diagnostic.get("point"), "point"));
    }
    if (diagnostic.containsKey("offset")) {
      validateOffset(asObject(diagnostic.get("offset"), "offset"));
    }
    Map<String, Object> fields = asObject(diagnostic.get("fields"), "fields");
    fields.values().forEach(value -> assertInstanceOf(String.class, value));
    optionalString(diagnostic, "expected");
    optionalString(diagnostic, "actual");
    asArray(diagnostic.get("relatedLocations"), "relatedLocations")
        .forEach(value -> validateRelated(asObject(value, "related location")));
    asArray(diagnostic.get("fixes"), "fixes")
        .forEach(value -> assertInstanceOf(String.class, value));
    if (diagnostic.containsKey("resourceLimit")) {
      Map<String, Object> limit = asObject(diagnostic.get("resourceLimit"), "resourceLimit");
      validateKeyOrder(
          limit, List.of("name", "limit", "observed"), Set.of("name", "limit", "observed"));
      requireString(limit, "name");
      requireString(limit, "limit");
      requireString(limit, "observed");
    }
  }

  private static void validateSpan(Map<String, Object> span) {
    validateKeyOrder(
        span,
        List.of("start", "endInclusive", "utf8Start", "utf8EndExclusive"),
        Set.of("start", "endInclusive", "utf8Start", "utf8EndExclusive"));
    validateLineColumn(asObject(span.get("start"), "start"));
    validateLineColumn(asObject(span.get("endInclusive"), "endInclusive"));
    assertTrue(asNumber(span.get("utf8Start"), "utf8Start").longValueExact() >= 0);
    assertTrue(asNumber(span.get("utf8EndExclusive"), "utf8EndExclusive").longValueExact() >= 0);
  }

  private static void validatePoint(Map<String, Object> point) {
    validateKeyOrder(
        point, List.of("line", "column", "utf8Offset"), Set.of("line", "column", "utf8Offset"));
    positiveInteger(point.get("line"), "line");
    positiveInteger(point.get("column"), "column");
    assertTrue(asNumber(point.get("utf8Offset"), "utf8Offset").longValueExact() >= 0);
  }

  private static void validateOffset(Map<String, Object> offset) {
    validateKeyOrder(offset, List.of("utf8Offset"), Set.of("utf8Offset"));
    assertTrue(asNumber(offset.get("utf8Offset"), "utf8Offset").longValueExact() >= 0);
  }

  private static void validateLineColumn(Map<String, Object> position) {
    validateKeyOrder(position, List.of("line", "column"), Set.of("line", "column"));
    positiveInteger(position.get("line"), "line");
    positiveInteger(position.get("column"), "column");
  }

  private static void validateRelated(Map<String, Object> related) {
    if (related.containsKey("sourcePath")) {
      validateKeyOrder(
          related,
          List.of("sourcePath", "point", "description"),
          Set.of("sourcePath", "point", "description"));
      requireString(related, "sourcePath");
      validatePoint(asObject(related.get("point"), "related point"));
    } else {
      validateKeyOrder(related, List.of("description"), Set.of("description"));
    }
    requireString(related, "description");
  }

  private static void validateProblem(Map<String, Object> problem) {
    validateKeyOrder(problem, List.of("kind", "message"), Set.of("kind", "message"));
    assertTrue(
        Set.of("usage", "io", "internal", "outputLimit").contains(requireString(problem, "kind")));
    requireString(problem, "message");
  }

  private static void validateKeyOrder(
      Map<String, Object> object, List<String> allowed, Set<String> required) {
    assertTrue(object.keySet().containsAll(required), "missing keys: " + required);
    int previous = -1;
    for (String key : object.keySet()) {
      int current = allowed.indexOf(key);
      assertTrue(current >= 0, "unknown key: " + key);
      assertTrue(current > previous, "key order: " + object.keySet());
      previous = current;
    }
  }

  private static String requireString(Map<String, Object> object, String key) {
    return assertInstanceOf(String.class, object.get(key), key);
  }

  private static void optionalString(Map<String, Object> object, String key) {
    if (object.containsKey(key)) {
      requireString(object, key);
    }
  }

  private static int positiveInteger(Object value, String label) {
    int result = asNumber(value, label).intValueExact();
    assertTrue(result >= 1, label);
    return result;
  }

  private static boolean asBoolean(Object value, String label) {
    return assertInstanceOf(Boolean.class, value, label);
  }

  private static BigDecimal asNumber(Object value, String label) {
    return assertInstanceOf(BigDecimal.class, value, label);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value, String label) {
    assertInstanceOf(Map.class, value, label);
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asArray(Object value, String label) {
    assertInstanceOf(List.class, value, label);
    return (List<Object>) value;
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
