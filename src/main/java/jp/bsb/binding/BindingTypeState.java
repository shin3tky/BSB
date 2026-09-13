package jp.bsb.binding;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.stdlib.ValueType;

/**
 * 宣言型の解析状態です。型が未確定または診断済みの場合も{@code null}を使わず明示します。
 *
 * @param status 型解析の状態
 * @param type 推論済みの場合だけ存在する型
 */
public record BindingTypeState(Status status, Optional<ValueType> type) {
  /** 状態と型の組合せを検証します。 */
  public BindingTypeState {
    Objects.requireNonNull(status, "status");
    type = Objects.requireNonNull(type, "type");
    if ((status == Status.INFERRED) != type.isPresent()) {
      throw new IllegalArgumentException("only an inferred binding type may contain a value type");
    }
  }

  /**
   * 未推論状態を作ります。
   *
   * @return まだ初期値の型検査をしていない状態
   */
  public static BindingTypeState uninferred() {
    return new BindingTypeState(Status.UNINFERRED, Optional.empty());
  }

  /**
   * 推論済み状態を作ります。
   *
   * @param type 初期値から推論した型
   * @return 推論済み状態
   */
  public static BindingTypeState inferred(ValueType type) {
    return new BindingTypeState(Status.INFERRED, Optional.of(type));
  }

  /**
   * 診断済み状態を作ります。
   *
   * @return 型診断を報告済みで、後段へ型を渡さない状態
   */
  public static BindingTypeState diagnosed() {
    return new BindingTypeState(Status.DIAGNOSED, Optional.empty());
  }

  /** 型解析の有限状態です。 */
  public enum Status {
    /** 未推論 */
    UNINFERRED,
    /** 推論済み */
    INFERRED,
    /** 型に関する診断を報告済み */
    DIAGNOSED
  }
}
