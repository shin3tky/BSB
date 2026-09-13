package jp.bsb.runtime;

import jp.bsb.stdlib.ValueTypeTraits;

/** 任意値と結果値が交互に入れ子になっても再帰せず処理する共通実装です。 */
final class RuntimeWrappedValueSupport {
  private RuntimeWrappedValueSupport() {}

  static String displayText(RuntimeValue value) {
    if (!ValueTypeTraits.isDisplayable(value.type())) {
      return "<redacted>";
    }
    var result = new StringBuilder();
    RuntimeValue current = value;
    int closingParentheses = 0;
    while (true) {
      if (current instanceof OptionalValue optional) {
        if (!optional.isPresent()) {
          return result.append("ない").append("）".repeat(closingParentheses)).toString();
        }
        result.append("ある（");
        closingParentheses++;
        current = optional.value().orElseThrow();
      } else if (current instanceof ResultValue wrapped) {
        result.append(wrapped.isSuccess() ? "成功（" : "失敗（");
        closingParentheses++;
        current = wrapped.value();
      } else {
        return result
            .append(current.displayText())
            .append("）".repeat(closingParentheses))
            .toString();
      }
    }
  }

  static boolean valuesEqual(RuntimeValue first, RuntimeValue second) {
    if (!first.type().equals(second.type())) {
      return false;
    }
    RuntimeValue left = first;
    RuntimeValue right = second;
    while (true) {
      if (left instanceof OptionalValue leftOptional
          && right instanceof OptionalValue rightOptional) {
        if (leftOptional.isPresent() != rightOptional.isPresent()) {
          return false;
        }
        if (!leftOptional.isPresent()) {
          return true;
        }
        left = leftOptional.value().orElseThrow();
        right = rightOptional.value().orElseThrow();
      } else if (left instanceof ResultValue leftResult
          && right instanceof ResultValue rightResult) {
        if (leftResult.state() != rightResult.state()) {
          return false;
        }
        left = leftResult.value();
        right = rightResult.value();
      } else {
        return left.equals(right);
      }
    }
  }

  static int valueHash(RuntimeValue value) {
    int hash = value.type().hashCode();
    RuntimeValue current = value;
    while (true) {
      if (current instanceof OptionalValue optional) {
        hash = 31 * hash + Boolean.hashCode(optional.isPresent());
        if (!optional.isPresent()) {
          return hash;
        }
        current = optional.value().orElseThrow();
      } else if (current instanceof ResultValue result) {
        hash = 31 * hash + result.state().hashCode();
        current = result.value();
      } else {
        return 31 * hash + current.hashCode();
      }
    }
  }
}
