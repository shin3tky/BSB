package jp.bsb.frontend.ast;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code #} から物理行末までのコメントです。トップレベルと単語本体の両方に現れ得るため、2種類の親インターフェースを実装します。
 *
 * @param text {@code #} 自身を含む、正規化していないコメント本文
 * @param span コメントのソース範囲
 */
public record Comment(String text, SourceSpan span) implements TopLevelElement, BodyElement {
  /** コメント本文、先頭記号、ソース範囲を検証します。 */
  public Comment {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(span, "span");
    if (!text.startsWith("#")) {
      throw new IllegalArgumentException("comment text must start with #");
    }
  }
}
