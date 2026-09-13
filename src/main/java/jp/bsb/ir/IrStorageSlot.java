package jp.bsb.ir;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.stdlib.ValueType;

/**
 * 静的に配置済みの値束縛スロットです。
 *
 * <p>束縛IDは説明とトレースの安定性、スロット番号は実行時の直接アクセスに使います。局所スロットは所有単語も持ち、同じ番号を別単語で安全に再利用できます。
 *
 * @param bindingId 静的束縛ID
 * @param bindingName 正規化済みの宣言名
 * @param bindingKind 定数または変数
 * @param storage 大域または局所保存領域
 * @param valueType 初期値から推論済みの型
 * @param slotIndex 保存領域内の0始まり番号
 * @param ownerWord 局所スロットの所有単語。大域では空
 */
public record IrStorageSlot(
    BindingId bindingId,
    String bindingName,
    BindingKind bindingKind,
    BindingStorage storage,
    ValueType valueType,
    int slotIndex,
    Optional<String> ownerWord) {
  /** 保存種別と所有単語の関係を含む不変条件を検証します。 */
  public IrStorageSlot {
    Objects.requireNonNull(bindingId, "bindingId");
    if (bindingName == null || bindingName.isBlank()) {
      throw new IllegalArgumentException("bindingName must not be blank");
    }
    Objects.requireNonNull(bindingKind, "bindingKind");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(valueType, "valueType");
    if (slotIndex < 0) {
      throw new IllegalArgumentException("slotIndex must not be negative");
    }
    ownerWord = Objects.requireNonNull(ownerWord, "ownerWord");
    ownerWord.ifPresent(
        owner -> {
          if (owner.isBlank()) {
            throw new IllegalArgumentException("ownerWord must not be blank");
          }
        });
    if ((storage == BindingStorage.LOCAL) != ownerWord.isPresent()) {
      throw new IllegalArgumentException("only a local slot must have an owner word");
    }
  }
}
