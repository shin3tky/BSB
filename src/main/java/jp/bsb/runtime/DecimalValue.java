package jp.bsb.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/** 係数末尾の0と負の0を保持しない、正確な正規10進小数値です。 */
public final class DecimalValue implements RuntimeValue {
  private final BigDecimal value;
  private final DecimalMetrics metrics;

  /**
   * Java小数を正規化し、数値演算の有効桁数とスケール上限を検証します。
   *
   * @param value 正確な10進小数
   */
  public DecimalValue(BigDecimal value) {
    this.value = DecimalMetrics.normalize(Objects.requireNonNull(value, "value"));
    metrics = DecimalMetrics.ofNormalized(this.value);
    if (metrics.precision() > RuntimeLimits.DECIMAL_PRECISION) {
      throw new IllegalArgumentException("decimal precision exceeds the normative limit");
    }
    if (metrics.absoluteScale() > RuntimeLimits.DECIMAL_ABSOLUTE_SCALE) {
      throw new IllegalArgumentException("decimal scale exceeds the normative limit");
    }
  }

  /**
   * 係数とスケールから正確な小数値を作ります。
   *
   * @param coefficient 符号付き10進係数
   * @param scale 小数スケール
   */
  public DecimalValue(BigInteger coefficient, int scale) {
    this(new BigDecimal(Objects.requireNonNull(coefficient, "coefficient"), scale));
  }

  /**
   * 正規化済みのJava小数を返します。
   *
   * @return 不変な正規小数
   */
  public BigDecimal value() {
    return value;
  }

  /**
   * 正規化済みの符号付き係数を返します。
   *
   * @return 末尾が0でない係数。値が0なら0
   */
  public BigInteger coefficient() {
    return value.unscaledValue();
  }

  /**
   * 正規化済みのスケールを返します。
   *
   * @return 小数スケール
   */
  public int scale() {
    return value.scale();
  }

  /**
   * 正規化済み係数の有効桁数を返します。
   *
   * @return 符号を除く係数桁数。値が0なら1
   */
  public int precision() {
    return metrics.precision();
  }

  /**
   * 表示文字列を作らず得た正規小数の計測値を返します。
   *
   * @return この値の有効桁数とスケール
   */
  public DecimalMetrics metrics() {
    return metrics;
  }

  @Override
  public ValueType type() {
    return ValueType.DECIMAL;
  }

  @Override
  public String displayText() {
    String plain = value.toPlainString();
    return value.scale() <= 0 ? plain + ".0" : plain;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DecimalValue decimal && value.compareTo(decimal.value) == 0;
  }

  @Override
  public int hashCode() {
    return 31 * coefficient().hashCode() + scale();
  }

  @Override
  public String toString() {
    return displayText();
  }
}
