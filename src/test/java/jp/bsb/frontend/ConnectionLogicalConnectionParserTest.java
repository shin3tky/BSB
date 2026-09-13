package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.frontend.ast.WordCall;
import org.junit.jupiter.api.Test;

class ConnectionLogicalConnectionParserTest {
  @Test
  void parsesADeclarationAndSeparateStaticResourceArgument() {
    var parsed =
        ParserTestSupport.parseText(
            "接続.bsb", "顧客管理APIは 論理接続。\n\n" + "メインとは （--）\n" + "    論理接続を確認する<顧客管理API>\n" + "こと。\n");

    assertTrue(parsed.parseResult().successful(), parsed.diagnostics().diagnostics().toString());
    var program = parsed.parseResult().programForAnalysis();
    assertEquals(
        List.of("顧客管理API"), program.logicalConnections().stream().map(c -> c.name()).toList());
    WordCall call = (WordCall) program.definitions().getFirst().body().getFirst();
    assertEquals("顧客管理API", call.logicalConnectionArgument().orElseThrow().name());
    assertTrue(call.explicitTypeArguments().isEmpty());
    assertEquals(4, call.span().start().line());
    assertEquals(5, call.span().start().column());
    assertEquals(23, call.span().end().column());
  }

  @Test
  void classifiesTheDeclarationKindAndCommaInTheStaticArgumentFrame() {
    var source = new SourceText("字句.bsb", "接続Aは 論理接続。\nメインとは （--）\n論理接続を確認する<接続A,接続B>\nこと。\n", 0);
    var diagnostics = new jp.bsb.diagnostics.DiagnosticCollector();
    LexResult result = new Lexer().lex(source, diagnostics);

    assertTrue(result.successful(), diagnostics.diagnostics().toString());
    assertTrue(
        result.tokens().stream()
            .anyMatch(t -> t.kind() == TokenKind.LOGICAL_CONNECTION_DECLARATION));
    assertTrue(result.tokens().stream().anyMatch(t -> t.kind() == TokenKind.RESULT_TYPE_SEPARATOR));
  }

  @Test
  void emitsEachNormativeSyntaxDiagnosticWithoutCascadingAtEof() {
    assertSyntaxFailure(
        "顧客管理APIは 論理接続 「secret」。\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_VALUE,
        Map.of("connection", "顧客管理API", "actual", "文字列"));
    assertSyntaxFailure(
        "メインとは （--）\n顧客管理APIは 論理接続。\nこと。\n",
        DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_SCOPE,
        Map.of("connection", "顧客管理API", "scope", "wordBody"));
    assertSyntaxFailure(
        "メインとは （--）\n論理接続を確認する\nこと。\n",
        DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START,
        Map.of("word", "論理接続を確認する"));
    assertSyntaxFailure(
        "メインとは （--）\n論理接続を確認する<>\nこと。\n",
        DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT,
        Map.of("word", "論理接続を確認する", "argumentIndex", "1"));
    assertSyntaxFailure(
        "メインとは （--）\n論理接続を確認する<接続A,接続B>\nこと。\n",
        DiagnosticCode.E_LOGICAL_CONNECTION_ARGUMENT_COUNT,
        Map.of("word", "論理接続を確認する", "expectedCount", "1", "actualCount", "2"));
    assertSyntaxFailure(
        "接続Aは 論理接続。\nメインとは （--）\n論理接続を確認する<接続A",
        DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END,
        Map.of("word", "論理接続を確認する", "connection", "接続A"));
  }

  @Test
  void requiresTheArgumentOpeningToBeAdjacent() {
    assertSyntaxFailure(
        "メインとは （--）\n論理接続を確認する <接続A>\nこと。\n",
        DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START,
        Map.of("word", "論理接続を確認する"));
  }

  private static void assertSyntaxFailure(
      String source, DiagnosticCode expectedCode, Map<String, String> expectedFields) {
    var parsed = ParserTestSupport.parseText("失敗.bsb", source);
    assertFalse(parsed.parseResult().successful());
    assertEquals(
        List.of(expectedCode),
        parsed.diagnostics().diagnostics().stream().map(d -> d.code()).toList(),
        parsed.diagnostics().diagnostics().toString());
    assertEquals(expectedFields, parsed.diagnostics().diagnostics().getFirst().fields());
  }
}
