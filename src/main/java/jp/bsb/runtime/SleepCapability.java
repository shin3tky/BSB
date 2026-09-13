package jp.bsb.runtime;

/** 実時間の経過方法を実行器から分離する待機能力です。 */
@FunctionalInterface
public interface SleepCapability {
  /** 待機結果です。 */
  enum Result {
    /** 指定時間の待機を完了しました。 */
    COMPLETED,
    /** ホストが待機を取り消しました。 */
    CANCELLED
  }

  /** 指定ミリ秒を待機し、完了または取消を返します。 */
  Result sleep(long milliseconds) throws CapabilityException;

  /** JVMの待機を使う公開CLI用能力を作ります。 */
  static SleepCapability system() {
    return milliseconds -> {
      try {
        Thread.sleep(milliseconds);
        return Result.COMPLETED;
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return Result.CANCELLED;
      }
    };
  }
}
