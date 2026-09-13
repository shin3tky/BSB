package jp.bsb.stdlib;

import java.util.ArrayDeque;
import java.util.Optional;

/** 表示と等値比較について、型引数を含む具体型全体の特性を判定します。 */
public final class ValueTypeTraits {
  private ValueTypeTraits() {}

  /** 型全体が表示可能ならtrueを返します。 */
  public static boolean isDisplayable(ValueType type) {
    return firstNonDisplayable(type).isEmpty();
  }

  /** 型全体が等値比較可能ならtrueを返します。 */
  public static boolean isEqualityComparable(ValueType type) {
    return firstNonEqualityComparable(type).isEmpty();
  }

  /** 成功型、失敗型の順に探索し、最初の表示不能な葉型を返します。 */
  public static Optional<ValueType> firstNonDisplayable(ValueType type) {
    return firstMatchingLeaf(type, ValueTypeTraits::isNonDisplayableLeaf);
  }

  /** 成功型、失敗型の順に探索し、最初の等値比較不能な葉型を返します。 */
  public static Optional<ValueType> firstNonEqualityComparable(ValueType type) {
    return firstMatchingLeaf(type, ValueTypeTraits::isNonEqualityComparableLeaf);
  }

  private static Optional<ValueType> firstMatchingLeaf(
      ValueType root, java.util.function.Predicate<ValueType> predicate) {
    if (root == null) {
      throw new NullPointerException("root");
    }
    var work = new ArrayDeque<ValueType>();
    work.push(root);
    while (!work.isEmpty()) {
      ValueType current = work.pop();
      if (current instanceof OptionalType optional) {
        work.push(optional.elementType());
      } else if (current instanceof ResultType result) {
        work.push(result.failureType());
        work.push(result.successType());
      } else if (predicate.test(current)) {
        return Optional.of(current);
      }
    }
    return Optional.empty();
  }

  private static boolean isNonDisplayableLeaf(ValueType type) {
    return isNonEqualityComparableLeaf(type) || type.equals(ValueType.BYTE_SEQUENCE);
  }

  private static boolean isNonEqualityComparableLeaf(ValueType type) {
    return type.equals(ValueType.REGEX)
        || type.equals(ValueType.INPUT_RESULT)
        || type.equals(ValueType.DATE_TIME)
        || type.equals(ValueType.JSON_PARSE_FAILURE)
        || type.equals(ValueType.UTF8_DECODE_FAILURE)
        || type.equals(ValueType.BASE64_DECODE_FAILURE)
        || type.equals(ValueType.HTTP_REQUEST)
        || type.equals(ValueType.HTTP_RESPONSE)
        || type.equals(ValueType.HTTP_SEND_FAILURE)
        || type.equals(ValueType.FILE_READ_FAILURE)
        || type.equals(ValueType.FILE_WRITE_FAILURE)
        || type.equals(ValueType.DELIMITED_TEXT_PARSE_FAILURE);
  }
}
