package jp.bsb.json;

import java.math.BigDecimal;

final class JsonSupport {
  private JsonSupport() {}

  static long saturatedAdd(long left, long right) {
    if (left > Long.MAX_VALUE - right) {
      return Long.MAX_VALUE;
    }
    return left + right;
  }

  static long utf8Length(String value) {
    long length = 0;
    for (int index = 0; index < value.length(); ) {
      char first = value.charAt(index);
      int codePoint;
      if (Character.isHighSurrogate(first)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new IllegalArgumentException("JSON text requires Unicode scalar values");
        }
        codePoint = Character.toCodePoint(first, value.charAt(index + 1));
        index += 2;
      } else if (Character.isLowSurrogate(first)) {
        throw new IllegalArgumentException("JSON text requires Unicode scalar values");
      } else {
        codePoint = first;
        index++;
      }
      length =
          saturatedAdd(
              length, codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4);
    }
    return length;
  }

  static long escapedStringUtf8Length(String value) {
    long length = 2;
    for (int index = 0; index < value.length(); ) {
      int codePoint = value.codePointAt(index);
      index += Character.charCount(codePoint);
      long addition =
          switch (codePoint) {
            case '"', '\\', '\b', '\f', '\n', '\r', '\t' -> 2;
            default -> codePoint < 0x20 ? 6 : utf8Width(codePoint);
          };
      length = saturatedAdd(length, addition);
    }
    return length;
  }

  static BigDecimal normalizeDecimal(BigDecimal value) {
    if (value.signum() == 0) {
      return BigDecimal.ZERO;
    }
    try {
      return value.stripTrailingZeros();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("JSON decimal scale cannot be normalized", exception);
    }
  }

  static int decimalAbsoluteScale(BigDecimal value) {
    long scale = value.scale();
    return (int) Math.min(Integer.MAX_VALUE, Math.abs(scale));
  }

  private static int utf8Width(int codePoint) {
    return codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
  }
}
