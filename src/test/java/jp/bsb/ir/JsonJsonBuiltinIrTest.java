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

class JsonJsonBuiltinIrTest {
  @Test
  void lowersJsonWordsToOrdinaryCallsWithConcreteEffects() {
    String source =
        "メインとは （--）\n"
            + "    空のJSONオブジェクト と 「x」 を JSONオブジェクトにキーがある\n"
            + "    一行表示する\n"
            + "    JSONから配列に変換する\n"
            + "    JSON配列に変換する\n"
            + "    一行表示する\n"
            + "こと。\n";
    var analysis =
        new SourceChecker().check("json-builtins.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    IrProgram repeated =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    assertEquals(program.mainWord().instructions(), repeated.mainWord().instructions());
    assertEquals(program.builtinWords(), repeated.builtinWords());
    List<Call> jsonCalls =
        program.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().contains("JSON"))
            .toList();
    Map<String, IrStackEffect> effects =
        jsonCalls.stream()
            .collect(
                Collectors.toMap(
                    Call::targetName,
                    call -> call.stackEffect().orElseThrow(),
                    (first, ignored) -> first));

    assertEquals(
        List.of("空のJSONオブジェクト", "JSONオブジェクトにキーがある", "JSONから配列に変換する", "JSON配列に変換する"),
        jsonCalls.stream().map(Call::targetName).toList());
    assertEquals(
        new IrStackEffect(List.of(), List.of(ValueType.JSON)), effects.get("空のJSONオブジェクト"));
    assertEquals(
        new IrStackEffect(
            List.of(ValueType.JSON, ValueType.STRING), List.of(ValueType.JSON, ValueType.BOOLEAN)),
        effects.get("JSONオブジェクトにキーがある"));
    assertEquals(
        new IrStackEffect(List.of(ValueType.JSON), List.of(ValueType.arrayOf(ValueType.JSON))),
        effects.get("JSONから配列に変換する"));
    assertEquals(
        new IrStackEffect(List.of(ValueType.arrayOf(ValueType.JSON)), List.of(ValueType.JSON)),
        effects.get("JSON配列に変換する"));
  }
}
