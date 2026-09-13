package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class TextRegexRegexLexerTest {
  @Test
  void keepsRawPatternFlagsAndEveryCodePointBoundary() {
    String source = "メインとは （--）\n" + "    正規表現「\\p{Han} # 、 か\u3099𠮷」smi\n" + "こと。\n";
    var lexed = LexerTestSupport.lexText("regex.bsb", source);

    assertFalse(lexed.diagnostics().hasErrors(), lexed.diagnostics().diagnostics().toString());
    Token token =
        lexed.result().tokens().stream()
            .filter(candidate -> candidate.kind() == TokenKind.REGEX_LITERAL)
            .findFirst()
            .orElseThrow();
    var metadata = token.regexMetadata().orElseThrow();
    assertEquals("\\p{Han} # 、 か\u3099𠮷", token.value());
    assertEquals("smi", metadata.flags());
    assertEquals("ims", metadata.canonicalFlags());
    assertEquals(
        token.value().codePointCount(0, token.value().length()) + 1,
        metadata.patternPositions().size());
    assertTrue(
        metadata.patternPositions().getLast().utf8Offset()
            > metadata.patternPositions().getFirst().utf8Offset());
  }

  @Test
  void doesNotMergeAnIdentifierAndASeparatedStringLiteral() {
    var lexed = LexerTestSupport.lexText("separate.bsb", "正規表現 「a」");

    assertEquals(
        List.of(TokenKind.IDENTIFIER, TokenKind.STRING_LITERAL),
        lexed.result().tokens().stream().map(Token::kind).toList());
  }

  @Test
  void reportsTheFourNormativeLexicalFailuresAtTheirExactPositions() throws Exception {
    assertFailure(
        "TEXT-F001.bsb",
        DiagnosticCode.E_UNTERMINATED_REGEX_LITERAL,
        2,
        5,
        Map.of("openedLine", "2", "openedColumn", "5"),
        "終わりかぎ括弧」",
        "入力末尾",
        "正規表現リテラルを」で閉じてください");
    assertFailure(
        "TEXT-F002.bsb",
        DiagnosticCode.E_NEWLINE_IN_REGEX_LITERAL,
        2,
        13,
        Map.of("openedLine", "2", "openedColumn", "5"),
        "改行を含まないrawパターン",
        "LF",
        "改行を\\nとして表すかパターンを1行にしてください");
    assertFailure(
        "TEXT-F003.bsb",
        DiagnosticCode.E_REGEX_FLAG,
        2,
        14,
        Map.of("flag", "x", "allowed", "i,m,s"),
        "フラグi,m,s",
        "x",
        "xを削除してください");
    assertFailure(
        "TEXT-F004.bsb",
        DiagnosticCode.E_REGEX_FLAG,
        2,
        15,
        Map.of("flag", "i", "reason", "duplicate"),
        "重複しないフラグ",
        "i",
        "2個目のiを削除してください");
  }

  private static void assertFailure(
      String resource,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix)
      throws Exception {
    var lexed = LexerTestSupport.lexTextRegexResource(resource);
    Diagnostic diagnostic = lexed.diagnostics().diagnostics().getFirst();

    assertEquals(1, lexed.diagnostics().diagnostics().size());
    assertEquals(code, diagnostic.code());
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line());
    assertEquals(column, diagnostic.location().displayPosition().orElseThrow().column());
    assertEquals(fields, diagnostic.fields());
    assertEquals(expected, diagnostic.expected().orElseThrow());
    assertEquals(actual, diagnostic.actual().orElseThrow());
    assertEquals(List.of(fix), diagnostic.fixes());

    byte[] source = TextRegexBytes("sources/" + resource);
    assertEquals(9, new SourceChecker().check(resource, source).exitCode());
  }

  private static byte[] TextRegexBytes(String path) throws Exception {
    try (var input =
        TextRegexRegexLexerTest.class.getResourceAsStream("/conformance/text-regex/" + path)) {
      if (input == null) {
        throw new IllegalArgumentException("missing TextRegex resource: " + path);
      }
      return input.readAllBytes();
    }
  }
}
