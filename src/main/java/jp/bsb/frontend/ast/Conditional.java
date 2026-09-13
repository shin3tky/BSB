package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code ならば ... (さもなければ ...)? つぎに} という条件分岐を表します。
 *
 * <p>【コンピュータ科学の観点：木構造】真側と偽側を別々の子リストとして持つことで、入れ子の終了語がどの開始語に対応するかをASTの形そのもので表現できます。
 *
 * @param openingSpan {@code ならば} の範囲
 * @param trueBody 条件が真の場合の本体
 * @param elseSpan {@code さもなければ} の範囲。偽側がなければ空
 * @param falseBody 条件が偽の場合の本体。偽側がなければ空
 * @param endSpan {@code つぎに} の範囲
 * @param span 開始語の先頭から終了語の直後までの範囲
 */
public record Conditional(
    SourceSpan openingSpan,
    List<BodyElement> trueBody,
    Optional<SourceSpan> elseSpan,
    List<BodyElement> falseBody,
    SourceSpan endSpan,
    SourceSpan span)
    implements BodyElement {
  /** 各本体を不変にし、偽側本体と{@code さもなければ}の対応を検査します。 */
  public Conditional {
    Objects.requireNonNull(openingSpan, "openingSpan");
    trueBody = List.copyOf(trueBody);
    elseSpan = Objects.requireNonNull(elseSpan, "elseSpan");
    falseBody = List.copyOf(falseBody);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
    if (elseSpan.isEmpty() && !falseBody.isEmpty()) {
      throw new IllegalArgumentException("false body requires an else marker");
    }
  }

  /**
   * 偽側が明示されているかを返します。
   *
   * @return {@code さもなければ}があればtrue
   */
  public boolean hasElse() {
    return elseSpan.isPresent();
  }
}
