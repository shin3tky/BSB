package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class JsonParseFailureValueTest {
  @Test
  void holdsOnlyTheFourPublicFieldsAndAlwaysRedactsTraceContent() {
    var value = new JsonParseFailureValue("unexpectedToken", 7, 2, 3);

    assertEquals(ValueType.JSON_PARSE_FAILURE, value.type());
    assertEquals("unexpectedToken", value.kind());
    assertEquals(7, value.utf8Offset());
    assertEquals(2, value.line());
    assertEquals(3, value.column());
    assertEquals("<redacted>", value.displayText());
    assertEquals(
        "JSON解析失敗:<redacted>", TraceValueFormatter.format(value, TraceValuePolicy.result()));
    assertFalse(TraceValuePolicy.result().mayReveal(value));
    assertEquals(
        List.of("kind", "utf8Offset", "line", "column"),
        java.util.Arrays.stream(JsonParseFailureValue.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList());
  }

  @Test
  void acceptsExactlyTheClosedKindSetAndValidPositions() {
    assertEquals(13, JsonParseFailureValue.KINDS.size());
    for (String kind : JsonParseFailureValue.KINDS) {
      assertEquals(kind, new JsonParseFailureValue(kind, 0, 1, 1).kind());
    }

    assertThrows(IllegalArgumentException.class, () -> new JsonParseFailureValue("other", 0, 1, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new JsonParseFailureValue("emptyInput", -1, 1, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new JsonParseFailureValue("emptyInput", 0, 0, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new JsonParseFailureValue("emptyInput", 0, 1, 0));
  }
}
