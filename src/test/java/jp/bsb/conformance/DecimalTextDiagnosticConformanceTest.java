package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DecimalTextDiagnosticConformanceTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("diagnostics")
  void matchesEveryIndependentRuntimeDiagnostic(DecimalTextConformanceData.DiagnosticSpec spec)
      throws Exception {
    String input = DecimalTextConformanceData.diagnosticInput(spec);
    String source = "メインとは （--）\n    「" + input + "」 を " + spec.word() + " を 一行表示する\nこと。\n";
    var output = new MemoryOutputSink();
    var result =
        new ProgramRunner()
            .run(
                spec.id() + ".bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertEquals(10, result.exitCode(), spec.id());
    assertEquals("", output.utf8Text(), spec.id());
    Diagnostic actual = result.diagnostics().getFirst();
    assertEquals(spec.code(), actual.code().name(), spec.id());
    assertEquals("RUNTIME", actual.stage().name(), spec.id());
    var position = actual.location().displayPosition().orElseThrow();
    assertEquals(spec.line(), position.line(), spec.id());
    assertEquals(spec.column(), position.column(), spec.id());
    spec.fields()
        .forEach(
            (key, value) ->
                assertEquals(value, actual.templateValues().get(key), spec.id() + '/' + key));
    assertEquals(spec.expected(), actual.expected().orElseThrow(), spec.id());
    assertEquals(spec.actual(), actual.actual().orElseThrow(), spec.id());
    assertEquals(spec.fix(), actual.fixes().getFirst(), spec.id());
    String preview = actual.fields().get("inputPreview");
    if (preview != null) {
      assertTrue(preview.codePointCount(0, preview.length()) <= 64, spec.id());
    }
    assertEquals("メイン", actual.fields().get("currentWord"), spec.id());
    assertEquals("メイン", actual.fields().get("callStack"), spec.id());
  }

  static java.util.stream.Stream<DecimalTextConformanceData.DiagnosticSpec> diagnostics()
      throws Exception {
    return DecimalTextConformanceData.loadDiagnostics().stream();
  }
}
