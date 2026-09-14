package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ByteSequenceBuiltinDictionaryTest {
  @Test
  void appendsElevenFixedPureWordsInNormativeOrder() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("BYTES"))
            .toList();

    assertEquals(184, BuiltinDictionary.words().size());
    assertEquals(
        List.of(
            "空のバイト列",
            "バイト列の長さ",
            "バイト列の一部を取り出す",
            "文字列をUTF8バイト列に変換する",
            "バイト列をUTF8文字列に変換して結果を返す",
            "UTF8復号失敗の種類を取り出す",
            "UTF8復号失敗のバイト位置を取り出す",
            "バイト列をBase64文字列に変換する",
            "Base64文字列をバイト列に変換して結果を返す",
            "Base64復号失敗の種類を取り出す",
            "Base64復号失敗の文字位置を取り出す"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            List.of("バイト列"),
            List.of("整数"),
            List.of("バイト列"),
            List.of("バイト列"),
            List.of("結果<文字列,UTF8復号失敗>"),
            List.of("文字列"),
            List.of("整数"),
            List.of("文字列"),
            List.of("結果<バイト列,Base64復号失敗>"),
            List.of("文字列"),
            List.of("整数")),
        words.stream().map(BuiltinWord::outputTypeNames).toList());
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(words.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
    assertTrue(words.stream().allMatch(BuiltinWord::returnsNormally));
  }
}
