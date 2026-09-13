package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.ir.IrStorageSlot;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

/** 実行時保存領域が不正IRをJava配列例外より前に拒否することを検証します。 */
class StorageAreaTest {
  @Test
  void rejectsUninitializedLoadAndOutOfRangeSlotExplicitly() {
    IrStorageSlot slot = slot(1, BindingKind.VARIABLE, 0);
    IrStorageSlot outOfRange = slot(2, BindingKind.VARIABLE, 1);
    var area = new StorageArea(List.of(slot), BindingStorage.GLOBAL);

    assertThrows(IllegalStateException.class, () -> area.load(slot));
    assertThrows(IllegalStateException.class, () -> area.load(outOfRange));
  }

  @Test
  void rejectsWrongTypesConstantStoresAndRepeatedGlobalInitialization() {
    IrStorageSlot constant = slot(1, BindingKind.CONSTANT, 0);
    var area = new StorageArea(List.of(constant), BindingStorage.GLOBAL);

    assertThrows(
        IllegalStateException.class, () -> area.initializeGlobal(constant, new StringValue("不正")));
    area.initializeGlobal(constant, new IntegerValue(BigInteger.ONE));
    assertThrows(
        IllegalStateException.class,
        () -> area.initializeGlobal(constant, new IntegerValue(BigInteger.TWO)));
    assertThrows(
        IllegalStateException.class, () -> area.store(constant, new IntegerValue(BigInteger.TWO)));
    assertEquals(List.of(Optional.of(new IntegerValue(BigInteger.ONE))), area.snapshot());
  }

  @Test
  void localInitializationOverwritesThePreviousLifetime() {
    IrStorageSlot variable = localSlot(1, BindingKind.VARIABLE, 0);
    var area = new StorageArea(List.of(variable), BindingStorage.LOCAL);

    area.initializeLocal(variable, new IntegerValue(BigInteger.ONE));
    area.initializeLocal(variable, new IntegerValue(BigInteger.TWO));

    assertEquals(new IntegerValue(BigInteger.TWO), area.load(variable));
  }

  private static IrStorageSlot slot(int bindingId, BindingKind kind, int index) {
    return new IrStorageSlot(
        new BindingId(bindingId),
        "値" + bindingId,
        kind,
        BindingStorage.GLOBAL,
        ValueType.INTEGER,
        index,
        Optional.empty());
  }

  private static IrStorageSlot localSlot(int bindingId, BindingKind kind, int index) {
    return new IrStorageSlot(
        new BindingId(bindingId),
        "値" + bindingId,
        kind,
        BindingStorage.LOCAL,
        ValueType.INTEGER,
        index,
        Optional.of("メイン"));
  }
}
