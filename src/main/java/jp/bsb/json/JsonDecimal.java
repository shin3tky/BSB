package jp.bsb.json;

import java.math.BigDecimal;
import java.util.Objects;

/** 整数とは異なる値種別を保つ正確なJSON小数です。 */
public final class JsonDecimal implements JsonValue {
  private final BigDecimal value;
  private final JsonMetrics metrics;

  public JsonDecimal(BigDecimal value) {
    this.value = JsonSupport.normalizeDecimal(Objects.requireNonNull(value, "value"));
    int precision = this.value.precision();
    int absoluteScale = JsonSupport.decimalAbsoluteScale(this.value);
    if (precision > JsonLimits.DECIMAL_PRECISION) {
      throw new IllegalArgumentException("JSON decimal precision exceeds the normative limit");
    }
    if (absoluteScale > JsonLimits.DECIMAL_ABSOLUTE_SCALE) {
      throw new IllegalArgumentException("JSON decimal scale exceeds the normative limit");
    }
    metrics = new JsonMetrics(1, 0, 0, canonicalUtf8Length(this.value));
  }

  public BigDecimal value() {
    return value;
  }

  public String canonicalText() {
    return canonicalText(value);
  }

  @Override
  public JsonKind kind() {
    return JsonKind.DECIMAL;
  }

  @Override
  public JsonMetrics metrics() {
    return metrics;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonDecimal decimal && value.compareTo(decimal.value) == 0;
  }

  @Override
  public int hashCode() {
    return 31 * value.unscaledValue().hashCode() + value.scale();
  }

  private static String canonicalText(BigDecimal value) {
    String plain = value.toPlainString();
    return value.scale() <= 0 ? plain + ".0" : plain;
  }

  private static long canonicalUtf8Length(BigDecimal value) {
    if (value.signum() == 0) {
      return 3;
    }
    long sign = value.signum() < 0 ? 1 : 0;
    long precision = value.precision();
    long scale = value.scale();
    if (scale <= 0) {
      return JsonSupport.saturatedAdd(sign, precision - scale + 2);
    }
    return JsonSupport.saturatedAdd(sign, scale < precision ? precision + 1 : scale + 2);
  }
}
