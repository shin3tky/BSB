package jp.bsb.runtime;

/** 作業領域の操作別1ファイル上限です。 */
public record WorkspacePolicy(long maximumReadBytes, long maximumWriteBytes) {
  /** 規範範囲内の不変方針だけを受理します。 */
  public WorkspacePolicy {
    requireLimit(maximumReadBytes, "maximumReadBytes");
    requireLimit(maximumWriteBytes, "maximumWriteBytes");
  }

  private static void requireLimit(long value, String name) {
    if (value < FileLimits.MIN_POLICY_BYTES || value > FileLimits.MAX_POLICY_BYTES) {
      throw new IllegalArgumentException(name + " is outside the workspace policy range");
    }
  }
}
