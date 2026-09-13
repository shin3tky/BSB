package jp.bsb.frontend.ast;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/** HTTP送信語へ渡す、値や型にはならない静的method引数です。 */
public record HttpMethodArgument(String value, String lexeme, SourceSpan span) {
  /** 正規method、原表記、位置を検証します。 */
  public HttpMethodArgument {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    Objects.requireNonNull(span, "span");
  }
}
