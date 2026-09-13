package jp.bsb.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonValueTest {
  @Test
  void keepsSevenKindsAndDistinguishesIntegerFromDecimal() {
    List<JsonValue> values =
        List.of(
            JsonNull.INSTANCE,
            new JsonBoolean(true),
            new JsonInteger(BigInteger.ONE),
            new JsonDecimal(new BigDecimal("1.0")),
            new JsonString("値"),
            new JsonArray(List.of()),
            new JsonObject(List.of()));

    assertEquals(List.of(JsonKind.values()), values.stream().map(JsonValue::kind).toList());
    assertNotEquals(values.get(2), values.get(3));
  }

  @Test
  void normalizesNumbersWhileKeepingTheirKinds() {
    var negativeIntegerZero = new JsonInteger(new BigInteger("-0"));
    var negativeDecimalZero = new JsonDecimal(new BigDecimal("-0.0"));
    var trailingZeroes = new JsonDecimal(new BigDecimal("1.2300"));
    var exponent = new JsonDecimal(new BigDecimal("1e2"));

    assertEquals(BigInteger.ZERO, negativeIntegerZero.value());
    assertEquals(BigDecimal.ZERO, negativeDecimalZero.value());
    assertEquals(new BigDecimal("1.23"), trailingZeroes.value());
    assertEquals("100.0", exponent.canonicalText());
  }

  @Test
  void defensivelyCopiesArraysAndObjectsAndRejectsDuplicateKeys() {
    var mutableElements = new ArrayList<JsonValue>();
    mutableElements.add(JsonNull.INSTANCE);
    var array = new JsonArray(mutableElements);
    mutableElements.clear();
    assertEquals(1, array.size());
    assertThrows(UnsupportedOperationException.class, () -> array.elements().clear());

    var mutableMembers = new ArrayList<>(List.of(new JsonMember("a", JsonNull.INSTANCE)));
    var object = new JsonObject(mutableMembers);
    mutableMembers.clear();
    assertEquals(List.of("a"), object.keys());
    assertThrows(UnsupportedOperationException.class, () -> object.members().clear());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JsonObject(
                List.of(
                    new JsonMember("x", JsonNull.INSTANCE),
                    new JsonMember("x", new JsonBoolean(false)))));
  }

  @Test
  void objectEqualityIgnoresOrderButArrayEqualityKeepsIt() {
    JsonValue one = new JsonInteger(BigInteger.ONE);
    JsonValue two = new JsonInteger(BigInteger.TWO);
    var first = new JsonObject(List.of(new JsonMember("a", one), new JsonMember("b", two)));
    var reordered = new JsonObject(List.of(new JsonMember("b", two), new JsonMember("a", one)));

    assertEquals(first, reordered);
    assertEquals(first.hashCode(), reordered.hashCode());
    assertNotEquals(new JsonArray(List.of(one, two)), new JsonArray(List.of(two, one)));
  }

  @Test
  void measuresNestedStructureAndEscapedOutputWithoutSerializingIt() {
    var value =
        new JsonObject(
            List.of(
                new JsonMember(
                    "x",
                    new JsonArray(
                        List.of(new JsonString("\n"), new JsonDecimal(new BigDecimal("1e2")))))));

    assertEquals(4, value.metrics().nodeCount());
    assertEquals(3, value.metrics().containerReferences());
    assertEquals(2, value.metrics().depth());
    assertEquals(18, value.metrics().serializedUtf8Bytes());
    assertEquals(7, value.metrics().constructionUnits());
  }

  @Test
  void validatesUnicodeAndStructureAtConstructionBoundaries() {
    assertThrows(IllegalArgumentException.class, () -> new JsonString("\uD800"));
    assertThrows(NullPointerException.class, () -> new JsonArray(null));
    assertThrows(NullPointerException.class, () -> new JsonObject(null));

    JsonValue nested = JsonNull.INSTANCE;
    for (int depth = 0; depth < JsonLimits.DEPTH; depth++) {
      nested = new JsonArray(List.of(nested));
    }
    assertEquals(JsonLimits.DEPTH, nested.metrics().depth());
    JsonValue maximum = nested;
    assertThrows(IllegalArgumentException.class, () -> new JsonArray(List.of(maximum)));

    var object = new JsonObject(List.of(new JsonMember("null", JsonNull.INSTANCE)));
    assertTrue(object.containsKey("null"));
    assertFalse(object.containsKey("missing"));
    assertEquals(JsonNull.INSTANCE, object.find("null").orElseThrow());
  }
}
