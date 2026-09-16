package jp.bsb.stdlib;

import java.util.Objects;
import java.util.Optional;

/**
 * 許可された葉型または1次元配列型を要素に持つ、最大2次元の配列型です。
 *
 * @param elementType 配列の全要素が持つ具体型
 */
public record ArrayType(ValueType elementType) implements ValueType {
  /** null、禁止葉型、ラッパー越しを含む3次元以上を拒否します。 */
  public ArrayType {
    Objects.requireNonNull(elementType, "elementType");
    if (!elementType.isArrayElementType()) {
      throw new IllegalArgumentException("the value type is not an available array element type");
    }
  }

  @Override
  public String sourceName() {
    return "配列<" + elementType.sourceName() + ">";
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public boolean isArray() {
    return true;
  }

  @Override
  public boolean isOptional() {
    return false;
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
    return Optional.of(elementType);
  }

  @Override
  public Optional<ValueType> optionalElementType() {
    return Optional.empty();
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
  public String toString() {
    return sourceName();
  }
}
