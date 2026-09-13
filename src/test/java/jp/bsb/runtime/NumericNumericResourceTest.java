package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class NumericNumericResourceTest {
  private static final String SOURCE_PATH = "numeric-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void acceptsAndRejectsTheDecimalResultPrecisionBoundaryAtomically() throws Exception {
    DecimalValue acceptedInput = decimalWithPrecision(RuntimeLimits.DECIMAL_PRECISION - 1);
    var accepted = stack(acceptedInput, new DecimalValue(BigInteger.valueOf(99), 0));
    execute("掛ける", accepted);

    assertEquals(1, accepted.size());
    assertEquals(RuntimeLimits.DECIMAL_PRECISION, ((DecimalValue) accepted.getFirst()).precision());

    DecimalValue rejectedInput = decimalWithPrecision(RuntimeLimits.DECIMAL_PRECISION);
    var rejected = stack(rejectedInput, new DecimalValue(BigInteger.valueOf(99), 0));
    List<RuntimeValue> before = List.copyOf(rejected);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("掛ける", rejected));
    Diagnostic diagnostic = failure.diagnostic();

    assertEquals(DiagnosticCode.E_DECIMAL_RESULT_PRECISION_LIMIT, diagnostic.code());
    assertEquals("decimalPrecision", diagnostic.limitName().orElseThrow());
    assertEquals("65536", diagnostic.limit().orElseThrow());
    assertEquals("65537", diagnostic.observed().orElseThrow());
    assertEquals(before, rejected);
  }

  @Test
  void acceptsAndRejectsTheNormalizedDecimalScaleBoundaryAtomically() throws Exception {
    var accepted =
        stack(new DecimalValue(BigInteger.ONE, 32_768), new DecimalValue(BigInteger.ONE, 32_768));
    execute("掛ける", accepted);

    assertEquals(
        RuntimeLimits.DECIMAL_ABSOLUTE_SCALE, ((DecimalValue) accepted.getFirst()).scale());

    var rejected =
        stack(new DecimalValue(BigInteger.ONE, 32_768), new DecimalValue(BigInteger.ONE, 32_769));
    List<RuntimeValue> before = List.copyOf(rejected);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("掛ける", rejected));
    Diagnostic diagnostic = failure.diagnostic();

    assertEquals(DiagnosticCode.E_DECIMAL_SCALE_LIMIT, diagnostic.code());
    assertEquals("decimalScale", diagnostic.limitName().orElseThrow());
    assertEquals("65537", diagnostic.observed().orElseThrow());
    assertEquals(before, rejected);
  }

  @Test
  void reportsPrecisionBeforeScaleWhenExactAdditionExceedsBoth() {
    var stack =
        stack(
            new DecimalValue(BigInteger.ONE, -RuntimeLimits.DECIMAL_ABSOLUTE_SCALE),
            new DecimalValue(BigInteger.ONE, RuntimeLimits.DECIMAL_ABSOLUTE_SCALE));
    List<RuntimeValue> before = List.copyOf(stack);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("足す", stack));

    assertEquals(DiagnosticCode.E_DECIMAL_RESULT_PRECISION_LIMIT, failure.diagnostic().code());
    assertEquals("131073", failure.diagnostic().observed().orElseThrow());
    assertEquals(before, stack);
  }

  @Test
  void boundsLargeNumericDiagnosticPreviewsDeterministically() {
    String sixtyFour = "1".repeat(64);
    String sixtyFive = "1".repeat(65);

    NumericPreview complete = NumericPreview.ofText(sixtyFour);
    NumericPreview truncated = NumericPreview.ofText(sixtyFive);

    assertEquals(sixtyFour, complete.text());
    assertFalse(complete.truncated());
    assertEquals("1".repeat(32) + "…(+17)" + "1".repeat(16), truncated.text());
    assertEquals(65, truncated.codePoints());
    assertTrue(truncated.truncated());
  }

  @Test
  void integratesBoundedPreviewsIntoDivisionByZeroDiagnosticsWithoutChangingTheStack() {
    BigInteger dividend = new BigInteger("1".repeat(65));
    var stack = stack(new IntegerValue(dividend), new IntegerValue(BigInteger.ZERO));
    List<RuntimeValue> before = List.copyOf(stack);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("割った商", stack));

    assertEquals(DiagnosticCode.E_DIVISION_BY_ZERO, failure.diagnostic().code());
    assertEquals(
        "1".repeat(32) + "…(+17)" + "1".repeat(16),
        failure.diagnostic().fields().get("dividendPreview"));
    assertEquals("65", failure.diagnostic().fields().get("dividendCodePoints"));
    assertEquals("0", failure.diagnostic().fields().get("divisorPreview"));
    assertEquals(before, stack);
  }

  @Test
  void floorDivisionMaintainsTheIdentityAndDivisorSignedRemainder() throws Exception {
    for (int dividend = -10; dividend <= 10; dividend++) {
      for (int divisor = -5; divisor <= 5; divisor++) {
        if (divisor == 0) {
          continue;
        }
        var stack =
            stack(
                new IntegerValue(BigInteger.valueOf(dividend)),
                new IntegerValue(BigInteger.valueOf(divisor)));
        execute("割った商と剰余", stack);
        BigInteger quotient = ((IntegerValue) stack.get(0)).value();
        BigInteger remainder = ((IntegerValue) stack.get(1)).value();

        assertEquals(
            BigInteger.valueOf(dividend),
            quotient.multiply(BigInteger.valueOf(divisor)).add(remainder),
            dividend + "/" + divisor);
        assertTrue(
            remainder.signum() == 0 || remainder.signum() == Integer.signum(divisor),
            dividend + "/" + divisor);
        assertTrue(
            remainder.abs().compareTo(BigInteger.valueOf(divisor).abs()) < 0,
            dividend + "/" + divisor);
      }
    }
  }

  private static DecimalValue decimalWithPrecision(int precision) {
    BigInteger coefficient = BigInteger.TEN.pow(precision - 1).add(BigInteger.ONE);
    return new DecimalValue(coefficient, 0);
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack) throws RuntimeFailure {
    var output = new BoundedOutput(SOURCE_PATH, new MemoryOutputSink());
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    new BuiltinExecutor(SOURCE_PATH, output, budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }
}
