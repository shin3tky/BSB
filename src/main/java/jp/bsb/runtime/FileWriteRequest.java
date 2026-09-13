package jp.bsb.runtime;

import java.util.Objects;

/** ファイル書込能力へ渡す、有限登録を指す1回分の要求です。 */
public final class FileWriteRequest {
  private final String workspaceName;
  private final WorkspaceHandle handle;
  private final String logicalName;
  private final ByteSequenceValue body;
  private final long maximumBytes;

  /** 不変な要求を作ります。本文値は元から不変であり、その参照だけを保持します。 */
  public FileWriteRequest(
      String workspaceName,
      WorkspaceHandle handle,
      String logicalName,
      ByteSequenceValue body,
      long maximumBytes) {
    this.workspaceName = Objects.requireNonNull(workspaceName, "workspaceName");
    this.handle = Objects.requireNonNull(handle, "handle");
    this.logicalName = Objects.requireNonNull(logicalName, "logicalName");
    this.body = Objects.requireNonNull(body, "body");
    if (maximumBytes < FileLimits.MIN_POLICY_BYTES || maximumBytes > FileLimits.MAX_POLICY_BYTES) {
      throw new IllegalArgumentException("maximumBytes is outside the write policy range");
    }
    this.maximumBytes = maximumBytes;
  }

  public String workspaceName() {
    return workspaceName;
  }

  public WorkspaceHandle handle() {
    return handle;
  }

  public String logicalName() {
    return logicalName;
  }

  public ByteSequenceValue body() {
    return body;
  }

  /**
   * 原子的書込能力へ本文の防御コピーを渡します。
   *
   * @return 本文の全バイト
   */
  public byte[] bodyBytes() {
    return body.copyBytes();
  }

  public long maximumBytes() {
    return maximumBytes;
  }

  /** 論理名、本文、参照を伏せた安全表現です。 */
  @Override
  public String toString() {
    return "FileWriteRequest[workspaceName="
        + workspaceName
        + ", operation=write, bodyBytes="
        + body.length()
        + ", maximumBytes="
        + maximumBytes
        + "]";
  }
}
