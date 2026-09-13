package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class HttpsHttpAnalyzerTest {
  @Test
  void resolvesAForwardConnectionAndChecksTheFixedSendEffect() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n" + "空のHTTP要求 HTTP要求を送信する<API,POST> 結果を捨てる\n" + "こと。\nAPIは 論理接続。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    var use = result.programForIrGeneration().logicalConnectionResolution().uses().getFirst();
    assertEquals("API", use.declaration().name());
    assertEquals("POST", use.httpMethod().orElseThrow());
    assertEquals("resolve", use.operation());
    assertEquals(
        List.of(ValueType.HTTP_REQUEST),
        result.programForIrGeneration().findCallSignature(use.call()).orElseThrow().inputTypes());
    assertEquals(
        List.of(ValueType.resultOf(ValueType.HTTP_RESPONSE, ValueType.HTTP_SEND_FAILURE)),
        result.programForIrGeneration().findCallSignature(use.call()).orElseThrow().outputTypes());
  }
}
