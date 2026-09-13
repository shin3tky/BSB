package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.runtime.RuntimeValue;

/**
 * 定数をデータスタックへ積むIR命令です。
 *
 * @param value 積む実行時値
 * @param span リテラルのソース範囲
 */
public record PushConst(RuntimeValue value, SourceSpan span) implements IrInstruction {
  /** 値と範囲を検証します。 */
  public PushConst {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "PushConst";
  }
}
