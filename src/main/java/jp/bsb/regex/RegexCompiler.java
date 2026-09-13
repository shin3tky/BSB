package jp.bsb.regex;

/**
 * rawパターンをUnicode 16.0規範部分集合としてコンパイルする境界です。
 *
 * <p>実装はRE2/J 1.8を照合核に使いますが、エンジンの型、例外、Unicode表、位置表現をこのAPIから公開しません。
 */
@FunctionalInterface
public interface RegexCompiler {
  /**
   * rawパターンと正規順のフラグをコンパイルします。
   *
   * @param rawPattern 通常文字列のエスケープを適用していないパターン
   * @param canonicalFlags {@code i}、{@code m}、{@code s}の正規順部分列
   * @return 成功またはBSB診断へ変換済みの失敗
   */
  RegexCompilationResult compile(String rawPattern, String canonicalFlags);
}
