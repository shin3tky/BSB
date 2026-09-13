package jp.bsb.stdlib;

/** 静的検査と実行系で共有する、配列の配列資源上限です。 */
public final class ArrayLimits {
  /** 1配列に格納できる最大要素数です。 */
  public static final int MAX_LENGTH = 65_536;

  /** 1つの2次元配列値が保持できる論理葉要素数です。 */
  public static final long MAX_NESTED_LEAF_ELEMENTS = 1_000_000L;

  /** 1回の実行で新しく配列へ格納できる論理要素参照数です。 */
  public static final long MAX_CONSTRUCTION_UNITS = 1_000_000L;

  /** 1回の実行で配列に比例して処理できる要素数です。 */
  public static final long MAX_ELEMENT_OPERATION_UNITS = 10_000_000L;

  private ArrayLimits() {}
}
