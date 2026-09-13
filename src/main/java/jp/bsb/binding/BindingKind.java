package jp.bsb.binding;

/** 名前付き値の可変性を表す宣言種別です。 */
public enum BindingKind {
  /** 初期化後に更新できない定数 */
  CONSTANT(DeclaredNameKind.CONSTANT, "constant"),
  /** 同じ型の値へ更新できる変数 */
  VARIABLE(DeclaredNameKind.VARIABLE, "variable");

  private final DeclaredNameKind declaredNameKind;
  private final String reportName;

  BindingKind(DeclaredNameKind declaredNameKind, String reportName) {
    this.declaredNameKind = declaredNameKind;
    this.reportName = reportName;
  }

  /**
   * 共通名前空間での宣言種別を返します。
   *
   * @return 共通名前空間での宣言種別
   */
  public DeclaredNameKind declaredNameKind() {
    return declaredNameKind;
  }

  /**
   * 束縛TSVで使う英小文字名を返します。
   *
   * @return 束縛TSVで使う英小文字名
   */
  public String reportName() {
    return reportName;
  }

  /**
   * 代入できるかを返します。
   *
   * @return 代入できる変数ならtrue
   */
  public boolean isMutable() {
    return this == VARIABLE;
  }
}
