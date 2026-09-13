package jp.bsb.runtime;

import jp.bsb.numeric.DecimalLimits;

/** ホスト入出力までの実行系で共有する、配列・文字列・正規表現以外の規範的な資源上限です。 */
public final class RuntimeLimits {
  /** 整数演算結果の符号を除く10進桁数上限です。 */
  public static final int INTEGER_DIGITS = 65_536;

  /** 正規化した小数係数の有効桁数上限です。 */
  public static final int DECIMAL_PRECISION = 65_536;

  /** 正規化した小数スケールの絶対値上限です。 */
  public static final int DECIMAL_ABSOLUTE_SCALE = DecimalLimits.ABSOLUTE_SCALE;

  /** データスタックへ同時に保持できる値数上限です。 */
  public static final int DATA_STACK_VALUES = 65_536;

  /** メインを1個目とするBSB呼出フレーム数上限です。 */
  public static final int CALL_STACK_FRAMES = 1_024;

  /** 実際に実行できるIR命令数上限です。 */
  public static final long EXECUTED_INSTRUCTIONS = 10_000_000L;

  /** 入力待ちと明示待機を除く、メインの能動実行時間上限です。 */
  public static final long ELAPSED_NANOS = 30_000_000_000L;

  /** 標準出力または標準エラーの各チャネルへ正常に書けるUTF-8バイト数上限です。 */
  public static final long OUTPUT_UTF8_BYTES = 67_108_864L;

  /** 1入力行の行終端を除くUTF-8バイト数上限です。 */
  public static final int INPUT_LINE_UTF8_BYTES = 16_777_216;

  /** 1実行で消費できる行終端込みの生入力バイト数上限です。 */
  public static final long INPUT_TOTAL_BYTES = 67_108_864L;

  /** 起動引数全体に許されるUTF-8バイト数上限です。 */
  public static final long ARGUMENT_TOTAL_UTF8_BYTES = 67_108_864L;

  /** 1回の待機に指定できる最大ミリ秒です。 */
  public static final long WAIT_CALL_MILLISECONDS = 86_400_000L;

  /** 1実行で正常完了できる待機要求の累積上限です。 */
  public static final long WAIT_TOTAL_MILLISECONDS = 604_800_000L;

  private RuntimeLimits() {}
}
