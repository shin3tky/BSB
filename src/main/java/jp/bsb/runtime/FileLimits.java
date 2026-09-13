package jp.bsb.runtime;

/** 作業領域・区切り表のファイル操作とバイト累積の固定上限です。 */
public final class FileLimits {
  /** 1実行で受理するファイル操作数です。 */
  public static final long MAX_OPERATIONS = 4_096;

  /** 1作業領域・1操作に設定できる最小バイト数です。 */
  public static final long MIN_POLICY_BYTES = 1;

  /** 1作業領域・1操作に設定できる最大バイト数です。 */
  public static final long MAX_POLICY_BYTES = 67_108_864;

  /** 1実行で成功として受理する読取バイト数です。 */
  public static final long MAX_READ_BYTES = 134_217_728;

  /** 1実行で受理する書込試行バイト数です。 */
  public static final long MAX_WRITE_ATTEMPT_BYTES = 134_217_728;

  private FileLimits() {}
}
