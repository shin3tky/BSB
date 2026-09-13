package jp.bsb.runtime;

/**
 * トレースへ保存する、配列反復内部状態の不変スナップショットです。
 *
 * @param index 本体へ渡している要素の0始まり添字
 * @param length 反復対象配列の長さ
 */
public record ArrayLoopTraceState(int index, int length) implements ControlTraceState {
  /** 現在添字が非空配列の範囲内にあることを検証します。 */
  public ArrayLoopTraceState {
    if (length <= 0 || index < 0 || index >= length) {
      throw new IllegalArgumentException("invalid array-loop trace state");
    }
  }

  @Override
  public String traceText() {
    return "array:" + index + "/" + length;
  }
}
