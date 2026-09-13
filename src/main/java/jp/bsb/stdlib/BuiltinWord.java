package jp.bsb.stdlib;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 静的検査と実行系が共有する組み込み単語の辞書項目です。
 *
 * <p>【コンピュータ科学の観点：単一の信頼できる情報源】 型検査器と実行系が別々に組み込み語の一覧を持つと、一方だけを変更した際に「検査には通るが実行できない」状態が生じます。
 * 辞書情報を1個の不変オブジェクトへまとめ、すべての処理が同じ項目を参照できるようにします。
 *
 * @param canonicalName 正規名
 * @param aliases 別名集合。言語コアでは常に空
 * @param description 一文の説明
 * @param inputTypeNames 辞書表記上の入力型列
 * @param outputTypeNames 辞書表記上の出力型列
 * @param typeRule 入力へ適用する型規則
 * @param operation 実行系が選択する処理。名前による再分岐を避ける識別子
 * @param capabilities 呼出し時に必要な実行環境能力
 * @param sideEffects 副作用能力の集合
 * @param returnsNormally 呼出し元の次命令へ戻り得る場合はtrue
 * @param featureGroup 機能グループID
 * @param example 使用例
 */
public record BuiltinWord(
    String canonicalName,
    Set<String> aliases,
    String description,
    List<String> inputTypeNames,
    List<String> outputTypeNames,
    BuiltinTypeRule typeRule,
    BuiltinOperation operation,
    Set<String> capabilities,
    Set<String> sideEffects,
    boolean returnsNormally,
    String featureGroup,
    String example) {
  /** 能力と副作用を同じ集合で表す辞書項目を作る簡略コンストラクタです。 */
  public BuiltinWord(
      String canonicalName,
      Set<String> aliases,
      String description,
      List<String> inputTypeNames,
      List<String> outputTypeNames,
      BuiltinTypeRule typeRule,
      BuiltinOperation operation,
      Set<String> sideEffects,
      String featureGroup,
      String example) {
    this(
        canonicalName,
        aliases,
        description,
        inputTypeNames,
        outputTypeNames,
        typeRule,
        operation,
        sideEffects,
        sideEffects,
        true,
        featureGroup,
        example);
  }

  /** 必須文字列を検証し、コレクションを不変コピーにします。 */
  public BuiltinWord {
    canonicalName = requireText(canonicalName, "canonicalName");
    aliases = orderedSet(aliases, "aliases");
    description = requireText(description, "description");
    inputTypeNames = List.copyOf(inputTypeNames);
    outputTypeNames = List.copyOf(outputTypeNames);
    Objects.requireNonNull(typeRule, "typeRule");
    Objects.requireNonNull(operation, "operation");
    capabilities = orderedSet(capabilities, "capabilities");
    sideEffects = orderedSet(sideEffects, "sideEffects");
    featureGroup = requireText(featureGroup, "featureGroup");
    example = requireText(example, "example");
  }

  private static Set<String> orderedSet(Set<String> values, String name) {
    Objects.requireNonNull(values, name);
    if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException(name + " must contain only non-blank names");
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(values));
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
