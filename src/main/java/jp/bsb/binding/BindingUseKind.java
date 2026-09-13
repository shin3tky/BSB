package jp.bsb.binding;

/** 名前付き値の利用方法です。 */
public enum BindingUseKind {
  /** 現在値をデータスタックへ積む参照 */
  READ("read"),
  /** データスタック最上部を保存する代入先 */
  WRITE("write");

  private final String reportName;

  BindingUseKind(String reportName) {
    this.reportName = reportName;
  }

  /**
   * 束縛TSVで使う英小文字名を返します。
   *
   * @return 束縛TSVで使う英小文字名
   */
  public String reportName() {
    return reportName;
  }
}
