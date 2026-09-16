package jp.bsb.stdlib;

import java.util.Objects;
import java.util.Optional;

/**
 * 値ありまたは値なしを表す、1個の具体的な内包型を持つ任意型です。
 *
 * @param elementType 内包する具体型
 */
public record OptionalType(ValueType elementType) implements ValueType {
  /** nullと型構築子深さ超過を拒否します。 */
  public OptionalType {
    Objects.requireNonNull(elementType, "elementType");
    if (ValueType.constructorDepth(elementType) >= MAX_TYPE_CONSTRUCTOR_DEPTH) {
      throw new IllegalArgumentException("optional type constructor depth exceeds the limit");
    }
  }

  @Override
  public String sourceName() {
    var result = new StringBuilder();
    ValueType current = this;
    int optionalDepth = 0;
    while (current instanceof OptionalType optional) {
      result.append("任意<");
      optionalDepth++;
      current = optional.elementType;
    }
    result.append(current.sourceName());
    return result.append(">".repeat(optionalDepth)).toString();
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
  public boolean isOptional() {
    return true;
  }

  @Override
  public boolean isResult() {
    return false;
  }

  @Override
  public boolean isArrayElementType() {
    return ValueType.arrayConstructorDepth(this) < 2;
  }

  @Override
  public Optional<ValueType> arrayElementType() {
    return Optional.empty();
  }

  @Override
  public Optional<ValueType> optionalElementType() {
    return Optional.of(elementType);
  }

  @Override
  public Optional<ValueType> resultSuccessType() {
    return Optional.empty();
  }

  @Override
  public Optional<ValueType> resultFailureType() {
    return Optional.empty();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof OptionalType optional)) {
      return false;
    }
    ValueType left = this;
    ValueType right = optional;
    while (left instanceof OptionalType leftOptional
        && right instanceof OptionalType rightOptional) {
      left = leftOptional.elementType;
      right = rightOptional.elementType;
    }
    return left.equals(right);
  }

  @Override
  public int hashCode() {
    ValueType current = this;
    int depth = 0;
    while (current instanceof OptionalType optional) {
      depth++;
      current = optional.elementType;
    }
    int hash = current.hashCode();
    for (int index = 0; index < depth; index++) {
      hash = 31 + hash;
    }
    return hash;
  }

  @Override
  public String toString() {
    return sourceName();
  }
}
