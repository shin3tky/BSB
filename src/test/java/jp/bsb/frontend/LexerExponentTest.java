package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LexerExponentTest {
  @ParameterizedTest
  @MethodSource("validExponentLiterals")
  void classifiesEveryNormativeExponentFormAsDecimal(String lexeme) {
    var lexed = LexerTestSupport.lexText("指数正常.bsb", lexeme);

    assertTrue(lexed.diagnostics().diagnostics().isEmpty(), lexeme);
    Token token = lexed.result().tokens().getFirst();
    assertEquals(TokenKind.DECIMAL_LITERAL, token.kind(), lexeme);
    assertEquals(lexeme, token.lexeme(), lexeme);
    assertEquals(lexeme, token.value(), lexeme);
  }

  @ParameterizedTest
  @MethodSource("invalidExponentLiterals")
  void assignsNormativeReasonsToMalformedExponentCandidates(
      String lexeme, String reason, String expected, String fix) {
    var lexed = LexerTestSupport.lexText("指数不正.bsb", lexeme);

    assertEquals(1, lexed.diagnostics().diagnostics().size(), lexeme);
    Diagnostic diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_INVALID_NUMBER_LITERAL, diagnostic.code(), lexeme);
    assertEquals(reason, diagnostic.fields().get("reason"), lexeme);
    assertEquals(expected, diagnostic.expected().orElseThrow(), lexeme);
    assertEquals(lexeme, diagnostic.actual().orElseThrow(), lexeme);
    assertEquals(fix, diagnostic.fixes().getFirst(), lexeme);
    assertEquals(0, lexed.result().tokens().size(), lexeme);
  }

  @Test
  void separatesACompleteExponentFromAnIdentifierButNotAnIncompleteExponent() {
    var missingSeparator = LexerTestSupport.lexText("区切り.bsb", "1e2円");
    Diagnostic separator = missingSeparator.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_MISSING_SEPARATOR, separator.code());
    assertEquals("円", separator.actual().orElseThrow());
    assertEquals("1e2 円", separator.fixes().getFirst());
    assertEquals(new SourcePosition(3, 1, 4), separator.location().displayPosition().orElseThrow());

    var incomplete = LexerTestSupport.lexText("未完成.bsb", "1e円");
    Diagnostic invalid = incomplete.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_INVALID_NUMBER_LITERAL, invalid.code());
    assertEquals("missingExponentDigits", invalid.fields().get("reason"));
    assertEquals("1e円", invalid.actual().orElseThrow());
    assertEquals("指数数字を追加するかeの前へ空白を入れてください", invalid.fixes().getFirst());
  }

  @Test
  void keepsE2AsAnIdentifier() {
    var lexed = LexerTestSupport.lexText("識別子.bsb", "e2");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(TokenKind.IDENTIFIER, lexed.result().tokens().getFirst().kind());
    assertEquals("e2", lexed.result().tokens().getFirst().value());
  }

  @Test
  void countsMantissaAndExponentDigitsAt4096And4097() {
    assertDigitBoundary("1" + "0".repeat(4094) + "e0", 4096, true);
    assertDigitBoundary("1" + "0".repeat(4095) + "e0", 4097, false);
    assertDigitBoundary("1e" + "0".repeat(4095), 4096, true);
    assertDigitBoundary("1e" + "0".repeat(4096), 4097, false);
  }

  private static void assertDigitBoundary(String lexeme, int digits, boolean accepted) {
    var lexed = LexerTestSupport.lexText("指数桁数.bsb", lexeme);
    if (accepted) {
      assertTrue(lexed.diagnostics().diagnostics().isEmpty());
      assertEquals(TokenKind.DECIMAL_LITERAL, lexed.result().tokens().getFirst().kind());
      return;
    }
    Diagnostic diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_NUMBER_LIMIT, diagnostic.code());
    assertEquals("4096", diagnostic.limit().orElseThrow());
    assertEquals(Integer.toString(digits), diagnostic.observed().orElseThrow());
  }

  private static Stream<String> validExponentLiterals() {
    return Stream.of(
        "1e2", "1E2", "1e+2", "1e-2", "1e002", "1.5e3", "1.25E-2", "1e0", "100e-2", "-0e10",
        "1.500e3");
  }

  private static Stream<Arguments> invalidExponentLiterals() {
    return Stream.of(
        Arguments.of("1e", "missingExponentDigits", "指数部のASCII数字", "指数部へ数字を追加してください"),
        Arguments.of("1E", "missingExponentDigits", "指数部のASCII数字", "指数部へ数字を追加してください"),
        Arguments.of("1e+", "missingExponentDigits", "指数符号後のASCII数字", "指数符号の後へ数字を追加してください"),
        Arguments.of("1e-", "missingExponentDigits", "指数符号後のASCII数字", "指数符号の後へ数字を追加してください"),
        Arguments.of(".5e2", "invalidMantissa", "整数部と小数部を持つ仮数", "0.5e2のように書いてください"),
        Arguments.of("1.e2", "invalidMantissa", "整数部と小数部を持つ仮数", "1.0e2のように書いてください"),
        Arguments.of("+1e2", "leadingPlus", "先頭符号なしの正数", "先頭の+を削除してください"),
        Arguments.of("01e2", "leadingZero", "0または非ゼロ数字で始まる整数部", "先頭の0を削除してください"),
        Arguments.of("1ee2", "repeatedExponent", "指数記号1個", "余分な指数記号を削除してください"),
        Arguments.of("1e2e3", "repeatedExponent", "指数記号1個", "余分な指数記号を削除してください"),
        Arguments.of("1e--2", "missingExponentDigits", "指数符号1個とASCII数字", "余分な-を削除してください"));
  }
}
