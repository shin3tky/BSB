package jp.bsb.runtime;

import java.util.Objects;
import jp.bsb.stdlib.ResultType;

/** 成功または失敗の選択側1値だけを、両側の具体型とともに保持する不変な結果値です。 */
public final class ResultValue implements RuntimeValue {
  /** 結果値の選択状態です。 */
  public enum State {
    SUCCESS,
    FAILURE
  }

  private final ResultType type;
  private final State state;
  private final RuntimeValue value;

  private ResultValue(ResultType type, State state, RuntimeValue value) {
    this.type = Objects.requireNonNull(type, "type");
    this.state = Objects.requireNonNull(state, "state");
    this.value = Objects.requireNonNull(value, "value");
    var expected = state == State.SUCCESS ? type.successType() : type.failureType();
    if (!value.type().equals(expected)) {
      throw new IllegalArgumentException("a result payload must match its selected declared type");
    }
  }

  /** 指定された結果型の成功値を作ります。 */
  public static ResultValue success(ResultType type, RuntimeValue value) {
    return new ResultValue(type, State.SUCCESS, value);
  }

  /** 指定された結果型の失敗値を作ります。 */
  public static ResultValue failure(ResultType type, RuntimeValue value) {
    return new ResultValue(type, State.FAILURE, value);
  }

  /** 成功ならtrueです。 */
  public boolean isSuccess() {
    return state == State.SUCCESS;
  }

  /** 失敗ならtrueです。 */
  public boolean isFailure() {
    return state == State.FAILURE;
  }

  /** 選択側の値を返します。 */
  public RuntimeValue value() {
    return value;
  }

  /** 選択状態を返します。 */
  public State state() {
    return state;
  }

  @Override
  public ResultType type() {
    return type;
  }

  @Override
  public String displayText() {
    return RuntimeWrappedValueSupport.displayText(this);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ResultValue result
        && RuntimeWrappedValueSupport.valuesEqual(this, result);
  }

  @Override
  public int hashCode() {
    return RuntimeWrappedValueSupport.valueHash(this);
  }

  @Override
  public String toString() {
    return displayText();
  }
}
