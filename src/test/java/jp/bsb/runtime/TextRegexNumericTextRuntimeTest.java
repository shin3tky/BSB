package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class TextRegexNumericTextRuntimeTest {
  private static final String SOURCE_PATH = "TextRegex-numeric-text.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsBothNormativeConversionCasesWithCanonicalOutput() throws Exception {
    assertRun("TEXT-N009", "-42\n1.23\n");
    assertRun("TEXT-N010", "0\n0.0\n123\n1.23\n");
  }

  @Test
  void reportsTheThreeNormativeFailuresWithoutConsumingTheInput() throws Exception {
    for (String caseId : List.of("TEXT-F022", "TEXT-F023", "TEXT-F024")) {
      var output = new MemoryOutputSink();
      ProgramRunResult result = run(caseId, output);

      assertEquals(10, result.exitCode(), caseId);
      assertEquals(expectedCode(caseId), result.diagnostics().getFirst().code(), caseId);
      assertEquals("", output.utf8Text(), caseId);
      assertEquals(1, result.finalDataStack().size(), caseId);
    }
  }

  @Test
  void acceptsOnlyTheNormativeAsciiIntegerAndDecimalGrammars() throws Exception {
    for (String input :
        List.of("", "-", "+1", "01", "-01", " 1", "1 ", "１", "1,000", "1.0", "1e3")) {
      assertInvalid("文字列を整数に変換する", input, DiagnosticCode.E_INTEGER_TEXT_INVALID);
    }
    for (String input : List.of("", "-", "+1.0", "01.0", ".5", "1.", "1", "1e", "１.０", "1,000.0")) {
      assertInvalid("文字列を小数に変換する", input, DiagnosticCode.E_DECIMAL_TEXT_INVALID);
    }
  }

  @Test
  void checksThe4096DigitBoundaryBeforeConstructingEitherNumericValue() throws Exception {
    String acceptedInteger = "1".repeat(4_096);
    var integerStack = stack(string(acceptedInteger));
    execute("文字列を整数に変換する", integerStack);
    assertEquals(new BigInteger(acceptedInteger), ((IntegerValue) integerStack.getFirst()).value());

    String acceptedDecimal = "1".repeat(4_095) + ".0";
    var decimalStack = stack(string(acceptedDecimal));
    execute("文字列を小数に変換する", decimalStack);
    assertEquals(4_095, ((DecimalValue) decimalStack.getFirst()).precision());

    assertDigitLimit("文字列を整数に変換する", "1".repeat(4_097), "整数");
    assertDigitLimit("文字列を小数に変換する", "1".repeat(4_096) + ".0", "小数");
  }

  private static void assertInvalid(String word, String input, DiagnosticCode code) {
    var stack = stack(string(input));
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute(word, stack));
    assertEquals(code, failure.diagnostic().code(), input);
    assertEquals(List.of(string(input)), stack, input);
    String preview = failure.diagnostic().fields().get("inputPreview");
    assertTrue(preview.codePointCount(0, preview.length()) <= 64, input);
  }

  private static void assertDigitLimit(String word, String input, String numericType) {
    var stack = stack(string(input));
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute(word, stack));
    assertEquals(DiagnosticCode.E_NUMERIC_TEXT_DIGIT_LIMIT, failure.diagnostic().code());
    assertEquals(numericType, failure.diagnostic().fields().get("numericType"));
    assertEquals("4097", failure.diagnostic().observed().orElseThrow());
    assertEquals(List.of(string(input)), stack);
  }

  private static void assertRun(String caseId, String expectedOutput) throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(caseId, output);
    assertTrue(result.successful(), caseId + ": " + result.diagnostics());
    assertEquals(expectedOutput, output.utf8Text(), caseId);
    assertTrue(result.finalDataStack().isEmpty(), caseId);
  }

  private static ProgramRunResult run(String caseId, MemoryOutputSink output) throws Exception {
    return new ProgramRunner()
        .run(
            caseId + ".bsb",
            source(caseId),
            new ExecutionContext(output, () -> 0L, TraceSink.none()));
  }

  private static byte[] source(String caseId) throws Exception {
    String resource = "/conformance/text-regex/sources/" + caseId + ".bsb";
    try (var input = TextRegexNumericTextRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static void execute(String builtinName, ArrayList<RuntimeValue> stack)
      throws RuntimeFailure {
    new BuiltinExecutor(
            SOURCE_PATH,
            new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()),
            new ExecutionBudget(SOURCE_PATH, () -> 0L))
        .execute(BuiltinDictionary.find(builtinName).orElseThrow(), stack, SPAN);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static StringValue string(String value) {
    return new StringValue(value);
  }

  private static DiagnosticCode expectedCode(String caseId) {
    return switch (caseId) {
      case "TEXT-F022" -> DiagnosticCode.E_INTEGER_TEXT_INVALID;
      case "TEXT-F023" -> DiagnosticCode.E_DECIMAL_TEXT_INVALID;
      case "TEXT-F024" -> DiagnosticCode.E_NUMERIC_TEXT_DIGIT_LIMIT;
      default -> throw new AssertionError(caseId);
    };
  }
}
