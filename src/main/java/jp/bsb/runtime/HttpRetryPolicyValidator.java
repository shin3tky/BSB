package jp.bsb.runtime;

import java.util.HashSet;
import java.util.Set;

/** HTTP信頼性方針を秘密なしの安定理由へ検証します。 */
final class HttpRetryPolicyValidator {
  static final Set<Integer> ALLOWED_STATUSES = Set.of(408, 425, 429, 500, 502, 503, 504);

  private HttpRetryPolicyValidator() {}

  static String validate(HttpRetryPolicy policy) {
    if (!Set.of("none", "bounded").contains(policy.mode())) return "MODE_INVALID";
    if (policy.maximumAttempts() < 1
        || policy.maximumAttempts() > 8
        || (policy.mode().equals("none") && policy.maximumAttempts() != 1))
      return "ATTEMPT_LIMIT_INVALID";
    var failures = new HashSet<>(policy.retryableFailureKinds());
    if (failures.size() != policy.retryableFailureKinds().size()
        || !HttpSendFailureValue.KINDS.containsAll(failures)
        || failures.contains("responseTooLarge")
        || failures.contains("contentDecodingFailure")) return "FAILURE_KIND_INVALID";
    var statuses = new HashSet<>(policy.retryableStatusCodes());
    if (statuses.size() != policy.retryableStatusCodes().size()
        || !ALLOWED_STATUSES.containsAll(statuses)) return "STATUS_CODE_INVALID";
    if (policy.initialDelayMilliseconds() < 0
        || policy.initialDelayMilliseconds() > 60_000
        || policy.maximumDelayMilliseconds() < policy.initialDelayMilliseconds()
        || policy.maximumDelayMilliseconds() > 300_000) return "DELAY_INVALID";
    if (policy.backoffMultiplier() != 1 && policy.backoffMultiplier() != 2)
      return "BACKOFF_INVALID";
    if (policy.maximumRetryAfterMilliseconds() < 0
        || policy.maximumRetryAfterMilliseconds() > 300_000) return "RETRY_AFTER_INVALID";
    if (!Set.of("safeOnly", "idempotencyKey", "apiGuaranteed").contains(policy.methodSafety()))
      return "METHOD_SAFETY_INVALID";
    boolean headerExpected = policy.methodSafety().equals("idempotencyKey");
    if (headerExpected != policy.idempotencyKeyHeader().isPresent()
        || policy
            .idempotencyKeyHeader()
            .filter(h -> HttpRequestSupport.headerNameProblem(h) != null)
            .isPresent()) return "IDEMPOTENCY_HEADER_INVALID";
    if (policy.minimumStartIntervalMilliseconds() < 0
        || policy.minimumStartIntervalMilliseconds() > 60_000) return "RATE_LIMIT_INVALID";
    if (!Set.of("disabled", "optional", "required").contains(policy.finalFailurePolicy()))
      return "FINAL_FAILURE_POLICY_INVALID";
    if (policy.mode().equals("none")
        && (!failures.isEmpty()
            || !statuses.isEmpty()
            || policy.initialDelayMilliseconds() != 0
            || policy.maximumDelayMilliseconds() != 0
            || policy.respectRetryAfter()
            || policy.maximumRetryAfterMilliseconds() != 0
            || policy.minimumStartIntervalMilliseconds() != 0
            || !policy.finalFailurePolicy().equals("disabled"))) return "MODE_INVALID";
    return null;
  }
}
