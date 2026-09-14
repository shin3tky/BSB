package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValueTypeTest {
  @Test
  void preservesTheExistingScalarOrderAndAppendsLaterFeatureTypes() {
    assertEquals(
        List.of(
            "整数",
            "真偽",
            "文字",
            "文字列",
            "小数",
            "丸め方法",
            "正規表現",
            "入力結果",
            "日時",
            "JSON",
            "JSON解析失敗",
            "バイト列",
            "UTF8復号失敗",
            "Base64復号失敗",
            "HTTP要求",
            "HTTP応答",
            "HTTP送信失敗",
            "ファイル読取失敗",
            "ファイル書込失敗",
            "区切りテキスト解析失敗",
            "JSON形状",
            "JSON形状失敗"),
        ValueType.scalarTypes().stream().map(ValueType::sourceName).toList());
    assertEquals(List.of(ValueType.values()), ValueType.scalarTypes());
    assertEquals("整数", ValueType.INTEGER.toString());
    assertEquals("真偽", ValueType.BOOLEAN.toString());
    assertEquals("文字", ValueType.CHARACTER.toString());
    assertEquals("文字列", ValueType.STRING.toString());
    assertEquals("小数", ValueType.DECIMAL.toString());
    assertEquals("丸め方法", ValueType.ROUNDING_MODE.toString());
    assertEquals("正規表現", ValueType.REGEX.toString());
    assertEquals("入力結果", ValueType.INPUT_RESULT.toString());
    assertEquals("日時", ValueType.DATE_TIME.toString());
    assertEquals("JSON", ValueType.JSON.toString());
    assertEquals("JSON解析失敗", ValueType.JSON_PARSE_FAILURE.toString());
    assertEquals("バイト列", ValueType.BYTE_SEQUENCE.toString());
    assertEquals("UTF8復号失敗", ValueType.UTF8_DECODE_FAILURE.toString());
    assertEquals("Base64復号失敗", ValueType.BASE64_DECODE_FAILURE.toString());
    assertEquals("HTTP要求", ValueType.HTTP_REQUEST.toString());
    assertEquals("HTTP応答", ValueType.HTTP_RESPONSE.toString());
    assertEquals("HTTP送信失敗", ValueType.HTTP_SEND_FAILURE.toString());
    assertEquals("ファイル読取失敗", ValueType.FILE_READ_FAILURE.toString());
    assertEquals("ファイル書込失敗", ValueType.FILE_WRITE_FAILURE.toString());
    assertEquals(
        List.of("整数", "真偽", "文字", "文字列", "小数", "JSON", "JSON形状失敗"),
        ValueType.arrayElementTypes().stream().map(ValueType::sourceName).toList());
  }

  @Test
  void comparesArrayTypesStructurallyIncludingTheirElementType() {
    ArrayType firstIntegerArray = ValueType.arrayOf(ValueType.INTEGER);
    ArrayType secondIntegerArray = ValueType.arrayOf(ValueType.INTEGER);
    ArrayType stringArray = ValueType.arrayOf(ValueType.STRING);

    assertNotSame(firstIntegerArray, secondIntegerArray);
    assertEquals(firstIntegerArray, secondIntegerArray);
    assertEquals(firstIntegerArray.hashCode(), secondIntegerArray.hashCode());
    assertNotEquals(firstIntegerArray, stringArray);
    assertEquals("配列<整数>", firstIntegerArray.sourceName());
    assertEquals("配列<整数>", firstIntegerArray.toString());

    ArrayType firstMatrix = ValueType.arrayOf(firstIntegerArray);
    ArrayType secondMatrix = ValueType.arrayOf(secondIntegerArray);
    assertEquals(firstMatrix, secondMatrix);
    assertEquals(firstMatrix.hashCode(), secondMatrix.hashCode());
    assertEquals("配列<配列<整数>>", firstMatrix.sourceName());
  }

  @Test
  void exposesConcreteKindAndArrayElementTypeWithoutCasts() {
    ValueType scalar = ValueType.CHARACTER;
    ValueType array = ValueType.arrayOf(ValueType.CHARACTER);

    assertTrue(scalar.isConcrete());
    assertFalse(scalar.isArray());
    assertTrue(scalar.isArrayElementType());
    assertEquals(Optional.empty(), scalar.arrayElementType());
    assertTrue(array.isConcrete());
    assertTrue(array.isArray());
    assertTrue(array.isArrayElementType());
    assertEquals(Optional.of(ValueType.CHARACTER), array.arrayElementType());
    assertFalse(ValueType.arrayOf(ValueType.arrayOf(ValueType.CHARACTER)).isArrayElementType());
    assertFalse(ValueType.ROUNDING_MODE.isArrayElementType());
    assertFalse(ValueType.REGEX.isArrayElementType());
    assertFalse(ValueType.DATE_TIME.isArrayElementType());
    assertFalse(ValueType.JSON_PARSE_FAILURE.isArrayElementType());
    assertFalse(ValueType.BYTE_SEQUENCE.isArrayElementType());
    assertFalse(ValueType.UTF8_DECODE_FAILURE.isArrayElementType());
    assertFalse(ValueType.BASE64_DECODE_FAILURE.isArrayElementType());
    assertTrue(ValueType.JSON.isArrayElementType());
    assertFalse(ValueType.JSON_SHAPE.isArrayElementType());
    assertTrue(ValueType.JSON_SHAPE_FAILURE.isArrayElementType());
  }

  @Test
  void parsesOnlyCanonicalConcreteTextFeatureTypeNames() {
    assertEquals(Optional.of(ValueType.INTEGER), ValueType.fromSourceName("整数"));
    assertEquals(Optional.of(ValueType.DECIMAL), ValueType.fromSourceName("小数"));
    assertEquals(Optional.of(ValueType.ROUNDING_MODE), ValueType.fromSourceName("丸め方法"));
    assertEquals(Optional.of(ValueType.REGEX), ValueType.fromSourceName("正規表現"));
    assertEquals(Optional.of(ValueType.DATE_TIME), ValueType.fromSourceName("日時"));
    assertEquals(Optional.of(ValueType.JSON), ValueType.fromSourceName("JSON"));
    assertEquals(Optional.of(ValueType.JSON_PARSE_FAILURE), ValueType.fromSourceName("JSON解析失敗"));
    assertEquals(Optional.of(ValueType.JSON_SHAPE), ValueType.fromSourceName("JSON形状"));
    assertEquals(Optional.of(ValueType.JSON_SHAPE_FAILURE), ValueType.fromSourceName("JSON形状失敗"));
    assertEquals(
        Optional.of(ValueType.arrayOf(ValueType.JSON_SHAPE_FAILURE)),
        ValueType.fromSourceName("配列<JSON形状失敗>"));
    assertEquals(Optional.of(ValueType.BYTE_SEQUENCE), ValueType.fromSourceName("バイト列"));
    assertEquals(Optional.of(ValueType.UTF8_DECODE_FAILURE), ValueType.fromSourceName("UTF8復号失敗"));
    assertEquals(
        Optional.of(ValueType.BASE64_DECODE_FAILURE), ValueType.fromSourceName("Base64復号失敗"));
    assertEquals(
        Optional.of(ValueType.arrayOf(ValueType.STRING)), ValueType.fromSourceName("配列<文字列>"));
    assertEquals(
        Optional.of(ValueType.arrayOf(ValueType.DECIMAL)), ValueType.fromSourceName("配列<小数>"));
    assertEquals(
        Optional.of(ValueType.arrayOf(ValueType.JSON)), ValueType.fromSourceName("配列<JSON>"));
    assertEquals(
        Optional.of(ValueType.arrayOf(ValueType.arrayOf(ValueType.STRING))),
        ValueType.fromSourceName("配列<配列<文字列>>"));
    assertEquals(
        Optional.of(ValueType.optionalOf(ValueType.INTEGER)), ValueType.fromSourceName("任意<整数>"));
    assertEquals(
        Optional.of(ValueType.optionalOf(ValueType.arrayOf(ValueType.JSON))),
        ValueType.fromSourceName("任意<配列<JSON>>"));
    assertEquals(
        Optional.of(ValueType.optionalOf(ValueType.optionalOf(ValueType.STRING))),
        ValueType.fromSourceName("任意<任意<文字列>>"));

    assertTrue(ValueType.fromSourceName(null).isEmpty());
    assertTrue(ValueType.fromSourceName("配列").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<整数").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<配列<配列<整数>>>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<丸め方法>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<正規表現>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<日時>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<JSON解析失敗>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<バイト列>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<UTF8復号失敗>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<Base64復号失敗>").isEmpty());
    assertTrue(ValueType.fromSourceName("配列 < 整数 >").isEmpty());
    assertTrue(ValueType.fromSourceName("任意").isEmpty());
    assertTrue(ValueType.fromSourceName("任意<>").isEmpty());
    assertTrue(ValueType.fromSourceName("任意<整数").isEmpty());
    assertTrue(ValueType.fromSourceName("配列<任意<整数>>").isEmpty());
    assertTrue(ValueType.fromSourceName("T").isEmpty());
    assertTrue(ValueType.fromSourceName("表示可能").isEmpty());
    assertTrue(ValueType.fromSourceName("数値").isEmpty());
  }

  @Test
  void comparesAndFormatsNestedOptionalTypesWithoutRecursiveObjectMethods() {
    ValueType first = ValueType.INTEGER;
    ValueType second = ValueType.INTEGER;
    for (int depth = 0; depth < ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH; depth++) {
      first = ValueType.optionalOf(first);
      second = ValueType.optionalOf(second);
    }

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertTrue(first.sourceName().startsWith("任意<任意<"));
    assertEquals(ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH, count(first.sourceName(), "任意<"));
    assertTrue(first.isOptional());
    assertTrue(first.optionalElementType().isPresent());
    ValueType maximum = first;
    assertThrows(IllegalArgumentException.class, () -> ValueType.optionalOf(maximum));
  }

  private static int count(String text, String needle) {
    int result = 0;
    for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + 1)) {
      result++;
    }
    return result;
  }

  @Test
  void rejectsNullForbiddenWrapperAndThreeDimensionalArrayElementTypes() {
    assertThrows(NullPointerException.class, () -> new ArrayType(null));
    assertThrows(NullPointerException.class, () -> ValueType.arrayOf(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> ValueType.arrayOf(ValueType.arrayOf(ValueType.arrayOf(ValueType.INTEGER))));
    assertThrows(
        IllegalArgumentException.class,
        () -> ValueType.arrayOf(ValueType.optionalOf(ValueType.INTEGER)));
    assertThrows(
        IllegalArgumentException.class,
        () -> ValueType.arrayOf(ValueType.resultOf(ValueType.INTEGER, ValueType.STRING)));
    assertThrows(IllegalArgumentException.class, () -> new ArrayType(ValueType.ROUNDING_MODE));
    assertThrows(IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.ROUNDING_MODE));
    assertThrows(IllegalArgumentException.class, () -> new ArrayType(ValueType.REGEX));
    assertThrows(IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.REGEX));
    assertThrows(IllegalArgumentException.class, () -> new ArrayType(ValueType.DATE_TIME));
    assertThrows(IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.DATE_TIME));
    assertThrows(IllegalArgumentException.class, () -> new ArrayType(ValueType.JSON_PARSE_FAILURE));
    assertThrows(
        IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.JSON_PARSE_FAILURE));
    assertThrows(IllegalArgumentException.class, () -> new ArrayType(ValueType.BYTE_SEQUENCE));
    assertThrows(IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.BYTE_SEQUENCE));
    assertThrows(
        IllegalArgumentException.class, () -> new ArrayType(ValueType.UTF8_DECODE_FAILURE));
    assertThrows(
        IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.UTF8_DECODE_FAILURE));
    assertThrows(
        IllegalArgumentException.class, () -> new ArrayType(ValueType.BASE64_DECODE_FAILURE));
    assertThrows(
        IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.BASE64_DECODE_FAILURE));
  }

  @Test
  void countsArrayConstructorsInTheSharedDepthMetric() {
    assertEquals(0, ValueType.constructorDepth(ValueType.INTEGER));
    assertEquals(1, ValueType.constructorDepth(ValueType.arrayOf(ValueType.INTEGER)));
    assertEquals(
        2, ValueType.constructorDepth(ValueType.optionalOf(ValueType.arrayOf(ValueType.INTEGER))));
    assertEquals(
        3,
        ValueType.constructorDepth(
            ValueType.resultOf(
                ValueType.arrayOf(ValueType.arrayOf(ValueType.INTEGER)), ValueType.STRING)));
  }
}
