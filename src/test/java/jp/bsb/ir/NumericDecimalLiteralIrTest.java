package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.runtime.DecimalValue;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class NumericDecimalLiteralIrTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void lowersNormativeDecimalLexemesToCanonicalPushConstants() throws Exception {
    IrProgram program = ir(numericsBytes("sources/NUM-N001.bsb"));
    List<DecimalValue> values =
        program.mainWord().instructions().stream()
            .filter(PushConst.class::isInstance)
            .map(PushConst.class::cast)
            .map(PushConst::value)
            .map(DecimalValue.class::cast)
            .toList();

    assertEquals(List.of("0.0", "1.23"), values.stream().map(DecimalValue::displayText).toList());
    assertEquals(new DecimalValue(new BigDecimal("0")), values.get(0));
    assertEquals(new DecimalValue(new BigDecimal("1.23")), values.get(1));
  }

  @Test
  void carriesDecimalInitializersThroughGlobalAndLocalStorageSlots() {
    String source =
        "大域値は 変数 1.50。\n\n"
            + "メインとは （--）\n"
            + "    局所値は 定数 2.500。\n"
            + "    大域値 を 一行表示する\n"
            + "    局所値 を 一行表示する\n"
            + "こと。\n";
    IrProgram program = ir(source.getBytes(StandardCharsets.UTF_8));

    assertEquals(ValueType.DECIMAL, program.globalSlots().getFirst().valueType());
    assertEquals(ValueType.DECIMAL, program.mainWord().localSlots().getFirst().valueType());
    PushConst globalPush =
        assertInstanceOf(
            PushConst.class, program.globalInitializer().orElseThrow().instructions().getFirst());
    PushConst localPush =
        assertInstanceOf(PushConst.class, program.mainWord().instructions().getFirst());
    assertEquals("1.5", globalPush.value().displayText());
    assertEquals("2.5", localPush.value().displayText());
  }

  @Test
  void rejectsASyntheticDecimalPushWhoseDeclaredIrTypeIsDifferent() {
    var mainSymbol = new SymbolId(0);
    var main =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(new PushConst(new DecimalValue(new BigDecimal("1.0")), SPAN), new Return(SPAN)),
            List.of(),
            new IrStackEffect(List.of(), List.of(ValueType.INTEGER)));

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrProgram(
                    "invalid-decimal.bsb", mainSymbol, Map.of(mainSymbol, main), Map.of(), 2));
    assertEquals("Return stack differs from its IR word effect", failure.getMessage());
  }

  private static IrProgram ir(byte[] source) {
    var analysis = new SourceChecker().check("decimal.bsb", source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
  }

  private static byte[] numericsBytes(String relativePath) throws Exception {
    String resource = "/conformance/numerics/" + relativePath;
    try (var input = NumericDecimalLiteralIrTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
