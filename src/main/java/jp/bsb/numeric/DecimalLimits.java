package jp.bsb.numeric;

/** BSBソース小数と実行小数が共有する規範資源上限です。 */
public final class DecimalLimits {
  /** 構築前と正規化後に許可する小数スケール絶対値です。 */
  public static final int ABSOLUTE_SCALE = 65_536;

  private DecimalLimits() {}
}
