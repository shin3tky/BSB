package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.SourceSpan;

/**
 * スタック最上部の1値で現在フレームの局所束縛を初期化するIR命令です。
 *
 * @param slot 初期化する局所スロット
 * @param span 宣言のソース範囲
 */
public record InitializeLocal(IrStorageSlot slot, SourceSpan span) implements StorageInstruction {
  /** 局所スロットとソース範囲を検証します。 */
  public InitializeLocal {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(span, "span");
    if (slot.storage() != BindingStorage.LOCAL) {
      throw new IllegalArgumentException("InitializeLocal requires a local slot");
    }
  }

  @Override
  public String opcode() {
    return "InitializeLocal";
  }
}
