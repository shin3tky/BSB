package jp.bsb.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import org.junit.jupiter.api.Test;

class ProgramExplainerTest {
  @Test
  void publishesEveryBuiltinWithStablePublicMetadata() {
    ProgramExplanation explanation = explain("メインとは （--）\nこと。\n");

    assertEquals(189, explanation.builtinWords().size());
    assertEquals("足す", explanation.builtinWords().getFirst().name());
    assertEquals("JSONオブジェクトを構築する", explanation.builtinWords().get(183).name());
    assertEquals("大小文字を無視して比較する", explanation.builtinWords().get(186).name());
    assertEquals("HTTP応答を成功状態として検査する", explanation.builtinWords().getLast().name());
    assertEquals("WST", findBuiltin(explanation, "ファイルを読む").featureGroup());
    assertEquals("NARRAY", findBuiltin(explanation, "空の整数二次元配列").featureGroup());
    assertEquals("CONN", findBuiltin(explanation, "論理接続を確認する").featureGroup());
    assertEquals("sameNumericType", explanation.builtinWords().getFirst().typeRule());
    assertEquals("IO", findBuiltin(explanation, "一行を入力する").featureGroup());
    assertEquals("IO", findBuiltin(explanation, "現在日時を得る").featureGroup());
    assertEquals("JSON", findBuiltin(explanation, "JSONを解析する").featureGroup());
    assertEquals("JERG", findBuiltin(explanation, "JSONをポインターで任意参照する").featureGroup());
    assertFalse(findBuiltin(explanation, "終了する").stackEffect().returnsNormally());
    assertTrue(
        explanation.builtinWords().stream()
            .allMatch(
                word ->
                    !word.description().contains("文字列・正規表現")
                        && !word.description().contains("ホスト入出力")
                        && !word.example().isBlank()));
  }

