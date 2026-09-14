package jp.bsb.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import org.junit.jupiter.api.Test;

class OptionalOptionalExplainTest {
  @Test
  void publishesAllOptionalRulesTypesAndPureFeatureMetadata() {
    String source =
        "既定は 定数 1 任意にする。\n\n"
            + "検索とは （JSON 文字列 -- 任意<JSON>）\n"
            + "    JSONオブジェクトから任意値を取り出す\n"
            + "こと。\n\n"
            + "メインとは （--）\nこと。\n";
    var checked =
        new SourceChecker().check("optional-explain.bsb", source.getBytes(StandardCharsets.UTF_8));
    ProgramExplanation explanation =
        new ProgramExplainer().explain(checked.programForIrGeneration());
    List<ProgramExplanation.BuiltinWordEntry> optionalWords =
        explanation.builtinWords().stream()
            .filter(word -> word.featureGroup().equals("OPT"))
            .toList();

    assertEquals(187, explanation.builtinWords().size());
    assertEquals(
        List.of("optionalWrap", "optionalPredicate", "optionalUnwrap", "optionalDrop", "fixed"),
        optionalWords.stream().map(ProgramExplanation.BuiltinWordEntry::typeRule).toList());
    assertEquals(List.of("T"), optionalWords.getFirst().stackEffect().inputs());
    assertEquals(List.of("任意<T>"), optionalWords.getFirst().stackEffect().outputs());
    assertEquals(List.of(), optionalWords.getLast().capabilities());
    assertEquals(List.of(), optionalWords.getLast().effects());
    assertEquals("任意<整数>", explanation.bindings().getFirst().type());
  }
}
