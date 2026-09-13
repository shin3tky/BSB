package jp.bsb.runtime;

import java.math.BigInteger;
import java.util.Objects;

/**
 * トレースへ保存する、回数ループ内部状態の不変スナップショットです。
 *
 * @param remaining 現在の反復を含む残り回数
 * @param initial ループ開始時の回数
 */
public record CountedLoopTraceState(BigInteger remaining, BigInteger initial)
    implements ControlTraceState {
  /** 正数で、残り回数が初期値以下であることを検証します。 */
  public CountedLoopTraceState {
    Objects.requireNonNull(remaining, "remaining");
    Objects.requireNonNull(initial, "initial");
    if (remaining.signum() <= 0 || initial.signum() <= 0 || remaining.compareTo(initial) > 0) {
      throw new IllegalArgumentException("invalid counted-loop trace state");
    }
  }

  @Override
  public String traceText() {
    return "count:" + remaining + "/" + initial;
  }
}
