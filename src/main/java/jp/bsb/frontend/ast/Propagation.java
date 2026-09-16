package jp.bsb.frontend.ast;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/** 任意の値なしまたは結果の失敗を、現在の利用者定義単語から局所的に伝播します。 */
public record Propagation(Kind kind, String lexeme, SourceSpan span) implements BodyElement {
  public Propagation {
    Objects.requireNonNull(kind, "kind");
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    Objects.requireNonNull(span, "span");
  }

  /** 伝播するラッパーの種類です。 */
  public enum Kind {
    OPTIONAL,
    RESULT
  }
}
