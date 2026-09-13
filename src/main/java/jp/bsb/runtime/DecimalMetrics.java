package jp.bsb.runtime;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 正規小数値の有効桁数とスケールを、表示文字列を作らず計測した結果です。
 *
 * @param precision 正規化済み係数の有効桁数
 * @param scale 正規化済みの小数スケール
 */
public record DecimalMetrics(int precision, long scale) {
  /** 計測値の内部整合性を検証します。 */
  public DecimalMetrics {
    if (precision < 1) {
      throw new IllegalArgumentException("decimal precision must be positive");
    }
  }

  /**
   * 入力を正規化してから有効桁数とスケールを計測します。
   *
   * <p>{@link BigDecimal#toPlainString()}を経由しないため、固定小数点表示で補われる0を複製せずに資源境界を判定できます。
   *
   * @param value 計測する小数
   * @return 正規化後の計測値
   */
  public static DecimalMetrics measure(BigDecimal value) {
    return ofNormalized(normalize(value));
  }

  /**
   * スケールの絶対値を、{@link Long#MIN_VALUE}でもオーバーフローしない形で返します。
   *
   * @return スケールの絶対値
   */
  public long absoluteScale() {
    return scale == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(scale);
  }

  /**
   * 数値演算の小数値上限を両方満たすかを返します。
   *
   * @return 有効桁数とスケール絶対値が上限内ならtrue
   */
  public boolean withinLimits() {
    return precision <= RuntimeLimits.DECIMAL_PRECISION
        && absoluteScale() <= RuntimeLimits.DECIMAL_ABSOLUTE_SCALE;
  }

  static BigDecimal normalize(BigDecimal value) {
    Objects.requireNonNull(value, "value");
    if (value.signum() == 0) {
      return BigDecimal.ZERO;
    }
    try {
      return value.stripTrailingZeros();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("decimal scale cannot be normalized", exception);
    }
  }

  static DecimalMetrics ofNormalized(BigDecimal value) {
    return new DecimalMetrics(value.precision(), value.scale());
  }
}
