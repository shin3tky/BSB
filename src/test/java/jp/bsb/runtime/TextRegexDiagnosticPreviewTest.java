package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.regex.Re2RegexCompiler;
import jp.bsb.regex.RegexCompilationResult;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

/** 文字列・rawパターン・置換テンプレートの64コードポイント診断プレビューを固定します。 */
class TextRegexDiagnosticPreviewTest {
  private static final String SOURCE_PATH = "TextRegex-diagnostic-preview.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void boundsInputAndRawPatternBeforeEscapingTheirDisplay() {
    String input = "i".repeat(70);
    String pattern = "p".repeat(70);
    RegexValue regex = regex(pattern);
    var stack = stack(new StringValue(input), regex);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("正規表現で最初を取り出す", stack));

    assertEquals(DiagnosticCode.E_REGEX_NO_MATCH, failure.diagnostic().code());
    assertEquals(preview('i'), failure.diagnostic().fields().get("inputPreview"));
    assertEquals(preview('p'), failure.diagnostic().fields().get("patternPreview"));
    assertEquals(List.of(new StringValue(input), regex), stack);
  }

  @Test
  void boundsAnInvalidTemplateExpressionAndReferenceBeforeMatching() {
    String reference = "x".repeat(70);
    String expression = "${" + reference + "}";
    RegexValue regex = regex("(?<n>a)");
    var stack = stack(new StringValue("a"), regex, new StringValue(expression));

    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute("正規表現で置き換える", stack));

    assertEquals(DiagnosticCode.E_REGEX_REPLACEMENT_TEMPLATE, failure.diagnostic().code());
    assertEquals(preview('x'), failure.diagnostic().fields().get("reference"));
    assertEquals(
        "${" + "x".repeat(30) + "…(+25)" + "x".repeat(15) + "}",
        failure.diagnostic().actual().orElseThrow());
    assertEquals(List.of(new StringValue("a"), regex, new StringValue(expression)), stack);
  }

  private static String preview(char value) {
    return String.valueOf(value).repeat(32) + "…(+22)" + String.valueOf(value).repeat(16);
  }

  private static RegexValue regex(String pattern) {
    RegexCompilationResult.Success success =
        (RegexCompilationResult.Success) new Re2RegexCompiler().compile(pattern, "");
    return new RegexValue(pattern, "", success.program());
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
}
