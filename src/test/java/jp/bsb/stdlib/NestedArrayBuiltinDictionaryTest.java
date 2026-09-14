package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 多次元配列の型付き空2次元配列値の公開辞書契約を検証します。 */
class NestedArrayBuiltinDictionaryTest {
  @Test
  void appendsSixTypedEmptyMatricesInLeafTypeOrder() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("NARRAY"))
            .toList();

    assertEquals(184, BuiltinDictionary.words().size());
    assertEquals(
        List.of("空の整数二次元配列", "空の真偽二次元配列", "空の文字二次元配列", "空の文字列二次元配列", "空の小数二次元配列", "空のJSON二次元配列"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            "配列<配列<整数>>", "配列<配列<真偽>>", "配列<配列<文字>>", "配列<配列<文字列>>", "配列<配列<小数>>", "配列<配列<JSON>>"),
        words.stream().map(word -> word.outputTypeNames().getFirst()).toList());
    assertTrue(
        words.stream()
            .allMatch(
                word ->
                    word.operation() == BuiltinOperation.EMPTY_ARRAY
                        && word.typeRule() == BuiltinTypeRule.FIXED
                        && word.inputTypeNames().isEmpty()
                        && word.capabilities().isEmpty()
                        && word.sideEffects().isEmpty()));
  }
}
