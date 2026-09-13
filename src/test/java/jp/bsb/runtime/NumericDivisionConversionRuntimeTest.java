package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class NumericDivisionConversionRuntimeTest {
  @Test
  void runsNormativeDivisionRoundingConversionAndRoundingModeCases() throws Exception {
    assertRun("NUM-N007", "3.333333333333333333333333333\n0.75\n1.5\n");
    assertRun("NUM-N008", "0.12\n0.13\n");
    assertRun("NUM-N009", "2\n4\n-3\n-2\n-2\n-3\n");
    assertRun("NUM-N012", "42.0\n42\n");
    assertRun("NUM-N013", "最近接偶数丸め\nいいえ\n");
  }

  @Test
  void reportsNormativeDivisionFailuresInPriorityOrderWithoutConsumingInputs() throws Exception {
    assertFailure(
        "NUM-F013",
        DiagnosticCode.E_DIVISION_BY_ZERO,
        Map.of("word", "小数で割る", "dividendPreview", "1.0", "divisorPreview", "0.0"),
        "0でない数値除数",
        "0.0",
        "除数を0以外にしてください",
        List.of("1.0", "0.0"));
    assertFailure(
        "NUM-F014",
        DiagnosticCode.E_DIVISION_BY_ZERO,
        Map.of(
            "word",
            "精度指定で割る",
            "dividendPreview",
            "1",
            "divisorPreview",
            "0",
            "precision",
            "0",
            "rounding",
            "最近接偶数丸め"),
        "0でない数値除数",
        "0",
        "除数を0以外にしてください",
        List.of("1", "0", "0", "最近接偶数丸め"));
    for (String caseId : List.of("NUM-F015", "NUM-F016")) {
      String precision = caseId.equals("NUM-F015") ? "0" : "4097";
      assertFailure(
          caseId,
          DiagnosticCode.E_DIVISION_PRECISION_OUT_OF_RANGE,
          Map.of(
              "dividendPreview",
              "1",
              "divisorPreview",
              "3",
              "precision",
              precision,
              "minimum",
              "1",
              "maximum",
              "4096"),
          "1以上4096以下",
          precision,
          "精度を1から4096にしてください",
          List.of("1", "3", precision, "最近接偶数丸め"));
    }
    assertFailure(
        "NUM-F017",
        DiagnosticCode.E_DECIMAL_NOT_INTEGER,
        Map.of("word", "整数に変換する", "valuePreview", "1.5"),
        "小数部が0の小数",
        "1.5",
        "丸め方法を明示して「整数に丸める」を使用してください",
        List.of("1.5"));
  }

  @Test
  void preservesOutputAndInputsForTheNormativeAtomicDivisionFailure() throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run("NUM-F019", output);

    assertEquals(10, result.exitCode());
    assertEquals(DiagnosticCode.E_DIVISION_BY_ZERO, result.diagnostics().getFirst().code());
    assertEquals("前\n", output.utf8Text());
    assertEquals(List.of("1", "0"), displayStack(result));
  }

  private static void assertFailure(
      String caseId,
      DiagnosticCode code,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      List<String> expectedStack)
      throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(caseId, output);
    Diagnostic diagnostic = result.diagnostics().getFirst();

    assertEquals(10, result.exitCode(), caseId);
    assertEquals(code, diagnostic.code(), caseId);
    fields.forEach(
        (name, value) -> assertEquals(value, diagnostic.fields().get(name), caseId + ": " + name));
    assertEquals(expected, diagnostic.expected().orElseThrow(), caseId);
    assertEquals(actual, diagnostic.actual().orElseThrow(), caseId);
    assertEquals(List.of(fix), diagnostic.fixes(), caseId);
    assertEquals(expectedStack, displayStack(result), caseId);
    assertEquals("", output.utf8Text(), caseId);
  }

  private static void assertRun(String caseId, String expectedOutput) throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(caseId, output);

    assertTrue(result.successful(), caseId + ": " + result.diagnostics());
    assertEquals(expectedOutput, output.utf8Text(), caseId);
    assertTrue(result.finalDataStack().isEmpty(), caseId);
  }

  private static List<String> displayStack(ProgramRunResult result) {
    return result.finalDataStack().stream().map(RuntimeValue::displayText).toList();
  }

  private static ProgramRunResult run(String caseId, MemoryOutputSink output) throws Exception {
    return new ProgramRunner()
        .run(
            caseId + ".bsb",
            numericsSource(caseId),
            new ExecutionContext(output, () -> 0L, TraceSink.none()));
  }

  private static byte[] numericsSource(String caseId) throws Exception {
    String resource = "/conformance/numerics/sources/" + caseId + ".bsb";
    try (var input = NumericDivisionConversionRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
