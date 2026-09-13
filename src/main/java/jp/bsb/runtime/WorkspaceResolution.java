package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;

/** 作業領域解決能力が返す、自由文や登録一覧を持たない閉じた応答です。 */
public record WorkspaceResolution(
    String workspaceName,
    String operation,
    State state,
    Optional<WorkspaceHandle> handle,
    Optional<WorkspacePolicy> policy,
    Optional<String> invalidReason) {
  /** 解決状態です。 */
  public enum State {
    RESOLVED,
    NOT_CONFIGURED,
    DENIED,
    INVALID
  }

  /** nullだけを拒否し、状態別形状は信頼境界のexecutorで検査します。 */
  public WorkspaceResolution {
    Objects.requireNonNull(workspaceName, "workspaceName");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(state, "state");
    handle = Objects.requireNonNull(handle, "handle");
    policy = Objects.requireNonNull(policy, "policy");
    invalidReason = Objects.requireNonNull(invalidReason, "invalidReason");
  }

  /** 正常な解決応答を作ります。 */
  public static WorkspaceResolution resolved(
      String workspaceName, String operation, WorkspaceHandle handle, WorkspacePolicy policy) {
    return new WorkspaceResolution(
        workspaceName,
        operation,
        State.RESOLVED,
        Optional.of(handle),
        Optional.of(policy),
        Optional.empty());
  }

  /** 未設定応答を作ります。 */
  public static WorkspaceResolution notConfigured(String workspaceName, String operation) {
    return empty(workspaceName, operation, State.NOT_CONFIGURED);
  }

  /** 利用拒否応答を作ります。 */
  public static WorkspaceResolution denied(String workspaceName, String operation) {
    return empty(workspaceName, operation, State.DENIED);
  }

  /** 閉じた理由を持つ設定不正応答を作ります。 */
  public static WorkspaceResolution invalid(String workspaceName, String operation, String reason) {
    return new WorkspaceResolution(
        workspaceName,
        operation,
        State.INVALID,
        Optional.empty(),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(reason, "reason")));
  }

  private static WorkspaceResolution empty(String workspaceName, String operation, State state) {
    return new WorkspaceResolution(
        workspaceName, operation, state, Optional.empty(), Optional.empty(), Optional.empty());
  }

  /** 方針と不透明参照を公開しない安全表現です。 */
  @Override
  public String toString() {
    return "WorkspaceResolution[workspaceName="
        + workspaceName
        + ", operation="
        + operation
        + ", state="
        + state
        + "]";
  }
}
