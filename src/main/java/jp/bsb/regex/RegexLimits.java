package jp.bsb.regex;

/** 文字列・正規表現の正規表現コンパイルと照合で共有する規範的な資源上限です。 */
public final class RegexLimits {
  /** 1個のrawパターンに許されるUTF-8バイト数です。 */
  public static final int MAX_PATTERN_UTF8_BYTES = 65_536;

  /** 1個の正規表現に許されるキャプチャ数です。 */
  public static final int MAX_CAPTURES = 64;

  /** Unicode 16.0適合層で展開後に許されるコンパイル命令数です。 */
  public static final int MAX_PROGRAM_INSTRUCTIONS = 65_536;

  /** 1回の正規表現呼出しに許される照合単位です。 */
  public static final long MAX_WORK_UNITS_PER_CALL = 1_000_000_000L;

  /** 1回のrunに許される累積照合単位です。 */
  public static final long MAX_WORK_UNITS_PER_EXECUTION = 10_000_000_000L;

  private RegexLimits() {}
}
