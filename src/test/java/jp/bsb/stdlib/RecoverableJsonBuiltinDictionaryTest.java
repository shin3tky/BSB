package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecoverableJsonBuiltinDictionaryTest {
  @Test
  void appendsFiveFixedPureWordsWithNormativeEffects() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("RJSON"))
            .toList();

    assertEquals(226, BuiltinDictionary.words().size());
    assertEquals(
        List.of(
            "JSONを解析して結果を返す",
            "JSON解析失敗の種類を取り出す",
            "JSON解析失敗のバイト位置を取り出す",
            "JSON解析失敗の行を取り出す",
            "JSON解析失敗の列を取り出す"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            List.of("結果<JSON,JSON解析失敗>"),
            List.of("文字列"),
            List.of("整数"),
            List.of("整数"),
            List.of("整数")),
        words.stream().map(BuiltinWord::outputTypeNames).toList());
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(words.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
    assertTrue(words.stream().allMatch(BuiltinWord::returnsNormally));
  }
}
