package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.frontend.ast.WordCall;
import org.junit.jupiter.api.Test;

class HttpsHttpParserTest {
  @Test
  void parsesAllSixStaticMethodsAndPreservesTheConnectionForm() {
    for (String method : List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")) {
      var parsed =
          ParserTestSupport.parseText(
              "http.bsb",
              "APIは 論理接続。\nメインとは （--）\n"
                  + "空のHTTP要求 HTTP要求を送信する<API,"
                  + method
                  + "> 結果を捨てる\nこと。\n");
      assertTrue(parsed.parseResult().successful(), parsed.diagnostics().diagnostics().toString());
      WordCall send =
          parsed.parseResult().programForAnalysis().definitions().getFirst().body().stream()
              .filter(WordCall.class::isInstance)
              .map(WordCall.class::cast)
              .filter(call -> call.name().equals("HTTP要求を送信する"))
              .findFirst()
              .orElseThrow();
      assertEquals("API", send.logicalConnectionArgument().orElseThrow().name());
      assertEquals(
          method,
          send.logicalConnectionArgument().orElseThrow().httpMethod().orElseThrow().value());
    }

    var Connection =
        ParserTestSupport.parseText(
            "connection.bsb", "APIは 論理接続。\nメインとは （--）\n論理接続を確認する<API>\nこと。\n");
    assertTrue(Connection.parseResult().successful());
  }

  @Test
  void emitsEachNewStaticArgumentDiagnosticWithoutCascading() {
    assertSyntaxFailure(
        "HTTP要求を送信する\n",
        DiagnosticCode.E_EXPECTED_HTTP_ARGUMENT_START,
        Map.of("word", "HTTP要求を送信する"));
    assertSyntaxFailure(
        "HTTP要求を送信する<,POST>\n",
        DiagnosticCode.E_EXPECTED_HTTP_CONNECTION_ARGUMENT,
        Map.of("word", "HTTP要求を送信する", "argumentIndex", "1"));
    assertSyntaxFailure(
        "HTTP要求を送信する<API,>\n",
        DiagnosticCode.E_EXPECTED_HTTP_METHOD_ARGUMENT,
        Map.of("word", "HTTP要求を送信する", "argumentIndex", "2"));
    assertSyntaxFailure(
        "HTTP要求を送信する<API,POST,GET>\n",
        DiagnosticCode.E_HTTP_ARGUMENT_COUNT,
        Map.of("word", "HTTP要求を送信する", "expectedCount", "2", "actualCount", "3"));
    assertSyntaxFailure(
        "HTTP要求を送信する<API,POST\n",
        DiagnosticCode.E_EXPECTED_HTTP_ARGUMENT_END,
        Map.of("word", "HTTP要求を送信する", "connection", "API", "method", "POST"));
    assertSyntaxFailure(
        "HTTP要求を送信する<API,post>\n",
        DiagnosticCode.E_HTTP_METHOD_INVALID,
        Map.of(
            "word", "HTTP要求を送信する",
            "method", "post",
            "allowedMethods", "GET,HEAD,POST,PUT,PATCH,DELETE"));
  }

  private static void assertSyntaxFailure(
      String statement, DiagnosticCode code, Map<String, String> fields) {
    var parsed = ParserTestSupport.parseText("failure.bsb", "メインとは （--）\n" + statement + "こと。\n");
    assertFalse(parsed.parseResult().successful());
    assertEquals(
        List.of(code), parsed.diagnostics().diagnostics().stream().map(d -> d.code()).toList());
    assertEquals(fields, parsed.diagnostics().diagnostics().getFirst().fields());
  }
}
