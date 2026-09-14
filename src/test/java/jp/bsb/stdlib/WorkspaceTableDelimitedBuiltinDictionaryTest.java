package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 作業領域・区切り表のCSV/TSV語と失敗型の公開辞書契約を検証します。 */
class WorkspaceTableDelimitedBuiltinDictionaryTest {
  @Test
  void appendsEightFixedWordsInSpecificationOrder() {
    List<BuiltinWord> words = BuiltinDictionary.words();
    List<BuiltinWord> delimited = words.subList(words.size() - 23, words.size() - 15);

    assertEquals(182, words.size());
    assertEquals(
        List.of(
            "CSVを表として解析する",
            "TSVを表として解析する",
            "表をCSVに変換する",
            "表をTSVに変換する",
            "区切りテキスト解析失敗の種類を取り出す",
            "区切りテキスト解析失敗のバイト位置を取り出す",
            "区切りテキスト解析失敗の行を取り出す",
            "区切りテキスト解析失敗の列を取り出す"),
        delimited.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            "結果<配列<配列<文字列>>,区切りテキスト解析失敗>",
            "結果<配列<配列<文字列>>,区切りテキスト解析失敗>",
            "文字列",
            "文字列",
            "文字列",
            "整数",
            "整数",
            "整数"),
        delimited.stream().map(word -> word.outputTypeNames().getFirst()).toList());
    assertTrue(
        delimited.stream()
            .allMatch(
                word ->
                    word.featureGroup().equals("WST")
                        && word.typeRule() == BuiltinTypeRule.FIXED
                        && word.capabilities().isEmpty()
                        && word.sideEffects().isEmpty()));
  }

  @Test
  void publishesNonDisplayableNonComparableFailureType() {
    assertEquals(
        ValueType.DELIMITED_TEXT_PARSE_FAILURE,
        ValueType.fromSourceName("区切りテキスト解析失敗").orElseThrow());
    assertFalse(ValueType.DELIMITED_TEXT_PARSE_FAILURE.isArrayElementType());
    assertFalse(ValueTypeTraits.isDisplayable(ValueType.DELIMITED_TEXT_PARSE_FAILURE));
    assertFalse(ValueTypeTraits.isEqualityComparable(ValueType.DELIMITED_TEXT_PARSE_FAILURE));
  }
}
