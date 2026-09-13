package jp.bsb.runtime;

import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.ValueType;

/** 二次元配列の論理葉要素数を、値や予算を変更する前に計測・検査します。 */
final class ArrayNestedLimit {
  private ArrayNestedLimit() {}

  static long measure(ValueType elementType, List<? extends RuntimeValue> elements) {
    if (!(elementType instanceof ArrayType)) {
      return elements.size();
    }
    long result = 0;
    for (RuntimeValue element : elements) {
      if (!(element instanceof ArrayValue row) || !row.type().equals(elementType)) {
        throw new IllegalArgumentException("nested array elements must have the declared row type");
      }
      result = Math.addExact(result, row.logicalLeafCount());
    }
    return result;
  }

  static long slice(ArrayValue array, int start, int end) {
    return measure(array.elementType(), array.elements().subList(start, end));
  }

  static long replace(ArrayValue array, int index, RuntimeValue replacement) {
    if (!(array.elementType() instanceof ArrayType)) {
      return array.size();
    }
    long oldRow = ((ArrayValue) array.get(index)).logicalLeafCount();
    long newRow = ((ArrayValue) replacement).logicalLeafCount();
    return Math.addExact(Math.subtractExact(array.logicalLeafCount(), oldRow), newRow);
  }

  static long append(ArrayValue array, RuntimeValue element) {
    if (!(array.elementType() instanceof ArrayType)) {
      return array.size() + 1L;
    }
    return Math.addExact(array.logicalLeafCount(), ((ArrayValue) element).logicalLeafCount());
  }

  static void requireAllowed(
      String sourcePath, SourceSpan span, String operation, ValueType elementType, long observed)
      throws RuntimeFailure {
    if (!(elementType instanceof ArrayType) || observed <= ArrayLimits.MAX_NESTED_LEAF_ELEMENTS) {
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
}
