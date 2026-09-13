package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 回数ループの残り回数を更新し、次の反復があれば本体先頭へ戻ります。
 *
 * @param bodyTargetIndex 反復を続ける場合の0始まり命令位置
 * @param span {@code 繰り返す} のソース範囲
 */
public record CountedLoopNext(int bodyTargetIndex, SourceSpan span) implements IrInstruction {
  /** 負の位置と欠けたソース範囲を拒否します。 */
  public CountedLoopNext {
    if (bodyTargetIndex < 0) {
      throw new IllegalArgumentException("loop body target must not be negative");
    }
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "CountedLoopNext";
  }
}
