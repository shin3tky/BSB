package jp.bsb.runtime;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/** RFC 4648基本alphabetの規範Base64だけを受理する、事前検査つきcodecです。 */
final class StrictBase64 {
  private StrictBase64() {}

  /** バイト列が公開する分類です。 */
  enum FailureKind {
    INVALID_CHARACTER("invalidCharacter"),
    INVALID_LENGTH("invalidLength"),
    INVALID_PADDING("invalidPadding"),
    NON_ZERO_PAD_BITS("nonZeroPadBits");

    private final String stableName;

    FailureKind(String stableName) {
      this.stableName = stableName;
    }

    String stableName() {
      return stableName;
    }
  }

  /** 最初の失敗分類と0始まりUnicodeスカラー値位置です。 */
  record Failure(FailureKind kind, int position) {}

  /** 文字、長さ、padding、pad bitの規範順に最初の失敗を返します。 */
  static Optional<Failure> firstFailure(String text) {
    Objects.requireNonNull(text, "text");
    int utf16 = 0;
    int position = 0;
    while (utf16 < text.length()) {
      int codePoint = text.codePointAt(utf16);
      if (codePoint != '=' && sextet(codePoint) < 0) {
        return Optional.of(new Failure(FailureKind.INVALID_CHARACTER, position));
      }
      utf16 += Character.charCount(codePoint);
      position++;
    }
    if ((position & 3) != 0) {
      return Optional.of(new Failure(FailureKind.INVALID_LENGTH, position));
    }

    for (int start = 0; start < text.length(); start += 4) {
      boolean finalGroup = start + 4 == text.length();
      if (!finalGroup) {
        for (int offset = 0; offset < 4; offset++) {
          if (text.charAt(start + offset) == '=') {
            return Optional.of(new Failure(FailureKind.INVALID_PADDING, start + offset));
          }
        }
        continue;
      }

      char first = text.charAt(start);
      char second = text.charAt(start + 1);
      char third = text.charAt(start + 2);
      char fourth = text.charAt(start + 3);
      if (first == '=') {
        return Optional.of(new Failure(FailureKind.INVALID_PADDING, start));
      }
      if (second == '=') {
        return Optional.of(new Failure(FailureKind.INVALID_PADDING, start + 1));
      }
      if (third == '=' && fourth != '=') {
        return Optional.of(new Failure(FailureKind.INVALID_PADDING, start + 3));
      }
      if (third == '=' && (sextet(second) & 0x0f) != 0) {
        return Optional.of(new Failure(FailureKind.NON_ZERO_PAD_BITS, start + 1));
      }
      if (third != '=' && fourth == '=' && (sextet(third) & 0x03) != 0) {
        return Optional.of(new Failure(FailureKind.NON_ZERO_PAD_BITS, start + 2));
      }
    }
    return Optional.empty();
  }

  /** 規範Base64文字列を返します。 */
  static String encode(ByteSequenceValue bytes) {
    return Base64.getEncoder().encodeToString(bytes.copyBytes());
  }

  /** {@link #firstFailure(String)}で成功した入力だけを復号します。 */
  static ByteSequenceValue decodeValidated(String text) {
    try {
      return ByteSequenceValue.takeOwnership(Base64.getDecoder().decode(text));
    } catch (IllegalArgumentException disagreement) {
      throw new IllegalStateException("strict Base64 validator and decoder disagree", disagreement);
    }
  }

  private static int sextet(int codePoint) {
    if (codePoint >= 'A' && codePoint <= 'Z') {
      return codePoint - 'A';
    }
    if (codePoint >= 'a' && codePoint <= 'z') {
      return codePoint - 'a' + 26;
    }
    if (codePoint >= '0' && codePoint <= '9') {
      return codePoint - '0' + 52;
    }
    if (codePoint == '+') {
      return 62;
    }
    return codePoint == '/' ? 63 : -1;
  }
}
