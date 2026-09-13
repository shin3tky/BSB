package jp.bsb.runtime;

import java.util.Objects;

/** HTTP能力境界で受け渡す、順序付きの名前と値です。 */
public final class HttpTransportHeader {
  private final String name;
  private final String value;

  /** 名前と値を不変に保持します。意味上の検証は能力境界で行います。 */
  public HttpTransportHeader(String name, String value) {
    this.name = Objects.requireNonNull(name, "name");
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return ヘッダー名
   */
  public String name() {
    return name;
  }

  /**
   * @return ヘッダー値
   */
  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof HttpTransportHeader header
        && name.equals(header.name)
        && value.equals(header.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }

  /** 内容をログへ漏らさない安全表現だけを返します。 */
  @Override
  public String toString() {
    return "<http-header>";
  }
}
