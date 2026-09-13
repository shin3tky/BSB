package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class DecimalTextDecimalTextRuntimeTest {
  private static final String SOURCE_PATH = "DecimalText-DecimalText.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void acceptsFixedAndExponentDecimalTextWithExactCanonicalValues() throws Exception {
    assertDecimal("1.0", "1.0");
    assertDecimal("1e2", "100.0");
    assertDecimal("1E2", "100.0");
    assertDecimal("1e+2", "100.0");
    assertDecimal("1e-2", "0.01");
    assertDecimal("1.5e3", "1500.0");
    assertDecimal("1e002", "100.0");
    assertDecimal("1.500e2", "150.0");
    assertDecimal("-0e10", "0.0");
  }

  @Test
  void requiresACompleteAsciiDecimalGrammarWithAPointOrExponent() {
    for (String input :
        List.of(
            "", "-", "1", "-0", "+1e2", "01e2", ".5e2", "1.e2", "1e", "1e+", "1e-", "1ee2", "1e2e3",
            " 1e2", "1e2 ", "１e２", "1,000e2")) {
      Diagnostic diagnostic = failure("文字列を小数に変換する", input);
      assertEquals(DiagnosticCode.E_DECIMAL_TEXT_INVALID, diagnostic.code(), input);
      assertEquals("小数点または指数部を持つASCII小数表記", diagnostic.expected().orElseThrow());
    }
  }

  @Test
  void leavesTheIntegerTextGrammarUnchanged() {
    Diagnostic diagnostic = failure("文字列を整数に変換する", "1e2");
    assertEquals(DiagnosticCode.E_INTEGER_TEXT_INVALID, diagnostic.code());
  }

  @Test
  void countsMantissaAndExponentDigitsBeforeConstructingAValue() throws Exception {
    String accepted = "1." + "0".repeat(4_093) + "1e0";
    assertEquals(4_096, asciiDigits(accepted));
    assertDecimal(accepted, "1." + "0".repeat(4_093) + "1");

    String overMantissa = "1." + "0".repeat(4_094) + "1e0";
    assertDigitLimit(overMantissa);

    String acceptedExponent = "1e" + "0".repeat(4_095);
    assertEquals(4_096, asciiDigits(acceptedExponent));
    assertDecimal(acceptedExponent, "1.0");

    assertDigitLimit("1e" + "0".repeat(4_096));
  }

  @Test
  void checksRawThenNormalizedScaleIncludingZero() throws Exception {
    for (String input : List.of("1e-65536", "1e65536", "10e65535", "0e65536")) {
      var stack = stack(input);
      execute("文字列を小数に変換する", stack);
      assertEquals(1, stack.size(), input);
    }

    assertScaleLimit("1e-65537", "rawScale");
    assertScaleLimit("1e65537", "rawScale");
    assertScaleLimit("10e65536", "normalizedScale");
    assertScaleLimit("0e65537", "rawScale");
  }

  @Test
  void saturatesAHugeExponentWithoutLeakingJavaExceptionsOrChangingTheStack() {
    String input = "1e" + "9".repeat(4_095);
    var stack = stack(input);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> executeDecimal(stack));
    Diagnostic diagnostic = failure.diagnostic();
    assertEquals(DiagnosticCode.E_DECIMAL_SCALE_LIMIT, diagnostic.code());
    assertEquals("65537", diagnostic.observed().orElseThrow());
    assertEquals("rawScale", diagnostic.fields().get("metric"));
    assertFalse(diagnostic.toString().contains("NumberFormatException"));
    assertFalse(diagnostic.toString().contains("ArithmeticException"));
    assertEquals(List.of(new StringValue(input)), stack);
  }

  private static void assertDecimal(String input, String expectedDisplay) throws Exception {
    var stack = stack(input);
    executeDecimal(stack);
    assertEquals(expectedDisplay, ((DecimalValue) stack.getFirst()).displayText(), input);
  }

  private static void assertDigitLimit(String input) {
    Diagnostic diagnostic = failure("文字列を小数に変換する", input);
    assertEquals(DiagnosticCode.E_NUMERIC_TEXT_DIGIT_LIMIT, diagnostic.code());
    assertEquals("4096", diagnostic.limit().orElseThrow());
    assertEquals("4097", diagnostic.observed().orElseThrow());
  }

  private static void assertScaleLimit(String input, String metric) {
    Diagnostic diagnostic = failure("文字列を小数に変換する", input);
    assertEquals(DiagnosticCode.E_DECIMAL_SCALE_LIMIT, diagnostic.code(), input);
    assertEquals(metric, diagnostic.fields().get("metric"), input);
    assertEquals("65536", diagnostic.limit().orElseThrow(), input);
    assertEquals("65537", diagnostic.observed().orElseThrow(), input);
  }

  private static Diagnostic failure(String word, String input) {
    var stack = stack(input);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute(word, stack));
    assertEquals(List.of(new StringValue(input)), stack, input);
    return failure.diagnostic();
  }

  private static int asciiDigits(String input) {
    return (int) input.chars().filter(character -> character >= '0' && character <= '9').count();
  }

  private static void executeDecimal(ArrayList<RuntimeValue> stack) throws RuntimeFailure {
    execute("文字列を小数に変換する", stack);
  }

  private static void execute(String builtinName, ArrayList<RuntimeValue> stack)
      throws RuntimeFailure {
    new BuiltinExecutor(
            SOURCE_PATH,
            new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()),
            new ExecutionBudget(SOURCE_PATH, () -> 0L))
        .execute(BuiltinDictionary.find(builtinName).orElseThrow(), stack, SPAN);
  }

  private static ArrayList<RuntimeValue> stack(String input) {
    return new ArrayList<>(List.of(new StringValue(input)));
  }
}
