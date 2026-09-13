package jp.bsb.runtime;

import java.util.Objects;

/** 能力不足または能力委譲失敗を、ホスト例外から切り離して表します。 */
public final class CapabilityException extends Exception {
  /** 能力境界で利用者へ報告できる失敗種別です。 */
  public enum Kind {
    /** 要求した能力を実行環境が提供していません。 */
    UNAVAILABLE,
    /** 提供された能力が処理を完了できませんでした。 */
    FAILURE
  }

  private final Kind kind;
  private final RuntimeCapability capability;
  private final String operation;

  private CapabilityException(Kind kind, RuntimeCapability capability, String operation) {
    super(null, null, false, false);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.capability = Objects.requireNonNull(capability, "capability");
    this.operation = requireOperation(operation);
  }

  /** 能力不足を作ります。 */
  public static CapabilityException unavailable(RuntimeCapability capability, String operation) {
    return new CapabilityException(Kind.UNAVAILABLE, capability, operation);
  }

  /** ホスト詳細を保持しない能力失敗を作ります。 */
  public static CapabilityException failure(RuntimeCapability capability, String operation) {
    return new CapabilityException(Kind.FAILURE, capability, operation);
  }

  /**
   * @return 失敗種別
   */
  public Kind kind() {
    return kind;
  }

  /**
   * @return 対象能力
   */
  public RuntimeCapability capability() {
    return capability;
  }

  /**
   * @return 仕様上の操作名
   */
  public String operation() {
    return operation;
  }

  private static String requireOperation(String value) {
    Objects.requireNonNull(value, "operation");
    if (value.isBlank()) {
      throw new IllegalArgumentException("operation must not be blank");
    }
    return value;
  }
}
