package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code 【...】}形式の配列リテラルです。
 *
 * @param openingSpan 開始記号{@code 【}の範囲
 * @param elements ソース順の要素式。{@code 【】}では空
 * @param endSpan 終了記号{@code 】}の範囲
 * @param span 開始記号から終了記号直後までの範囲
 */
public record ArrayLiteral(
    SourceSpan openingSpan, List<ArrayElement> elements, SourceSpan endSpan, SourceSpan span)
    implements BodyElement {
  /** 要素列を不変コピーし、必須位置を検証します。 */
  public ArrayLiteral {
    Objects.requireNonNull(openingSpan, "openingSpan");
    elements = List.copyOf(elements);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }
}
