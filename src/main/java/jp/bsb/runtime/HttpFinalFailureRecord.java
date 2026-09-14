package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** 本文・URI・header・秘密を持たない、使い切ったHTTP送信の閉じた記録です。 */
public record HttpFinalFailureRecord(
    String connectionName,
    String method,
    int attempts,
    String classification,
    Optional<String> failureKind,
    OptionalInt status) {
  public HttpFinalFailureRecord {
    Objects.requireNonNull(connectionName, "connectionName");
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(classification, "classification");
    failureKind = Objects.requireNonNull(failureKind, "failureKind");
    status = Objects.requireNonNull(status, "status");
    if (attempts < 1 || attempts > 8) throw new IllegalArgumentException("invalid attempts");
    if (classification.equals("transportFailure") != failureKind.isPresent()
        || classification.equals("httpStatus") != status.isPresent()
        || (!classification.equals("transportFailure") && !classification.equals("httpStatus"))) {
      throw new IllegalArgumentException("invalid final HTTP failure classification");
    }
  }

  @Override
  public String toString() {
    return "HttpFinalFailureRecord[connectionName="
        + connectionName
        + ", method="
        + method
        + ", attempts="
        + attempts
        + ", classification="
        + classification
        + "]";
  }
}
