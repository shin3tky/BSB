package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuiltinDictionaryTest {
  @Test
  void publishesCoreArrayAndNumericWordsInSpecificationOrder() {
    assertEquals(
        List.of(
            "足す",
            "引く",
            "掛ける",
            "等しい",
            "表示する",
            "一行表示する",
            "改行する",
            "比べて小さい",
            "空の整数配列",
            "空の真偽配列",
            "空の文字配列",
            "空の文字列配列",
            "配列の長さ",
            "配列から取り出す",
            "配列の一部を取り出す",
            "配列の要素を置き換える",
            "配列の末尾へ追加する",
            "割った商",
            "割った剰余",
            "割った商と剰余",
            "小数で割る",
            "精度指定で割る",
            "絶対値",
            "最小値",
            "最大値",
            "小数に変換する",
            "整数に変換する",
            "整数に丸める",
            "最近接偶数丸め",
            "四捨五入",
            "0方向へ丸め",
            "正方向へ丸め",
            "負方向へ丸め",
            "空の小数配列"),
        BuiltinDictionary.words().subList(0, 34).stream().map(BuiltinWord::canonicalName).toList());
    assertTrue(
        BuiltinDictionary.words().subList(0, 7).stream()
            .allMatch(word -> word.featureGroup().equals("CORE")));
    assertEquals("FLOW", BuiltinDictionary.find("比べて小さい").orElseThrow().featureGroup());
    assertTrue(
        BuiltinDictionary.words().subList(8, 17).stream()
            .allMatch(word -> word.featureGroup().equals("ARRAY")));
    assertTrue(
        BuiltinDictionary.words().subList(17, 34).stream()
            .allMatch(word -> word.featureGroup().equals("NUM")));
    assertTrue(BuiltinDictionary.words().stream().allMatch(word -> word.aliases().isEmpty()));
  }

  @Test
  void publishesTypeRulesAndConsoleOutputCapability() {
    assertEquals(
        BuiltinTypeRule.SAME_TYPE_PAIR, BuiltinDictionary.find("等しい").orElseThrow().typeRule());
    assertEquals(
        BuiltinTypeRule.DISPLAYABLE, BuiltinDictionary.find("表示する").orElseThrow().typeRule());
    assertEquals(
        Set.of(BuiltinDictionary.CONSOLE_OUTPUT),
        BuiltinDictionary.find("一行表示する").orElseThrow().sideEffects());
    assertEquals(
        Set.of(BuiltinDictionary.CONSOLE_OUTPUT),
        BuiltinDictionary.find("改行する").orElseThrow().sideEffects());
    assertEquals(
        List.of(
            BuiltinOperation.ADD,
            BuiltinOperation.SUBTRACT,
            BuiltinOperation.MULTIPLY,
            BuiltinOperation.EQUALS,
            BuiltinOperation.DISPLAY,
            BuiltinOperation.DISPLAY_LINE,
            BuiltinOperation.NEWLINE,
            BuiltinOperation.LESS_THAN,
            BuiltinOperation.EMPTY_ARRAY,
            BuiltinOperation.EMPTY_ARRAY,
            BuiltinOperation.EMPTY_ARRAY,
            BuiltinOperation.EMPTY_ARRAY,
            BuiltinOperation.ARRAY_LENGTH,
            BuiltinOperation.ARRAY_GET,
            BuiltinOperation.ARRAY_SLICE,
            BuiltinOperation.ARRAY_REPLACE,
            BuiltinOperation.ARRAY_APPEND,
            BuiltinOperation.INTEGER_QUOTIENT,
            BuiltinOperation.INTEGER_REMAINDER,
            BuiltinOperation.INTEGER_QUOTIENT_AND_REMAINDER,
            BuiltinOperation.DECIMAL_DIVIDE,
            BuiltinOperation.PRECISION_DECIMAL_DIVIDE,
            BuiltinOperation.ABSOLUTE,
            BuiltinOperation.MINIMUM,
            BuiltinOperation.MAXIMUM,
            BuiltinOperation.INTEGER_TO_DECIMAL,
            BuiltinOperation.DECIMAL_TO_INTEGER,
            BuiltinOperation.ROUND_TO_INTEGER,
            BuiltinOperation.ROUNDING_MODE_VALUE,
            BuiltinOperation.ROUNDING_MODE_VALUE,
            BuiltinOperation.ROUNDING_MODE_VALUE,
            BuiltinOperation.ROUNDING_MODE_VALUE,
            BuiltinOperation.ROUNDING_MODE_VALUE,
            BuiltinOperation.EMPTY_ARRAY),
        BuiltinDictionary.words().subList(0, 34).stream().map(BuiltinWord::operation).toList());
  }

  @Test
  void publishesGenericRulesForAllArrayOperations() {
    assertEquals(
        List.of(
            BuiltinTypeRule.ARRAY_LENGTH,
            BuiltinTypeRule.ARRAY_GET,
            BuiltinTypeRule.ARRAY_SLICE,
            BuiltinTypeRule.ARRAY_REPLACE,
            BuiltinTypeRule.ARRAY_APPEND),
        BuiltinDictionary.words().subList(12, 17).stream().map(BuiltinWord::typeRule).toList());
    assertEquals(
        List.of("配列<T>", "整数", "T"),
        BuiltinDictionary.find("配列の要素を置き換える").orElseThrow().inputTypeNames());
    assertTrue(
        BuiltinDictionary.words().subList(12, 17).stream()
            .allMatch(word -> word.sideEffects().isEmpty()));
  }

  @Test
  void appendsSixExtendedArrayOperationsWithoutRenumberingExistingWords() {
    List<BuiltinWord> words = BuiltinDictionary.words().subList(189, 195);

    assertEquals(
        List.of("配列をつなぐ", "配列の先頭へ追加する", "配列を逆順にする", "配列に含まれる", "配列から検索する", "配列が空である"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            BuiltinTypeRule.ARRAY_CONCAT,
            BuiltinTypeRule.ARRAY_PREPEND,
            BuiltinTypeRule.ARRAY_REVERSE,
            BuiltinTypeRule.ARRAY_CONTAINS,
            BuiltinTypeRule.ARRAY_FIND,
            BuiltinTypeRule.ARRAY_IS_EMPTY),
        words.stream().map(BuiltinWord::typeRule).toList());
    assertTrue(words.stream().allMatch(word -> word.featureGroup().equals("ARRAY")));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
  }

  @Test
  void appendsFiveSelectionAndDeletionOperationsAfterTheFirstArrayExtension() {
    List<BuiltinWord> words = BuiltinDictionary.words().subList(195, 200);

    assertEquals(
        List.of("配列の先頭を任意で取り出す", "配列の末尾を任意で取り出す", "配列の先頭を削除する", "配列の末尾を削除する", "配列の一部を削除する"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            BuiltinTypeRule.ARRAY_EDGE_OPTIONAL,
            BuiltinTypeRule.ARRAY_EDGE_OPTIONAL,
            BuiltinTypeRule.ARRAY_DELETE_EDGE,
            BuiltinTypeRule.ARRAY_DELETE_EDGE,
            BuiltinTypeRule.ARRAY_DELETE_RANGE),
        words.stream().map(BuiltinWord::typeRule).toList());
    assertTrue(words.stream().allMatch(word -> word.featureGroup().equals("ARRAY")));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
  }

  @Test
  void appendsSixNonHigherOrderArrayOperationsAfterSelectionAndDeletion() {
    List<BuiltinWord> words = BuiltinDictionary.words().subList(200, 206);

    assertEquals(
        List.of("配列内の個数を数える", "配列の位置へ挿入する", "配列の位置を削除する", "開始位置から配列を検索する", "配列の重複を除く", "同じ値で配列を作る"),
        words.stream().map(BuiltinWord::canonicalName).toList());
    assertEquals(
        List.of(
            BuiltinTypeRule.ARRAY_COUNT,
            BuiltinTypeRule.ARRAY_INSERT,
            BuiltinTypeRule.ARRAY_DELETE_AT,
            BuiltinTypeRule.ARRAY_FIND_FROM,
            BuiltinTypeRule.ARRAY_UNIQUE,
            BuiltinTypeRule.ARRAY_REPEAT_VALUE),
        words.stream().map(BuiltinWord::typeRule).toList());
    assertTrue(words.stream().allMatch(word -> word.featureGroup().equals("ARRAY")));
    assertTrue(words.stream().allMatch(word -> word.sideEffects().isEmpty()));
  }

  @Test
  void publishesConcreteTypesForAllTypedEmptyArrayValues() {
    assertEquals(
        List.of(
            "配列<整数>",
            "配列<真偽>",
            "配列<文字>",
            "配列<文字列>",
            "配列<小数>",
            "配列<配列<整数>>",
            "配列<配列<真偽>>",
            "配列<配列<文字>>",
            "配列<配列<文字列>>",
            "配列<配列<小数>>",
            "配列<配列<JSON>>"),
        BuiltinDictionary.words().stream()
            .filter(word -> word.operation() == BuiltinOperation.EMPTY_ARRAY)
            .map(word -> word.outputTypeNames().getFirst())
            .toList());
    assertTrue(
        BuiltinDictionary.words().stream()
            .filter(word -> word.operation() == BuiltinOperation.EMPTY_ARRAY)
            .allMatch(
                word ->
                    word.inputTypeNames().isEmpty()
                        && word.sideEffects().isEmpty()
                        && word.typeRule() == BuiltinTypeRule.FIXED));
    assertEquals("NUM", BuiltinDictionary.find("空の小数配列").orElseThrow().featureGroup());
  }

  @Test
  void publishesTheSpecifiedLessThanEntry() {
    BuiltinWord comparison = BuiltinDictionary.find("比べて小さい").orElseThrow();

    assertEquals(List.of("N", "N"), comparison.inputTypeNames());
    assertEquals(List.of("真偽"), comparison.outputTypeNames());
    assertEquals(BuiltinTypeRule.SAME_NUMERIC_TYPE, comparison.typeRule());
    assertEquals(Set.of(), comparison.sideEffects());
    assertEquals("3 と 5 を 比べて小さい", comparison.example());
  }

  @Test
  void publishesNumericConstraintsWithoutUsingThemAsConcreteTypes() {
    assertEquals(
        BuiltinTypeRule.SAME_NUMERIC_TYPE, BuiltinDictionary.find("足す").orElseThrow().typeRule());
    assertEquals(List.of("N", "N"), BuiltinDictionary.find("最小値").orElseThrow().inputTypeNames());
    assertEquals(
        BuiltinTypeRule.INDEPENDENT_NUMERIC_INPUTS,
        BuiltinDictionary.find("小数で割る").orElseThrow().typeRule());
    assertEquals(
        List.of("数値", "数値", "整数", "丸め方法"),
        BuiltinDictionary.find("精度指定で割る").orElseThrow().inputTypeNames());
    assertEquals(
        List.of("整数", "整数"), BuiltinDictionary.find("割った商と剰余").orElseThrow().outputTypeNames());
  }

  @Test
  void publishesFiveTypedRoundingModeValues() {
    List<BuiltinWord> values = BuiltinDictionary.words().subList(28, 33);

    assertTrue(values.stream().allMatch(word -> word.inputTypeNames().isEmpty()));
    assertTrue(values.stream().allMatch(word -> word.outputTypeNames().equals(List.of("丸め方法"))));
    assertTrue(values.stream().allMatch(word -> word.typeRule() == BuiltinTypeRule.FIXED));
    assertTrue(
        values.stream().allMatch(word -> word.operation() == BuiltinOperation.ROUNDING_MODE_VALUE));
  }
}
