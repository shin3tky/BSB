package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 同じ利用者定義単語内の指定命令へ、無条件に制御を移します。
 *
 * <p>【コンピュータ科学の観点：相対名ではなく解決済み位置】構文上の「つぎに」などの名前を実行時に探さず、IR生成時に確定した命令位置を保持します。
 *
 * @param targetIndex 移動先の0始まり命令位置
 * @param countedLoopStatesToDiscard 移動時に破棄する回数ループ内部状態の個数
 * @param arrayLoopStatesToDiscard 移動時に破棄する配列反復内部状態の個数
 * @param span この移動を生んだ制御語のソース範囲
 */
public record Jump(
    int targetIndex, int countedLoopStatesToDiscard, int arrayLoopStatesToDiscard, SourceSpan span)
    implements IrInstruction {
  /**
   * 配列反復状態を破棄しない、制御フローまでの移動命令を作ります。
   *
   * @param targetIndex 移動先の0始まり命令位置
   * @param countedLoopStatesToDiscard 移動時に破棄する回数ループ内部状態の個数
   * @param span この移動を生んだ制御語のソース範囲
   */
  public Jump(int targetIndex, int countedLoopStatesToDiscard, SourceSpan span) {
    this(targetIndex, countedLoopStatesToDiscard, 0, span);
  }

  /** 負の位置や破棄数を、単語へ組み込む前にも拒否します。 */
  public Jump {
    if (targetIndex < 0) {
      throw new IllegalArgumentException("jump target must not be negative");
    }
    if (countedLoopStatesToDiscard < 0) {
      throw new IllegalArgumentException("discard count must not be negative");
    }
    if (arrayLoopStatesToDiscard < 0) {
      throw new IllegalArgumentException("array loop discard count must not be negative");
    }
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "Jump";
  }
}
