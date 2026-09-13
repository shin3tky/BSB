package jp.bsb.runtime;

import jp.bsb.stdlib.ValueType;

/**
 * {@code はい}/{@code いいえ} の真偽値です。
 *
 * @param value Java上の真偽値
 */
public record BooleanValue(boolean value) implements RuntimeValue {
  @Override
  public ValueType type() {
    return ValueType.BOOLEAN;
  }

  @Override
  public String displayText() {
    return value ? "はい" : "いいえ";
  }
}
