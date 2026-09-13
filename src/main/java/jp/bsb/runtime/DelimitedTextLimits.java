package jp.bsb.runtime;

/** CSV/TSV変換で共有する規範的な資源上限です。 */
public final class DelimitedTextLimits {
  /** 1回の実行で解析・直列化できる区切りテキスト作業単位です。 */
  public static final long MAX_WORK_UNITS = 134_217_728L;

  private DelimitedTextLimits() {}
}
