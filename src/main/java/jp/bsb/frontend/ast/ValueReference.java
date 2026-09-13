package jp.bsb.frontend.ast;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 定数または変数の現在値を読み出す名前利用です。実際の宣言との対応は名前解決段階で確定します。
 *
 * @param name Unicode正規化済みの名前
 * @param lexeme 元ソースに書かれた表記
 * @param span 名前のソース範囲
 */
public record ValueReference(String name, String lexeme, SourceSpan span) implements BodyElement {
  /** 必須値を検証します。 */
  public ValueReference {
    name = requireText(name, "name");
    lexeme = requireText(lexeme, "lexeme");
    Objects.requireNonNull(span, "span");
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
