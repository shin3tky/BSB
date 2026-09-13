package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ArrayValueTest {
  @Test
  void copiesItsInputAndReturnsUnmodifiableElements() {
    var source = new ArrayList<RuntimeValue>();
    source.add(integer(1));
    ArrayValue value = new ArrayValue(ScalarType.INTEGER, source);

    source.set(0, integer(2));

    assertEquals(List.of(integer(1)), value.elements());
    assertThrows(UnsupportedOperationException.class, () -> value.elements().add(integer(3)));
    assertEquals(ValueType.arrayOf(ValueType.INTEGER), value.type());
  }

  @Test
  void replacementAppendAndSliceNeverChangeTheOriginalValue() {
    ArrayValue original = integers(1, 2, 3);
    ArrayValue replaced = original.replaced(1, integer(9));
    ArrayValue appended = original.appended(integer(4));
    ArrayValue sliced = original.slice(1, 3);

    assertEquals("【1、2、3】", original.displayText());
    assertEquals("【1、9、3】", replaced.displayText());
    assertEquals("【1、2、3、4】", appended.displayText());
    assertEquals("【2、3】", sliced.displayText());
    assertNotSame(original, replaced);
    assertNotSame(original, appended);
    assertNotSame(original, sliced);
  }

  @Test
  void rendersCharacterAndStringElementsAsCanonicalArrayLiterals() {
    ArrayValue characters =
        new ArrayValue(
            ScalarType.CHARACTER, List.of(new CharacterValue("'"), new CharacterValue("\n")));
    ArrayValue strings =
        new ArrayValue(ScalarType.STRING, List.of(new StringValue("赤"), new StringValue("「青」")));

    assertEquals("【'\\''、'\\n'】", characters.displayText());
    assertEquals("【「赤」、「\\「青\\」」】", strings.displayText());
  }

  @Test
  void rejectsWrongElementTypesAndLengthsBeyondTheNormativeMaximum() {
    assertThrows(
        IllegalArgumentException.class, () -> new ArrayValue(ScalarType.ROUNDING_MODE, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArrayValue(ScalarType.INTEGER, List.of(new StringValue("違う"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ArrayValue(
                ScalarType.INTEGER, Collections.nCopies(ArrayLimits.MAX_LENGTH + 1, integer(0))));
  }

  @Test
  void representsRaggedTwoDimensionalArraysAndCachesTheirLogicalLeafCount() {
    ArrayValue empty = integers();
    ArrayValue first = integers(1, 2);
    ArrayValue second = integers(3);
    ArrayValue matrix =
        new ArrayValue(ValueType.arrayOf(ValueType.INTEGER), List.of(first, empty, second));

    assertEquals(ValueType.arrayOf(ValueType.arrayOf(ValueType.INTEGER)), matrix.type());
    assertEquals(3, matrix.size());
    assertEquals(3, matrix.logicalLeafCount());
    assertEquals(2, first.logicalLeafCount());
    assertEquals(0, empty.logicalLeafCount());
    assertEquals("【【1、2】、【】、【3】】", matrix.displayText());
  }

  @Test
  void comparesNestedValuesStructurallyWithoutChangingOneDimensionalEquality() {
    ArrayValue first =
        new ArrayValue(ValueType.arrayOf(ValueType.INTEGER), List.of(integers(1), integers(2)));
    ArrayValue same =
        new ArrayValue(ValueType.arrayOf(ValueType.INTEGER), List.of(integers(1), integers(2)));
    ArrayValue different =
        new ArrayValue(ValueType.arrayOf(ValueType.INTEGER), List.of(integers(1), integers(3)));

    assertEquals(first, same);
    assertEquals(first.hashCode(), same.hashCode());
    assertNotEquals(first, different);
    assertEquals(integers(1, 2), integers(1, 2));
  }

  @Test
  void rejectsWrongRowTypesThreeDimensionsBrokenMetricsAndLeafOverflow() {
    ValueType integerRows = ValueType.arrayOf(ValueType.INTEGER);
    ArrayValue integerRow = integers(1);
    ArrayValue stringRow = new ArrayValue(ScalarType.STRING, List.of(new StringValue("異なる")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new ArrayValue(integerRows, List.of(integerRow, stringRow)));
    assertThrows(
        IllegalArgumentException.class, () -> new ArrayValue(integerRows, List.of(integerRow), 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArrayValue(ValueType.arrayOf(integerRows), List.of()));

    ArrayValue maximumRow =
        new ArrayValue(ScalarType.INTEGER, Collections.nCopies(ArrayLimits.MAX_LENGTH, integer(0)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArrayValue(integerRows, Collections.nCopies(16, maximumRow)));
  }

  private static ArrayValue integers(int... values) {
    return new ArrayValue(
        ScalarType.INTEGER,
        java.util.Arrays.stream(values).mapToObj(value -> (RuntimeValue) integer(value)).toList());
  }

  private static IntegerValue integer(int value) {
    return new IntegerValue(BigInteger.valueOf(value));
  }
}
