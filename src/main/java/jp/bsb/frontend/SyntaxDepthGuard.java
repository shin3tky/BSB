package jp.bsb.frontend;

/**
 * 再帰下降構文解析で現在の入れ子深さを追跡する小さな安全装置です。
 *
 * <p>条件分岐、ループ、配列リテラルを相互に入れ子にできます。Javaの呼出スタックを無制限に消費しないよう、再帰処理へ入る直前の境界判定をこのクラスへ分離しています。
 */
final class SyntaxDepthGuard {
  static final int MAX_DEPTH = 256;

  private int depth;

  /** 上限を超えなければ深さを1増やし、超える場合は状態を変えず false を返します。 */
  boolean tryEnter() {
    if (depth >= MAX_DEPTH) {
      return false;
    }
    depth++;
    return true;
  }

  /** 対応する構文要素の解析完了時に深さを1戻します。 */
  void exit() {
    if (depth == 0) {
      throw new IllegalStateException("syntax depth is already zero");
    }
    depth--;
  }

  int depth() {
    return depth;
  }
}
