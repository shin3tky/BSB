package jp.bsb.analyzer;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 分岐またはループへ入った時点の基準状態です。
 *
 * <p>解析器はこのフレームを入れ子順に保持します。ループの「打ち切る」「続ける」は最も内側にあるループフレームを探し、その入口スタックと出口スタックを比較します。
 *
 * @param kind 制御構文の種類
 * @param entryStack 構文へ入る直前の抽象スタック
 * @param openerSpan 開始語のソース範囲
 */
public record ControlFrame(Kind kind, AbstractStack entryStack, SourceSpan openerSpan) {
  /** 制御構文の種類、入口スタック、開始位置が存在することを検査します。 */
  public ControlFrame {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(entryStack, "entryStack");
    Objects.requireNonNull(openerSpan, "openerSpan");
  }

  /**
   * このフレームがループを表すかを返します。
   *
   * @return いずれかのループならtrue
   */
  public boolean isLoop() {
    return kind != Kind.BRANCH;
  }

  /** 静的検査で追跡する制御構文の種類です。 */
  public enum Kind {
    /** 「ならば」で始まる条件分岐です。 */
    BRANCH,

    /** 「回だけ」で始まる回数ループです。 */
    COUNTED_LOOP,

    /** 「ここから」で始まる条件ループです。 */
    CONDITIONAL_LOOP,

    /** 「各要素について」で始まる配列反復です。 */
    ARRAY_LOOP
  }
}
