package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/** Base64復号失敗について、安定した種類と0始まり文字位置だけを保持します。 */
public final class Base64DecodeFailureValue implements RuntimeValue {
  /** 公開する失敗種別を判定順に並べた閉じた集合です。 */
  public static final List<String> KINDS =
      List.of("invalidCharacter", "invalidLength", "invalidPadding", "nonZeroPadBits");

  private final String kind;
  private final long characterOffset;

  /** 種類と位置を検証して失敗値を作ります。 */
  public Base64DecodeFailureValue(String kind, long characterOffset) {
    this.kind = Objects.requireNonNull(kind, "kind");
    if (!KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown Base64 decode failure kind: " + kind);
    }
    if (characterOffset < 0) {
      throw new IllegalArgumentException("a Base64 decode failure offset must be non-negative");
    }
    this.characterOffset = characterOffset;
  }

  /** 安定したASCII失敗種別を返します。 */
  public String kind() {
    return kind;
  }

  /** 0始まりのUnicodeスカラー値位置を返します。 */
  public long characterOffset() {
    return characterOffset;
  }

  @Override
  public ValueType type() {
    return ValueType.BASE64_DECODE_FAILURE;
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
