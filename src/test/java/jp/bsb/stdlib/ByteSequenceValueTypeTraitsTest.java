package jp.bsb.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ByteSequenceValueTypeTraitsTest {
  @Test
  void byteSequenceIsComparableButNotDisplayable() {
    assertFalse(ValueTypeTraits.isDisplayable(ValueType.BYTE_SEQUENCE));
    assertTrue(ValueTypeTraits.isEqualityComparable(ValueType.BYTE_SEQUENCE));
    assertEquals(
        Optional.of(ValueType.BYTE_SEQUENCE),
        ValueTypeTraits.firstNonDisplayable(ValueType.BYTE_SEQUENCE));
    assertEquals(
        Optional.empty(), ValueTypeTraits.firstNonEqualityComparable(ValueType.BYTE_SEQUENCE));
  }

  @Test
  void bothFailureTypesAreNeitherDisplayableNorComparable() {
    for (ValueType type :
        java.util.List.of(ValueType.UTF8_DECODE_FAILURE, ValueType.BASE64_DECODE_FAILURE)) {
      assertFalse(ValueTypeTraits.isDisplayable(type));
      assertFalse(ValueTypeTraits.isEqualityComparable(type));
    }
  }

  @Test
  void wrappersInspectBothTypeArgumentsInSuccessThenFailureOrder() {
    ValueType both =
        ValueType.resultOf(ValueType.UTF8_DECODE_FAILURE, ValueType.BASE64_DECODE_FAILURE);
    assertEquals(
        Optional.of(ValueType.UTF8_DECODE_FAILURE), ValueTypeTraits.firstNonDisplayable(both));
    assertEquals(
        Optional.of(ValueType.UTF8_DECODE_FAILURE),
        ValueTypeTraits.firstNonEqualityComparable(both));

    ValueType nonSelectedByte =
        ValueType.resultOf(ValueType.INTEGER, ValueType.optionalOf(ValueType.BYTE_SEQUENCE));
    assertFalse(ValueTypeTraits.isDisplayable(nonSelectedByte));
    assertTrue(ValueTypeTraits.isEqualityComparable(nonSelectedByte));
  }

  @Test
  void byteSequenceRemainsAvailableAtTheMaximumTypeDepth() {
    ValueType nested = ValueType.BYTE_SEQUENCE;
    for (int depth = 0; depth < ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH; depth++) {
      nested = ValueType.optionalOf(nested);
    }
    assertEquals(ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH, ValueType.constructorDepth(nested));
  }
}
