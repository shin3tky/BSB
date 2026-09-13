package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.ir.IrStorageSlot;

/**
 * Initialize、Load、Storeの対象と、命令前後の保存値です。
 *
 * @param slot 静的に解決済みの保存スロット
 * @param valueBefore 命令前の値。初期化前だけ空
 * @param valueAfter 命令後の値
 */
public record StorageTraceState(
    IrStorageSlot slot, Optional<RuntimeValue> valueBefore, Optional<RuntimeValue> valueAfter) {
  /** 必須値と、命令後には値が存在するという不変条件を検証します。 */
  public StorageTraceState {
    Objects.requireNonNull(slot, "slot");
    valueBefore = Objects.requireNonNull(valueBefore, "valueBefore");
    valueAfter = Objects.requireNonNull(valueAfter, "valueAfter");
    if (valueAfter.isEmpty()) {
      throw new IllegalArgumentException("a traced storage instruction must leave a value");
    }
    valueBefore.ifPresent(value -> requireType(slot, value));
    valueAfter.ifPresent(value -> requireType(slot, value));
  }

  private static void requireType(IrStorageSlot slot, RuntimeValue value) {
    if (!value.type().equals(slot.valueType())) {
      throw new IllegalArgumentException("a traced value must match its storage slot type");
    }
  }
}
