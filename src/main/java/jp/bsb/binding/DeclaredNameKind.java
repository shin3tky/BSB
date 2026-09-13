package jp.bsb.binding;

/** 単語、定数、変数が共有する利用者可視名前空間での宣言種別です。 */
public enum DeclaredNameKind {
  /** 利用者定義単語 */
  WORD("単語"),
  /** 初期化後に更新できない名前付き値 */
  CONSTANT("定数"),
  /** 同じ型の値へ更新できる名前付き値 */
  VARIABLE("変数"),
  /** ホスト側の定義を静的に参照する論理接続 */
  LOGICAL_CONNECTION("論理接続"),
  /** ホスト側の有限登録を静的に参照する作業領域 */
  WORKSPACE("作業領域");

  private final String sourceName;

  DeclaredNameKind(String sourceName) {
    this.sourceName = sourceName;
  }

  /**
   * 診断で使う日本語名を返します。
   *
   * @return 種別の日本語名
   */
  public String sourceName() {
    return sourceName;
  }
}
