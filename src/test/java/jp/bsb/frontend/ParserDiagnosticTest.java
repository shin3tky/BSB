package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.stream.Stream;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParserDiagnosticTest {
  @ParameterizedTest
  @MethodSource("syntaxFailures")
  void reportsTheSpecifiedSyntaxDiagnostic(
      String sourceName, DiagnosticCode code, int line, int column) throws IOException {
    var parsed = ParserTestSupport.parseResource(sourceName);

    assertFalse(parsed.parseResult().successful(), sourceName);
    assertEquals(1, parsed.diagnostics().diagnostics().size(), sourceName);
    Diagnostic diagnostic = parsed.diagnostics().diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line(), sourceName);
    assertEquals(
        column, diagnostic.location().displayPosition().orElseThrow().column(), sourceName);
  }

  @Test
  void reportsTheSpecifiedStructuredDetails() throws IOException {
    Diagnostic mixed = diagnostic("CORE-F011.bsb");
    Diagnostic separator = diagnostic("CORE-F012.bsb");
    Diagnostic comment = diagnostic("CORE-F012c.bsb");
    Diagnostic adjacency = diagnostic("CORE-F013.bsb");
    Diagnostic wordEnd = diagnostic("CORE-F014.bsb");
    Diagnostic topLevel = diagnostic("CORE-F015.bsb");

    assertDetails(mixed, "）", ")", "括弧の種類をそろえてください");
    assertDetails(separator, "--", "）", "ここに -- を追加してください");
    assertDetails(comment, "スタック効果の後のコメント", "# 効果", "コメントを次の行へ移してください");
    assertDetails(adjacency, "メインとは", "メイン とは", "空白を削除してください");
    assertEquals("メイン", wordEnd.fields().get("word"));
    assertDetails(wordEnd, "こと。", "EOF", "ここにこと。を追加してください");
    assertDetails(topLevel, "単語定義", "42", "単語定義の本体へ移してください");
  }

  @Test
  void recoversAtTheNextDefinitionWithoutProducingACascade() throws IOException {
    var parsed = ParserTestSupport.parseResource("CORE-F015.bsb");

    assertEquals(1, parsed.diagnostics().size());
    assertEquals(1, parsed.parseResult().partialProgram().definitions().size());
    assertEquals("メイン", parsed.parseResult().partialProgram().definitions().getFirst().name());
  }

  @Test
  void rejectsANonHorizontalGapBetweenWordEndAndMark() {
    var parsed = ParserTestSupport.parseText("終端.bsb", "メインとは （--）\nこと\n。\n");

    assertEquals(1, parsed.diagnostics().size());
    assertEquals(
        DiagnosticCode.E_EXPECTED_DEFINITION_END_MARK,
        parsed.diagnostics().diagnostics().getFirst().code());
  }

  private static Stream<Arguments> syntaxFailures() {
    return Stream.of(
        Arguments.of("CORE-F011.bsb", DiagnosticCode.E_MIXED_STACK_PARENTHESES, 1, 10),
        Arguments.of("CORE-F012.bsb", DiagnosticCode.E_EXPECTED_STACK_SEPARATOR, 1, 10),
        Arguments.of("CORE-F012c.bsb", DiagnosticCode.E_COMMENT_NOT_ALLOWED, 1, 7),
        Arguments.of("CORE-F013.bsb", DiagnosticCode.E_DEFINITION_ADJACENCY, 1, 5),
        Arguments.of("CORE-F014.bsb", DiagnosticCode.E_EXPECTED_WORD_END, 3, 1),
        Arguments.of("CORE-F015.bsb", DiagnosticCode.E_UNEXPECTED_TOP_LEVEL, 1, 1));
  }

  private static Diagnostic diagnostic(String sourceName) throws IOException {
    return ParserTestSupport.parseResource(sourceName).diagnostics().diagnostics().getFirst();
  }

  private static void assertDetails(
      Diagnostic diagnostic, String expected, String actual, String fix) {
    assertEquals(expected, diagnostic.expected().orElseThrow());
    assertEquals(actual, diagnostic.actual().orElseThrow());
    assertEquals(fix, diagnostic.fixes().getFirst());
  }
}
