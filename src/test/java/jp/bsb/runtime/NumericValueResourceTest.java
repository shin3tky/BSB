package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

/** 値モデルだけで再現できるNUM-R001、NUM-R002、NUM-R005の規範境界を検証します。 */
class NumericValueResourceTest {
  @Test
  void numericR001MeasuresThe65536DigitPrecisionBoundaryWithoutRenderingIt() {
    BigInteger acceptedCoefficient =
        BigInteger.TEN.pow(RuntimeLimits.DECIMAL_PRECISION - 1).add(BigInteger.ONE);
    BigInteger rejectedCoefficient =
        BigInteger.TEN.pow(RuntimeLimits.DECIMAL_PRECISION).add(BigInteger.ONE);

    DecimalValue accepted = new DecimalValue(acceptedCoefficient, 0);
    DecimalMetrics rejected = DecimalMetrics.measure(new BigDecimal(rejectedCoefficient));

    assertEquals(RuntimeLimits.DECIMAL_PRECISION, accepted.precision());
    assertTrue(accepted.metrics().withinLimits());
    assertEquals(RuntimeLimits.DECIMAL_PRECISION + 1, rejected.precision());
    assertFalse(rejected.withinLimits());
    assertThrows(IllegalArgumentException.class, () -> new DecimalValue(rejectedCoefficient, 0));
  }

  @Test
  void numericR002ChecksTheNormalizedAbsoluteScaleBoundary() {
    DecimalValue positiveBoundary =
        new DecimalValue(BigInteger.ONE, RuntimeLimits.DECIMAL_ABSOLUTE_SCALE);
    DecimalValue negativeBoundary =
        new DecimalValue(BigInteger.ONE, -RuntimeLimits.DECIMAL_ABSOLUTE_SCALE);
    DecimalValue normalizedIntoBoundary =
        new DecimalValue(BigInteger.TEN, RuntimeLimits.DECIMAL_ABSOLUTE_SCALE + 1);

    assertEquals(RuntimeLimits.DECIMAL_ABSOLUTE_SCALE, positiveBoundary.metrics().absoluteScale());
    assertEquals(RuntimeLimits.DECIMAL_ABSOLUTE_SCALE, negativeBoundary.metrics().absoluteScale());
    assertEquals(RuntimeLimits.DECIMAL_ABSOLUTE_SCALE, normalizedIntoBoundary.scale());
    assertThrows(
        IllegalArgumentException.class,
        () -> new DecimalValue(BigInteger.ONE, RuntimeLimits.DECIMAL_ABSOLUTE_SCALE + 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DecimalValue(BigInteger.ONE, -RuntimeLimits.DECIMAL_ABSOLUTE_SCALE - 1));
  }

  @Test
  void numericR005BoundsAndRedactsDecimalTraceValues() {
    DecimalValue thirtyTwoCodePoints = new DecimalValue(BigInteger.ONE, -29);
    DecimalValue thirtyThreeCodePoints = new DecimalValue(BigInteger.ONE, -30);
    String fullThirtyTwo = thirtyTwoCodePoints.displayText();
    String fullThirtyThree = thirtyThreeCodePoints.displayText();

    assertEquals(32, fullThirtyTwo.codePointCount(0, fullThirtyTwo.length()));
    assertEquals(33, fullThirtyThree.codePointCount(0, fullThirtyThree.length()));
    assertEquals(
        "小数:" + fullThirtyTwo,
        TraceValueFormatter.format(thirtyTwoCodePoints, TraceValuePolicy.numerics()));
    assertEquals(
        "小数:" + fullThirtyThree.substring(0, 32) + "…",
        TraceValueFormatter.format(thirtyThreeCodePoints, TraceValuePolicy.numerics()));
    assertEquals(
        "小数:<redacted>",
        TraceValueFormatter.format(thirtyTwoCodePoints, TraceValuePolicy.arrays()));
    assertEquals(
        "丸め方法:最近接偶数丸め",
        TraceValueFormatter.format(RoundingModeValue.NEAREST_EVEN, TraceValuePolicy.numerics()));
  }

  @Test
  void rendersCanonicalDecimalArrayElementsAndKeepsTheTraceSchema() {
    ArrayValue decimals =
        new ArrayValue(
            ScalarType.DECIMAL,
            List.of(
                new DecimalValue(new BigDecimal("1.50")), new DecimalValue(new BigDecimal("2"))));

    assertEquals("【1.5、2.0】", decimals.displayText());
    assertEquals(
        "配列<小数>:【1.5、2.0】", TraceValueFormatter.format(decimals, TraceValuePolicy.numerics()));
    assertEquals(
        "配列<小数>:<redacted>", TraceValueFormatter.format(decimals, TraceValuePolicy.arrays()));
    assertEquals(
        TraceTsvFormatter.formatArray(List.of()), TraceTsvFormatter.formatNumeric(List.of()));
  }

  @Test
  void numericFeatureRedactionLeaksNoDecimalShapeOrArrayLength() {
    DecimalValue decimal = new DecimalValue(BigInteger.ONE, -30);
    ArrayValue decimals =
        new ArrayValue(
            ScalarType.DECIMAL,
            List.of(
                new DecimalValue(new BigDecimal("1.5")),
                new DecimalValue(new BigDecimal("2.0")),
                new DecimalValue(new BigDecimal("3.25"))));
    TraceValuePolicy redactAll = ignored -> false;

    assertEquals("小数:<redacted>", TraceValueFormatter.format(decimal, redactAll));
    assertEquals("配列<小数>:<redacted>", TraceValueFormatter.format(decimals, redactAll));
  }
}
