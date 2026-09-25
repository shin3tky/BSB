package jp.bsb.runtime;

import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ValueType;

/** 二次元配列の論理葉要素数を、値や予算を変更する前に計測・検査します。 */
final class ArrayNestedLimit {
  private ArrayNestedLimit() {}

  static long measure(ValueType elementType, List<? extends RuntimeValue> elements) {
    if (ValueType.arrayConstructorDepth(elementType) == 0) {
      return elements.size();
    }
    long result = 0;
    for (RuntimeValue element : elements) {
      if (!element.type().equals(elementType)) {
        throw new IllegalArgumentException("array elements must have the declared element type");
      }
      result = Math.addExact(result, logicalLeafCount(element));
    }
    return result;
  }

  static long slice(ArrayValue array, int start, int end) {
    return measure(array.elementType(), array.elements().subList(start, end));
  }

  static long replace(ArrayValue array, int index, RuntimeValue replacement) {
    if (ValueType.arrayConstructorDepth(array.elementType()) == 0) {
      return array.size();
    }
    long oldRow = logicalLeafCount(array.get(index));
    long newRow = logicalLeafCount(replacement);
    return Math.addExact(Math.subtractExact(array.logicalLeafCount(), oldRow), newRow);
  }

  static long append(ArrayValue array, RuntimeValue element) {
    if (ValueType.arrayConstructorDepth(array.elementType()) == 0) {
      return array.size() + 1L;
    }
    return Math.addExact(array.logicalLeafCount(), logicalLeafCount(element));
  }

  static long prepend(ArrayValue array, RuntimeValue element) {
    return append(array, element);
  }

  static long concatenate(ArrayValue first, ArrayValue second) {
    return Math.addExact(first.logicalLeafCount(), second.logicalLeafCount());
  }

  static long deleteRange(ArrayValue array, int start, int end) {
    if (ValueType.arrayConstructorDepth(array.elementType()) == 0) {
      return array.size() - (long) (end - start);
    }
    long removed = measure(array.elementType(), array.elements().subList(start, end));
    return Math.subtractExact(array.logicalLeafCount(), removed);
  }

  static void requireAllowed(
      String sourcePath, SourceSpan span, String operation, ValueType elementType, long observed)
      throws RuntimeFailure {
    if (ValueType.arrayConstructorDepth(elementType) == 0
        || observed <= ArrayLimits.MAX_NESTED_LEAF_ELEMENTS) {
      return;
    }
    throw new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_ARRAY_NESTED_ELEMENT_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("operation", operation)
            .limit("arrayNestedLeafElements", ArrayLimits.MAX_NESTED_LEAF_ELEMENTS, observed)
            .expected(ArrayLimits.MAX_NESTED_LEAF_ELEMENTS + "個以下")
            .actual(observed + "個")
            .fix("行数または各行の要素数を減らしてください。")
            .build());
  }

  private static long logicalLeafCount(RuntimeValue value) {
    RuntimeValue current = value;
    while (true) {
      if (current instanceof OptionalValue optional) {
        if (!optional.isPresent()) {
          return 0;
        }
        current = optional.value().orElseThrow();
      } else if (current instanceof ResultValue result) {
        current = result.value();
      } else if (current instanceof ArrayValue array) {
        return array.logicalLeafCount();
      } else {
        return 1;
      }
    }
  }
}
