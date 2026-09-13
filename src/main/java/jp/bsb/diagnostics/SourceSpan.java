package jp.bsb.diagnostics;

import java.util.Optional;

/**
 * ソースコード上の「開始位置」から「終了位置」までの範囲（Span: 半開区間 {@code [start, end)}）を表すレコードです。
 *
 * <p>【コンピュータ科学の観点：スパン（Span / Range）】 コンパイラにおいて、トークンや構文木（AST: Abstract Syntax Tree）のノードは
 * 単一の点ではなく「幅（区間）」を持ちます。エラー箇所を波線（下線）でハイライト表示したり、エディタ用の位置表現へ変換したりするには、
 * この開始位置（包含）と終了位置（非包含）のペアが必要となります。LSPなどへ渡す場合は、書記素クラスタ列を相手側の位置エンコーディングへ変換します。
 *
 * @param start 範囲の開始位置（この位置の文字を含む）
 * @param end 範囲の終了位置（この位置の文字は含まない：半開区間）
 */
public record SourceSpan(SourcePosition start, SourcePosition end) implements DiagnosticLocation {
  /**
   * コンパクトコンストラクタによるバリデーション。
   *
   * @throws NullPointerException {@code start} または {@code end} が null の場合
   * @throws IllegalArgumentException {@code end} のUTF-8バイト位置が {@code start} より前にある場合
   */
  public SourceSpan {
    if (start == null || end == null) {
      throw new NullPointerException("start and end must not be null");
    }
    if (end.utf8Offset() < start.utf8Offset()) {
      throw new IllegalArgumentException("end must not precede start");
    }
  }

  /**
   * 範囲の開始バイト位置（{@code start.utf8Offset()}）を返します。
   *
   * @return 開始バイトオフセット
   */
  @Override
  public long utf8Offset() {
    return start.utf8Offset();
  }

  /**
   * 人間向けのエラー表示用として、範囲の開始位置（{@code start}）を返します。
   *
   * @return {@code Optional.of(start)}
   */
  @Override
  public Optional<SourcePosition> displayPosition() {
    return Optional.of(start);
  }
}
