package jp.bsb.frontend.ast;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code 打ち切る}、{@code 続ける}、{@code 戻る} のいずれか1個を表します。
 *
 * <p>これらは通常の単語呼出しではなく、実行位置そのものを変更する構文です。専用ノードにすることで、名前解決器が未定義語として扱うことを防ぎます。
 *
 * @param kind 制御移行の種類
 * @param lexeme 元ソース上の表記
 * @param span 制御移行語の範囲
 */
public record ControlTransfer(Kind kind, String lexeme, SourceSpan span) implements BodyElement {
  /** 制御移行の種類、元表記、位置が存在することを検査します。 */
  public ControlTransfer {
    Objects.requireNonNull(kind, "kind");
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    Objects.requireNonNull(span, "span");
  }

  /** 制御フローで利用できる制御移行の種類です。 */
  public enum Kind {
    /** 最も内側のループを終了します。 */
    BREAK,

    /** 最も内側のループの次反復へ進みます。 */
    CONTINUE,

    /** 現在の利用者定義単語から復帰します。 */
    RETURN
  }
}
