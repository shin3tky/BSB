package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrayLexerTest {
  @Test
  void tokenizesArrayPunctuationOnlyInsideAnArray() throws IOException {
    var lexed = LexerTestSupport.lexArrayResource("ARRAY-N019.bsb");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of("【", "，", "，", "】"),
        lexed.result().tokens().stream()
            .filter(
                token ->
                    token.kind() == TokenKind.ARRAY_OPEN
                        || token.kind() == TokenKind.ARRAY_SEPARATOR
                        || token.kind() == TokenKind.ARRAY_CLOSE)
            .map(Token::lexeme)
            .toList());

    var outside = LexerTestSupport.lexText("読点.bsb", "1、2，3");
    assertTrue(outside.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of(TokenKind.INTEGER_LITERAL, TokenKind.INTEGER_LITERAL, TokenKind.INTEGER_LITERAL),
        outside.result().tokens().stream().map(Token::kind).toList());
  }

  @Test
  void tokenizesArrayTypesAndArrayLoopOpeningWithDedicatedKinds() throws IOException {
    var type = LexerTestSupport.lexArrayResource("ARRAY-N012.bsb");
    var loop = LexerTestSupport.lexArrayResource("ARRAY-N013.bsb");

    assertTrue(type.diagnostics().diagnostics().isEmpty());
    assertTrue(loop.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of("<", ">"),
        type.result().tokens().stream()
            .filter(
                token ->
                    token.kind() == TokenKind.ARRAY_TYPE_OPEN
                        || token.kind() == TokenKind.ARRAY_TYPE_CLOSE)
            .map(Token::lexeme)
            .toList());
    assertEquals(
        1,
        loop.result().tokens().stream()
            .filter(token -> token.kind() == TokenKind.ARRAY_LOOP_START)
            .count());
  }

  @Test
  void leavesArrayPunctuationInsideLiteralsAndCommentsAsData() {
    var lexed = LexerTestSupport.lexText("非構造.bsb", "【「【、】」、'、'、1# 【、】\n】");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of(
            TokenKind.ARRAY_OPEN,
            TokenKind.STRING_LITERAL,
            TokenKind.ARRAY_SEPARATOR,
            TokenKind.CHARACTER_LITERAL,
            TokenKind.ARRAY_SEPARATOR,
            TokenKind.INTEGER_LITERAL,
            TokenKind.COMMENT,
            TokenKind.ARRAY_CLOSE),
        lexed.result().tokens().stream().map(Token::kind).toList());
    assertEquals("【、】", lexed.result().tokens().get(1).value());
    assertEquals("# 【、】", lexed.result().tokens().get(6).value());
  }
}
