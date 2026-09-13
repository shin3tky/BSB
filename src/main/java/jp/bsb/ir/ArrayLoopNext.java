package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 次要素があれば積んで配列反復本体へ戻り、なければ反復状態を破棄します。
 *
 * @param bodyTargetIndex 次要素がある場合に戻る0始まり命令位置
 * @param span 配列反復終了語のソース範囲
 */
public record ArrayLoopNext(int bodyTargetIndex, SourceSpan span) implements IrInstruction {
  /** 非負の本体位置とソース位置を検証します。 */
  public ArrayLoopNext {
    if (bodyTargetIndex < 0) {
      throw new IllegalArgumentException("array loop body target must not be negative");
    }
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "ArrayLoopNext";
  }
}
