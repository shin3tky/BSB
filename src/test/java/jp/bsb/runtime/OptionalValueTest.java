package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigInteger;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class OptionalValueTest {
  @Test
  void keepsDeclaredTypeStateAndValueImmutable() {
    OptionalValue present = OptionalValue.present(new IntegerValue(BigInteger.valueOf(42)));
    OptionalValue absent = OptionalValue.absent(ValueType.INTEGER);

    assertEquals(ValueType.optionalOf(ValueType.INTEGER), present.type());
    assertEquals("ある（42）", present.displayText());
    assertEquals("ない", absent.displayText());
    assertNotEquals(present, absent);
    assertEquals(OptionalValue.absent(ValueType.INTEGER), absent);
  }

  @Test
  void formatsAndComparesDepth256WithoutRecursiveObjectMethods() {
    RuntimeValue left = new IntegerValue(BigInteger.ONE);
    RuntimeValue right = new IntegerValue(BigInteger.ONE);
    for (int depth = 0; depth < 256; depth++) {
      left = OptionalValue.present(left);
      right = OptionalValue.present(right);
    }

    assertEquals(left, right);
    assertEquals(left.hashCode(), right.hashCode());
    assertEquals(256, count(left.displayText(), "ある（"));
  }

  private static int count(String text, String needle) {
    int result = 0;
    for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + 1)) {
      result++;
    }
    return result;
  }
}
