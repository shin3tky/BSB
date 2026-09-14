package jp.bsb.runtime;

/** 使い切ったHTTP送信を秘密なしの閉じた形で受け取る任意ホスト能力です。 */
@FunctionalInterface
public interface HttpFinalFailureSink {
  enum Result {
    RECORDED,
    CANCELLED
  }

  Result record(HttpFinalFailureRecord record) throws CapabilityException;
}