  @Test
  void publishesJsonTypesInUserWordsAndBindings() {
    ProgramExplanation explanation =
        explain(
            "既定値は 定数 JSONヌル。\n\n"
                + "JSON列を保つとは （配列<JSON> -- 配列<JSON>）\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "こと。\n");

    assertEquals("JSON", explanation.bindings().getFirst().type());
    assertEquals(List.of("配列<JSON>"), findUser(explanation, "JSON列を保つ").stackEffect().inputs());
    assertEquals(List.of("配列<JSON>"), findUser(explanation, "JSON列を保つ").stackEffect().outputs());
  }

  @Test
  void publishesConcreteTwoDimensionalTypesAndAllSixTypedEmptyValues() {
    ProgramExplanation explanation =
        explain("保つとは （配列<配列<文字列>> -- 配列<配列<文字列>>）\n" + "こと。\n\n" + "メインとは （--）\n" + "こと。\n");

    assertEquals(List.of("配列<配列<文字列>>"), findUser(explanation, "保つ").stackEffect().inputs());
    assertEquals(
        6,
        explanation.builtinWords().stream()
            .filter(word -> word.featureGroup().equals("NARRAY"))
            .count());
    assertEquals(
        List.of("配列<配列<整数>>"), findBuiltin(explanation, "空の整数二次元配列").stackEffect().outputs());
  }

  @Test
  void propagatesCapabilitiesAcrossMutualRecursionAndSummarizesMain() {
    String source =
        "甲とは （--）\n"
            + "    はい ならば\n"
            + "        乙\n"
            + "    つぎに\n"
            + "こと。\n\n"
            + "乙とは （--）\n"
            + "    「記録」を エラー一行表示する\n"
            + "    はい ならば\n"
            + "        甲\n"
            + "    つぎに\n"
            + "こと。\n\n"
            + "未使用とは （--）\n"
            + "    一行を入力する\n"
            + "    入力結果を捨てる\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    甲\n"
            + "こと。\n";

    ProgramExplanation explanation = explain(source);
    var first = findUser(explanation, "甲");
    var second = findUser(explanation, "乙");
    var unused = findUser(explanation, "未使用");

    assertEquals(List.of(), first.directCapabilities());
    assertEquals(List.of("console.error"), first.capabilities());
    assertEquals(List.of("console.error"), second.directCapabilities());
    assertEquals(List.of("console.error"), second.capabilities());
    assertEquals(List.of("console.input"), unused.capabilities());
    assertFalse(unused.reachableFromMain());
    assertEquals(List.of("甲", "乙", "メイン"), explanation.summary().reachableUserWords());
    assertEquals(List.of("エラー一行表示する"), explanation.summary().reachableBuiltinWords());
    assertEquals(List.of("console.error"), explanation.summary().capabilities());
  }

  @Test
  void reachesTheLeastFixedPointForSelfRecursion() {
    String source =
        "自己とは （--）\n"
            + "    一行を入力する 入力結果を捨てる\n"
            + "    はい ならば\n"
            + "        自己\n"
            + "    つぎに\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    自己\n"
            + "こと。\n";

    ProgramExplanation explanation = explain(source);

    assertEquals(List.of("console.input"), findUser(explanation, "自己").capabilities());
    assertEquals(List.of("console.input"), explanation.summary().capabilities());
  }

  @Test
  void excludesStructurallyUnreachableCapabilityCalls() {
    String source =
        "止めるとは （--）\n"
            + "    0 を 終了する\n"
            + "    一行を入力する\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    止める\n"
            + "こと。\n";

    ProgramExplanation explanation = explain(source);
    var stop = findUser(explanation, "止める");

    assertEquals(List.of("process.exit"), stop.directCapabilities());
    assertEquals(List.of("process.exit"), explanation.summary().capabilities());
    assertEquals(List.of("終了する"), explanation.summary().reachableBuiltinWords());
    assertFalse(stop.stackEffect().returnsNormally());
  }

  @Test
  void explainsLogicalConnectionDeclarationsUsesAndTransitiveRequirements() {
    String source =
        "接続Aは 論理接続。\n"
            + "接続Bは 論理接続。\n\n"
            + "相互Aとは （--）\n"
            + "    論理接続を確認する<接続A>\n"
            + "    相互B\n"
            + "こと。\n\n"
            + "相互Bとは （--）\n"
            + "    論理接続を確認する<接続B>\n"
            + "    相互A\n"
            + "こと。\n\n"
            + "未使用とは （--）\n"
            + "    論理接続を確認する<接続B>\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    相互A\n"
            + "こと。\n";

    ProgramExplanation explanation = explain(source);
    var parameterized = explanation.parameterizedCapabilities();

    assertEquals(1, parameterized.schemaVersion());
    assertEquals(
        List.of("接続A", "接続B"),
        parameterized.declarations().stream().map(entry -> entry.name()).toList());
    assertEquals(
        List.of("相互A"),
        parameterized.declarations().getFirst().uses().stream()
            .map(use -> use.ownerWord())
            .toList());
    assertEquals(
        List.of(true, false),
        parameterized.declarations().getLast().uses().stream()
            .map(use -> use.reachableFromMain())
            .toList());
    assertEquals(
        List.of("接続A", "接続B"),
        parameterized.summaryRequirements().stream().map(entry -> entry.name()).toList());
    var first =
        parameterized.userWords().stream()
            .filter(word -> word.name().equals("相互A"))
            .findFirst()
            .orElseThrow();
    assertEquals(List.of("接続A"), first.directRequirements().stream().map(r -> r.name()).toList());
    assertEquals(List.of("接続A", "接続B"), first.requirements().stream().map(r -> r.name()).toList());
    assertEquals(List.of("connection.resolve"), explanation.summary().capabilities());
    assertEquals(explanation.summary().capabilities(), explanation.summary().effects());
  }

  @Test
  void explainsUnusedDeclarationsScopesAndReadWriteUses() {
    String source =
        "未使用は 定数 1。\n"
            + "合計は 変数 0。\n\n"
            + "メインとは （--）\n"
            + "    局所は 変数 2。\n"
            + "    局所 を 一行表示する\n"
            + "    3 を 局所 に 入れる\n"
            + "    合計 を 一行表示する\n"
            + "こと。\n";

    ProgramExplanation explanation = explain(source);

    assertEquals(3, explanation.bindings().size());
    assertTrue(explanation.bindings().getFirst().uses().isEmpty());
    assertEquals("global", explanation.bindings().getFirst().storage());
    assertEquals(1, explanation.bindings().getFirst().initializationOrder().orElseThrow());
    var local = explanation.bindings().getLast();
    assertEquals("local", local.storage());
    assertEquals(List.of("read", "write"), local.uses().stream().map(u -> u.kind()).toList());
    assertEquals("S2", local.scopeId());
    assertEquals(
        List.of("global", "wordBody"), explanation.scopes().stream().map(s -> s.kind()).toList());
  }

  @Test
  void explainsEverySyntaxScopeEvenWhenTheProgramHasNoBindings() {
    String source =
        "メインとは （--）\n"
            + "    はい ならば\n"
            + "        一行を入力する 入力結果を捨てる\n"
            + "    さもなければ\n"
            + "        「記録」を エラー表示する\n"
            + "    つぎに\n"
            + "    0 回だけ\n"
            + "        起動引数を得る 配列の長さ 一行表示する\n"
            + "    繰り返す\n"
            + "    ここから\n"
            + "        いいえ\n"
            + "    続く間\n"
            + "        1 を 待つ\n"
            + "    繰り返す\n"
            + "    【1】を 各要素について\n"
            + "        を 一行表示する\n"
            + "    繰り返す\n"
            + "こと。\n";

    ProgramExplanation explanation = explain(source);

    assertEquals(
        List.of(
            "global",
            "wordBody",
            "conditionalTrue",
            "conditionalFalse",
            "countedLoopBody",
            "conditionLoopCondition",
            "conditionLoopBody",
            "arrayLoopBody"),
        explanation.scopes().stream().map(s -> s.kind()).toList());
    assertEquals(
        List.of(
            "console.input", "console.output", "console.error", "process.arguments", "time.sleep"),
        explanation.summary().capabilities());
    assertEquals(explanation.summary().capabilities(), explanation.summary().effects());
    assertEquals(
        List.of("S1", "S2", "S2", "S2", "S2", "S2", "S2"),
        explanation.scopes().stream().skip(1).map(s -> s.parentId().orElseThrow()).toList());
  }

  @Test
  void keepsSiblingBindingsDistinctAcrossEveryBlockScope() {
    String source =
        "メインとは （--）\n"
            + "    はい ならば\n"
            + "        値は 定数 1。\n"
            + "        値 を 一行表示する\n"
            + "    さもなければ\n"
            + "        値は 定数 2。\n"
            + "        値 を 一行表示する\n"
            + "    つぎに\n"
            + "    0 回だけ\n"
            + "        回値は 定数 3。\n"
            + "    繰り返す\n"
            + "    ここから\n"
            + "        条件値は 定数 4。\n"
            + "        いいえ\n"
            + "    続く間\n"
            + "        本体値は 定数 5。\n"
            + "    繰り返す\n"
            + "    【1】を 各要素について\n"
            + "        を 一行表示する\n"
            + "        配列値は 定数 6。\n"
            + "    繰り返す\n"
            + "こと。\n";

    ProgramExplanation explanation = explain(source);

    assertEquals(6, explanation.bindings().size());
    assertEquals(
        List.of("値", "値"),
        explanation.bindings().stream()
            .filter(binding -> binding.name().equals("値"))
            .map(binding -> binding.name())
            .toList());
    assertEquals(
        List.of("S3", "S4"),
        explanation.bindings().stream()
            .filter(binding -> binding.name().equals("値"))
            .map(binding -> binding.scopeId())
            .toList());
    assertEquals(
        List.of("S3", "S4", "S5", "S6", "S7", "S8"),
        explanation.bindings().stream().map(binding -> binding.scopeId()).toList());
    assertEquals(
        List.of("read"),
        explanation.bindings().getFirst().uses().stream().map(u -> u.kind()).toList());
  }

  private static ProgramExplanation explain(String source) {
    var analysis =
        new SourceChecker().check("explain.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new ProgramExplainer().explain(analysis.programForIrGeneration());
  }

  private static ProgramExplanation.BuiltinWordEntry findBuiltin(
      ProgramExplanation explanation, String name) {
    return explanation.builtinWords().stream()
        .filter(word -> word.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static ProgramExplanation.UserWordEntry findUser(
      ProgramExplanation explanation, String name) {
    return explanation.userWords().stream()
        .filter(word -> word.name().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
