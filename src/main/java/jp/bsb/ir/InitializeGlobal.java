package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.SourceSpan;

/**
 * スタック最上部の1値で大域束縛を1回初期化するIR命令です。
 *
 * @param slot 初期化する大域スロット
 * @param span 宣言のソース範囲
 */
public record InitializeGlobal(IrStorageSlot slot, SourceSpan span) implements StorageInstruction {
  /** 大域スロットとソース範囲を検証します。 */
  public InitializeGlobal {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(span, "span");
    if (slot.storage() != BindingStorage.GLOBAL) {
      throw new IllegalArgumentException("InitializeGlobal requires a global slot");
    }
  }

  @Override
  public String opcode() {
    return "InitializeGlobal";
  }
}
