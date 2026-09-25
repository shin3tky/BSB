package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResultBuiltinDictionaryTest {
  @Test
  void publishesSevenResultWordsAfterTheExistingDictionaryWithNormativeMetadata() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("RESULT"))
            .toList();

    assertEquals(206, BuiltinDictionary.words().size());
    assertEquals(
        List.of("成功にする", "失敗にする", "結果が成功である", "結果が失敗である", "結果から成功値を取り出す", "結果から失敗値を取り出す", "結果を捨てる"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            BuiltinTypeRule.RESULT_SUCCESS_WRAP,
            BuiltinTypeRule.RESULT_FAILURE_WRAP,
            BuiltinTypeRule.RESULT_PREDICATE,
            BuiltinTypeRule.RESULT_PREDICATE,
            BuiltinTypeRule.RESULT_SUCCESS_UNWRAP,
            BuiltinTypeRule.RESULT_FAILURE_UNWRAP,
            BuiltinTypeRule.RESULT_DROP),
        words.stream().map(BuiltinWord::typeRule).toList());
    assertTrue(words.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
    assertTrue(words.stream().allMatch(BuiltinWord::returnsNormally));
  }
}
