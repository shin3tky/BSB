package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExponentDiagnosticConformanceTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("diagnostics")
  void matchesEveryIndependentDiagnosticRow(ExponentConformanceData.DiagnosticSpec spec)
      throws Exception {
    byte[] source = sourceFor(spec.id());
    Diagnostic actual;
    int exit;
    if (spec.command().equals("run")) {
      var output = new MemoryOutputSink();
      var result =
          new ProgramRunner()
              .run(
                  spec.id() + ".bsb",
                  source,
                  new ExecutionContext(output, () -> 0L, TraceSink.none()));
      exit = result.exitCode();
      actual = result.diagnostics().getFirst();
      assertEquals(0, output.bytes().length, spec.id());
    } else {
      var result = new SourceChecker().check(spec.id() + ".bsb", source);
      exit = result.exitCode();
      actual = result.diagnostics().getFirst();
    }

    assertEquals(
        spec.id().equals("EXP-F011")
            ? 10
            : spec.id().equals("EXP-F012") ? 8 : spec.id().equals("EXP-F007") ? 8 : 9,
        exit,
        spec.id());
    assertEquals(spec.code(), actual.code().name(), spec.id());
    assertEquals(
        spec.severity(), actual.severity().name().toLowerCase(java.util.Locale.ROOT), spec.id());
    assertEquals(spec.stage(), stageName(actual), spec.id());
    var position = actual.location().displayPosition().orElseThrow();
    assertEquals(spec.line(), position.line(), spec.id());
    assertEquals(spec.column(), position.column(), spec.id());

    var expectedFields = new LinkedHashMap<>(spec.fields());
    String limit = expectedFields.remove("limit");
    String observed = expectedFields.remove("observed");
    if (spec.stage().equals("runtime")) {
      expectedFields.forEach(
          (key, value) -> assertEquals(value, actual.fields().get(key), spec.id() + '/' + key));
      assertEquals("メイン", actual.fields().get("currentWord"), spec.id());
      assertEquals("[文字列:1e]", actual.fields().get("dataStack"), spec.id());
      assertEquals("メイン", actual.fields().get("callStack"), spec.id());
    } else {
      assertEquals(expectedFields, actual.fields(), spec.id());
    }
    if (limit != null) {
      assertEquals(limit, actual.limit().orElseThrow(), spec.id());
    }
    if (observed != null) {
      assertEquals(observed, actual.observed().orElseThrow(), spec.id());
    }
    assertEquals(spec.expected(), actual.expected().orElseThrow(), spec.id());
    assertEquals(spec.actual(), actual.actual().orElseThrow(), spec.id());
    assertEquals(spec.fix(), actual.fixes().getFirst(), spec.id());
  }

  static Stream<ExponentConformanceData.DiagnosticSpec> diagnostics() throws Exception {
    return ExponentConformanceData.loadDiagnostics().stream();
  }

  private static byte[] sourceFor(String id) throws Exception {
    if (id.equals("EXP-F011")) {
      return ExponentConformanceData.resourceBytes("sources/EXP-F-string-invalid.bsb");
    }
    if (id.equals("EXP-F012")) {
      return ExponentConformanceData.resourceBytes("sources/EXP-F-source-scale.bsb");
    }
    Map<String, ExponentConformanceData.LiteralSpec> firstById =
        ExponentConformanceData.loadLiterals().stream()
            .collect(
                Collectors.toMap(
                    ExponentConformanceData.LiteralSpec::id,
                    Function.identity(),
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    String input = firstById.get(id).input();
    return ("メインとは （--）\n    " + input + " を 一行表示する\nこと。\n").getBytes(StandardCharsets.UTF_8);
  }

  private static String stageName(Diagnostic diagnostic) {
    return switch (diagnostic.stage()) {
      case UTF8 -> "utf8";
      case LEXICAL -> "lexical";
      case SYNTAX -> "syntax";
      case NAME -> "name";
      case TYPE_AND_STACK -> "typeAndStack";
      case IR -> "ir";
      case RUNTIME -> "runtime";
    };
  }
}
