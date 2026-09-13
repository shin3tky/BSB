package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code 回だけ ... 繰り返す} という回数ループを表します。
 *
 * @param openingSpan {@code 回だけ} の範囲
 * @param body 反復する本体
 * @param endSpan {@code 繰り返す} の範囲
 * @param span 開始語の先頭から終了語の直後までの範囲
 */
public record CountedLoop(
    SourceSpan openingSpan, List<BodyElement> body, SourceSpan endSpan, SourceSpan span)
    implements BodyElement {
  /** 反復本体を不変の一覧として保持します。 */
  public CountedLoop {
    Objects.requireNonNull(openingSpan, "openingSpan");
    body = List.copyOf(body);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }
}
