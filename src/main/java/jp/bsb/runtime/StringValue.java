package jp.bsb.runtime;

import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/**
 * Unicodeスカラー値列からなる文字列値です。
 *
 * @param value エスケープ展開済み文字列
 */
public record StringValue(String value) implements RuntimeValue {
  /** 値を検証します。UTF-8長は字句解析済みASTが保証します。 */
  public StringValue {
    Objects.requireNonNull(value, "value");
    UnicodeText.of(value);
  }

  @Override
  public ValueType type() {
    return ValueType.STRING;
  }

  @Override
  public String displayText() {
    return value;
  }
}
