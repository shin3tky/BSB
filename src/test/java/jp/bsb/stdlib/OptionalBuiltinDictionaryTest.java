package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OptionalBuiltinDictionaryTest {
  @Test
  void publishesFiveOptionalWordsInNormativeOrderAndMetadata() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("OPT"))
            .toList();

    assertEquals(187, BuiltinDictionary.words().size());
    assertEquals(
        List.of("任意にする", "任意に値がある", "任意から値を取り出す", "任意を捨てる", "JSONオブジェクトから任意値を取り出す"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            BuiltinTypeRule.OPTIONAL_WRAP,
            BuiltinTypeRule.OPTIONAL_PREDICATE,
            BuiltinTypeRule.OPTIONAL_UNWRAP,
            BuiltinTypeRule.OPTIONAL_DROP,
            BuiltinTypeRule.FIXED),
        words.stream().map(BuiltinWord::typeRule).toList());
    assertTrue(words.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
  }
}
