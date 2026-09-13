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

class RecoverableJsonRecoverableJsonIrTest {
  @Test
  void lowersTheFiveFixedWordsWithConcretePublicTypes() {
    String source =
        "解析とは （文字列 -- 結果<JSON,JSON解析失敗>）\n"
            + "    JSONを解析して結果を返す\n"
            + "こと。\n\n"
            + "種類とは （JSON解析失敗 -- 文字列）\n"
            + "    JSON解析失敗の種類を取り出す\n"
            + "こと。\n\n"
            + "バイトとは （JSON解析失敗 -- 整数）\n"
            + "    JSON解析失敗のバイト位置を取り出す\n"
            + "こと。\n\n"
            + "行とは （JSON解析失敗 -- 整数）\n"
            + "    JSON解析失敗の行を取り出す\n"
            + "こと。\n\n"
            + "列とは （JSON解析失敗 -- 整数）\n"
            + "    JSON解析失敗の列を取り出す\n"
            + "こと。\n\n"
            + "メインとは （--）\nこと。\n";
    var analysis =
        new SourceChecker()
            .check("RecoverableJson-ir.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    Map<String, IrStackEffect> effects =
        program.userWords().values().stream()
            .flatMap(word -> word.instructions().stream())
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().startsWith("JSON"))
            .collect(Collectors.toMap(Call::targetName, call -> call.stackEffect().orElseThrow()));

    ValueType result = ValueType.resultOf(ValueType.JSON, ValueType.JSON_PARSE_FAILURE);
    assertEquals(
        new IrStackEffect(List.of(ValueType.STRING), List.of(result)),
        effects.get("JSONを解析して結果を返す"));
    assertEquals(
        new IrStackEffect(List.of(ValueType.JSON_PARSE_FAILURE), List.of(ValueType.STRING)),
        effects.get("JSON解析失敗の種類を取り出す"));
    for (String name : List.of("JSON解析失敗のバイト位置を取り出す", "JSON解析失敗の行を取り出す", "JSON解析失敗の列を取り出す")) {
      assertEquals(
          new IrStackEffect(List.of(ValueType.JSON_PARSE_FAILURE), List.of(ValueType.INTEGER)),
          effects.get(name));
    }
  }
}
