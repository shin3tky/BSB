package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** 書込能力の閉じた応答です。本文、論理名、一時資源を安全表現へ出しません。 */
public final class FileWriteResult {
  /** 応答状態です。UNKNOWNは契約違反を試験するためだけの閉じた番兵です。 */
  public enum State {
    SUCCESS,
    FAILURE,
    NOT_MAPPED,
    ACCESS_DENIED,
    CANCELLED,
    UNKNOWN
  }

  private final String workspaceName;
  private final WorkspaceHandle handle;
  private final String logicalName;
  private final State state;
  private final OptionalLong publishedBytes;
  private final Optional<String> failureKind;

  /** 状態別形状をexecutorで検査できる、埋込みホスト向け完全コンストラクタです。 */
  public FileWriteResult(
      String workspaceName,
      WorkspaceHandle handle,
      String logicalName,
      State state,
      OptionalLong publishedBytes,
      Optional<String> failureKind) {
    this.workspaceName = Objects.requireNonNull(workspaceName, "workspaceName");
    this.handle = Objects.requireNonNull(handle, "handle");
    this.logicalName = Objects.requireNonNull(logicalName, "logicalName");
    this.state = Objects.requireNonNull(state, "state");
    this.publishedBytes = Objects.requireNonNull(publishedBytes, "publishedBytes");
    this.failureKind = Objects.requireNonNull(failureKind, "failureKind");
  }

  /** 公開成功応答を作ります。 */
  public static FileWriteResult success(FileWriteRequest request) {
    return success(request, request.body().length());
  }

  /** 能力契約試験用に公開バイト数を明示した成功応答を作ります。 */
  public static FileWriteResult success(FileWriteRequest request, long publishedBytes) {
    return new FileWriteResult(
        request.workspaceName(),
        request.handle(),
        request.logicalName(),
        State.SUCCESS,
        OptionalLong.of(publishedBytes),
        Optional.empty());
  }

  /** 回復可能な閉じた失敗応答を作ります。 */
  public static FileWriteResult failure(FileWriteRequest request, String kind) {
    if (!FileWriteFailureValue.KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown file write failure kind");
    }
    return new FileWriteResult(
        request.workspaceName(),
        request.handle(),
        request.logicalName(),
        State.FAILURE,
        OptionalLong.empty(),
        Optional.of(kind));
  }

  public static FileWriteResult notMapped(FileWriteRequest request) {
    return empty(request, State.NOT_MAPPED);
  }

  public static FileWriteResult accessDenied(FileWriteRequest request) {
    return empty(request, State.ACCESS_DENIED);
  }

  public static FileWriteResult cancelled(FileWriteRequest request) {
    return empty(request, State.CANCELLED);
  }

  private static FileWriteResult empty(FileWriteRequest request, State state) {
    return new FileWriteResult(
        request.workspaceName(),
        request.handle(),
        request.logicalName(),
        state,
        OptionalLong.empty(),
        Optional.empty());
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

  public State state() {
    return state;
  }

  public OptionalLong publishedBytes() {
    return publishedBytes;
  }

  public Optional<String> failureKind() {
    return failureKind;
  }

  @Override
  public String toString() {
    return "FileWriteResult[workspaceName="
        + workspaceName
        + ", operation=write, state="
        + state
        + "]";
  }
}
