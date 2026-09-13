package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.SourceSpan;

/**
 * スタック最上部の1値を現在フレームの局所変数へ保存するIR命令です。
 *
 * @param slot 書き込む局所変数スロット
 * @param span 代入のソース範囲
 */
public record StoreLocal(IrStorageSlot slot, SourceSpan span) implements StorageInstruction {
  /** 書込み可能な局所スロットとソース範囲を検証します。 */
  public StoreLocal {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(span, "span");
    if (slot.storage() != BindingStorage.LOCAL) {
      throw new IllegalArgumentException("StoreLocal requires a local slot");
    }
    if (!slot.bindingKind().isMutable()) {
      throw new IllegalArgumentException("StoreLocal requires a variable slot");
    }
  }

  @Override
  public String opcode() {
    return "StoreLocal";
  }
}
