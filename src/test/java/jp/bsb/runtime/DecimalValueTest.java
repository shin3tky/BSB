package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class DecimalValueTest {
  @Test
  void normalizesZeroCoefficientAndScaleBeforeExposingTheValue() {
    DecimalValue negativeZero = decimal("-0.0");
    DecimalValue trailingZeroes = decimal("1.2300");
    DecimalValue whole = decimal("1200");

    assertEquals(BigInteger.ZERO, negativeZero.coefficient());
    assertEquals(0, negativeZero.scale());
    assertEquals(1, negativeZero.precision());
    assertEquals("0.0", negativeZero.displayText());
    assertEquals(BigInteger.valueOf(123), trailingZeroes.coefficient());
    assertEquals(2, trailingZeroes.scale());
    assertEquals("1.23", trailingZeroes.displayText());
    assertEquals(BigInteger.valueOf(12), whole.coefficient());
    assertEquals(-2, whole.scale());
    assertEquals("1200.0", whole.displayText());
    assertEquals(ValueType.DECIMAL, whole.type());
  }

  @Test
  void usesNumericEqualityAndMatchingHashes() {
    DecimalValue first = decimal("1.0");
    DecimalValue second = decimal("1.00");
    DecimalValue different = decimal("1.01");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, different);
    assertNotEquals(first, new IntegerValue(BigInteger.ONE));
  }

  @Test
  void alwaysUsesLocaleIndependentFixedPointDisplay() {
    assertEquals("0.001", decimal("1E-3").displayText());
    assertEquals("100000000000000000000.0", decimal("1E+20").displayText());
    assertEquals("-0.005", decimal("-5E-3").displayText());
    assertTrue(decimal("1E+20").displayText().chars().noneMatch(character -> character == 'E'));
    assertEquals(decimal("1.25").displayText(), decimal("1.25").toString());
  }

  @Test
  void rejectsNullAndValuesOutsideEitherNormativeLimit() {
    assertThrows(NullPointerException.class, () -> new DecimalValue((BigDecimal) null));
    assertThrows(NullPointerException.class, () -> new DecimalValue(null, 0));

    BigInteger excessivePrecision =
        BigInteger.TEN.pow(RuntimeLimits.DECIMAL_PRECISION).add(BigInteger.ONE);
    IllegalArgumentException precisionFailure =
        assertThrows(IllegalArgumentException.class, () -> new DecimalValue(excessivePrecision, 0));
    assertEquals("decimal precision exceeds the normative limit", precisionFailure.getMessage());

    IllegalArgumentException scaleFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new DecimalValue(BigInteger.ONE, RuntimeLimits.DECIMAL_ABSOLUTE_SCALE + 1));
    assertEquals("decimal scale exceeds the normative limit", scaleFailure.getMessage());

    IllegalArgumentException normalizationFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new DecimalValue(new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE)));
    assertEquals("decimal scale cannot be normalized", normalizationFailure.getMessage());
  }

  private static DecimalValue decimal(String value) {
    return new DecimalValue(new BigDecimal(value));
  }
}
