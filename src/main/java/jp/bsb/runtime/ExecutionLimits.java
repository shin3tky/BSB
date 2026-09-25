package jp.bsb.runtime;

/** 1回のプログラム実行へ適用する、利用者が選択可能な実行上限です。 */
public record ExecutionLimits(long instructionLimit) {
  public ExecutionLimits {
    if (instructionLimit <= 0) {
      throw new IllegalArgumentException("instruction limit must be positive");
    }
  }

  /** 従来どおりの規範的な既定上限を返します。 */
  public static ExecutionLimits defaults() {
    return new ExecutionLimits(RuntimeLimits.EXECUTED_INSTRUCTIONS);
  }
}
