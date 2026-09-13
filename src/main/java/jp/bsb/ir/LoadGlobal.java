package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 大域束縛の現在値をデータスタックへ積むIR命令です。
 *
 * @param slot 読み出す大域スロット
 * @param span 値参照のソース範囲
 */
public record LoadGlobal(IrStorageSlot slot, SourceSpan span) implements StorageInstruction {
  /** 大域スロットとソース範囲を検証します。 */
  public LoadGlobal {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(span, "span");
    if (slot.storage() != BindingStorage.GLOBAL) {
      throw new IllegalArgumentException("LoadGlobal requires a global slot");
    }
  }

  @Override
  public String opcode() {
    return "LoadGlobal";
  }
}
