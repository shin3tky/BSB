package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class NumericExtendedMathRuntimeTest {
  @Test
  void runsSignClampGcdLcmAndExactIntegerPowers() {
    String source =
        "メインとは （--）\n"
            + "    -42 を 符号を得る を 一行表示する\n"
            + "    0.0 を 符号を得る を 一行表示する\n"
            + "    3.5 を 符号を得る を 一行表示する\n"
            + "    -5 と 0 と 10 を 範囲内に収める を 一行表示する\n"
            + "    5.5 と 1.0 と 4.0 を 範囲内に収める を 一行表示する\n"
            + "    -48 と 18 を 最大公約数 を 一行表示する\n"
            + "    0 と 0 を 最大公約数 を 一行表示する\n"
            + "    -12 と 18 を 最小公倍数 を 一行表示する\n"
            + "    0 と 99 を 最小公倍数 を 一行表示する\n"
            + "    -2 と 3 で 整数乗する を 一行表示する\n"
            + "    0 と 0 で 整数乗する を 一行表示する\n"
            + "    1.5 と 3 で 整数乗する を 一行表示する\n"
            + "こと。\n";

    var output = new MemoryOutputSink();
    ProgramRunResult result = run("extended-math.bsb", source, output);

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("-1\n0\n1\n0\n4.0\n6\n0\n36\n0\n-8\n1\n3.375\n", output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
  }

  @Test
  void invalidClampRangeKeepsAllInputs() {
    ProgramRunResult result = failingRun("5 と 10 と 0 を 範囲内に収める");
    Diagnostic diagnostic = result.diagnostics().getFirst();

    assertEquals(DiagnosticCode.E_NUMERIC_RANGE_INVALID, diagnostic.code());
    assertEquals("10", diagnostic.fields().get("lowerPreview"));
    assertEquals("0", diagnostic.fields().get("upperPreview"));
    assertEquals("10 > 0", diagnostic.actual().orElseThrow());
    assertEquals(List.of("下限と上限を入れ替えるか、同じ値にしてください"), diagnostic.fixes());
    assertEquals(3, result.finalDataStack().size());
  }

  @Test
  void negativeExponentKeepsBaseAndExponent() {
    ProgramRunResult result = failingRun("2 と -1 で 整数乗する");
    Diagnostic diagnostic = result.diagnostics().getFirst();

    assertEquals(DiagnosticCode.E_NEGATIVE_EXPONENT, diagnostic.code());
    assertEquals("-1", diagnostic.fields().get("exponentPreview"));
    assertEquals(List.of("指数を0以上にしてください"), diagnostic.fixes());
    assertEquals(2, result.finalDataStack().size());
  }

  @Test
  void powersRespectIntegerAndDecimalResultLimitsWithoutConsumingInputs() {
    ProgramRunResult integer = failingRun("10 と 65536 で 整数乗する");
    assertEquals(DiagnosticCode.E_INTEGER_RESULT_LIMIT, integer.diagnostics().getFirst().code());
    assertEquals(2, integer.finalDataStack().size());

    ProgramRunResult decimal = failingRun("0.1 と 65537 で 整数乗する");
    assertEquals(DiagnosticCode.E_DECIMAL_SCALE_LIMIT, decimal.diagnostics().getFirst().code());
    assertEquals(2, decimal.finalDataStack().size());
  }

  @Test
  void testsParityForPositiveNegativeAndZeroIntegers() {
    String source =
        "メインとは （--）\n"
            + "    0 を 偶数である を 一行表示する\n"
            + "    -2 を 偶数である を 一行表示する\n"
            + "    3 を 偶数である を 一行表示する\n"
            + "    0 を 奇数である を 一行表示する\n"
            + "    -3 を 奇数である を 一行表示する\n"
            + "    2 を 奇数である を 一行表示する\n"
            + "こと。\n";

    var output = new MemoryOutputSink();
    ProgramRunResult result = run("parity.bsb", source, output);

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("はい\nはい\nいいえ\nいいえ\nはい\nいいえ\n", output.utf8Text());
  }

  @Test
  void roundsToDecimalPlacesAndSignificantDigitsWithExistingModes() {
    String source =
        "メインとは （--）\n"
            + "    1.245 と 2 と 最近接偶数丸め で 小数桁で丸める を 一行表示する\n"
            + "    1.245 と 2 と 四捨五入 で 少数桁で丸める を 一行表示する\n"
            + "    -1.239 と 2 と 0方向へ丸め で 小数桁で丸める を 一行表示する\n"
            + "    1.231 と 2 と 正方向へ丸め で 小数桁で丸める を 一行表示する\n"
            + "    -1.231 と 2 と 負方向へ丸め で 小数桁で丸める を 一行表示する\n"
            + "    12345.0 と 3 と 最近接偶数丸め で 有効桁で丸める を 一行表示する\n"
            + "    0.001255 と 3 と 最近接偶数丸め で 有効桁で丸める を 一行表示する\n"
            + "こと。\n";

    var output = new MemoryOutputSink();
    ProgramRunResult result = run("rounding.bsb", source, output);

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("1.24\n1.25\n-1.23\n1.24\n-1.24\n12300.0\n0.00126\n", output.utf8Text());
  }

  @Test
  void invalidRoundingDigitsKeepAllInputs() {
    ProgramRunResult decimalPlaces = failingRun("1.23 と -1 と 最近接偶数丸め で 小数桁で丸める");
    Diagnostic decimalDiagnostic = decimalPlaces.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_ROUNDING_DIGITS_OUT_OF_RANGE, decimalDiagnostic.code());
    assertEquals("0", decimalDiagnostic.fields().get("minimum"));
    assertEquals("-1", decimalDiagnostic.fields().get("digits"));
    assertEquals(3, decimalPlaces.finalDataStack().size());

    ProgramRunResult significant = failingRun("1.23 と 0 と 最近接偶数丸め で 有効桁で丸める");
    Diagnostic significantDiagnostic = significant.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_ROUNDING_DIGITS_OUT_OF_RANGE, significantDiagnostic.code());
    assertEquals("1", significantDiagnostic.fields().get("minimum"));
    assertEquals("0", significantDiagnostic.fields().get("digits"));
    assertEquals(3, significant.finalDataStack().size());
  }

  private static ProgramRunResult failingRun(String expression) {
    return run(
        "extended-math-failure.bsb",
        "メインとは （--）\n    " + expression + " を 一行表示する\nこと。\n",
        new MemoryOutputSink());
  }

  private static ProgramRunResult run(String path, String source, MemoryOutputSink output) {
    return new ProgramRunner()
        .run(
            path,
            source.getBytes(StandardCharsets.UTF_8),
            new ExecutionContext(output, () -> 0L, TraceSink.none()));
  }
}
