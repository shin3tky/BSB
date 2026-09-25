package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HttpApiBuiltinDictionaryTest {
  @Test
  void appendsTwoFixedPureWordsInNormativeOrder() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("HTTP-API"))
            .toList();

    assertEquals(200, BuiltinDictionary.words().size());
    assertEquals(
        List.of("文字列表をフォームURL符号化する", "HTTP応答を成功状態として検査する"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of("配列<配列<文字列>>"),
        BuiltinDictionary.find("文字列表をフォームURL符号化する").orElseThrow().inputTypeNames());
    assertEquals(
        List.of("結果<HTTP応答,HTTP応答>"),
        BuiltinDictionary.find("HTTP応答を成功状態として検査する").orElseThrow().outputTypeNames());
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(
        words.stream()
            .allMatch(word -> word.capabilities().isEmpty() && word.sideEffects().isEmpty()));
  }
}
