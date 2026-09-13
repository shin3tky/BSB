package jp.bsb.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class JsonCodecTest {
  @Test
  void parsesEveryTopLevelKindAndWritesCanonicalBytes() throws Exception {
    assertEquals("null", roundTrip(" null "));
    assertEquals("true", roundTrip("\ttrue\r\n"));
    assertEquals("0", roundTrip("-0"));
    assertEquals("0.0", roundTrip("-0.0"));
    assertEquals("1.23", roundTrip("1.2300"));
    assertEquals("100.0", roundTrip("1e2"));
    assertEquals("\"値\"", roundTrip("\"値\""));
    assertEquals("[]", roundTrip("[]"));
    assertEquals("{}", roundTrip("{}"));
  }

  @Test
  void decodesAllEscapesAndSurrogatePairs() throws Exception {
    String source = "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\\uD842\\uDFB7\"";
    JsonString value = assertInstanceOf(JsonString.class, JsonCodec.parse(source));

    assertEquals("\"\\/\b\f\n\r\tA𠮷", value.value());
    assertEquals("\"\\\"\\\\/\\b\\f\\n\\r\\tA𠮷\"", JsonCodec.serialize(value));
  }

  @Test
  void preservesObjectOrderForWritingAndIgnoresItForEquality() throws Exception {
    JsonValue first = JsonCodec.parse("{\"b\":2,\"a\":1}");
    JsonValue second = JsonCodec.parse("{\"a\":1,\"b\":2}");

    assertEquals("{\"b\":2,\"a\":1}", JsonCodec.serialize(first));
    assertEquals(first, second);
    assertNotEquals(JsonCodec.serialize(first), JsonCodec.serialize(second));
  }

  @Test
  void reportsStableSyntaxReasonsAndUtf8LineColumns() {
    JsonParseException trailing =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse("𠮷\r\ntrue false"));
    assertEquals(JsonParseErrorKind.SYNTAX, trailing.kind());
    assertEquals("unexpectedToken", trailing.reason());
    assertEquals(0, trailing.utf8Offset());
    assertEquals(1, trailing.line());
    assertEquals(1, trailing.column());

    JsonParseException located =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse("\r\n[\"𠮷\",]"));
    assertEquals("unexpectedToken", located.reason());
    assertEquals(10, located.utf8Offset());
    assertEquals(2, located.line());
    assertEquals(6, located.column());
  }

  @Test
  void rejectsNonJsonExtensionsAndMalformedNumbers() {
    assertFailure("", "emptyInput");
    assertFailure("\uFEFFnull", "unexpectedToken");
    assertFailure("/*x*/null", "unexpectedToken");
    assertFailure("[1,]", "unexpectedToken");
    assertFailure("{\"x\":1,}", "expectedObjectKey");
    assertFailure("\"\\x\"", "invalidEscape");
    assertFailure("\"\\u12x4\"", "invalidUnicodeEscape");
    assertFailure("\"\\uＦＦＦＦ\"", "invalidUnicodeEscape");
    assertFailure("\"\\uD800\"", "isolatedSurrogate");
    assertFailure("\"\\uDC00\"", "isolatedSurrogate");
    assertFailure("01", "invalidNumber");
    assertFailure("1.", "invalidNumber");
    assertFailure("1e", "invalidNumber");
    assertFailure("true false", "trailingContent");
  }

  @Test
  void rejectsDuplicateExpandedKeysWithoutNormalizingUnicode() throws Exception {
    JsonParseException duplicate =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse("{\"x\":1,\"\\u0078\":2}"));
    assertEquals(JsonParseErrorKind.DUPLICATE_KEY, duplicate.kind());

    JsonObject distinct =
        assertInstanceOf(JsonObject.class, JsonCodec.parse("{\"é\":1,\"e\\u0301\":2}"));
    assertEquals(List.of("é", "e\u0301"), distinct.keys());
  }

  @Test
  void rejectsNumberAndDepthBoundariesBeforeHostFailures() throws Exception {
    String acceptedDigits = "1".repeat(JsonLimits.INPUT_NUMBER_DIGITS);
    assertEquals(new JsonInteger(new BigInteger(acceptedDigits)), JsonCodec.parse(acceptedDigits));

    JsonParseException digits =
        assertThrows(
            JsonParseException.class,
            () -> JsonCodec.parse("1".repeat(JsonLimits.INPUT_NUMBER_DIGITS + 1)));
    assertEquals(JsonParseErrorKind.NUMBER_LIMIT, digits.kind());
    assertEquals("inputDigits", digits.reason());

    JsonParseException scale =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse("1e-65537"));
    assertEquals(JsonParseErrorKind.NUMBER_LIMIT, scale.kind());
    assertEquals("scale", scale.reason());

    String maximum = "[".repeat(JsonLimits.DEPTH) + "0" + "]".repeat(JsonLimits.DEPTH);
    JsonValue nested = JsonCodec.parse(maximum);
    assertEquals(JsonLimits.DEPTH, nested.metrics().depth());
    JsonParseException excessive =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse("[" + maximum + "]"));
    assertEquals(JsonParseErrorKind.DEPTH_LIMIT, excessive.kind());
  }

  @Test
  void deterministicWriterMatchesIndependentManualValue() throws Exception {
    JsonValue manual =
        new JsonObject(
            List.of(
                new JsonMember("整数", new JsonInteger(BigInteger.valueOf(-2))),
                new JsonMember("小数", new JsonDecimal(new BigDecimal("0.0100"))),
                new JsonMember(
                    "配列",
                    new JsonArray(
                        List.of(
                            JsonNull.INSTANCE,
                            new JsonBoolean(false),
                            new JsonString("/\u2028\u2029"))))));
    String expected = "{\"整数\":-2,\"小数\":0.01,\"配列\":[null,false,\"/\u2028\u2029\"]}";

    assertEquals(expected, JsonCodec.serialize(manual));
    assertEquals(manual, JsonCodec.parse(expected));
    assertEquals(JsonCodec.serialize(manual), JsonCodec.serialize(manual));
  }

  @Test
  void preflightsOutputLimitBeforeBuildingTheResult() {
    var value = new JsonString("a".repeat((int) JsonLimits.OUTPUT_UTF8_BYTES));

    JsonWriteException failure =
        assertThrows(JsonWriteException.class, () -> JsonCodec.serialize(value));
    assertEquals(JsonLimits.OUTPUT_UTF8_BYTES, failure.limit());
    assertEquals(JsonLimits.OUTPUT_UTF8_BYTES + 2, failure.observed());
  }

  @Test
  void decimalMetricsDoNotNeedTheFixedPointOutput() {
    var decimal =
        new JsonDecimal(BigDecimal.ONE.scaleByPowerOfTen(JsonLimits.DECIMAL_ABSOLUTE_SCALE));

    assertEquals(JsonLimits.DECIMAL_ABSOLUTE_SCALE + 3L, decimal.metrics().serializedUtf8Bytes());
    assertTrue(decimal.metrics().serializedUtf8Bytes() < JsonLimits.OUTPUT_UTF8_BYTES);
  }

  @Test
  void generatedValuesRoundTripWithStableBytes() throws Exception {
    var random = new Random(0x42534209L);
    for (int count = 0; count < 500; count++) {
      JsonValue value = generateValue(random, 0);
      String first = JsonCodec.serialize(value);
      JsonValue parsed = JsonCodec.parse(first);
      String second = JsonCodec.serialize(parsed);

      assertEquals(value, parsed);
      assertEquals(first, second);
    }
  }

  private static String roundTrip(String source) throws Exception {
    return JsonCodec.serialize(JsonCodec.parse(source));
  }

  private static void assertFailure(String source, String reason) {
    JsonParseException failure =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse(source));
    assertEquals(JsonParseErrorKind.SYNTAX, failure.kind());
    assertEquals(reason, failure.reason());
  }

  private static JsonValue generateValue(Random random, int depth) {
    int kind = depth >= 4 ? random.nextInt(5) : random.nextInt(7);
    return switch (kind) {
      case 0 -> JsonNull.INSTANCE;
      case 1 -> new JsonBoolean(random.nextBoolean());
      case 2 -> new JsonInteger(BigInteger.valueOf(random.nextLong(-1_000_000, 1_000_001)));
      case 3 ->
          new JsonDecimal(
              new BigDecimal(BigInteger.valueOf(random.nextLong(-100_000, 100_001)), 3));
      case 4 -> new JsonString(List.of("", "値", "𠮷", "e\u0301", "\n\"\\/").get(random.nextInt(5)));
      case 5 -> {
        var elements = new ArrayList<JsonValue>();
        int length = random.nextInt(5);
        for (int index = 0; index < length; index++) {
          elements.add(generateValue(random, depth + 1));
        }
        yield new JsonArray(elements);
      }
      default -> {
        var members = new ArrayList<JsonMember>();
        int length = random.nextInt(5);
        for (int index = 0; index < length; index++) {
          members.add(new JsonMember("key" + index, generateValue(random, depth + 1)));
        }
        yield new JsonObject(members);
      }
    };
  }
}
