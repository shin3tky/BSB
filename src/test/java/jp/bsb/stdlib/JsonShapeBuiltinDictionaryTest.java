package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsonShapeBuiltinDictionaryTest {
  @Test
  void appendsFifteenFixedPureWordsInNormativeOrder() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("JSHAPE"))
            .toList();

    assertEquals(206, BuiltinDictionary.words().size());
    assertEquals(
        List.of(
            "JSONヌルの形状",
            "JSON真偽の形状",
            "JSON整数の形状",
            "JSON小数の形状",
            "JSON文字列の形状",
            "JSON配列の形状にする",
            "空のJSONオブジェクト形状",
            "JSON形状に必須キーを設定する",
            "JSON形状に任意キーを設定する",
            "JSON形状をヌル許容にする",
            "JSONの形状を検証する",
            "JSON形状失敗の種類を取り出す",
            "JSON形状失敗のパスを取り出す",
            "JSON形状失敗の期待種類を取り出す",
            "JSON形状失敗の実際種類を取り出す"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(List.of("結果<JSON,配列<JSON形状失敗>>"), words.get(10).outputTypeNames());
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(words.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
    assertTrue(words.stream().allMatch(BuiltinWord::returnsNormally));
  }
}
