package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/** {@code または ... つぎに} または {@code かつ ... つぎに} の短絡評価を表します。 */
public record ShortCircuitEvaluation(
    ShortCircuitOperator operator,
    SourceSpan openingSpan,
    List<BodyElement> rightBody,
    SourceSpan endSpan,
    SourceSpan span)
    implements BodyElement {
  public ShortCircuitEvaluation {
    Objects.requireNonNull(operator, "operator");
    Objects.requireNonNull(openingSpan, "openingSpan");
    rightBody = List.copyOf(rightBody);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }
}
