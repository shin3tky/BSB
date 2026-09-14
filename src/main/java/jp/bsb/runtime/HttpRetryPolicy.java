package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** HTTP送信の有限再試行、待機、最終失敗記録を表す不変な非秘密方針です。 */
public record HttpRetryPolicy(
    String mode,
    int maximumAttempts,
    List<String> retryableFailureKinds,
    List<Integer> retryableStatusCodes,
    long initialDelayMilliseconds,
    long maximumDelayMilliseconds,
    int backoffMultiplier,
    boolean respectRetryAfter,
    long maximumRetryAfterMilliseconds,
    String methodSafety,
    Optional<String> idempotencyKeyHeader,
    long minimumStartIntervalMilliseconds,
    String finalFailurePolicy) {

  private static final HttpRetryPolicy NONE =
      new HttpRetryPolicy(
          "none",
          1,
          List.of(),
          List.of(),
          0,
          0,
          1,
          false,
          0,
          "safeOnly",
          Optional.empty(),
          0,
          "disabled");

  /** 可変コレクションから切り離し、nullを拒否します。意味検証は能力境界で行います。 */
  public HttpRetryPolicy {
    Objects.requireNonNull(mode, "mode");
    retryableFailureKinds = List.copyOf(retryableFailureKinds);
    retryableStatusCodes = List.copyOf(retryableStatusCodes);
    Objects.requireNonNull(methodSafety, "methodSafety");
    idempotencyKeyHeader = Objects.requireNonNull(idempotencyKeyHeader, "idempotencyKeyHeader");
    Objects.requireNonNull(finalFailurePolicy, "finalFailurePolicy");
  }

  /** 従来どおり再試行しない方針です。 */
  public static HttpRetryPolicy none() {
    return NONE;
  }

  /** 設定内容を公開しない安全表現です。 */
  @Override
  public String toString() {
    return "<http-retry-policy>";
  }
}
