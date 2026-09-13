package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.stdlib.OptionalType;
import jp.bsb.stdlib.ValueType;

/** 値ありと値なしを区別し、内包型を常に保持する不変な任意値です。 */
public final class OptionalValue implements RuntimeValue {
  private final OptionalType type;
  private final RuntimeValue value;

  private OptionalValue(OptionalType type, RuntimeValue value) {
    this.type = Objects.requireNonNull(type, "type");
    this.value = value;
    if (value != null && !value.type().equals(type.elementType())) {
      throw new IllegalArgumentException("an optional value must match its declared element type");
    }
  }

  /** 指定値を同じ型の値あり任意値へ包みます。 */
  public static OptionalValue present(RuntimeValue value) {
    Objects.requireNonNull(value, "value");
    return new OptionalValue(ValueType.optionalOf(value.type()), value);
  }

  /** 明示された内包型の値なし任意値を作ります。 */
  public static OptionalValue absent(ValueType elementType) {
    return new OptionalValue(
        ValueType.optionalOf(Objects.requireNonNull(elementType, "elementType")), null);
  }

  /** 値ありならtrueです。 */
  public boolean isPresent() {
    return value != null;
  }

  /** 内包値を返します。値なしなら空です。 */
  public Optional<RuntimeValue> value() {
    return Optional.ofNullable(value);
  }

  @Override
  public OptionalType type() {
    return type;
  }

  @Override
  public String displayText() {
    return RuntimeWrappedValueSupport.displayText(this);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OptionalValue optional
        && RuntimeWrappedValueSupport.valuesEqual(this, optional);
  }

  @Override
  public int hashCode() {
    return RuntimeWrappedValueSupport.valueHash(this);
  }

  @Override
  public String toString() {
    return displayText();
  }
}
