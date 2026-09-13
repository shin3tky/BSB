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

class ResultResultIrTest {
  @Test
  void lowersEveryResultRuleWithBothSelectedAndNonSelectedConcreteTypes() {
    String source =
        "成功化とは （整数 -- 結果<整数,文字列>）\n"
            + "    成功にする<整数,文字列>\n"
            + "こと。\n\n"
            + "失敗化とは （文字列 -- 結果<整数,文字列>）\n"
            + "    失敗にする<整数,文字列>\n"
            + "こと。\n\n"
            + "成功判定とは （結果<整数,文字列> -- 結果<整数,文字列> 真偽）\n"
            + "    結果が成功である\n"
            + "こと。\n\n"
            + "失敗判定とは （結果<整数,文字列> -- 結果<整数,文字列> 真偽）\n"
            + "    結果が失敗である\n"
            + "こと。\n\n"
            + "成功取出しとは （結果<整数,文字列> -- 整数）\n"
            + "    結果から成功値を取り出す\n"
            + "こと。\n\n"
            + "失敗取出しとは （結果<整数,文字列> -- 文字列）\n"
            + "    結果から失敗値を取り出す\n"
            + "こと。\n\n"
            + "破棄とは （結果<整数,文字列> --）\n"
            + "    結果を捨てる\n"
            + "こと。\n\n"
            + "メインとは （--）\nこと。\n";
    var analysis =
        new SourceChecker().check("result-ir.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    Map<String, IrStackEffect> effects =
        program.userWords().values().stream()
            .flatMap(word -> word.instructions().stream())
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().contains("結果") || call.targetName().endsWith("にする"))
            .collect(Collectors.toMap(Call::targetName, call -> call.stackEffect().orElseThrow()));

    ValueType result = ValueType.resultOf(ValueType.INTEGER, ValueType.STRING);
    assertEquals(
        new IrStackEffect(List.of(ValueType.INTEGER), List.of(result)), effects.get("成功にする"));
    assertEquals(
        new IrStackEffect(List.of(ValueType.STRING), List.of(result)), effects.get("失敗にする"));
    assertEquals(
        new IrStackEffect(List.of(result), List.of(result, ValueType.BOOLEAN)),
        effects.get("結果が成功である"));
    assertEquals(
        new IrStackEffect(List.of(result), List.of(result, ValueType.BOOLEAN)),
        effects.get("結果が失敗である"));
    assertEquals(
        new IrStackEffect(List.of(result), List.of(ValueType.INTEGER)),
        effects.get("結果から成功値を取り出す"));
    assertEquals(
        new IrStackEffect(List.of(result), List.of(ValueType.STRING)), effects.get("結果から失敗値を取り出す"));
    assertEquals(new IrStackEffect(List.of(result), List.of()), effects.get("結果を捨てる"));
  }
}
