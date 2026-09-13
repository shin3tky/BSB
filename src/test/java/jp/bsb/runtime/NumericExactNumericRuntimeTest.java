package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class NumericExactNumericRuntimeTest {
  @Test
  void runsExactArithmeticComparisonAbsoluteAndExtrema() throws Exception {
    assertRun("NUM-N002", "0.3\n3.25\n3.6\n");
    assertRun("NUM-N003", "はい\nはい\nいいえ\n");
    assertRun("NUM-N010", "42\n1.25\n");
    assertRun("NUM-N011", "-2\n2.5\n");
    assertRun("NUM-N016", "2.5\n");
  }

  @Test
  void runsAllFloorDivisionSignsAndPreservesThePairStackOrder() throws Exception {
    assertRun("NUM-N004", "3\n-4\n-4\n3\n");
    assertRun("NUM-N005", "1\n2\n-2\n-1\n");
    assertRun("NUM-N006", "1\n3\n");
  }

  @Test
  void reportsAllThreeNormativeIntegerDivisionByZeroCasesWithoutConsumingInputs() throws Exception {
    for (String caseId : List.of("NUM-F010", "NUM-F011", "NUM-F012")) {
      var output = new MemoryOutputSink();
      ProgramRunResult result = run(caseId, output);
      Diagnostic diagnostic = result.diagnostics().getFirst();
      String word =
          switch (caseId) {
            case "NUM-F010" -> "割った商";
            case "NUM-F011" -> "割った剰余";
            case "NUM-F012" -> "割った商と剰余";
            default -> throw new AssertionError(caseId);
          };

      assertEquals(10, result.exitCode(), caseId);
      assertEquals(DiagnosticCode.E_DIVISION_BY_ZERO, diagnostic.code(), caseId);
      assertEquals(word, diagnostic.fields().get("word"), caseId);
      assertEquals("1", diagnostic.fields().get("dividendPreview"), caseId);
      assertEquals("0", diagnostic.fields().get("divisorPreview"), caseId);
      assertEquals("メイン", diagnostic.fields().get("currentWord"), caseId);
      assertEquals("0でない整数除数", diagnostic.expected().orElseThrow(), caseId);
      assertEquals("0", diagnostic.actual().orElseThrow(), caseId);
      assertEquals(List.of("除数を0以外にしてください"), diagnostic.fixes(), caseId);
      assertEquals(
          List.of(new IntegerValue(BigInteger.ONE), new IntegerValue(BigInteger.ZERO)),
          result.finalDataStack(),
          caseId);
      assertEquals("", output.utf8Text(), caseId);
    }
  }

  @Test
  void failureKeepsLowerStackValuesAndPriorOutputAndStorageChanges() {
    String source =
        "値は 変数 7。\n\n"
            + "失敗するとは （整数 -- 整数）\n"
            + "    1 と 0 を 割った商\n"
            + "    足す\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    「前」 を 一行表示する\n"
            + "    5 を 値 に 入れる\n"
            + "    99 を 失敗する\n"
            + "    一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "atomic-numeric.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertEquals(10, result.exitCode());
    assertEquals("前\n", output.utf8Text());
    assertEquals(
        List.of(
            new IntegerValue(BigInteger.valueOf(99)),
            new IntegerValue(BigInteger.ONE),
            new IntegerValue(BigInteger.ZERO)),
        result.finalDataStack());
    assertEquals(
        new IntegerValue(BigInteger.valueOf(5)),
        result.finalGlobalValues().getFirst().orElseThrow());
  }

  @Test
  void reportsTheNormativeDecimalScaleFailureBeforeChangingTheVariableOrStack() throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run("NUM-F018", output);
    Diagnostic diagnostic = result.diagnostics().getFirst();

    assertEquals(10, result.exitCode());
    assertEquals(DiagnosticCode.E_DECIMAL_SCALE_LIMIT, diagnostic.code());
    assertEquals("掛ける", diagnostic.fields().get("word"));
    assertEquals("numerics", diagnostic.fields().get("inputPreviewPolicy"));
    assertEquals("メイン", diagnostic.fields().get("currentWord"));
    assertEquals("decimalScale", diagnostic.limitName().orElseThrow());
    assertEquals("65536", diagnostic.limit().orElseThrow());
    assertEquals("81920", diagnostic.observed().orElseThrow());
    assertEquals("スケール絶対値65536以下", diagnostic.expected().orElseThrow());
    assertEquals("81920", diagnostic.actual().orElseThrow());
    assertEquals(List.of("入力の小数桁または演算回数を減らしてください"), diagnostic.fixes());
    assertEquals("", output.utf8Text());
    assertEquals(2, result.finalDataStack().size());
    assertTrue(
        result.finalDataStack().stream()
            .map(DecimalValue.class::cast)
            .allMatch(value -> value.scale() == 40_960));
    assertEquals(
        40_960, ((DecimalValue) result.finalGlobalValues().getFirst().orElseThrow()).scale());
  }

  private static void assertRun(String caseId, String expectedOutput) throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(caseId, output);

    assertTrue(result.successful(), caseId + ": " + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), caseId);
    assertEquals(expectedOutput, output.utf8Text(), caseId);
    assertTrue(result.finalDataStack().isEmpty(), caseId);
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
    try (var input = NumericExactNumericRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
