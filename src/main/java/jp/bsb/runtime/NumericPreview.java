package jp.bsb.runtime;

import java.math.BigInteger;
import java.util.Objects;

/** 数値演算の診断へ載せる数値を、決定的な64コードポイント境界で省略した結果です。 */
record NumericPreview(String text, int codePoints, boolean truncated) {
  private static final int FULL_LIMIT = 64;
  private static final int HEAD = 32;
  private static final int TAIL = 16;

  /** 表示文字列、元の長さ、省略有無を検証します。 */
  NumericPreview {
    Objects.requireNonNull(text, "text");
    if (codePoints < 1) {
      throw new IllegalArgumentException("numeric preview length must be positive");
    }
  }

  /** 整数を符号付き10進表記から省略します。 */
  static NumericPreview ofInteger(BigInteger value) {
    return ofText(Objects.requireNonNull(value, "value").toString());
  }

  /** 整数または正規小数の値表示を省略します。 */
  static NumericPreview ofValue(RuntimeValue value) {
    return switch (Objects.requireNonNull(value, "value")) {
      case IntegerValue integer -> ofInteger(integer.value());
      case DecimalValue decimal -> ofText(decimal.displayText());
      default -> throw new IllegalArgumentException("numeric preview requires a numeric value");
    };
  }

  /** 正規表示を必要な場合だけ先頭・省略数・末尾へ分けます。 */
  static NumericPreview ofText(String value) {
    Objects.requireNonNull(value, "value");
    int length = value.codePointCount(0, value.length());
    if (length <= FULL_LIMIT) {
      return new NumericPreview(value, length, false);
    }
    int headEnd = value.offsetByCodePoints(0, HEAD);
    int tailStart = value.offsetByCodePoints(0, length - TAIL);
    int omitted = length - HEAD - TAIL;
    return new NumericPreview(
        value.substring(0, headEnd) + "…(+" + omitted + ")" + value.substring(tailStart),
        length,
        true);
  }
}
