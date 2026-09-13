package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class BindingLexerTest {
  @Test
  void classifiesDeclarationAssignmentAndBothKindsOfFullStop() throws IOException {
    var lexed = LexerTestSupport.lexBindingResource("BIND-N002.bsb");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of(
            TokenKind.IDENTIFIER,
            TokenKind.DECLARATION_MARKER,
            TokenKind.VARIABLE_DECLARATION,
            TokenKind.INTEGER_LITERAL,
            TokenKind.DECLARATION_END),
        lexed.result().tokens().stream().limit(5).map(Token::kind).toList());
    assertTrue(
        lexed.result().tokens().stream().anyMatch(token -> token.kind() == TokenKind.ASSIGNMENT));
    assertEquals(TokenKind.DEFINITION_END, lexed.result().tokens().getLast().kind());
  }

  @Test
  void splitsOnlyADeclarationCandidateEndingInMarker() {
    var lexed = LexerTestSupport.lexText("宣言候補.bsb", "値は 定数 1。\n母は強い を 表示する\n説明は # コメント\n変数 0。\n");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of("値", "は", "定数", "1", "。", "母は強い", "を", "表示する", "説明", "は"),
        lexed.result().tokens().stream().limit(10).map(Token::value).toList());
    assertEquals(TokenKind.IDENTIFIER, lexed.result().tokens().get(5).kind());
    assertEquals(TokenKind.DECLARATION_MARKER, lexed.result().tokens().get(9).kind());
  }

  @Test
  void recognizesASeparatedMarkerForTheAdjacencyDiagnostic() throws IOException {
    var lexed = LexerTestSupport.lexBindingResource("BIND-F001.bsb");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(TokenKind.IDENTIFIER, lexed.result().tokens().get(0).kind());
    assertEquals(TokenKind.DECLARATION_MARKER, lexed.result().tokens().get(1).kind());
    assertEquals(2, lexed.result().tokens().get(0).span().end().column());
    assertEquals(3, lexed.result().tokens().get(1).span().start().column());
  }
}
