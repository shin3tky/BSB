package jp.bsb.runtime;

import java.nio.charset.StandardCharsets;
import jp.bsb.json.JsonValue;

/** 表示文字列全体を作る前に、決定的なUTF-8長を有限幅で計測します。 */
final class DisplayMetrics {
  private static final long SATURATED = Long.MAX_VALUE;

  private DisplayMetrics() {}

  static long utf8Bytes(RuntimeValue value) {
    if (value instanceof OptionalValue || value instanceof ResultValue) {
      return wrappedUtf8Bytes(value);
    }
    return unwrappedUtf8Bytes(value);
  }

  private static long wrappedUtf8Bytes(RuntimeValue value) {
    long result = 0;
    int closingParentheses = 0;
    RuntimeValue current = value;
    while (true) {
      if (current instanceof OptionalValue optional) {
        if (!optional.isPresent()) {
          return add(add(result, bytes("ない")), 3L * closingParentheses);
        }
        result = add(result, bytes("ある（"));
        closingParentheses++;
        current = optional.value().orElseThrow();
      } else if (current instanceof ResultValue wrapped) {
        result = add(result, bytes(wrapped.isSuccess() ? "成功（" : "失敗（"));
        closingParentheses++;
        current = wrapped.value();
      } else {
        return add(add(result, unwrappedUtf8Bytes(current)), 3L * closingParentheses);
      }
    }
  }

  private static long unwrappedUtf8Bytes(RuntimeValue value) {
    if (value instanceof ArrayValue array) {
      long result = 6;
      if (array.size() > 1) {
        result = add(result, 3L * (array.size() - 1));
      }
      for (RuntimeValue element : array.elements()) {
        result = add(result, arrayElementUtf8Bytes(element));
      }
      return result;
    }
    if (value instanceof JsonRuntimeValue json) {
      return json.value().metrics().serializedUtf8Bytes();
    }
    return bytes(value.displayText());
  }

  private static long arrayElementUtf8Bytes(RuntimeValue value) {
    if (value instanceof ArrayValue || value instanceof JsonRuntimeValue) {
      return unwrappedUtf8Bytes(value);
    }
    return bytes(ArrayValue.elementDisplayText(value));
  }

  static long nestedJsonWork(ArrayValue array) {
    long result = 0;
    for (RuntimeValue rowValue : array.elements()) {
      ArrayValue row = (ArrayValue) rowValue;
      for (RuntimeValue leaf : row.elements()) {
        if (leaf instanceof JsonRuntimeValue json) {
          JsonValue value = json.value();
          result =
              add(result, add(value.metrics().serializedUtf8Bytes(), jsonTraversalUnits(value)));
        }
      }
    }
    return result;
  }

  static long nestedJsonEqualityWork(ArrayValue first, ArrayValue second) {
    long result = 0;
    for (int rowIndex = 0; rowIndex < first.size(); rowIndex++) {
      ArrayValue firstRow = (ArrayValue) first.get(rowIndex);
      ArrayValue secondRow = (ArrayValue) second.get(rowIndex);
      int cells = Math.min(firstRow.size(), secondRow.size());
      for (int cellIndex = 0; cellIndex < cells; cellIndex++) {
        JsonValue firstJson = ((JsonRuntimeValue) firstRow.get(cellIndex)).value();
        JsonValue secondJson = ((JsonRuntimeValue) secondRow.get(cellIndex)).value();
        result = add(result, jsonTraversalUnits(firstJson));
        result = add(result, jsonTraversalUnits(secondJson));
      }
    }
    return result;
  }

  private static long jsonTraversalUnits(JsonValue value) {
    return add(value.metrics().nodeCount(), value.metrics().containerReferences());
  }

  private static long bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  private static long add(long left, long right) {
    return right > SATURATED - left ? SATURATED : left + right;
  }
}
