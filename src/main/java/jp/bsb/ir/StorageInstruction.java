package jp.bsb.ir;

import jp.bsb.binding.BindingId;

/** 静的に解決済みの保存スロットを読み書きするIR命令です。 */
public sealed interface StorageInstruction extends IrInstruction
    permits InitializeGlobal, InitializeLocal, LoadGlobal, LoadLocal, StoreGlobal, StoreLocal {
  /**
   * 命令が操作する検証済みスロットを返します。
   *
   * @return 保存スロット
   */
  IrStorageSlot slot();

  /**
   * 説明用の束縛IDを返します。
   *
   * @return 静的束縛ID
   */
  default BindingId bindingId() {
    return slot().bindingId();
  }

  /**
   * 実行時に直接参照するスロット番号を返します。
   *
   * @return 0始まりのスロット番号
   */
  default int slotIndex() {
    return slot().slotIndex();
  }
}
