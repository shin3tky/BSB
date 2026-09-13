package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LexerTest {
  @Test
  void tokenizesDefinitionsStackEffectsValuesAndParticles() throws IOException {
    var lexed = LexerTestSupport.lexResource("CORE-N001.bsb");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of(
            TokenKind.IDENTIFIER,
            TokenKind.RESERVED_SYNTAX,
            TokenKind.STACK_OPEN_FULLWIDTH,
            TokenKind.IDENTIFIER,
            TokenKind.STACK_SEPARATOR,
            TokenKind.IDENTIFIER,
            TokenKind.STACK_CLOSE_FULLWIDTH),
        lexed.result().tokens().stream().limit(7).map(Token::kind).toList());
    assertEquals("二倍", lexed.result().tokens().get(0).value());
    assertEquals("とは", lexed.result().tokens().get(1).value());
    assertTrue(
        lexed.result().tokens().stream()
            .anyMatch(
                token -> token.kind() == TokenKind.INTEGER_LITERAL && token.value().equals("21")));
    assertTrue(
        lexed.result().tokens().stream()
            .anyMatch(token -> token.kind() == TokenKind.PARTICLE && token.value().equals("を")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CORE-N002.bsb",
        "CORE-N003.bsb",
        "CORE-N004.bsb",
        "CORE-N005.bsb",
        "CORE-N006.bsb",
        "CORE-N008.bsb",
        "CORE-N009.bsb",
        "CORE-N010.bsb",
        "CORE-N011.bsb",
        "CORE-N012.bsb",
        "CORE-N013.bsb",
        "CORE-N014.bsb",
        "CORE-N015.bsb",
        "CORE-N016.bsb",
        "CORE-N017.bsb",
        "CORE-N018.bsb",
        "CORE-N019.bsb",
        "CORE-N020.bsb",
        "CORE-N022.bsb"
      })
  void acceptsTheLexicallyValidConformanceSources(String sourceName) throws IOException {
    var lexed = LexerTestSupport.lexResource(sourceName);

    assertTrue(lexed.diagnostics().diagnostics().isEmpty(), sourceName);
  }

  @Test
  void normalizesIdentifiersButNotStringValues() throws IOException {
    var fullwidth = LexerTestSupport.lexResource("CORE-N005.bsb");
    var nfc = LexerTestSupport.lexResource("CORE-N006.bsb");
    var strictStrings = LexerTestSupport.lexResource("CORE-N017.bsb");

    assertEquals("請求A1", fullwidth.result().tokens().getFirst().value());
    assertEquals("が数える", nfc.result().tokens().getFirst().value());
    List<String> values =
        strictStrings.result().tokens().stream()
            .filter(token -> token.kind() == TokenKind.STRING_LITERAL)
            .map(Token::value)
            .toList();
    assertEquals(List.of("が", "か\u3099"), values);
  }

  @Test
  void decodesAllSpecifiedEscapesAndCharacterClusters() throws IOException {
    var escaped = LexerTestSupport.lexResource("CORE-N013.bsb");
    var combiningCharacter = LexerTestSupport.lexResource("CORE-N014.bsb");
    var supplementaryCharacter = LexerTestSupport.lexResource("CORE-N015.bsb");

    assertEquals(
        "一行目\n二行目\t\\\"'「内」",
        escaped.result().tokens().stream()
            .filter(token -> token.kind() == TokenKind.STRING_LITERAL)
            .findFirst()
            .orElseThrow()
            .value());
    assertEquals(
        "か\u3099",
        combiningCharacter.result().tokens().stream()
            .filter(token -> token.kind() == TokenKind.CHARACTER_LITERAL)
            .findFirst()
            .orElseThrow()
            .value());
    assertEquals(
        "𠮷",
        supplementaryCharacter.result().tokens().stream()
            .filter(token -> token.kind() == TokenKind.CHARACTER_LITERAL)
            .findFirst()
            .orElseThrow()
            .value());
  }

  @Test
  void keepsCommentsButDoesNotCountThemAsExecutionTokens() {
    var lexed = LexerTestSupport.lexText("コメント.bsb", "# 先頭\r\nはい、いいえ，# 末尾");

    assertEquals(
        List.of(
            TokenKind.COMMENT,
            TokenKind.BOOLEAN_LITERAL,
            TokenKind.BOOLEAN_LITERAL,
            TokenKind.COMMENT),
        lexed.result().tokens().stream().map(Token::kind).toList());
    assertEquals(2, lexed.result().countedTokenCount());
    assertEquals("# 先頭", lexed.result().tokens().getFirst().value());
    assertEquals("# 末尾", lexed.result().tokens().getLast().value());
  }

  @Test
  void splitsDefinitionStartOnlyAtTopLevel() {
    var lexed = LexerTestSupport.lexText("文脈.bsb", "語とは （--）\n意味とは\nこと。\n\n次とは （--）\nこと。\n");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of("語", "とは", "（", "--", "）", "意味とは", "こと", "。", "次", "とは"),
        lexed.result().tokens().stream().limit(10).map(Token::value).toList());
    assertEquals(TokenKind.IDENTIFIER, lexed.result().tokens().get(5).kind());
  }

  @Test
  void recognizesAsciiParenthesesDecimalsAndBooleanValues() {
    var lexed = LexerTestSupport.lexText("表記.bsb", "(-- ) -0 1.50 はい いいえ --");

    assertTrue(lexed.diagnostics().diagnostics().isEmpty());
    assertEquals(
        List.of(
            TokenKind.STACK_OPEN_ASCII,
            TokenKind.STACK_SEPARATOR,
            TokenKind.STACK_CLOSE_ASCII,
            TokenKind.INTEGER_LITERAL,
            TokenKind.DECIMAL_LITERAL,
            TokenKind.BOOLEAN_LITERAL,
            TokenKind.BOOLEAN_LITERAL,
            TokenKind.STACK_SEPARATOR),
        lexed.result().tokens().stream().map(Token::kind).toList());
  }

  @Test
  void producesTheSameTokensForLfCrAndCrlf() throws IOException {
    String lf = LexerTestSupport.lexResource("CORE-N001.bsb").source().text();
    var expected = LexerTestSupport.lexText("LF.bsb", lf).result().tokens();

    for (String lineEnding : List.of("\r", "\r\n")) {
      var actual = LexerTestSupport.lexText("改行.bsb", lf.replace("\n", lineEnding));
      assertTrue(actual.diagnostics().diagnostics().isEmpty());
      assertEquals(
          expected.stream().map(token -> List.of(token.kind(), token.value())).toList(),
          actual.result().tokens().stream()
              .map(token -> List.of(token.kind(), token.value()))
              .toList());
    }
  }
}
