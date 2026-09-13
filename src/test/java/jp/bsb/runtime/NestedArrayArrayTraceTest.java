package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigInteger;
import java.util.List;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

/** 多次元配列の各階層8要素省略とJSON葉の推移的非開示を検証します。 */
class NestedArrayArrayTraceTest {
  @Test
  void formatsRaggedArraysAndTruncatesEachDimensionIndependently() {
    ArrayValue ragged = matrix(List.of(row(1, 2), row(3), row()));
    assertEquals(
        "配列<配列<整数>>:【【1、2】、【3】、【】】",
        TraceValueFormatter.format(ragged, TraceValuePolicy.byteSequence()));

    ArrayValue outerNine =
        matrix(java.util.stream.IntStream.rangeClosed(1, 9).mapToObj(value -> row(value)).toList());
    assertEquals(
        "配列<配列<整数>>:【【1】、【2】、【3】、【4】、【5】、【6】、【7】、【8】、…(+1)】",
        TraceValueFormatter.format(outerNine, TraceValuePolicy.byteSequence()));

    ArrayValue innerNine = matrix(List.of(row(1, 2, 3, 4, 5, 6, 7, 8, 9)));
    assertEquals(
        "配列<配列<整数>>:【【1、2、3、4、5、6、7、8、…(+1)】】",
        TraceValueFormatter.format(innerNine, TraceValuePolicy.byteSequence()));
  }

  @Test
  void redactsTheWholeNestedArrayWhenItsLeafTypeIsJson() {
    ArrayValue jsonRow =
        new ArrayValue(
            ScalarType.JSON, List.of(new JsonRuntimeValue(new JsonString("SECRET_JSON"))));
    ArrayValue matrix = new ArrayValue(new ArrayType(ScalarType.JSON), List.of(jsonRow));

    String formatted = TraceValueFormatter.format(matrix, TraceValuePolicy.byteSequence());

    assertEquals("配列<配列<JSON>>:<redacted>", formatted);
    assertFalse(formatted.contains("SECRET_JSON"));
  }

  private static ArrayValue matrix(List<ArrayValue> rows) {
    return new ArrayValue(
        new ArrayType(ScalarType.INTEGER), rows.stream().map(RuntimeValue.class::cast).toList());
  }

  private static ArrayValue row(int... values) {
    return new ArrayValue(
        ScalarType.INTEGER,
        java.util.Arrays.stream(values)
            .mapToObj(value -> (RuntimeValue) new IntegerValue(BigInteger.valueOf(value)))
            .toList());
  }
}
