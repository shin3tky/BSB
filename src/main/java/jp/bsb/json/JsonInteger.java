package jp.bsb.json;

import java.math.BigInteger;
import java.util.Objects;

/** 値種別を保つ正確なJSON整数です。 */
public final class JsonInteger implements JsonValue {
  private final BigInteger value;
  private final JsonMetrics metrics;

  public JsonInteger(BigInteger value) {
    this.value = Objects.requireNonNull(value, "value");
    int digits = decimalDigits(value);
    if (digits > JsonLimits.INTEGER_DIGITS) {
      throw new IllegalArgumentException("JSON integer digits exceed the normative limit");
    }
    metrics = new JsonMetrics(1, 0, 0, digits + (value.signum() < 0 ? 1L : 0L));
  }

  public BigInteger value() {
    return value;
  }

  @Override
  public JsonKind kind() {
    return JsonKind.INTEGER;
  }

  @Override
  public JsonMetrics metrics() {
    return metrics;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonInteger integer && value.equals(integer.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  private static int decimalDigits(BigInteger value) {
    return value.signum() == 0 ? 1 : value.abs().toString().length();
  }
}
