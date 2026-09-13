package jp.bsb.binding;

/** 名前付き値を保持する保存領域と寿命です。 */
public enum BindingStorage {
  /** プログラム開始時にソース順で1回初期化する大域保存領域 */
  GLOBAL("global"),
  /** 宣言へ到達するたび初期化し、呼出しフレームごとに独立する局所保存領域 */
  LOCAL("local");

  private final String reportName;

  BindingStorage(String reportName) {
    this.reportName = reportName;
  }

  /**
   * 束縛説明で使う英小文字名を返します。
   *
   * @return 束縛説明で使う英小文字名
   */
  public String reportName() {
    return reportName;
  }
}
