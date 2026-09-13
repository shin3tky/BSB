package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResultResultTypeTest {
  @Test
  void roundTripsBothBranchesAndDistinguishesTheirOrder() {
    ValueType type =
        ValueType.resultOf(
            ValueType.optionalOf(ValueType.arrayOf(ValueType.JSON)),
            ValueType.resultOf(ValueType.STRING, ValueType.BOOLEAN));

    assertEquals("結果<任意<配列<JSON>>,結果<文字列,真偽>>", type.sourceName());
    assertEquals(type, ValueType.fromSourceName(type.sourceName()).orElseThrow());
    assertNotEquals(
        type,
        ValueType.resultOf(
            ValueType.resultOf(ValueType.STRING, ValueType.BOOLEAN),
            ValueType.optionalOf(ValueType.arrayOf(ValueType.JSON))));
    assertTrue(ValueType.fromSourceName("結果<整数>").isEmpty());
    assertTrue(ValueType.fromSourceName("結果<整数,文字列,真偽>").isEmpty());
  }

  @Test
  void handlesMaximumMixedDepthAndWideSharedTreesWithoutRecursiveObjectMethods() {
    ValueType first = ValueType.INTEGER;
    ValueType second = ValueType.INTEGER;
    for (int depth = 0; depth < ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH; depth++) {
      first =
          depth % 2 == 0
              ? ValueType.resultOf(first, ValueType.STRING)
              : ValueType.optionalOf(first);
      second =
          depth % 2 == 0
              ? ValueType.resultOf(second, ValueType.STRING)
              : ValueType.optionalOf(second);
    }

    assertEquals(ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH, ValueType.constructorDepth(first));
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    ValueType maximum = first;
    assertThrows(
        IllegalArgumentException.class, () -> ValueType.resultOf(maximum, ValueType.BOOLEAN));

    ValueType wide = ValueType.INTEGER;
    for (int depth = 0; depth < 15; depth++) {
      wide = ValueType.resultOf(wide, wide);
    }
    assertEquals(15, ValueType.constructorDepth(wide));
    assertEquals(wide, ValueType.fromSourceName(wide.sourceName()).orElseThrow());
  }
}
