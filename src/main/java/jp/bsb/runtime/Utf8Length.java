package jp.bsb.runtime;

import java.util.Objects;

/** 中間バイト配列を作らず、Unicodeスカラー値列のUTF-8長を上限付きで測ります。 */
public final class Utf8Length {
  private Utf8Length() {}

  /**
   * 上限を超えた最初のコードポイントで計測を終了します。
   *
   * @param value 対象文字列
   * @param maximumBytes 許容する最大UTF-8バイト数
   * @return 観測済みバイト数と上限超過状態
   */
  public static Measurement measureUpTo(String value, long maximumBytes) {
    Objects.requireNonNull(value, "value");
    if (maximumBytes < 0) {
      throw new IllegalArgumentException("maximumBytes must not be negative");
    }
    long bytes = 0;
    for (int index = 0; index < value.length(); ) {
      int codePoint = value.codePointAt(index);
      int width = Character.charCount(codePoint);
      if (width == 1 && Character.isSurrogate(value.charAt(index))) {
        throw new IllegalArgumentException("a UTF-8 measurement requires Unicode scalar values");
      }
      bytes += encodedBytes(codePoint);
      if (bytes > maximumBytes) {
        return new Measurement(bytes, true);
      }
      index += width;
    }
    return new Measurement(bytes, false);
  }

  /**
   * 計測結果です。
   *
   * @param bytes 終了時までに観測したUTF-8バイト数
   * @param exceededMaximum 上限を超えて早期終了した場合true
   */
  public record Measurement(long bytes, boolean exceededMaximum) {
    /** 非負の観測値を保証します。 */
    public Measurement {
      if (bytes < 0) {
        throw new IllegalArgumentException("bytes must not be negative");
      }
    }
  }

  private static int encodedBytes(int codePoint) {
    if (codePoint <= 0x7F) {
      return 1;
    }
    if (codePoint <= 0x7FF) {
      return 2;
    }
    return codePoint <= 0xFFFF ? 3 : 4;
  }
}
