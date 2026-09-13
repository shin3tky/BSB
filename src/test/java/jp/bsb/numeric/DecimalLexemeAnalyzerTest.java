package jp.bsb.numeric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonParseErrorKind;
import jp.bsb.json.JsonParseException;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Invalid;
import jp.bsb.numeric.DecimalLexemeAnalyzer.InvalidReason;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Kind;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Valid;
import org.junit.jupiter.api.Test;

class DecimalLexemeAnalyzerTest {
  private static final int SCALE_LIMIT = 65_536;

  @Test
  void classifiesIntegersFixedDecimalsAndExponentDecimals() {
    assertValid("-12", Kind.INTEGER, false, "12", "12", 2, 0, 0, 0, 2);
    assertValid("1.2300", Kind.DECIMAL, false, "12300", "123", 5, 4, 4, 2, 3);
    assertValid("1e2", Kind.DECIMAL, true, "1", "1", 2, 0, -2, -2, 1);
    assertValid("1.500e3", Kind.DECIMAL, true, "1500", "15", 5, 3, 0, -2, 2);
    assertValid("100e-2", Kind.DECIMAL, true, "100", "1", 4, 0, 2, 0, 1);
  }

  @Test
  void normalizesZeroWithoutHidingTheRawScale() {
    Valid exact = valid("-0e65536");
    assertTrue(exact.zero());
    assertTrue(exact.negative());
    assertEquals(-65_536, exact.rawScale());
    assertEquals(0, exact.normalizedScale());

    Valid over = valid("0e65537");
    assertEquals(-65_537, over.rawScale());
    assertEquals(0, over.normalizedScale());
  }

  @Test
  void measuresRawAndNormalizedScaleBoundariesWithoutBigDecimal() {
    assertEquals(65_536, valid("1e-65536").rawScale());
    assertEquals(65_537, valid("1e-65537").rawScale());
    assertEquals(-65_536, valid("1e65536").rawScale());
    assertEquals(-65_537, valid("1e65537").rawScale());
    assertEquals(-65_536, valid("10e65535").normalizedScale());
    assertEquals(-65_537, valid("10e65536").normalizedScale());
  }

  @Test
  void saturatesA4095DigitExponentAndCountsEveryDigit() {
    Valid huge = valid("1e" + "9".repeat(4095));
    assertEquals(4096, huge.inputDigits());
    assertEquals(65_537, huge.exponent());
    assertEquals(-65_537, huge.rawScale());

    Valid leadingZeros = valid("1e" + "0".repeat(4095));
    assertEquals(4096, leadingZeros.inputDigits());
    assertEquals(0, leadingZeros.exponent());
    assertEquals(0, leadingZeros.rawScale());

    Valid excessiveDigits = valid("1e" + "9".repeat(4096));
    assertEquals(4097, excessiveDigits.inputDigits());
    assertEquals(65_537, excessiveDigits.exponent());
  }

  @Test
  void matchesExistingJsonScaleBoundaryVectorsWithoutMigratingItsParser() {
    for (String lexeme : new String[] {"0e65536", "1e65536", "1e-65536", "10e65535"}) {
      Valid valid = valid(lexeme);
      assertTrue(Math.abs(valid.rawScale()) <= SCALE_LIMIT, lexeme);
      assertTrue(Math.abs(valid.normalizedScale()) <= SCALE_LIMIT, lexeme);
      assertDoesNotThrow(() -> JsonCodec.parse(lexeme), lexeme);
    }

    for (String lexeme : new String[] {"0e65537", "1e65537", "1e-65537", "10e65536"}) {
      Valid valid = valid(lexeme);
      assertTrue(
          Math.abs(valid.rawScale()) > SCALE_LIMIT
              || Math.abs(valid.normalizedScale()) > SCALE_LIMIT,
          lexeme);
      JsonParseException failure =
          assertThrows(JsonParseException.class, () -> JsonCodec.parse(lexeme), lexeme);
      assertEquals(JsonParseErrorKind.NUMBER_LIMIT, failure.kind(), lexeme);
      assertEquals("scale", failure.reason(), lexeme);
    }
  }

  @Test
  void assignsStableReasonsToEveryNormativeInvalidForm() {
    assertInvalid("1e", InvalidReason.MISSING_EXPONENT_DIGITS);
    assertInvalid("1e+", InvalidReason.MISSING_EXPONENT_DIGITS);
    assertInvalid("1e-", InvalidReason.MISSING_EXPONENT_DIGITS);
    assertInvalid(".5e2", InvalidReason.INVALID_MANTISSA);
    assertInvalid("1.e2", InvalidReason.INVALID_MANTISSA);
    assertInvalid("+1e2", InvalidReason.LEADING_PLUS);
    assertInvalid("01e2", InvalidReason.LEADING_ZERO);
    assertInvalid("1ee2", InvalidReason.REPEATED_EXPONENT);
    assertInvalid("1e2e3", InvalidReason.REPEATED_EXPONENT);
    assertInvalid("1e--2", InvalidReason.MISSING_EXPONENT_DIGITS);
  }

  @Test
  void rejectsInvalidLimitArguments() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> DecimalLexemeAnalyzer.analyze("1e2", -1));
    assertEquals(
        (long) Integer.MAX_VALUE + 1,
        validAtLimit("1e-99999999999999999999", Integer.MAX_VALUE).rawScale());
  }

  private static void assertValid(
      String lexeme,
      Kind kind,
      boolean exponentPresent,
      String coefficient,
      String normalizedCoefficient,
      int inputDigits,
      int fractionDigits,
      long rawScale,
      long normalizedScale,
      int precision) {
    Valid valid = valid(lexeme);
    assertEquals(kind, valid.kind());
    assertEquals(exponentPresent, valid.exponentPresent());
    assertEquals(coefficient, valid.coefficientDigits());
    assertEquals(normalizedCoefficient, valid.normalizedCoefficientDigits());
    assertEquals(inputDigits, valid.inputDigits());
    assertEquals(fractionDigits, valid.fractionDigits());
    assertEquals(rawScale, valid.rawScale());
    assertEquals(normalizedScale, valid.normalizedScale());
    assertEquals(precision, valid.precision());
  }

  private static Valid valid(String lexeme) {
    return validAtLimit(lexeme, SCALE_LIMIT);
  }

  private static Valid validAtLimit(String lexeme, int scaleLimit) {
    return assertInstanceOf(Valid.class, DecimalLexemeAnalyzer.analyze(lexeme, scaleLimit), lexeme);
  }

  private static void assertInvalid(String lexeme, InvalidReason reason) {
    Invalid invalid =
        assertInstanceOf(Invalid.class, DecimalLexemeAnalyzer.analyze(lexeme, SCALE_LIMIT), lexeme);
    assertEquals(reason, invalid.reason(), lexeme);
  }
}
