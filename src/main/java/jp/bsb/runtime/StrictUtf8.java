package jp.bsb.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;

/** Unicodeスカラー値だけを受理する、割当て前検査つきUTF-8処理です。 */
final class StrictUtf8 {
  private StrictUtf8() {}

  /** バイト列が公開する分類です。 */
  enum FailureKind {
    INVALID_LEADING_BYTE("invalidLeadingByte"),
    INVALID_CONTINUATION_BYTE("invalidContinuationByte"),
    TRUNCATED_SEQUENCE("truncatedSequence"),
    OVERLONG_ENCODING("overlongEncoding"),
    SURROGATE_CODE_POINT("surrogateCodePoint"),
    CODE_POINT_OUT_OF_RANGE("codePointOutOfRange");

    private final String stableName;

    FailureKind(String stableName) {
      this.stableName = stableName;
    }

    String stableName() {
      return stableName;
    }
  }

  /** 最初の失敗分類と0始まりバイト位置です。 */
  record Failure(FailureKind kind, int offset) {}

  /**
   * @return 最初の不正バイト位置。不正がなければ空
   */
  static OptionalInt firstInvalidOffset(byte[] bytes) {
    var failure = firstFailure(bytes);
    return failure.isEmpty() ? OptionalInt.empty() : OptionalInt.of(failure.orElseThrow().offset());
  }

  /** 配列入力を、出力文字列を割り当てる前に完全検査します。 */
  static Optional<Failure> firstFailure(byte[] bytes) {
    return firstFailure(bytes.length, index -> bytes[index] & 0xff);
  }

  /** 不変バイト列をコピーせず、出力文字列を割り当てる前に完全検査します。 */
  static Optional<Failure> firstFailure(ByteSequenceValue bytes) {
    return firstFailure(bytes.length(), index -> bytes.byteAt(index) & 0xff);
  }

  private static Optional<Failure> firstFailure(int size, IntUnaryOperator byteAt) {
    int index = 0;
    while (index < size) {
      int first = byteAt.applyAsInt(index);
      if (first <= 0x7f) {
        index++;
        continue;
      }
      int length;
      int minimum;
      int codePoint;
      if (first >= 0xc2 && first <= 0xdf) {
        length = 2;
        minimum = 0x80;
        codePoint = first & 0x1f;
      } else if (first >= 0xe0 && first <= 0xef) {
        length = 3;
        minimum = 0x800;
        codePoint = first & 0x0f;
      } else if (first >= 0xf0 && first <= 0xf4) {
        length = 4;
        minimum = 0x10000;
        codePoint = first & 0x07;
      } else {
        return Optional.of(new Failure(FailureKind.INVALID_LEADING_BYTE, index));
      }
      for (int continuation = 1; continuation < length; continuation++) {
        int position = index + continuation;
        if (position >= size) {
          return Optional.of(new Failure(FailureKind.TRUNCATED_SEQUENCE, index));
        }
        int next = byteAt.applyAsInt(position);
        if ((next & 0xc0) != 0x80) {
          return Optional.of(new Failure(FailureKind.INVALID_CONTINUATION_BYTE, position));
        }
        codePoint = (codePoint << 6) | (next & 0x3f);
      }
      if (codePoint < minimum) {
        return Optional.of(new Failure(FailureKind.OVERLONG_ENCODING, index));
      }
      if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
        return Optional.of(new Failure(FailureKind.SURROGATE_CODE_POINT, index));
      }
      if (codePoint > 0x10ffff) {
        return Optional.of(new Failure(FailureKind.CODE_POINT_OUT_OF_RANGE, index));
      }
      index += length;
    }
    return Optional.empty();
  }

  static String decodeValidated(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  static String decodeValidated(ByteSequenceValue bytes) {
    return decodeValidated(bytes.copyBytes());
  }
}
