package jp.bsb.runtime;

/** バイト列の不変バイト列と、その1実行内処理で共有する規範的な上限です。 */
public final class ByteSequenceLimits {
  /** 1個のバイト列値に許される論理バイト数です。 */
  public static final int MAX_VALUE_BYTES = 67_108_864;

  /** 1実行で新たに所有できるバイト列の累積バイト数です。 */
  public static final long MAX_CONSTRUCTION_BYTES = 134_217_728L;

  /** 1実行で走査・符号化・復号できる累積バイト数です。 */
  public static final long MAX_WORK_BYTES = 268_435_456L;

  private ByteSequenceLimits() {}
}
