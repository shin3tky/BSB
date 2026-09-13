package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;

/** 読取能力の閉じた応答です。内容や論理名を安全表現へ出しません。 */
public final class FileReadResult {
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
  private final Optional<ByteSequenceValue> body;
  private final Optional<String> failureKind;
  private final long observedBytes;
  private final boolean complete;

  /** 状態別形状をexecutorで検査できる、埋込みホスト向け完全コンストラクタです。 */
  public FileReadResult(
      String workspaceName,
      WorkspaceHandle handle,
      String logicalName,
      State state,
      Optional<ByteSequenceValue> body,
      Optional<String> failureKind,
      long observedBytes,
      boolean complete) {
    this.workspaceName = Objects.requireNonNull(workspaceName, "workspaceName");
    this.handle = Objects.requireNonNull(handle, "handle");
    this.logicalName = Objects.requireNonNull(logicalName, "logicalName");
    this.state = Objects.requireNonNull(state, "state");
    this.body = Objects.requireNonNull(body, "body");
    this.failureKind = Objects.requireNonNull(failureKind, "failureKind");
    if (observedBytes < 0) throw new IllegalArgumentException("observedBytes must not be negative");
    this.observedBytes = observedBytes;
    this.complete = complete;
  }

  /** 完全な成功応答を作ります。入力bytesは防御コピーされます。 */
  public static FileReadResult success(FileReadRequest request, byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return new FileReadResult(
        request.workspaceName(),
        request.handle(),
        request.logicalName(),
        State.SUCCESS,
        Optional.of(ByteSequenceValue.copyOf(bytes)),
        Optional.empty(),
        bytes.length,
        true);
  }

  /** 回復可能な閉じた失敗応答を作ります。 */
  public static FileReadResult failure(FileReadRequest request, String kind) {
    if (!FileReadFailureValue.KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown file read failure kind");
    }
    long observed = kind.equals("tooLarge") ? request.maximumBytes() + 1 : 0;
    return new FileReadResult(
        request.workspaceName(),
        request.handle(),
        request.logicalName(),
        State.FAILURE,
        Optional.empty(),
        Optional.of(kind),
        observed,
        false);
  }

  public static FileReadResult notMapped(FileReadRequest request) {
    return empty(request, State.NOT_MAPPED);
  }

  public static FileReadResult accessDenied(FileReadRequest request) {
    return empty(request, State.ACCESS_DENIED);
  }

  public static FileReadResult cancelled(FileReadRequest request) {
    return empty(request, State.CANCELLED);
  }

  private static FileReadResult empty(FileReadRequest request, State state) {
    return new FileReadResult(
        request.workspaceName(),
        request.handle(),
        request.logicalName(),
        state,
        Optional.empty(),
        Optional.empty(),
        0,
        false);
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

  Optional<ByteSequenceValue> body() {
    return body;
  }

  public Optional<String> failureKind() {
    return failureKind;
  }

  public long observedBytes() {
    return observedBytes;
  }

  public boolean complete() {
    return complete;
  }

  @Override
  public String toString() {
    return "FileReadResult[workspaceName="
        + workspaceName
        + ", operation=read, state="
        + state
        + "]";
  }
}
