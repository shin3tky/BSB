package jp.bsb.runtime;

/** 命令トレースへ保存できる、実行中ループの不変な内部状態です。 */
public sealed interface ControlTraceState permits ArrayLoopTraceState, CountedLoopTraceState {
  /**
   * {@code controlBefore}・{@code controlAfter}列へ書く正規表現を返します。
   *
   * @return ループ種別を含む1状態の表現
   */
  String traceText();
}
