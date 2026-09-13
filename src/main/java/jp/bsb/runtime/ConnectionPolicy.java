package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 論理接続の非秘密方針です。
 *
 * <p>値は不変コピーとして保持します。規範妥当性は能力境界で検査するため、この型は不正設定も表現できます。
 */
public record ConnectionPolicy(
    String baseUri,
    List<String> allowedOrigins,
    List<String> allowedMethods,
    String authenticationKind,
    Optional<CredentialReference> credentialReference,
    long connectTimeoutMilliseconds,
    long responseTimeoutMilliseconds,
    long maximumRequestBytes,
    long maximumResponseBytes,
    String redirectPolicy,
    String retryPolicy) {
  /** nullを拒否し、可変コレクションから切り離します。 */
  public ConnectionPolicy {
    Objects.requireNonNull(baseUri, "baseUri");
    allowedOrigins = List.copyOf(allowedOrigins);
    allowedMethods = List.copyOf(allowedMethods);
    Objects.requireNonNull(authenticationKind, "authenticationKind");
    credentialReference = Objects.requireNonNull(credentialReference, "credentialReference");
    Objects.requireNonNull(redirectPolicy, "redirectPolicy");
    Objects.requireNonNull(retryPolicy, "retryPolicy");
  }

  /**
   * 能力境界と同じ規範順で方針を検証します。
   *
   * @return 有効なら空、無効なら秘密を含まない安定理由
   */
  public Optional<String> validationProblem() {
    return Optional.ofNullable(ConnectionPolicyValidator.validate(this));
  }

  /** 接続定義の内容を含まない安全表現だけを返します。 */
  @Override
  public String toString() {
    return "<connection-policy>";
  }
}
