package jp.bsb.runtime;

import java.util.Optional;

/** 埋込みホストとCLIがHTTP再試行方針を事前検証する公開入口です。 */
public final class HttpRetryPolicies {
  private HttpRetryPolicies() {}

  /** 有効なら空、不正なら秘密を含まない安定理由を返します。 */
  public static Optional<String> validationProblem(HttpRetryPolicy policy) {
    return Optional.ofNullable(HttpRetryPolicyValidator.validate(policy));
  }
}
