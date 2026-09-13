package jp.bsb.binding;

/**
 * 到達可能な定数・変数を元ソース順に識別する、1始まりの安定した番号です。
 *
 * <p>利用者が書いた名前とは別の値なので、IRと実行器は文字列検索をせず、このIDから保存スロットを選べます。
 *
 * @param value 1以上の識別番号
 */
public record BindingId(int value) {
  /** 1以上であることを検証します。 */
  public BindingId {
    if (value < 1) {
      throw new IllegalArgumentException("binding id must be at least 1");
    }
  }

  /**
   * 束縛説明で使用する安定表記を返します。
   *
   * @return {@code B1}、{@code B2}のような表記
   */
  public String displayName() {
    return "B" + value;
  }

  @Override
  public String toString() {
    return displayName();
  }
}
