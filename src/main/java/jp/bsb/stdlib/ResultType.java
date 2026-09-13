package jp.bsb.stdlib;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

/** 成功型と失敗型をともに保持する不変な結果型です。 */
public final class ResultType implements ValueType {
  private final ValueType successType;
  private final ValueType failureType;

  /** nullと型構築子深さ超過を拒否します。 */
  public ResultType(ValueType successType, ValueType failureType) {
    this.successType = Objects.requireNonNull(successType, "successType");
    this.failureType = Objects.requireNonNull(failureType, "failureType");
    if (ValueType.constructorDepth(this) > MAX_TYPE_CONSTRUCTOR_DEPTH) {
      throw new IllegalArgumentException("result type constructor depth exceeds the limit");
    }
  }

  public ValueType successType() {
    return successType;
  }

  public ValueType failureType() {
    return failureType;
  }

  @Override
  public String sourceName() {
    var output = new StringBuilder();
    var work = new ArrayDeque<Object>();
    work.push(this);
    while (!work.isEmpty()) {
      Object item = work.pop();
      if (item instanceof String text) {
        output.append(text);
      } else if (item instanceof ResultType result) {
        work.push(">");
        work.push(result.failureType);
        work.push(",");
        work.push(result.successType);
        work.push("結果<");
      } else if (item instanceof OptionalType optional) {
        work.push(">");
        work.push(optional.elementType());
        work.push("任意<");
      } else {
        output.append(((ValueType) item).sourceName());
      }
    }
    return output.toString();
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public boolean isResult() {
    return true;
  }

  @Override
  public boolean isOptional() {
    return false;
  }

  @Override
  public boolean isArrayElementType() {
    return false;
  }

  @Override
  public Optional<ValueType> arrayElementType() {
    return Optional.empty();
  }

  @Override
  public Optional<ValueType> optionalElementType() {
    return Optional.empty();
  }

  @Override
  public Optional<ValueType> resultSuccessType() {
    return Optional.of(successType);
  }

  @Override
  public Optional<ValueType> resultFailureType() {
    return Optional.of(failureType);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ResultType result)) {
      return false;
    }
    var work = new ArrayDeque<TypePair>();
    work.push(new TypePair(this, result));
    while (!work.isEmpty()) {
      TypePair pair = work.pop();
      if (pair.left instanceof ResultType left && pair.right instanceof ResultType right) {
        work.push(new TypePair(left.failureType, right.failureType));
        work.push(new TypePair(left.successType, right.successType));
      } else if (pair.left instanceof OptionalType left
          && pair.right instanceof OptionalType right) {
        work.push(new TypePair(left.elementType(), right.elementType()));
      } else if (!pair.left.equals(pair.right)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return sourceName().hashCode();
  }

  @Override
  public String toString() {
    return sourceName();
  }

  private record TypePair(ValueType left, ValueType right) {}
}
