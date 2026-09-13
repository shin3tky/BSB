package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 配列を消費し、非空なら反復状態と先頭要素を作り、空なら出口へ分岐します。
 *
 * @param exitTargetIndex 空配列または反復終了時に移る0始まり命令位置
 * @param span 配列反復開始語のソース範囲
 */
public record ArrayLoopStart(int exitTargetIndex, SourceSpan span) implements IrInstruction {
  /** 非負の出口位置とソース位置を検証します。 */
  public ArrayLoopStart {
    if (exitTargetIndex < 0) {
      throw new IllegalArgumentException("array loop exit target must not be negative");
    }
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "ArrayLoopStart";
  }
}
