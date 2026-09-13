package jp.bsb.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.ir.IrStorageSlot;

/**
 * 大域または1呼出しフレーム分の名前付き値保存領域です。
 *
 * <p>スロット説明表と値配列を分け、すべてのアクセスで番号と説明の一致を配列参照前に検証します。これにより合成不正IRをJavaの配列例外として漏らしません。
 */
final class StorageArea {
  private final List<IrStorageSlot> slots;
  private final BindingStorage storage;
  private final RuntimeValue[] values;
  private final boolean[] initialized;

  StorageArea(List<IrStorageSlot> slots, BindingStorage storage) {
    this.slots = List.copyOf(slots);
    this.storage = Objects.requireNonNull(storage, "storage");
    for (int index = 0; index < this.slots.size(); index++) {
      IrStorageSlot slot = this.slots.get(index);
      if (slot.storage() != storage || slot.slotIndex() != index) {
        throw new IllegalStateException(
            "a storage area requires contiguous slots of one storage kind");
      }
    }
    values = new RuntimeValue[this.slots.size()];
    initialized = new boolean[this.slots.size()];
  }

  void initializeGlobal(IrStorageSlot slot, RuntimeValue value) {
    requireStorage(BindingStorage.GLOBAL);
    int index = checkedIndex(slot);
    if (initialized[index]) {
      throw new IllegalStateException("a global slot may be initialized only once: " + slot);
    }
    writeChecked(index, slot, value);
  }

  void initializeLocal(IrStorageSlot slot, RuntimeValue value) {
    requireStorage(BindingStorage.LOCAL);
    int index = checkedIndex(slot);
    writeChecked(index, slot, value);
  }

  RuntimeValue load(IrStorageSlot slot) {
    int index = checkedInitializedIndex(slot);
    return values[index];
  }

  RuntimeValue store(IrStorageSlot slot, RuntimeValue value) {
    int index = checkedInitializedIndex(slot);
    if (slot.bindingKind() != BindingKind.VARIABLE) {
      throw new IllegalStateException("a constant slot may not be stored: " + slot);
    }
    RuntimeValue previous = values[index];
    writeChecked(index, slot, value);
    return previous;
  }

  List<Optional<RuntimeValue>> snapshot() {
    var result = new ArrayList<Optional<RuntimeValue>>(values.length);
    for (int index = 0; index < values.length; index++) {
      result.add(initialized[index] ? Optional.of(values[index]) : Optional.empty());
    }
    return List.copyOf(result);
  }

  private int checkedInitializedIndex(IrStorageSlot slot) {
    int index = checkedIndex(slot);
    if (!initialized[index]) {
      throw new IllegalStateException("a storage slot was used before initialization: " + slot);
    }
    return index;
  }

  private int checkedIndex(IrStorageSlot slot) {
    Objects.requireNonNull(slot, "slot");
    int index = slot.slotIndex();
    if (index < 0 || index >= slots.size() || !slots.get(index).equals(slot)) {
      throw new IllegalStateException("an instruction refers to an invalid storage slot: " + slot);
    }
    return index;
  }

  private void writeChecked(int index, IrStorageSlot slot, RuntimeValue value) {
    Objects.requireNonNull(value, "value");
    if (!value.type().equals(slot.valueType())) {
      throw new IllegalStateException(
          "a storage value type does not match its slot: "
              + value.type()
              + " != "
              + slot.valueType());
    }
    values[index] = value;
    initialized[index] = true;
  }

  private void requireStorage(BindingStorage expected) {
    if (storage != expected) {
      throw new IllegalStateException(expected + " initialization used the wrong storage area");
    }
  }
}
