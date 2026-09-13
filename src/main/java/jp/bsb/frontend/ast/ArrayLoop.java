package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code 各要素について ... 繰り返す}という配列反復です。
 *
 * @param inputParticle 開始語の直前にあった構文上の助詞{@code を}
 * @param openingSpan {@code 各要素について}の範囲
 * @param body 各要素に対して実行する本体
 * @param endSpan {@code 繰り返す}の範囲
 * @param span 開始語から終了語直後までの範囲
 */
public record ArrayLoop(
    Optional<Particle> inputParticle,
    SourceSpan openingSpan,
    List<BodyElement> body,
    SourceSpan endSpan,
    SourceSpan span)
    implements BodyElement {
  /** 助詞、本体、各位置を検証して不変に保持します。 */
  public ArrayLoop {
    inputParticle = Objects.requireNonNull(inputParticle, "inputParticle");
    inputParticle.ifPresent(
        particle -> {
          if (!particle.name().equals("を")) {
            throw new IllegalArgumentException("an array loop input particle must be を");
          }
        });
    Objects.requireNonNull(openingSpan, "openingSpan");
    body = List.copyOf(body);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }
}
