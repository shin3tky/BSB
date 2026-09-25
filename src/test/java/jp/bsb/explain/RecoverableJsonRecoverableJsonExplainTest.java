package jp.bsb.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import org.junit.jupiter.api.Test;

class RecoverableJsonRecoverableJsonExplainTest {
  @Test
  void publishesTheStableTypeEffects() {
    var analysis =
        new SourceChecker()
            .check(
                "RecoverableJson-explain.bsb",
                ("解析とは （文字列 -- 結果<JSON,JSON解析失敗>）\n"
                        + "    JSONを解析して結果を返す\n"
                        + "こと。\n\n"
                        + "メインとは （--）\nこと。\n")
                    .getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());

    ProgramExplanation explanation =
        new ProgramExplainer().explain(analysis.programForIrGeneration());
    assertEquals(217, explanation.builtinWords().size());
    ProgramExplanation.BuiltinWordEntry parser =
        explanation.builtinWords().stream()
            .filter(word -> word.name().equals("JSONを解析して結果を返す"))
            .findFirst()
            .orElseThrow();
    assertEquals("RJSON", parser.featureGroup());
    assertEquals("fixed", parser.typeRule());
    assertEquals(List.of("文字列"), parser.stackEffect().inputs());
    assertEquals(List.of("結果<JSON,JSON解析失敗>"), parser.stackEffect().outputs());
    assertEquals(List.of(), parser.capabilities());
    assertEquals(List.of(), parser.effects());
  }
}
