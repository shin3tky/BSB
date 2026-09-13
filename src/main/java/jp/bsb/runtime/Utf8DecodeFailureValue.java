package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/** UTF-8復号失敗について、安定した種類と0始まりバイト位置だけを保持します。 */
public final class Utf8DecodeFailureValue implements RuntimeValue {
  /** 公開する失敗種別を判定順に並べた閉じた集合です。 */
  public static final List<String> KINDS =
      List.of(
          "invalidLeadingByte",
          "invalidContinuationByte",
          "truncatedSequence",
          "overlongEncoding",
          "surrogateCodePoint",
          "codePointOutOfRange");

  private final String kind;
  private final long byteOffset;

  /** 種類と位置を検証して失敗値を作ります。 */
  public Utf8DecodeFailureValue(String kind, long byteOffset) {
    this.kind = Objects.requireNonNull(kind, "kind");
    if (!KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown UTF-8 decode failure kind: " + kind);
    }
    if (byteOffset < 0) {
      throw new IllegalArgumentException("a UTF-8 decode failure offset must be non-negative");
    }
    this.byteOffset = byteOffset;
  }

  /** 安定したASCII失敗種別を返します。 */
  public String kind() {
    return kind;
  }

  /** 0始まりのバイト位置を返します。 */
  public long byteOffset() {
    return byteOffset;
  }

  @Override
  public ValueType type() {
    return ValueType.UTF8_DECODE_FAILURE;
  }

  @Override
  public String displayText() {
    return "<redacted>";
  }

  @Override
  public String toString() {
    return traceText();
  }
}
