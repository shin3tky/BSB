package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class OptionalOptionalBuiltinIrTest {
  @Test
  void lowersEveryOptionalRuleWithItsConcreteEffect() {
    String source =
        "検査とは （整数 -- 整数）\n"
            + "    任意にする\n"
            + "    任意に値がある\n"
            + "    ならば\n"
            + "        任意から値を取り出す\n"
            + "    さもなければ\n"
            + "        任意を捨てる 0\n"
            + "    つぎに\n"
            + "こと。\n\n"
            + "検索とは （JSON 文字列 -- 任意<JSON>）\n"
            + "    JSONオブジェクトから任意値を取り出す\n"
            + "こと。\n\n"
            + "メインとは （--）\nこと。\n";
    var analysis =
        new SourceChecker().check("optional-ir.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    Map<String, IrStackEffect> effects =
        program.userWords().values().stream()
            .flatMap(word -> word.instructions().stream())
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().contains("任意"))
            .collect(
                Collectors.toMap(
                    Call::targetName,
                    call -> call.stackEffect().orElseThrow(),
                    (left, right) -> left));

    ValueType optionalInteger = ValueType.optionalOf(ValueType.INTEGER);
    assertEquals(
        new IrStackEffect(List.of(ValueType.INTEGER), List.of(optionalInteger)),
        effects.get("任意にする"));
    assertEquals(
        new IrStackEffect(List.of(optionalInteger), List.of(optionalInteger, ValueType.BOOLEAN)),
        effects.get("任意に値がある"));
    assertEquals(
        new IrStackEffect(List.of(optionalInteger), List.of(ValueType.INTEGER)),
        effects.get("任意から値を取り出す"));
    assertEquals(new IrStackEffect(List.of(optionalInteger), List.of()), effects.get("任意を捨てる"));
    assertEquals(
        new IrStackEffect(
            List.of(ValueType.JSON, ValueType.STRING),
            List.of(ValueType.optionalOf(ValueType.JSON))),
        effects.get("JSONオブジェクトから任意値を取り出す"));
  }
}
