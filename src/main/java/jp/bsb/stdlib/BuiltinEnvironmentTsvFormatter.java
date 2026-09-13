package jp.bsb.stdlib;

import java.util.Objects;
import java.util.stream.Collectors;

/** 実行環境に関係する辞書項目を、仕様順の決定的なTSVへ変換します。 */
public final class BuiltinEnvironmentTsvFormatter {
  private BuiltinEnvironmentTsvFormatter() {}

  /** 能力語、副作用語、ホスト入出力語をヘッダーと末尾LFつきで返します。 */
  public static String format(Iterable<BuiltinWord> words) {
    Objects.requireNonNull(words, "words");
    var result = new StringBuilder("word\tinputs\toutputs\tcapabilities\teffects\tfeature_group\n");
    for (BuiltinWord word : words) {
      if (!word.featureGroup().equals("IO")
          && word.capabilities().isEmpty()
          && word.sideEffects().isEmpty()) {
        continue;
      }
      result
          .append(word.canonicalName())
          .append('\t')
          .append(String.join(",", word.inputTypeNames()))
          .append('\t')
          .append(word.returnsNormally() ? String.join(",", word.outputTypeNames()) : "⊥")
          .append('\t')
          .append(join(word.capabilities()))
          .append('\t')
          .append(join(word.sideEffects()))
          .append('\t')
          .append(word.featureGroup())
          .append('\n');
    }
    return result.toString();
  }

  private static String join(java.util.Set<String> values) {
    return values.stream().collect(Collectors.joining(","));
  }
}
