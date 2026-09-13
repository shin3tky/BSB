package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class NumericDivisionConversionResourceTest {
  private static final String SOURCE_PATH = "division-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void dividesAllFourConcreteNumericInputPairsWithoutPaddingExactResults() throws Exception {
    for (List<RuntimeValue> inputs :
        List.<List<RuntimeValue>>of(
            List.of(integer(6), integer(4)),
            List.of(decimal(6), integer(4)),
            List.of(integer(6), decimal(4)),
            List.of(decimal(6), decimal(4)))) {
      var stack = new ArrayList<>(inputs);
      execute("小数で割る", stack);

      assertEquals(List.of("1.5"), displayStack(stack));
    }
  }

  @Test
  void acceptsBothExplicitPrecisionBoundariesAndRejectsValuesOutsideThemAtomically()
      throws Exception {
    var minimum = explicitDivision(BigInteger.ONE);
    execute("精度指定で割る", minimum);
    assertEquals("0.3", minimum.getFirst().displayText());

    var maximum = explicitDivision(BigInteger.valueOf(4_096));
    execute("精度指定で割る", maximum);
    assertEquals(4_096, ((DecimalValue) maximum.getFirst()).precision());

    for (BigInteger precision : List.of(BigInteger.ZERO, BigInteger.valueOf(4_097))) {
      var rejected = explicitDivision(precision);
      List<RuntimeValue> before = List.copyOf(rejected);
      RuntimeFailure failure =
          assertThrows(RuntimeFailure.class, () -> execute("精度指定で割る", rejected));

      assertEquals(DiagnosticCode.E_DIVISION_PRECISION_OUT_OF_RANGE, failure.diagnostic().code());
      assertEquals(precision.toString(), failure.diagnostic().fields().get("precision"));
      assertEquals(before, rejected);
    }
  }

  @Test
  void checksDivisionByZeroBeforeAnInvalidExplicitPrecision() {
    var stack =
        stack(
            integer(1),
            integer(0),
            new IntegerValue(BigInteger.ZERO),
            RoundingModeValue.NEAREST_EVEN);
    List<RuntimeValue> before = List.copyOf(stack);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("精度指定で割る", stack));

    assertEquals(DiagnosticCode.E_DIVISION_BY_ZERO, failure.diagnostic().code());
    assertEquals(before, stack);
  }

  @Test
  void boundsAnOversizedExplicitPrecisionInRangeAndZeroDivisionDiagnostics() {
    BigInteger precision = new BigInteger("9".repeat(65));
    String expectedPreview = "9".repeat(32) + "…(+17)" + "9".repeat(16);
    for (long divisor : List.of(3L, 0L)) {
      var stack =
          stack(
              integer(1),
              integer(divisor),
              new IntegerValue(precision),
              RoundingModeValue.NEAREST_EVEN);
      RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("精度指定で割る", stack));

      assertEquals(expectedPreview, failure.diagnostic().fields().get("precision"));
      assertEquals("65", failure.diagnostic().fields().get("precisionCodePoints"));
    }
  }

  @Test
  void rejectsADivisionScaleBeyondTheDecimalLimitWithoutConsumingInputs() {
    var stack = stack(new DecimalValue(BigInteger.ONE, 65_536), integer(10));
    List<RuntimeValue> before = List.copyOf(stack);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("小数で割る", stack));

    assertEquals(DiagnosticCode.E_DECIMAL_SCALE_LIMIT, failure.diagnostic().code());
    assertEquals("65537", failure.diagnostic().observed().orElseThrow());
    assertEquals(before, stack);
  }

  @Test
  void convertsTheMaximumIntegerToDecimalWithoutLosingItsValue() throws Exception {
    BigInteger input = BigInteger.TEN.pow(RuntimeLimits.INTEGER_DIGITS - 1).add(BigInteger.ONE);
    var stack = stack(new IntegerValue(input));
    execute("小数に変換する", stack);

    DecimalValue result = assertInstanceOf(DecimalValue.class, stack.getFirst());
    assertEquals(0, new java.math.BigDecimal(input).compareTo(result.value()));
  }

  @Test
  void acceptsAndRejectsTheDecimalToIntegerDigitBoundaryBeforeConstructingTheResult()
      throws Exception {
    var accepted = stack(new DecimalValue(BigInteger.ONE, -65_535));
    execute("整数に変換する", accepted);
    assertEquals(RuntimeLimits.INTEGER_DIGITS, digits((IntegerValue) accepted.getFirst()));

    var rejected = stack(new DecimalValue(BigInteger.ONE, -65_536));
    List<RuntimeValue> before = List.copyOf(rejected);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("整数に変換する", rejected));
    Diagnostic diagnostic = failure.diagnostic();

    assertEquals(DiagnosticCode.E_INTEGER_RESULT_LIMIT, diagnostic.code());
    assertEquals("integerDigits", diagnostic.limitName().orElseThrow());
    assertEquals("65536", diagnostic.limit().orElseThrow());
    assertEquals("65537", diagnostic.observed().orElseThrow());
    assertEquals(before, rejected);
  }

  @Test
  void appliesAllFiveRoundingDirectionsAndTheSameIntegerBoundary() throws Exception {
    assertRounded("2.5", RoundingModeValue.NEAREST_EVEN, "2");
    assertRounded("3.5", RoundingModeValue.NEAREST_EVEN, "4");
    assertRounded("-2.5", RoundingModeValue.NEAREST_AWAY_FROM_ZERO, "-3");
    assertRounded("-2.9", RoundingModeValue.TOWARD_ZERO, "-2");
    assertRounded("-2.1", RoundingModeValue.TOWARD_POSITIVE_INFINITY, "-2");
    assertRounded("-2.1", RoundingModeValue.TOWARD_NEGATIVE_INFINITY, "-3");

    var rejected =
        stack(
            new DecimalValue(BigInteger.ONE, -65_536), RoundingModeValue.TOWARD_NEGATIVE_INFINITY);
    List<RuntimeValue> before = List.copyOf(rejected);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("整数に丸める", rejected));

    assertEquals(DiagnosticCode.E_INTEGER_RESULT_LIMIT, failure.diagnostic().code());
    assertEquals("65537", failure.diagnostic().observed().orElseThrow());
    assertEquals(before, rejected);
  }

  private static void assertRounded(String input, RoundingModeValue rounding, String expected)
      throws Exception {
    var stack = stack(new DecimalValue(new java.math.BigDecimal(input)), rounding);
    execute("整数に丸める", stack);
    assertEquals(List.of(expected), displayStack(stack));
  }

  private static ArrayList<RuntimeValue> explicitDivision(BigInteger precision) {
    return stack(
        integer(1), integer(3), new IntegerValue(precision), RoundingModeValue.NEAREST_EVEN);
  }

  private static IntegerValue integer(long value) {
    return new IntegerValue(BigInteger.valueOf(value));
  }

  private static DecimalValue decimal(long value) {
    return new DecimalValue(BigInteger.valueOf(value), 0);
  }

  private static int digits(IntegerValue value) {
    return value.value().abs().toString().length();
  }

  private static List<String> displayStack(List<RuntimeValue> stack) {
    return stack.stream().map(RuntimeValue::displayText).toList();
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
