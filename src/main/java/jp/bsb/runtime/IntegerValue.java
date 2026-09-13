package jp.bsb.runtime;

import java.math.BigInteger;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/**
 * 任意精度の符号付き整数値です。
 *
 * @param value Javaの任意精度整数
 */
public record IntegerValue(BigInteger value) implements RuntimeValue {
  /** 値を検証します。 */
  public IntegerValue {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public ValueType type() {
    return ValueType.INTEGER;
  }

  @Override
  public String displayText() {
    return value.toString();
  }
}
