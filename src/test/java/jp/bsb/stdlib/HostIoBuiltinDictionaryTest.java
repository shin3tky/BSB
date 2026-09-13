package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HostIoBuiltinDictionaryTest {
  @Test
  void publishesEveryImplementedHostIoWordInNormativeOrder() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("IO"))
            .toList();

    assertEquals(
        List.of(
            "一行を入力する",
            "入力行である",
            "入力終端である",
            "入力キャンセルである",
            "入力行を取り出す",
            "入力結果を捨てる",
            "エラー表示する",
            "エラー一行表示する",
            "エラー改行する",
            "終了する",
            "起動引数を得る",
            "プログラム名を得る",
            "プログラムの場所を得る",
            "待つ",
            "単調ミリ秒を得る",
            "現在日時を得る",
            "日時を文字列に変換する"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of("入力結果", "真偽"), BuiltinDictionary.find("入力行である").orElseThrow().outputTypeNames());
    assertEquals(
        List.of("文字列"), BuiltinDictionary.find("入力行を取り出す").orElseThrow().outputTypeNames());
  }

  @Test
  void capabilitiesAndEffectsAreIndependentOrderedMetadata() {
    BuiltinWord input = BuiltinDictionary.find("一行を入力する").orElseThrow();
    BuiltinWord error = BuiltinDictionary.find("エラー一行表示する").orElseThrow();
    BuiltinWord exit = BuiltinDictionary.find("終了する").orElseThrow();
    BuiltinWord predicate = BuiltinDictionary.find("入力終端である").orElseThrow();

    assertEquals(Set.of(BuiltinDictionary.CONSOLE_INPUT), input.capabilities());
    assertEquals(input.capabilities(), input.sideEffects());
    assertEquals(Set.of(BuiltinDictionary.CONSOLE_ERROR), error.capabilities());
    assertEquals(Set.of(BuiltinDictionary.PROCESS_EXIT), exit.capabilities());
    assertTrue(predicate.capabilities().isEmpty());
    assertTrue(predicate.sideEffects().isEmpty());
    assertTrue(input.returnsNormally());
    assertFalse(exit.returnsNormally());

    assertEquals(
        Set.of(BuiltinDictionary.PROCESS_ARGUMENTS),
        BuiltinDictionary.find("起動引数を得る").orElseThrow().capabilities());
    assertEquals(
        Set.of(BuiltinDictionary.PROGRAM_IDENTITY),
        BuiltinDictionary.find("プログラム名を得る").orElseThrow().capabilities());

    for (String stdout : List.of("表示する", "一行表示する", "改行する")) {
      BuiltinWord word = BuiltinDictionary.find(stdout).orElseThrow();
      assertEquals(Set.of(BuiltinDictionary.CONSOLE_OUTPUT), word.capabilities());
      assertEquals(word.capabilities(), word.sideEffects());
    }
  }
}
