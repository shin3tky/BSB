package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 現在フレームの局所束縛値をデータスタックへ積むIR命令です。
 *
 * @param slot 読み出す局所スロット
 * @param span 値参照のソース範囲
 */
public record LoadLocal(IrStorageSlot slot, SourceSpan span) implements StorageInstruction {
  /** 局所スロットとソース範囲を検証します。 */
  public LoadLocal {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(span, "span");
    if (slot.storage() != BindingStorage.LOCAL) {
      throw new IllegalArgumentException("LoadLocal requires a local slot");
    }
  }

  @Override
  public String opcode() {
    return "LoadLocal";
  }
}
