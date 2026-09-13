package jp.bsb.ir;

/**
 * 名前解決済みの呼出先を小さな整数で参照する識別子です。
 *
 * @param value 非負の識別番号
 */
public record SymbolId(int value) {
  /** 非負であることを検証します。 */
  public SymbolId {
    if (value < 0) {
      throw new IllegalArgumentException("symbol id must not be negative");
    }
  }
}
