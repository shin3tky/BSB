package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class HttpsHttpIrTest {
  @Test
  void lowersTheResolvedConnectionMethodAndFixedEffect() {
    String source = "APIは 論理接続。\nメインとは （--）\n" + "空のHTTP要求 HTTP要求を送信する<API,PATCH> 結果を捨てる\nこと。\n";
    var analysis = new SourceChecker().check("http.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    Call send =
        program.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().equals("HTTP要求を送信する"))
            .findFirst()
            .orElseThrow();

    assertEquals("API", send.logicalConnection().orElseThrow().name());
    assertEquals("PATCH", send.logicalConnection().orElseThrow().httpMethod().orElseThrow());
    assertEquals(
        new IrStackEffect(
            List.of(ValueType.HTTP_REQUEST),
            List.of(ValueType.resultOf(ValueType.HTTP_RESPONSE, ValueType.HTTP_SEND_FAILURE))),
        send.stackEffect().orElseThrow());
  }
}
