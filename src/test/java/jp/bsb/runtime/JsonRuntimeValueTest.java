package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import jp.bsb.json.JsonArray;
import jp.bsb.json.JsonInteger;
import jp.bsb.json.JsonMember;
import jp.bsb.json.JsonNull;
import jp.bsb.json.JsonObject;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class JsonRuntimeValueTest {
  @Test
  void wrapsAllJsonKindsAsOneBsbTypeAndUsesJsonDisplay() {
    var value =
        new JsonRuntimeValue(
            new JsonObject(
                List.of(
                    new JsonMember(
                        "values",
                        new JsonArray(
                            List.of(
                                JsonNull.INSTANCE, new JsonInteger(BigInteger.valueOf(42))))))));

    assertEquals(ValueType.JSON, value.type());
    assertEquals("{\"values\":[null,42]}", value.displayText());
    assertEquals(value.displayText(), value.toString());
  }

  @Test
  void wrapperAndJsonArraysUseStructuralEqualityAndCanonicalDisplay() {
    var first = new JsonRuntimeValue(new JsonInteger(BigInteger.ONE));
    var equivalent = new JsonRuntimeValue(new JsonInteger(BigInteger.ONE));
    var different = new JsonRuntimeValue(new JsonInteger(BigInteger.TWO));
    var array = new ArrayValue(ScalarType.JSON, List.of(first, different));

    assertEquals(first, equivalent);
    assertFalse(first.equals(different));
    assertEquals("【1、2】", array.displayText());
    assertEquals(ValueType.arrayOf(ValueType.JSON), array.type());
  }

  @Test
  void everyDefaultTracePolicyRedactsJsonAndJsonArraysWithoutWalkingValues() {
    var secret = new JsonRuntimeValue(new jp.bsb.json.JsonString("token-secret"));
    var array = new ArrayValue(ScalarType.JSON, List.of(secret));

    for (TraceValuePolicy policy :
        List.of(
            TraceValuePolicy.bindings(),
            TraceValuePolicy.arrays(),
            TraceValuePolicy.numerics(),
            TraceValuePolicy.textRegex(),
            TraceValuePolicy.hostIo())) {
      assertEquals("JSON:<redacted>", TraceValueFormatter.format(secret, policy));
      assertEquals("配列<JSON>:<redacted>", TraceValueFormatter.format(array, policy));
    }
    assertEquals("JSON:<redacted>", secret.traceText());
  }

  @Test
  void rejectsNullAndWrongArrayElements() {
    assertThrows(NullPointerException.class, () -> new JsonRuntimeValue(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArrayValue(ScalarType.JSON, List.of(new StringValue("not JSON"))));
  }
}
