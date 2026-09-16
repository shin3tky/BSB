package jp.bsb.runtime;

import java.util.Objects;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.OptionalType;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ValueType;

/** トレース値を1行へ安全に収め、将来の非開示値を伏せる共通変換です。 */
final class TraceValueFormatter {
  static final int MAX_DISPLAY_CODE_POINTS = 32;
  static final int MAX_ARRAY_ELEMENTS = 8;
  static final int MAX_ARRAY_TEXT_ELEMENT_CODE_POINTS = 16;

  private TraceValueFormatter() {}

  static String format(RuntimeValue value, TraceValuePolicy policy) {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(policy, "policy");
    String type = value.type().sourceName();
    if (containsJsonShapeType(value.type()) || !policy.mayReveal(value)) {
      return type + ":<redacted>";
    }
    if (value instanceof OptionalValue || value instanceof ResultValue) {
      if (!mayRevealWrapped(value, policy)) {
        return type + ":<redacted>";
      }
      return type + ":" + formatWrapped(value);
    }
    if (value instanceof ArrayValue array) {
      return type + ":" + formatArray(array);
    }
    String display = value.displayText();
    int codePoints = display.codePointCount(0, display.length());
    if (codePoints > MAX_DISPLAY_CODE_POINTS) {
      int end = display.offsetByCodePoints(0, MAX_DISPLAY_CODE_POINTS);
      display = display.substring(0, end) + "…";
    }
    return type + ":" + escapeControls(display);
  }

  private static boolean containsJsonShapeType(ValueType root) {
    var work = new java.util.ArrayDeque<ValueType>();
    work.push(root);
    while (!work.isEmpty()) {
      ValueType type = work.pop();
      if (type.equals(ValueType.JSON_SHAPE) || type.equals(ValueType.JSON_SHAPE_FAILURE)) {
        return true;
      }
      if (type instanceof OptionalType optional) {
        work.push(optional.elementType());
      } else if (type instanceof ResultType result) {
        work.push(result.successType());
        work.push(result.failureType());
      } else if (type instanceof ArrayType array) {
        work.push(array.elementType());
      }
    }
    return false;
  }

  private static boolean mayRevealWrapped(RuntimeValue value, TraceValuePolicy policy) {
    RuntimeValue current = value;
    while (true) {
      if (!policy.mayReveal(current)) {
        return false;
      }
      if (current instanceof OptionalValue optional) {
        if (!optional.isPresent()) {
          return true;
        }
        current = optional.value().orElseThrow();
      } else if (current instanceof ResultValue result) {
        current = result.value();
      } else {
        return true;
      }
    }
  }

  private static String formatWrapped(RuntimeValue value) {
    var result = new StringBuilder();
    RuntimeValue current = value;
    int wrapperDepth = 0;
    while (true) {
      if (current instanceof OptionalValue optional) {
        if (!optional.isPresent()) {
          return result.append("ない").append(")".repeat(wrapperDepth)).toString();
        }
        result.append("ある(");
        wrapperDepth++;
        current = optional.value().orElseThrow();
      } else if (current instanceof ResultValue wrapped) {
        result.append(wrapped.isSuccess() ? "成功(" : "失敗(");
        wrapperDepth++;
        current = wrapped.value();
      } else {
        break;
      }
    }
    if (current instanceof ArrayValue array) {
      return result.append(formatArray(array)).append(")".repeat(wrapperDepth)).toString();
    }
    String display = current.displayText();
    int codePoints = display.codePointCount(0, display.length());
    if (codePoints > MAX_DISPLAY_CODE_POINTS) {
      display = display.substring(0, display.offsetByCodePoints(0, MAX_DISPLAY_CODE_POINTS)) + "…";
    }
    return result.append(escapeControls(display)).append(")".repeat(wrapperDepth)).toString();
  }

  private static String formatArray(ArrayValue array) {
    int displayed = Math.min(array.size(), MAX_ARRAY_ELEMENTS);
    var result = new StringBuilder("【");
    for (int index = 0; index < displayed; index++) {
      if (index > 0) {
        result.append('、');
      }
      result.append(formatArrayElement(array.get(index)));
    }
    if (array.size() > displayed) {
      if (displayed > 0) {
        result.append('、');
      }
      result.append("…(+").append(array.size() - displayed).append(')');
    }
    return result.append('】').toString();
  }

  private static String formatArrayElement(RuntimeValue value) {
    return switch (value) {
      case CharacterValue character -> "'" + formatTextElement(character.value(), true) + "'";
      case StringValue string -> "「" + formatTextElement(string.value(), false) + "」";
      case IntegerValue integer -> integer.displayText();
      case DecimalValue decimal -> decimal.displayText();
      case BooleanValue bool -> bool.displayText();
      case RoundingModeValue roundingMode -> roundingMode.displayText();
      case RegexValue regex -> regex.displayText();
      case JsonRuntimeValue json -> json.displayText();
      case JsonParseFailureValue ignored ->
          throw new IllegalArgumentException("JSON parse failures cannot be array elements");
      case ByteSequenceValue ignored ->
          throw new IllegalArgumentException("byte sequences cannot be array elements");
      case Utf8DecodeFailureValue ignored ->
          throw new IllegalArgumentException("UTF-8 decode failures cannot be array elements");
      case Base64DecodeFailureValue ignored ->
          throw new IllegalArgumentException("Base64 decode failures cannot be array elements");
      case HttpRequestValue ignored ->
          throw new IllegalArgumentException("HTTP requests cannot be array elements");
      case HttpResponseValue ignored ->
          throw new IllegalArgumentException("HTTP responses cannot be array elements");
      case HttpSendFailureValue ignored ->
          throw new IllegalArgumentException("HTTP send failures cannot be array elements");
      case FileReadFailureValue ignored ->
          throw new IllegalArgumentException("file read failures cannot be array elements");
      case FileWriteFailureValue ignored ->
          throw new IllegalArgumentException("file write failures cannot be array elements");
      case DelimitedTextParseFailureValue ignored ->
          throw new IllegalArgumentException(
              "delimited text parse failures cannot be array elements");
      case JsonShapeValue ignored ->
          throw new IllegalArgumentException("JSON shapes cannot be array elements");
      case JsonShapeFailureValue ignored ->
          throw new IllegalArgumentException("JSON shape failures cannot be traced");
      case ArrayValue array -> formatArray(array);
      case InputResultValue ignored ->
          throw new IllegalArgumentException("input results cannot be array elements");
      case DateTimeValue ignored ->
          throw new IllegalArgumentException("date-times cannot be array elements");
      case OptionalValue optional -> formatWrapped(optional);
      case ResultValue result -> formatWrapped(result);
    };
  }

  private static String formatTextElement(String value, boolean characterLiteral) {
    int codePoints = value.codePointCount(0, value.length());
    int displayed = Math.min(codePoints, MAX_ARRAY_TEXT_ELEMENT_CODE_POINTS);
    int end = value.offsetByCodePoints(0, displayed);
    String result = ArrayValue.escapeLiteral(value.substring(0, end), characterLiteral);
    return displayed < codePoints ? result + "…" : result;
  }

  static String escapeControls(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
