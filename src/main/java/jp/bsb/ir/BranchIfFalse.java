package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 真偽値を1個消費し、偽の場合だけ同じ単語内の指定命令へ移ります。
 *
 * @param targetIndex 偽の場合の0始まり命令位置
 * @param span {@code ならば} または {@code 続く間} のソース範囲
 */
public record BranchIfFalse(int targetIndex, SourceSpan span) implements IrInstruction {
  /** 負の位置と欠けたソース範囲を拒否します。 */
  public BranchIfFalse {
    if (targetIndex < 0) {
      throw new IllegalArgumentException("branch target must not be negative");
    }
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "BranchIfFalse";
  }
}
