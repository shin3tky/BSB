package jp.bsb.runtime;

import java.util.Objects;

/** 実行開始からの公開経過ミリ秒を取得する能力です。 */
@FunctionalInterface
public interface MonotonicTime {
  /** 実行開始からの非減少ミリ秒値を返します。 */
  long milliseconds() throws CapabilityException;

  /** 指定時計の構築時を0とする能力を作ります。 */
  static MonotonicTime elapsed(MonotonicClock clock) {
    MonotonicClock source = Objects.requireNonNull(clock, "clock");
    long start = source.nanoTime();
    return () -> {
      long now = source.nanoTime();
      if (now < start) {
        throw new IllegalStateException("monotonic time moved backwards");
      }
      try {
        return Math.subtractExact(now, start) / 1_000_000L;
      } catch (ArithmeticException overflow) {
        throw new IllegalStateException("monotonic elapsed time overflow", overflow);
      }
    };
  }
}
