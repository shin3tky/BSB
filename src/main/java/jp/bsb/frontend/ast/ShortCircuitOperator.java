package jp.bsb.frontend.ast;

/** 短絡評価ブロックの論理演算子です。 */
public enum ShortCircuitOperator {
  /** 左辺が真なら右辺を評価しない論理和です。 */
  OR("または"),
  /** 左辺が偽なら右辺を評価しない論理積です。 */
  AND("かつ");

  private final String sourceName;

  ShortCircuitOperator(String sourceName) {
    this.sourceName = sourceName;
  }

  /** ソース上の予約語を返します。 */
  public String sourceName() {
    return sourceName;
  }
}
