package jp.bsb.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import jp.bsb.frontend.UnicodeRules;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.ValueType;

/**
 * 同じ具体型の値を順序つきで保持する、不変な最大2次元の配列値です。
 *
 * @param elementType 全要素に共通する具体型
 * @param elements ソース順または操作結果順に並ぶ不変の要素列
 * @param logicalLeafCount 値内の論理葉要素数
 */
public record ArrayValue(ValueType elementType, List<RuntimeValue> elements, long logicalLeafCount)
    implements RuntimeValue {
  /** 葉要素数を導出して不変値を作ります。 */
  public ArrayValue(ValueType elementType, List<RuntimeValue> elements) {
    this(elementType, elements, measuredLogicalLeafCount(elementType, elements));
  }

  /** 要素列を防御的に複製し、型・長さ・cached metricsを実行時値の境界で検証します。 */
  public ArrayValue {
    Objects.requireNonNull(elementType, "elementType");
    if (!elementType.isArrayElementType()) {
      throw new IllegalArgumentException("an array value requires an available element type");
    }
    elements = List.copyOf(elements);
    if (elements.size() > ArrayLimits.MAX_LENGTH) {
      throw new IllegalArgumentException("an array value exceeds the maximum length");
    }
    if (elements.stream().anyMatch(element -> !element.type().equals(elementType))) {
      throw new IllegalArgumentException("every array element must have the declared value type");
    }
    long measured = measuredLogicalLeafCount(elementType, elements);
    if (logicalLeafCount != measured) {
      throw new IllegalArgumentException(
          "the cached logical leaf count does not match the elements");
    }
    if (ValueType.arrayConstructorDepth(elementType) > 0
        && logicalLeafCount > ArrayLimits.MAX_NESTED_LEAF_ELEMENTS) {
      throw new IllegalArgumentException("a nested array value exceeds the logical leaf limit");
    }
  }

  /**
   * 要素数を返します。
   *
   * @return 配列に含まれる要素数
   */
  public int size() {
    return elements.size();
  }

  /** 検査済みの0始まり位置にある要素を返します。 */
  RuntimeValue get(int index) {
    return elements.get(index);
  }

  /** 検査済み半開区間を同じ要素型の新しい不変値として返します。 */
  ArrayValue slice(int start, int end) {
    return new ArrayValue(elementType, elements.subList(start, end));
  }

  /** 検査済み位置だけを置き換え、元の値を変更しない新しい配列を返します。 */
  ArrayValue replaced(int index, RuntimeValue replacement) {
    var result = new ArrayList<>(elements);
    result.set(index, replacement);
    return new ArrayValue(elementType, result);
  }

  /** 末尾へ1要素を加え、元の値を変更しない新しい配列を返します。 */
  ArrayValue appended(RuntimeValue element) {
    var result = new ArrayList<RuntimeValue>(elements.size() + 1);
    result.addAll(elements);
    result.add(element);
    return new ArrayValue(elementType, result);
  }

  /** 先頭へ1要素を加え、元の値を変更しない新しい配列を返します。 */
  ArrayValue prepended(RuntimeValue element, long resultLogicalLeafCount) {
    var result = new ArrayList<RuntimeValue>(elements.size() + 1);
    result.add(element);
    result.addAll(elements);
    return new ArrayValue(elementType, result, resultLogicalLeafCount);
  }

  /** 同じ要素型の第2配列を後ろへ連結した新しい配列を返します。 */
  ArrayValue concatenated(ArrayValue second, long resultLogicalLeafCount) {
    var result = new ArrayList<RuntimeValue>(elements.size() + second.elements.size());
    result.addAll(elements);
    result.addAll(second.elements);
    return new ArrayValue(elementType, result, resultLogicalLeafCount);
  }

  /** 要素順だけを反転し、元の値を変更しない新しい配列を返します。 */
  ArrayValue reversed() {
    var result = new ArrayList<>(elements);
    Collections.reverse(result);
    return new ArrayValue(elementType, result, logicalLeafCount);
  }

  /** 指定した半開区間を除き、元の値を変更しない新しい配列を返します。 */
  ArrayValue deletedRange(int start, int end, long resultLogicalLeafCount) {
    var result = new ArrayList<RuntimeValue>(elements.size() - (end - start));
    result.addAll(elements.subList(0, start));
    result.addAll(elements.subList(end, elements.size()));
    return new ArrayValue(elementType, result, resultLogicalLeafCount);
  }

  /** 指定位置の直前へ1要素を挿入し、元の値を変更しない新しい配列を返します。 */
  ArrayValue inserted(int index, RuntimeValue element, long resultLogicalLeafCount) {
    var result = new ArrayList<RuntimeValue>(elements.size() + 1);
    result.addAll(elements.subList(0, index));
    result.add(element);
    result.addAll(elements.subList(index, elements.size()));
    return new ArrayValue(elementType, result, resultLogicalLeafCount);
  }

  @Override
  public ValueType type() {
    return new ArrayType(elementType);
  }

  @Override
  public String displayText() {
    return elements.stream()
        .map(ArrayValue::elementDisplayText)
        .collect(java.util.stream.Collectors.joining("、", "【", "】"));
  }

  static String elementDisplayText(RuntimeValue value) {
    return switch (value) {
      case CharacterValue character -> "'" + escapeLiteral(character.value(), true) + "'";
      case StringValue string -> "「" + escapeLiteral(string.value(), false) + "」";
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
          throw new IllegalArgumentException("JSON shape failures cannot be displayed");
      case ArrayValue array -> array.displayText();
      case InputResultValue ignored ->
          throw new IllegalArgumentException("input results cannot be array elements");
      case DateTimeValue ignored ->
          throw new IllegalArgumentException("date-times cannot be array elements");
      case OptionalValue optional -> optional.displayText();
      case ResultValue result -> result.displayText();
    };
  }

  /** フォーマッタと同じ規則で、値から文字・文字列リテラルの標準形を再構成します。 */
  static String escapeLiteral(String value, boolean characterLiteral) {
    var result = new StringBuilder(value.length());
    value
        .codePoints()
        .forEach(
            codePoint -> {
              switch (codePoint) {
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\\' -> result.append("\\\\");
                case '\'' -> result.append(characterLiteral ? "\\'" : "'");
                case '「' -> result.append(characterLiteral ? "「" : "\\「");
                case '」' -> result.append(characterLiteral ? "」" : "\\」");
                default -> {
                  if (mustUseUnicodeEscape(codePoint)) {
                    result
                        .append("\\u{")
                        .append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT))
                        .append('}');
                  } else {
                    result.appendCodePoint(codePoint);
                  }
                }
              }
            });
    return result.toString();
  }

  private static boolean mustUseUnicodeEscape(int codePoint) {
    return (codePoint >= 0x00 && codePoint <= 0x1F)
        || (codePoint >= 0x7F && codePoint <= 0x9F)
        || UnicodeRules.isForbiddenIdentifierCodePoint(codePoint);
  }

  private static long measuredLogicalLeafCount(ValueType elementType, List<RuntimeValue> elements) {
    if (elements == null) {
      return -1;
    }
    try {
      return ArrayNestedLimit.measure(elementType, elements);
    } catch (IllegalArgumentException | ArithmeticException failure) {
      return -1;
    }
  }
}
