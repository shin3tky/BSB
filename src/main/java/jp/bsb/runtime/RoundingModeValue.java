package jp.bsb.runtime;

import java.util.Arrays;
import java.util.Optional;
import jp.bsb.stdlib.ValueType;

/** Javaの丸め列挙型から独立した、数値演算の5種類の丸め方法値です。 */
public enum RoundingModeValue implements RuntimeValue {
  /** 最も近い値へ丸め、中間では末尾が偶数になる方を選びます。 */
  NEAREST_EVEN("最近接偶数丸め"),

  /** 最も近い値へ丸め、中間では0から遠い方を選びます。 */
  NEAREST_AWAY_FROM_ZERO("四捨五入"),

  /** 0へ近づく方向へ丸めます。 */
  TOWARD_ZERO("0方向へ丸め"),

  /** 正の無限大方向へ丸めます。 */
  TOWARD_POSITIVE_INFINITY("正方向へ丸め"),

  /** 負の無限大方向へ丸めます。 */
  TOWARD_NEGATIVE_INFINITY("負方向へ丸め");

  private final String sourceName;

  RoundingModeValue(String sourceName) {
    this.sourceName = sourceName;
  }

  /**
   * 正規名から丸め方法値を検索します。
   *
   * @param name BSBソース上の名前
   * @return 一致する値。不明またはnullなら空
   */
  public static Optional<RoundingModeValue> fromSourceName(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(value -> value.sourceName.equals(name)).findFirst();
  }

  /**
   * BSBソースと値表示で共有する正規名を返します。
   *
   * @return 丸め方法の正規名
   */
  public String sourceName() {
    return sourceName;
  }

  @Override
  public ValueType type() {
    return ValueType.ROUNDING_MODE;
  }

  @Override
  public String displayText() {
    return sourceName;
  }

  @Override
  public String toString() {
    return sourceName;
  }
}
