package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class ConnectionLogicalConnectionIrTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void lowersTheResolvedConnectionAndEmptyEffectIntoTheCall() {
    String source = "顧客管理APIは 論理接続。\n\n" + "メインとは （--）\n" + "    論理接続を確認する<顧客管理API>\n" + "こと。\n";
    var analysis = new SourceChecker().check("接続.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());

    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    Call call =
        program.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .findFirst()
            .orElseThrow();

    assertEquals(new IrStackEffect(List.of(), List.of()), call.stackEffect().orElseThrow());
    assertEquals("顧客管理API", call.logicalConnection().orElseThrow().name());
    assertEquals("resolve", call.logicalConnection().orElseThrow().operation());
    assertEquals(1, call.logicalConnection().orElseThrow().declarationNameSpan().start().line());
    assertEquals(4, call.logicalConnection().orElseThrow().argumentSpan().start().line());
  }

  @Test
  void rejectsASyntheticConnectionCheckWithoutAResolvedResourceReference() {
    var mainSymbol = new SymbolId(0);
    var builtinSymbol = new SymbolId(1);
    var effect = new IrStackEffect(List.of(), List.of());
    var main =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(
                new Call(builtinSymbol, "論理接続を確認する", List.of(), effect, SPAN), new Return(SPAN)),
            List.of(),
            effect);

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrProgram(
                    "不正接続IR.bsb",
                    mainSymbol,
                    Map.of(mainSymbol, main),
                    Map.of(builtinSymbol, BuiltinDictionary.find("論理接続を確認する").orElseThrow()),
                    2));

    assertEquals(
        "only logical connection checks may carry a logical connection reference",
        failure.getMessage());
  }
}
