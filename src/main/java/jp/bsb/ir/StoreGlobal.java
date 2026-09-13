package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.SourceSpan;

/**
 * スタック最上部の1値を大域変数へ保存するIR命令です。
 *
 * @param slot 書き込む大域変数スロット
 * @param span 代入のソース範囲
 */
public record StoreGlobal(IrStorageSlot slot, SourceSpan span) implements StorageInstruction {
  /** 書込み可能な大域スロットとソース範囲を検証します。 */
  public StoreGlobal {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(span, "span");
    if (slot.storage() != BindingStorage.GLOBAL) {
      throw new IllegalArgumentException("StoreGlobal requires a global slot");
    }
    if (!slot.bindingKind().isMutable()) {
      throw new IllegalArgumentException("StoreGlobal requires a variable slot");
    }
  }

  @Override
  public String opcode() {
    return "StoreGlobal";
  }
}
