package jp.bsb.analyzer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;

/** 利用可能名、構文専用名、将来予約名を一元分類します。 */
final class LanguageNames {
  static final String MAIN = "メイン";
  static final String ARRAY_TYPE = "配列";
  static final String OPTIONAL_TYPE = "任意";
  static final String RESULT_TYPE = "結果";
  static final Set<String> TYPE_CONSTRAINTS = Set.of("T", "E", "表示可能", "N", "数値");
  static final Set<String> BOOLEAN_VALUES = Set.of("はい", "いいえ");
  static final Set<String> PARTICLES = Set.of("を", "に", "と", "から", "で");
  static final Set<String> SYNTAX_NAMES =
      Set.of(
          "とは",
          "こと",
          "ならば",
          "さもなければ",
          "つぎに",
          "または",
          "かつ",
          "回だけ",
          "ここから",
          "続く間",
          "繰り返す",
          "打ち切る",
          "続ける",
          "戻る",
          "任意から値を取り出すか戻る",
          "結果から成功値を取り出すか戻る",
          "各要素について",
          "は",
          "定数",
          "変数",
          "論理接続",
          "作業領域",
          "入れる");
  static final Map<String, String> FUTURE_FEATURES = futureFeatures();

  private LanguageNames() {}

  static boolean reservedForDefinition(String name) {
    return !name.equals(MAIN)
        && (name.equals(ARRAY_TYPE)
            || name.equals(OPTIONAL_TYPE)
            || name.equals(RESULT_TYPE)
            || ValueType.fromSourceName(name).isPresent()
            || TYPE_CONSTRAINTS.contains(name)
            || BOOLEAN_VALUES.contains(name)
            || PARTICLES.contains(name)
            || SYNTAX_NAMES.contains(name)
            || BuiltinDictionary.find(name).isPresent()
            || FUTURE_FEATURES.containsKey(name));
  }

  static boolean reservedForValue(String name) {
    return name.equals(MAIN) || reservedForDefinition(name);
  }

  static Optional<NonCallableKind> currentNonCallableKind(String name) {
    if (name.equals(ARRAY_TYPE)
        || name.equals(OPTIONAL_TYPE)
        || name.equals(RESULT_TYPE)
        || ValueType.fromSourceName(name).isPresent()) {
      return Optional.of(new NonCallableKind("型名", "型名 " + name));
    }
    if (TYPE_CONSTRAINTS.contains(name)) {
      return Optional.of(new NonCallableKind("型制約", "型制約 " + name));
    }
    if (SYNTAX_NAMES.contains(name)) {
      return Optional.of(new NonCallableKind("構文名", "構文名 " + name));
    }
    if (name.equals(MAIN)) {
      return Optional.of(new NonCallableKind("特別名", "特別名 " + name));
    }
    return Optional.empty();
  }

  static List<String> predefinedConfusableNames() {
    var names = new java.util.ArrayList<String>();
    BuiltinDictionary.words().forEach(word -> names.add(word.canonicalName()));
    for (ValueType type : ValueType.values()) {
      names.add(type.sourceName());
    }
    names.add(ARRAY_TYPE);
    names.add(OPTIONAL_TYPE);
    names.add(RESULT_TYPE);
    names.addAll(BOOLEAN_VALUES.stream().sorted().toList());
    names.addAll(SYNTAX_NAMES.stream().sorted().toList());
    names.addAll(FUTURE_FEATURES.keySet());
    return List.copyOf(names);
  }

  private static Map<String, String> futureFeatures() {
    var result = new LinkedHashMap<String, String>();
    add(result, "将来予約", "JSON配列", "JSONオブジェクト", "ある", "ない");
    return Collections.unmodifiableMap(result);
  }

  private static void add(Map<String, String> target, String availability, String... names) {
    for (String name : names) {
      target.put(name, availability);
    }
  }

  record NonCallableKind(String fieldValue, String actualText) {}
}
