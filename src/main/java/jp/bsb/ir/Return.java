package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 現在の利用者定義単語から呼出し元へ戻るIR命令です。
 *
 * @param span 定義終端のソース範囲
 */
public record Return(SourceSpan span) implements IrInstruction {
  /** 範囲を検証します。 */
  public Return {
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "Return";
  }
}
