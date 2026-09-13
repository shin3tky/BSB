package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.stream.Stream;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LexerDiagnosticTest {
  @Test
  void reportsDisallowedNbspAtTheSpecifiedPosition() {
    var lexed = LexerTestSupport.lexText("CORE-F002.bsb", "メインとは\u00A0（--）\nこと。\n");

    Diagnostic diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertDiagnostic(diagnostic, DiagnosticCode.E_DISALLOWED_WHITESPACE, 1, 6);
    assertEquals("U+00A0", diagnostic.actual().orElseThrow());
    assertFalse(lexed.result().successful());
    assertThrows(IllegalStateException.class, lexed.result()::tokensForParsing);
  }

  @Test
  void reportsBidiControlInsideAnIdentifier() {
    var lexed =
        LexerTestSupport.lexText("CORE-F003.bsb", "名\u202E前とは （--）\nこと。\n\nメインとは （--）\nこと。\n");

    Diagnostic diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertDiagnostic(diagnostic, DiagnosticCode.E_INVISIBLE_CHARACTER, 1, 2);
    assertEquals("U+202E", diagnostic.actual().orElseThrow());
  }

  @Test
  void reports4097DigitsAtTheStartOfTheNumber() {
    String source = "メインとは （--）\n    " + "9".repeat(4097) + "\nこと。\n";
    var lexed = LexerTestSupport.lexText("CORE-F006.bsb", source);

    Diagnostic diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertDiagnostic(diagnostic, DiagnosticCode.E_NUMBER_LIMIT, 2, 5);
    assertEquals("4096", diagnostic.limit().orElseThrow());
    assertEquals("4097", diagnostic.observed().orElseThrow());
  }

  @ParameterizedTest
  @MethodSource("lexicalFailures")
  void reportsConformanceDiagnostics(String sourceName, DiagnosticCode code, int line, int column)
      throws IOException {
    var lexed = LexerTestSupport.lexResource(sourceName);

    assertEquals(1, lexed.diagnostics().diagnostics().size(), sourceName);
    assertDiagnostic(lexed.diagnostics().diagnostics().getFirst(), code, line, column);
  }

  @Test
  void reportsTheMissingSeparatorAndSuggestedReplacement() throws IOException {
    Diagnostic diagnostic = firstDiagnostic("CORE-F004.bsb");

    assertEquals("円", diagnostic.actual().orElseThrow());
    assertEquals("123 円", diagnostic.fixes().getFirst());
  }

  @Test
  void distinguishesTheThreeInvalidNumberForms() throws IOException {
    Diagnostic leadingZero = firstDiagnostic("CORE-F005a.bsb");
    Diagnostic missingInteger = firstDiagnostic("CORE-F005b.bsb");
    Diagnostic missingFraction = firstDiagnostic("CORE-F005c.bsb");

    assertEquals("0または0以外で始まる整数", leadingZero.expected().orElseThrow());
    assertEquals("1を使用してください", leadingZero.fixes().getFirst());
    assertEquals("小数点前の数字", missingInteger.expected().orElseThrow());
    assertEquals("0.5を使用してください", missingInteger.fixes().getFirst());
    assertEquals("小数点後の数字", missingFraction.expected().orElseThrow());
    assertEquals("1.0を使用してください", missingFraction.fixes().getFirst());
  }

  @Test
  void reportsInvalidEscapeAndUnicodeScalarDetails() throws IOException {
    Diagnostic escape = firstDiagnostic("CORE-F008.bsb");
    Diagnostic scalar = firstDiagnostic("CORE-F009.bsb");

    assertEquals("\\q", escape.actual().orElseThrow());
    assertEquals("qを直接書くか定義済み表記を使ってください", escape.fixes().getFirst());
    assertEquals("U+D800", scalar.actual().orElseThrow());
    assertEquals("サロゲート以外を指定してください", scalar.fixes().getFirst());
  }

  @Test
  void acceptsExponentNotationAsOneDecimalToken() {
    var lexed = LexerTestSupport.lexText("指数.bsb", "1e3");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(TokenKind.DECIMAL_LITERAL, lexed.result().tokens().getFirst().kind());
    assertEquals("1e3", lexed.result().tokens().getFirst().lexeme());
  }

  @Test
  void reportsAsciiCommaAsAnUnexpectedCharacter() {
    var lexed = LexerTestSupport.lexText("カンマ.bsb", ",");

    assertEquals(
        DiagnosticCode.E_UNEXPECTED_CHARACTER, lexed.diagnostics().diagnostics().getFirst().code());
  }

  private static Stream<Arguments> lexicalFailures() {
    return Stream.of(
        Arguments.of("CORE-F004.bsb", DiagnosticCode.E_MISSING_SEPARATOR, 2, 8),
        Arguments.of("CORE-F005a.bsb", DiagnosticCode.E_INVALID_NUMBER_LITERAL, 2, 5),
        Arguments.of("CORE-F005b.bsb", DiagnosticCode.E_INVALID_NUMBER_LITERAL, 2, 5),
        Arguments.of("CORE-F005c.bsb", DiagnosticCode.E_INVALID_NUMBER_LITERAL, 2, 5),
        Arguments.of("CORE-F007.bsb", DiagnosticCode.E_UNTERMINATED_STRING, 2, 13),
        Arguments.of("CORE-F008.bsb", DiagnosticCode.E_INVALID_ESCAPE, 2, 8),
        Arguments.of("CORE-F009.bsb", DiagnosticCode.E_INVALID_UNICODE_SCALAR, 2, 6),
        Arguments.of("CORE-F010a.bsb", DiagnosticCode.E_CHARACTER_LENGTH, 2, 5),
        Arguments.of("CORE-F010b.bsb", DiagnosticCode.E_CHARACTER_LENGTH, 2, 5));
  }

  private static Diagnostic firstDiagnostic(String sourceName) throws IOException {
    return LexerTestSupport.lexResource(sourceName).diagnostics().diagnostics().getFirst();
  }

  private static void assertDiagnostic(
      Diagnostic diagnostic, DiagnosticCode code, int line, int column) {
    assertEquals(code, diagnostic.code());
    assertEquals(
        new SourcePosition(diagnostic.location().utf8Offset(), line, column),
        diagnostic.location().displayPosition().orElseThrow());
  }
}
