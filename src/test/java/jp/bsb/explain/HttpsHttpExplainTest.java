package jp.bsb.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import org.junit.jupiter.api.Test;

class HttpsHttpExplainTest {
  @Test
  void reportsAllWordsFlatCapabilitiesAndConnectionSpecificMethodsWithoutRequestData() {
    String source =
        "APIは 論理接続。\n"
            + "送るとは （HTTP要求 -- 結果<HTTP応答,HTTP送信失敗>）\n"
            + "HTTP要求を送信する<API,POST>\nこと。\n"
            + "メインとは （--）\n"
            + "空のHTTP要求 「secret-v1/items」を HTTP要求に経路を設定する 送る 結果を捨てる\n"
            + "こと。\n";
    var analysis = new SourceChecker().check("http.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    ProgramExplanation explanation =
        new ProgramExplainer().explain(analysis.programForIrGeneration());

    assertEquals(182, explanation.builtinWords().size());
    assertEquals(List.of("connection.resolve", "http.send"), explanation.summary().capabilities());
    var parameterized = explanation.parameterizedCapabilities();
    assertEquals(
        List.of("resolve", "POST"), parameterized.summaryRequirements().getFirst().operations());
    var send = parameterized.declarations().getFirst().uses().getFirst();
    assertEquals("POST", send.operation());
    assertTrue(send.reachableFromMain());
    assertEquals(
        List.of("resolve", "POST"),
        parameterized.userWords().stream()
            .filter(word -> word.name().equals("メイン"))
            .findFirst()
            .orElseThrow()
            .requirements()
            .getFirst()
            .operations());
    String rendered = explanation.toString();
    assertFalse(rendered.contains("secret-v1/items"));
    assertFalse(rendered.contains("secret"));
  }
}
