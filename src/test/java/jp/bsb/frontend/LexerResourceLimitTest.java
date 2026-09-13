package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class LexerResourceLimitTest {
  @Test
  void acceptsExactly250000CountedTokens() {
    var lexed = LexerTestSupport.lexText("CORE-R003-ok.bsb", "はい ".repeat(Lexer.MAX_TOKENS));

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(Lexer.MAX_TOKENS, lexed.result().countedTokenCount());
    assertEquals(Lexer.MAX_TOKENS, lexed.result().tokens().size());
  }

  @Test
  void rejectsThe250001stCountedToken() {
    var lexed = LexerTestSupport.lexText("CORE-R003-error.bsb", "はい ".repeat(Lexer.MAX_TOKENS + 1));

    var diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_TOKEN_LIMIT, diagnostic.code());
    assertEquals("250000", diagnostic.limit().orElseThrow());
    assertEquals("250001", diagnostic.observed().orElseThrow());
    assertEquals(Lexer.MAX_TOKENS, lexed.result().countedTokenCount());
  }

  @Test
  void acceptsAStringAtTheUtf8ByteLimit() {
    String value = "a".repeat(Lexer.MAX_STRING_UTF8_BYTES);
    var lexed = LexerTestSupport.lexText("CORE-R007-ok.bsb", "「" + value + "」");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(value.length(), lexed.result().tokens().getFirst().value().length());
  }

  @Test
  void rejectsAStringOneUtf8ByteOverTheLimit() {
    String value = "a".repeat(Lexer.MAX_STRING_UTF8_BYTES + 1);
    var lexed = LexerTestSupport.lexText("CORE-R007-error.bsb", "「" + value + "」");

    var diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_STRING_LIMIT, diagnostic.code());
    assertEquals("16777216", diagnostic.limit().orElseThrow());
    assertEquals("16777217", diagnostic.observed().orElseThrow());
  }

  @Test
  void acceptsANumberAtTheDigitLimit() {
    String number = "9".repeat(Lexer.MAX_NUMBER_DIGITS);
    var lexed = LexerTestSupport.lexText("CORE-R008-ok.bsb", number);

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(TokenKind.INTEGER_LITERAL, lexed.result().tokens().getFirst().kind());
  }

  @Test
  void rejectsANumberOneDigitOverTheLimit() {
    String number = "9".repeat(Lexer.MAX_NUMBER_DIGITS + 1);
    var lexed = LexerTestSupport.lexText("CORE-R008-error.bsb", number);

    var diagnostic = lexed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_NUMBER_LIMIT, diagnostic.code());
    assertEquals("4096", diagnostic.limit().orElseThrow());
    assertEquals("4097", diagnostic.observed().orElseThrow());
  }
}
