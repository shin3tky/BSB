package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;

/** 論理接続解決能力の閉じた応答です。 */
public record ConnectionResolution(
    String connectionName,
    String operation,
    State state,
    Optional<ConnectionPolicy> policy,
    Optional<String> invalidReason) {
  /** 解決状態です。 */
  public enum State {
    /** 接続定義を解決しました。 */
    RESOLVED,
    /** 対象名は設定されていません。 */
    NOT_CONFIGURED,
    /** 呼出主体には利用が許可されていません。 */
    DENIED,
    /** ホストが設定不正を検出しました。 */
    INVALID
  }

  /** nullを拒否します。状態ごとの形は実行境界で契約として検査します。 */
  public ConnectionResolution {
    Objects.requireNonNull(connectionName, "connectionName");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(state, "state");
    policy = Objects.requireNonNull(policy, "policy");
    invalidReason = Objects.requireNonNull(invalidReason, "invalidReason");
  }

  /** 正常な解決応答を作ります。 */
  public static ConnectionResolution resolved(String connectionName, ConnectionPolicy policy) {
    return new ConnectionResolution(
        connectionName, "resolve", State.RESOLVED, Optional.of(policy), Optional.empty());
  }

  /** 未設定応答を作ります。 */
  public static ConnectionResolution notConfigured(String connectionName) {
    return empty(connectionName, State.NOT_CONFIGURED);
  }

  /** 拒否応答を作ります。 */
  public static ConnectionResolution denied(String connectionName) {
    return empty(connectionName, State.DENIED);
  }

  /** 設定不正応答を作ります。 */
  public static ConnectionResolution invalid(String connectionName, String reason) {
    return new ConnectionResolution(
        connectionName, "resolve", State.INVALID, Optional.empty(), Optional.of(reason));
  }

  private static ConnectionResolution empty(String connectionName, State state) {
    return new ConnectionResolution(
        connectionName, "resolve", state, Optional.empty(), Optional.empty());
  }

  /** 接続方針や資格情報参照を含まない安全表現だけを返します。 */
  @Override
  public String toString() {
    return "ConnectionResolution[connectionName="
        + connectionName
        + ", operation="
        + operation
        + ", state="
        + state
        + "]";
  }
}
