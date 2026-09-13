package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextRegexBuiltinDictionaryTest {
  @Test
  void publishesEveryTextFeatureOperationInNormativeOrderWithConcreteEffects() {
    List<BuiltinWord> words = BuiltinDictionary.words().subList(34, 57);

    assertEquals(
        List.of(
            "つなぐ",
            "文字列を比較する",
            "前後の空白を除く",
            "文字列の長さ",
            "文字列から取り出す",
            "文字列の一部を取り出す",
            "文字列から探す",
            "文字列を置き換える",
            "文字列を分割する",
            "文字列のコードポイント数",
            "文字列からコードポイントを取り出す",
            "コードポイント範囲で文字列の一部を取り出す",
            "コードポイント位置で文字列から探す",
            "整数を文字列に変換する",
            "小数を文字列に変換する",
            "文字列を整数に変換する",
            "文字列を小数に変換する",
            "正規表現に完全一致する",
            "正規表現を含む",
            "正規表現で最初を取り出す",
            "正規表現の名前付き部分を取り出す",
            "正規表現で置き換える",
            "正規表現で分割する"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertTrue(words.stream().allMatch(word -> word.featureGroup().equals("TEXT")));
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
    assertEquals(
        List.of("文字列", "正規表現", "文字列"),
        BuiltinDictionary.find("正規表現で置き換える").orElseThrow().inputTypeNames());
    assertEquals(
        List.of("配列<文字列>"), BuiltinDictionary.find("正規表現で分割する").orElseThrow().outputTypeNames());
  }
}
