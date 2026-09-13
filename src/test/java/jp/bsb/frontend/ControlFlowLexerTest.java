package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ControlFlowLexerTest {
  @Test
  void classifiesEveryControlWordAsItsDedicatedTokenKind() {
    var lexed = LexerTestSupport.lexText("制御語.bsb", "ならば さもなければ つぎに 回だけ ここから 続く間 繰り返す 打ち切る 続ける 戻る");

    assertTrue(lexed.result().successful(), lexed.diagnostics().diagnostics().toString());
    assertEquals(
        List.of(
            TokenKind.CONDITIONAL_START,
            TokenKind.CONDITIONAL_ELSE,
            TokenKind.CONDITIONAL_END,
            TokenKind.COUNTED_LOOP_START,
            TokenKind.CONDITION_LOOP_START,
            TokenKind.LOOP_CONDITION_SEPARATOR,
            TokenKind.LOOP_END,
            TokenKind.BREAK,
            TokenKind.CONTINUE,
            TokenKind.RETURN),
        lexed.result().tokens().stream().map(Token::kind).toList());
  }

  @Test
  void keepsUnsupportedAliasesAsOrdinaryIdentifiers() {
    var lexed = LexerTestSupport.lexText("別名.bsb", "打ち切り もう一度 終り");

    assertTrue(lexed.result().successful());
    assertEquals(
        List.of(TokenKind.IDENTIFIER, TokenKind.IDENTIFIER, TokenKind.IDENTIFIER),
        lexed.result().tokens().stream().map(Token::kind).toList());
  }
}
