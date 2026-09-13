package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * データスタックから反復回数を取り出し、正数なら回数ループの内部状態を開始します。
 *
 * @param exitTargetIndex 0回の場合に移る、ループ直後の0始まり命令位置
 * @param span {@code 回だけ} のソース範囲
 */
public record CountedLoopStart(int exitTargetIndex, SourceSpan span) implements IrInstruction {
  /** 負の位置と欠けたソース範囲を拒否します。 */
  public CountedLoopStart {
    if (exitTargetIndex < 0) {
      throw new IllegalArgumentException("loop exit target must not be negative");
    }
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "CountedLoopStart";
  }
}
