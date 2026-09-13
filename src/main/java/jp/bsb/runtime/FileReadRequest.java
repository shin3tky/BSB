package jp.bsb.runtime;

import java.util.Objects;

/** ファイル読取能力へ渡す、有限登録を指す1回分の要求です。 */
public record FileReadRequest(
    String workspaceName, WorkspaceHandle handle, String logicalName, long maximumBytes) {
  /** 入力を検証します。論理名はexecutorで規範検証済みです。 */
  public FileReadRequest {
    Objects.requireNonNull(workspaceName, "workspaceName");
    Objects.requireNonNull(handle, "handle");
    Objects.requireNonNull(logicalName, "logicalName");
    if (maximumBytes < 0 || maximumBytes > FileLimits.MAX_POLICY_BYTES) {
      throw new IllegalArgumentException("maximumBytes is outside the effective read range");
    }
  }

  /** 論理名と参照を伏せた安全表現です。 */
  @Override
  public String toString() {
    return "FileReadRequest[workspaceName="
        + workspaceName
        + ", operation=read, maximumBytes="
        + maximumBytes
        + "]";
  }
}
