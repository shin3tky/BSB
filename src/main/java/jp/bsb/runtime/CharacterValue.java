package jp.bsb.runtime;

import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/**
 * Unicode拡張書記素クラスタ1個からなる文字値です。
 *
 * @param value エスケープ展開済み文字列
 */
public record CharacterValue(String value) implements RuntimeValue {
  /** 値を検証します。書記素数の検査は字句解析済みASTが保証します。 */
  public CharacterValue {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public ValueType type() {
    return ValueType.CHARACTER;
  }

  @Override
  public String displayText() {
    return value;
  }
}
