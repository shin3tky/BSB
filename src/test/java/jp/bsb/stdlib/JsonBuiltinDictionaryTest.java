package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonBuiltinDictionaryTest {
  private static final List<String> NAMES =
      List.of(
          "JSONヌル",
          "空のJSON配列",
          "空のJSONオブジェクト",
          "JSONを解析する",
          "JSONを文字列に変換する",
          "真偽をJSONに変換する",
          "整数をJSONに変換する",
          "小数をJSONに変換する",
          "文字列をJSONに変換する",
          "JSONから真偽を取り出す",
          "JSONから整数を取り出す",
          "JSONから小数を取り出す",
          "JSONから文字列を取り出す",
          "JSONヌルである",
          "JSON真偽である",
          "JSON整数である",
          "JSON小数である",
          "JSON文字列である",
          "JSON配列である",
          "JSONオブジェクトである",
          "JSONから配列に変換する",
          "JSON配列に変換する",
          "JSON配列の長さ",
          "JSON配列から取り出す",
          "JSON配列の一部を取り出す",
          "JSON配列の要素を置き換える",
          "JSON配列の末尾へ追加する",
          "JSONオブジェクトの要素数",
          "JSONオブジェクトのキー一覧",
          "JSONオブジェクトにキーがある",
          "JSONオブジェクトから必須値を取り出す",
          "JSONオブジェクトに設定する",
          "JSONオブジェクトから削除する");

  @Test
  void publishesAllJsonWordsInNormativeOrderWithPureFixedMetadata() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("JSON"))
            .toList();

    assertEquals(226, BuiltinDictionary.words().size());
    assertEquals(NAMES, words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(33, words.stream().map(BuiltinWord::operation).distinct().count());
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(words.stream().allMatch(word -> word.aliases().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
    assertTrue(words.stream().allMatch(BuiltinWord::returnsNormally));
    assertTrue(words.stream().allMatch(word -> !word.description().isBlank()));
    assertTrue(words.stream().allMatch(word -> !word.example().isBlank()));
  }

  @Test
  void appendsJsonErgonomicsWordsAsAnIndependentFeatureGroup() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("JERG"))
            .toList();

    assertEquals(
        List.of("JSONをポインターで任意参照する", "JSONオブジェクトを構築する"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(words.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
  }

  @Test
  void keepsPredicatesAndKeyLookupConcreteStackEffects() {
    for (String name :
        List.of(
            "JSONヌルである",
            "JSON真偽である",
            "JSON整数である",
            "JSON小数である",
            "JSON文字列である",
            "JSON配列である",
            "JSONオブジェクトである")) {
      BuiltinWord predicate = BuiltinDictionary.find(name).orElseThrow();
      assertEquals(List.of("JSON"), predicate.inputTypeNames(), name);
      assertEquals(List.of("JSON", "真偽"), predicate.outputTypeNames(), name);
    }

    BuiltinWord contains = BuiltinDictionary.find("JSONオブジェクトにキーがある").orElseThrow();
    assertEquals(List.of("JSON", "文字列"), contains.inputTypeNames());
    assertEquals(List.of("JSON", "真偽"), contains.outputTypeNames());
    assertEquals(Set.of(), contains.sideEffects());
  }
}
