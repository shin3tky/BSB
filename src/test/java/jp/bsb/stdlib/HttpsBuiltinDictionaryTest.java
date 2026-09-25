package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpsBuiltinDictionaryTest {
  @Test
  void appendsThirteenWordsInNormativeOrder() {
    List<BuiltinWord> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("HTTPS"))
            .toList();

    assertEquals(200, BuiltinDictionary.words().size());
    assertEquals(
        List.of(
            "空のHTTP要求",
            "HTTP要求に経路を設定する",
            "HTTP要求に問い合わせ項目を追加する",
            "HTTP要求にヘッダーを設定する",
            "HTTP要求にJSON本文を設定する",
            "HTTP要求に文字列本文を設定する",
            "HTTP要求にバイト列本文を設定する",
            "HTTP要求を送信する",
            "HTTP応答から状態コードを取り出す",
            "HTTP応答からヘッダー値一覧を取り出す",
            "HTTP応答から本文を取り出す",
            "HTTP送信失敗の種類を取り出す",
            "HTTP要求に本文がある"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertTrue(words.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertEquals(
        Set.of(BuiltinDictionary.CONNECTION_RESOLVE, BuiltinDictionary.HTTP_SEND),
        BuiltinDictionary.find("HTTP要求を送信する").orElseThrow().capabilities());
    assertTrue(
        words.stream()
            .filter(word -> word.operation() != BuiltinOperation.HTTP_SEND)
            .allMatch(word -> word.capabilities().isEmpty() && word.sideEffects().isEmpty()));
  }
}
