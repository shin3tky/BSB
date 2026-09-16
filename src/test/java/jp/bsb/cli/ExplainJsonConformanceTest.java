package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.format.FormatResult;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ProgramRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExplainJsonConformanceTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void preservesEveryIndependentExpectedDocumentAndAppendsLaterFeatureWords(
      ExplainJsonConformanceData.CaseSpec spec) throws IOException {
    Invocation invocation = invoke(spec);

    assertEquals(spec.exitCode(), invocation.exitCode(), spec.id());
    assertEquals(0, invocation.stderr().length, spec.id() + ": stderr");
    assertPhysicalJson(invocation.stdout(), spec.id());
    Map<String, Object> expected =
        withEmptyWorkspaceDeclarations(asObject(parse(spec.expectedBytes()), "expected document"));
    Map<String, Object> actual = asObject(parse(invocation.stdout()), "actual document");
    validateDocument(expected, 125);
    validateDocument(actual, 189);
    if (spec.exitCode() == 0) {
      List<Object> expectedBuiltins = asArray(expected.get("builtinWords"), "expected builtins");
      List<Object> actualBuiltins = asArray(actual.get("builtinWords"), "actual builtins");
      assertEquals(
          expectedBuiltins, actualBuiltins.subList(0, 125), spec.id() + ": builtin prefix");
    }
    assertEquals(
        withoutBuiltinWords(expected), withoutBuiltinWords(actual), spec.id() + ": stable fields");
    String output = new String(invocation.stdout(), StandardCharsets.UTF_8);
    assertFalse(output.contains("Exception"), spec.id() + ": host exception type");
    assertFalse(output.contains("java."), spec.id() + ": internal Java name");
    assertFalse(output.contains("/Users/"), spec.id() + ": unexpected absolute path");
  }

  @Test
  void strictValidatorRejectsMissingUnknownAndMisorderedMembers() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> StrictJsonParser.parse("{\"a\":1,\"a\":2}"));
    org.junit.jupiter.api.Assertions.assertThrows(
        AssertionError.class,
        () -> validateDocument(asObject(StrictJsonParser.parse("{}"), "document")));
    org.junit.jupiter.api.Assertions.assertThrows(
        AssertionError.class,
        () ->
            validateDocument(
                asObject(
                    StrictJsonParser.parse(
                        "{\"command\":\"explain\",\"schemaVersion\":1,\"success\":false,"
                            + "\"exitCode\":2,\"diagnostics\":[],\"builtinWords\":[],"
                            + "\"userWords\":[],\"scopes\":[],\"bindings\":[],"
                            + "\"problem\":{\"kind\":\"usage\",\"message\":\"x\"}}"),
                    "document")));
  }

  static Stream<ExplainJsonConformanceData.CaseSpec> cases() throws IOException {
    return ExplainJsonConformanceData.load().stream();
  }

  private static Invocation invoke(ExplainJsonConformanceData.CaseSpec spec) throws IOException {
    return switch (spec.kind()) {
      case "file", "missing" -> invoke(new BsbCli(), spec.source());
      case "usage" -> invoke(new BsbCli(), null);
      case "synthetic-internal" ->
          invoke(
              new BsbCli(throwingBackend(), DiagnosticMessageCatalog.loadDefault()), spec.source());
      case "synthetic-output-limit" -> {
        byte[] source = "メインとは （--）\nこと。\n".getBytes(StandardCharsets.UTF_8);
        AnalysisResult result = new SourceChecker().check(spec.source(), source);
        BsbCli cli =
            new BsbCli(
                backendReturning(result),
                DiagnosticMessageCatalog.loadDefault(),
                new CliJsonRenderer(),
                new ExplainJsonRenderer(300));
        yield invoke(cli, spec.source());
      }
      default -> throw new IllegalStateException("unknown case kind: " + spec.kind());
    };
  }

  private static Invocation invoke(BsbCli cli, String source) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    String[] arguments =
        source == null
            ? new String[] {"explain", "--json"}
            : new String[] {"explain", "--json", source};
    int exitCode = cli.run(arguments, stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
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

  private static void validateDocument(Map<String, Object> document) {
    validateDocument(document, 189);
  }

  private static void validateDocument(Map<String, Object> document, int successfulBuiltinCount) {
    validateKeyOrder(
        document,
        List.of(
            "schemaVersion",
            "command",
            "source",
            "success",
            "exitCode",
            "diagnostics",
            "summary",
            "builtinWords",
            "userWords",
            "scopes",
            "bindings",
            "parameterizedCapabilities",
            "problem"),
        Set.of(
            "schemaVersion",
            "command",
            "success",
            "exitCode",
            "diagnostics",
            "builtinWords",
            "userWords",
            "scopes",
            "bindings"));
    assertEquals(new BigDecimal("1"), document.get("schemaVersion"));
    assertEquals("explain", document.get("command"));
    optionalString(document, "source");
    boolean success = asBoolean(document.get("success"), "success");
    int exitCode = asNumber(document.get("exitCode"), "exitCode").intValueExact();
    assertEquals(exitCode == 0, success);
    List<Object> diagnostics = asArray(document.get("diagnostics"), "diagnostics");
    diagnostics.forEach(value -> validateDiagnostic(asObject(value, "diagnostic")));
    List<Object> builtins = asArray(document.get("builtinWords"), "builtinWords");
    List<Object> users = asArray(document.get("userWords"), "userWords");
    List<Object> scopes = asArray(document.get("scopes"), "scopes");
    List<Object> bindings = asArray(document.get("bindings"), "bindings");
    if (success) {
      validateSummary(asObject(document.get("summary"), "summary"));
      assertEquals(successfulBuiltinCount, builtins.size());
      builtins.forEach(value -> validateBuiltin(asObject(value, "builtin word")));
      users.forEach(value -> validateUserWord(asObject(value, "user word")));
      scopes.forEach(value -> validateScope(asObject(value, "scope")));
      bindings.forEach(value -> validateBinding(asObject(value, "binding")));
      validateParameterizedCapabilities(
          asObject(document.get("parameterizedCapabilities"), "parameterizedCapabilities"));
      assertFalse(document.containsKey("problem"));
    } else {
      assertFalse(document.containsKey("summary"));
      assertTrue(builtins.isEmpty());
      assertTrue(users.isEmpty());
      assertTrue(scopes.isEmpty());
      assertTrue(bindings.isEmpty());
      assertFalse(document.containsKey("parameterizedCapabilities"));
      if (document.containsKey("problem")) {
        assertTrue(diagnostics.isEmpty());
        validateProblem(asObject(document.get("problem"), "problem"));
        assertTrue(exitCode == 2 || exitCode == 3 || exitCode == 70);
      } else {
        assertTrue(exitCode == 8 || exitCode == 9);
        assertFalse(diagnostics.isEmpty());
      }
    }
  }

  private static Map<String, Object> withoutBuiltinWords(Map<String, Object> document) {
    var copy = new LinkedHashMap<>(document);
    copy.remove("builtinWords");
    return copy;
  }

  private static Map<String, Object> withEmptyWorkspaceDeclarations(Map<String, Object> document) {
    Object capabilities = document.get("parameterizedCapabilities");
    if (!(capabilities instanceof Map<?, ?> raw) || raw.containsKey("workspaceDeclarations")) {
      return document;
    }
    var parameterized = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      String key = (String) entry.getKey();
      parameterized.put(key, entry.getValue());
      if (key.equals("declarations")) parameterized.put("workspaceDeclarations", List.of());
    }
    var copy = new LinkedHashMap<>(document);
    copy.put("parameterizedCapabilities", parameterized);
    return copy;
  }

  private static void validateParameterizedCapabilities(Map<String, Object> value) {
    validateKeyOrder(
        value,
        List.of(
            "schemaVersion",
            "declarations",
            "workspaceDeclarations",
            "summaryRequirements",
            "userWords"),
        Set.of(
            "schemaVersion",
            "declarations",
            "workspaceDeclarations",
            "summaryRequirements",
            "userWords"));
    assertEquals(new BigDecimal("1"), value.get("schemaVersion"));
    asArray(value.get("declarations"), "connection declarations")
        .forEach(
            item -> {
              Map<String, Object> declaration = asObject(item, "connection declaration");
              validateKeyOrder(
                  declaration,
                  List.of("name", "spelling", "declaration", "uses"),
                  Set.of("name", "spelling", "declaration", "uses"));
              requireString(declaration, "name");
              requireString(declaration, "spelling");
              validateLocatedSpan(asObject(declaration.get("declaration"), "declaration"));
              asArray(declaration.get("uses"), "connection uses")
                  .forEach(use -> validateConnectionUse(asObject(use, "connection use")));
            });
    asArray(value.get("workspaceDeclarations"), "workspace declarations")
        .forEach(
            item -> {
              Map<String, Object> declaration = asObject(item, "workspace declaration");
              validateKeyOrder(
                  declaration,
                  List.of("name", "spelling", "declaration", "uses"),
                  Set.of("name", "spelling", "declaration", "uses"));
              requireString(declaration, "name");
              requireString(declaration, "spelling");
              validateLocatedSpan(asObject(declaration.get("declaration"), "declaration"));
              asArray(declaration.get("uses"), "workspace uses")
                  .forEach(use -> validateWorkspaceUse(asObject(use, "workspace use")));
            });
    asArray(value.get("summaryRequirements"), "summary requirements")
        .forEach(item -> validateRequirement(asObject(item, "summary requirement")));
    asArray(value.get("userWords"), "parameterized user words")
        .forEach(
            item -> {
              Map<String, Object> word = asObject(item, "parameterized user word");
              validateKeyOrder(
                  word,
                  List.of("name", "directRequirements", "requirements"),
                  Set.of("name", "directRequirements", "requirements"));
              requireString(word, "name");
              asArray(word.get("directRequirements"), "direct requirements")
                  .forEach(
                      requirement ->
                          validateRequirement(asObject(requirement, "direct requirement")));
              asArray(word.get("requirements"), "requirements")
                  .forEach(
                      requirement -> validateRequirement(asObject(requirement, "requirement")));
            });
  }

  private static void validateConnectionUse(Map<String, Object> use) {
    validateKeyOrder(
        use,
        List.of("ownerWord", "operation", "reachableFromMain", "location"),
        Set.of("ownerWord", "operation", "reachableFromMain", "location"));
    requireString(use, "ownerWord");
    assertEquals("resolve", requireString(use, "operation"));
    asBoolean(use.get("reachableFromMain"), "reachableFromMain");
    validateLocatedSpan(asObject(use.get("location"), "location"));
  }

  private static void validateWorkspaceUse(Map<String, Object> use) {
    validateKeyOrder(
        use,
        List.of("ownerWord", "operation", "reachableFromMain", "location"),
        Set.of("ownerWord", "operation", "reachableFromMain", "location"));
    requireString(use, "ownerWord");
    String operation = requireString(use, "operation");
    assertTrue(operation.equals("read") || operation.equals("write"));
    assertTrue(use.get("reachableFromMain") instanceof Boolean);
    validateLocatedSpan(asObject(use.get("location"), "workspace use location"));
  }

  private static void validateRequirement(Map<String, Object> requirement) {
    validateKeyOrder(
        requirement, List.of("kind", "name", "operations"), Set.of("kind", "name", "operations"));
    assertEquals("logicalConnection", requireString(requirement, "kind"));
    requireString(requirement, "name");
    validateStrings(requirement.get("operations"), "operations");
  }

  private static void validateSummary(Map<String, Object> summary) {
    validateKeyOrder(
        summary,
        List.of(
            "entryPoint", "reachableUserWords", "reachableBuiltinWords", "capabilities", "effects"),
        Set.of(
            "entryPoint",
            "reachableUserWords",
            "reachableBuiltinWords",
            "capabilities",
            "effects"));
    assertEquals("メイン", requireString(summary, "entryPoint"));
    validateStrings(summary.get("reachableUserWords"), "reachableUserWords");
    validateStrings(summary.get("reachableBuiltinWords"), "reachableBuiltinWords");
    validateStrings(summary.get("capabilities"), "capabilities");
    validateStrings(summary.get("effects"), "effects");
  }

  private static void validateBuiltin(Map<String, Object> word) {
    validateKeyOrder(
        word,
        List.of(
            "name",
            "aliases",
            "description",
            "stackEffect",
            "typeRule",
            "capabilities",
            "effects",
            "featureGroup",
            "example"),
        Set.of(
            "name",
            "aliases",
            "description",
            "stackEffect",
            "typeRule",
            "capabilities",
            "effects",
            "featureGroup",
            "example"));
    requireString(word, "name");
    validateStrings(word.get("aliases"), "aliases");
    requireString(word, "description");
    validateStackEffect(asObject(word.get("stackEffect"), "stackEffect"));
    requireString(word, "typeRule");
    validateStrings(word.get("capabilities"), "capabilities");
    validateStrings(word.get("effects"), "effects");
    requireString(word, "featureGroup");
    requireString(word, "example");
  }

  private static void validateUserWord(Map<String, Object> word) {
    validateKeyOrder(
        word,
        List.of(
            "name",
            "spelling",
            "declaration",
            "stackEffect",
            "reachableFromMain",
            "directCapabilities",
            "capabilities",
            "directEffects",
            "effects"),
        Set.of(
            "name",
            "spelling",
            "declaration",
            "stackEffect",
            "reachableFromMain",
            "directCapabilities",
            "capabilities",
            "directEffects",
            "effects"));
    requireString(word, "name");
    requireString(word, "spelling");
    validateLocatedSpan(asObject(word.get("declaration"), "declaration"));
    validateStackEffect(asObject(word.get("stackEffect"), "stackEffect"));
    asBoolean(word.get("reachableFromMain"), "reachableFromMain");
    validateStrings(word.get("directCapabilities"), "directCapabilities");
    validateStrings(word.get("capabilities"), "capabilities");
    validateStrings(word.get("directEffects"), "directEffects");
    validateStrings(word.get("effects"), "effects");
  }

  private static void validateScope(Map<String, Object> scope) {
    validateKeyOrder(
        scope,
        List.of("id", "kind", "parentId", "ownerWord", "location"),
        Set.of("id", "kind", "location"));
    requireString(scope, "id");
    requireString(scope, "kind");
    optionalString(scope, "parentId");
    optionalString(scope, "ownerWord");
    validateLocatedSpan(asObject(scope.get("location"), "location"));
  }

  private static void validateBinding(Map<String, Object> binding) {
    validateKeyOrder(
        binding,
        List.of(
            "id",
            "name",
            "spelling",
            "kind",
            "storage",
            "scopeId",
            "type",
            "initializationOrder",
            "declaration",
            "uses"),
        Set.of(
            "id", "name", "spelling", "kind", "storage", "scopeId", "type", "declaration", "uses"));
    requireString(binding, "id");
    requireString(binding, "name");
    requireString(binding, "spelling");
    requireString(binding, "kind");
    requireString(binding, "storage");
    requireString(binding, "scopeId");
    requireString(binding, "type");
    if (binding.containsKey("initializationOrder")) {
      positiveInteger(binding.get("initializationOrder"), "initializationOrder");
    }
    validateLocatedSpan(asObject(binding.get("declaration"), "declaration"));
    asArray(binding.get("uses"), "uses")
        .forEach(value -> validateUse(asObject(value, "binding use")));
  }

  private static void validateUse(Map<String, Object> use) {
    validateKeyOrder(
        use, List.of("spelling", "kind", "location"), Set.of("spelling", "kind", "location"));
    requireString(use, "spelling");
    assertTrue(Set.of("read", "write").contains(requireString(use, "kind")));
    validateLocatedSpan(asObject(use.get("location"), "location"));
  }

  private static void validateStackEffect(Map<String, Object> effect) {
    validateKeyOrder(
        effect,
        List.of("inputs", "outputs", "returnsNormally"),
        Set.of("inputs", "outputs", "returnsNormally"));
    validateStrings(effect.get("inputs"), "inputs");
    validateStrings(effect.get("outputs"), "outputs");
    asBoolean(effect.get("returnsNormally"), "returnsNormally");
  }

  private static void validateLocatedSpan(Map<String, Object> location) {
    validateKeyOrder(location, List.of("span"), Set.of("span"));
    validateSpan(asObject(location.get("span"), "span"));
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
    requireString(diagnostic, "severity");
    requireString(diagnostic, "stage");
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
    asObject(diagnostic.get("fields"), "fields")
        .values()
        .forEach(value -> assertInstanceOf(String.class, value));
    optionalString(diagnostic, "expected");
    optionalString(diagnostic, "actual");
    asArray(diagnostic.get("relatedLocations"), "relatedLocations")
        .forEach(value -> validateRelated(asObject(value, "related location")));
    validateStrings(diagnostic.get("fixes"), "fixes");
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

  private static void validateStrings(Object value, String label) {
    asArray(value, label).forEach(element -> assertInstanceOf(String.class, element, label));
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
