package jp.bsb.binding;

/**
 * 1回の静的解析内で字句スコープを識別する、1始まりの番号です。
 *
 * @param value 1以上の識別番号
 */
public record ScopeId(int value) {
  /** 1以上であることを検証します。 */
  public ScopeId {
    if (value < 1) {
      throw new IllegalArgumentException("scope id must be at least 1");
    }
  }

  @Override
  public String toString() {
    return "S" + value;
  }
}
