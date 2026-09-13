package jp.bsb.runtime;

/** 文字列・正規表現の文字列値と文字列生成操作で共有する規範的な資源上限です。 */
public final class StringLimits {
  /** 1個の文字列値に許されるUTF-8バイト数です。 */
  public static final int MAX_UTF8_BYTES = 16_777_216;

  private StringLimits() {}
}
