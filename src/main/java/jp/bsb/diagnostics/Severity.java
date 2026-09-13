package jp.bsb.diagnostics;

/**
 * 診断メッセージの重要度（深刻度）を表す列挙型です。
 *
 * <p>【コンピュータ科学の観点】 コンパイラやインタープリタなどの言語処理系では、プログラムの検証中に発見された問題を その影響度合いに応じて分類します：
 *
 * <ul>
 *   <li>{@link #ERROR}: 現在の処理を継続できない問題。発生段階に応じて後続フェーズまたは実行を中止します。
 *   <li>{@link #WARNING}: 実行自体は可能だが、潜在的なバグや紛らわしい記述などの注意点。
 * </ul>
 */
public enum Severity {
  /** エラー：文法違反や型の不整合、実行時上限など、処理の継続を許可しない問題 */
  ERROR("エラー"),

  /** 警告：紛らわしい変数名など、文法上は受理できるが望ましくない状態 */
  WARNING("警告");

  /** 人間向けに表示する際の日本語名称（例: "エラー", "警告"） */
  private final String displayName;

  Severity(String displayName) {
    this.displayName = displayName;
  }

  /**
   * 画面表示用の日本語名称を返します。
   *
   * @return 表示用名称
   */
  public String displayName() {
    return displayName;
  }
}
