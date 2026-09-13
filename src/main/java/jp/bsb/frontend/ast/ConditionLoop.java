package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code ここから ... 続く間 ... 繰り返す} という条件ループを表します。
 *
 * <p>条件を1個の式ではなく要素列として保持するため、複数の呼出しや入れ子の制御構文で真偽値を作る処理も失われません。
 *
 * @param openingSpan {@code ここから} の範囲
 * @param conditionBody 反復ごとに条件を計算する本体
 * @param separatorSpan {@code 続く間} の範囲
 * @param body 条件が真の場合に実行する本体
 * @param endSpan {@code 繰り返す} の範囲
 * @param span 開始語の先頭から終了語の直後までの範囲
 */
public record ConditionLoop(
    SourceSpan openingSpan,
    List<BodyElement> conditionBody,
    SourceSpan separatorSpan,
    List<BodyElement> body,
    SourceSpan endSpan,
    SourceSpan span)
    implements BodyElement {
  /** 条件計算部と反復本体を不変の一覧として保持します。 */
  public ConditionLoop {
    Objects.requireNonNull(openingSpan, "openingSpan");
    conditionBody = List.copyOf(conditionBody);
    Objects.requireNonNull(separatorSpan, "separatorSpan");
    body = List.copyOf(body);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }
}
