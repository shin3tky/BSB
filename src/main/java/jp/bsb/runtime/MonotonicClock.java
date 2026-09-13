package jp.bsb.runtime;

/** 壁時計の変更に影響されない、差し替え可能な単調増加時計です。 */
@FunctionalInterface
public interface MonotonicClock {
  /**
   * 任意の起点からのナノ秒値を返します。
   *
   * @return 単調増加するナノ秒値
   */
  long nanoTime();

  /**
   * JVMの標準単調増加時計を返します。
   *
   * @return {@link System#nanoTime()} を使う時計
   */
  static MonotonicClock system() {
    return System::nanoTime;
  }
}
