package jp.bsb.json;

import java.util.ArrayDeque;
import java.util.Objects;

final class JsonWriter {
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private JsonWriter() {}

  static String write(JsonValue value) throws JsonWriteException {
    Objects.requireNonNull(value, "value");
    long length = value.metrics().serializedUtf8Bytes();
    if (length > JsonLimits.OUTPUT_UTF8_BYTES) {
      throw new JsonWriteException(JsonLimits.OUTPUT_UTF8_BYTES, length);
    }
    var output = new StringBuilder((int) Math.min(Integer.MAX_VALUE, length));
    var actions = new ArrayDeque<Action>();
    actions.push(new ValueAction(value));
    while (!actions.isEmpty()) {
      Action action = actions.pop();
      if (action instanceof TextAction text) {
        output.append(text.text);
      } else if (action instanceof StringAction string) {
        appendString(output, string.value);
      } else {
        appendValue(output, ((ValueAction) action).value, actions);
      }
    }
    return output.toString();
  }

  private static void appendValue(
      StringBuilder output, JsonValue value, ArrayDeque<Action> actions) {
    switch (value.kind()) {
      case NULL -> output.append("null");
      case BOOLEAN -> output.append(((JsonBoolean) value).value() ? "true" : "false");
      case INTEGER -> output.append(((JsonInteger) value).value());
      case DECIMAL -> output.append(((JsonDecimal) value).canonicalText());
      case STRING -> appendString(output, ((JsonString) value).value());
      case ARRAY -> pushArray((JsonArray) value, actions);
      case OBJECT -> pushObject((JsonObject) value, actions);
    }
  }

  private static void pushArray(JsonArray array, ArrayDeque<Action> actions) {
    actions.push(new TextAction("]"));
    for (int index = array.size() - 1; index >= 0; index--) {
      actions.push(new ValueAction(array.get(index)));
      if (index > 0) {
        actions.push(new TextAction(","));
      }
    }
    actions.push(new TextAction("["));
  }

  private static void pushObject(JsonObject object, ArrayDeque<Action> actions) {
    actions.push(new TextAction("}"));
    for (int index = object.members().size() - 1; index >= 0; index--) {
      JsonMember member = object.members().get(index);
      actions.push(new ValueAction(member.value()));
      actions.push(new TextAction(":"));
      actions.push(new StringAction(member.key()));
      if (index > 0) {
        actions.push(new TextAction(","));
      }
    }
    actions.push(new TextAction("{"));
  }

  private static void appendString(StringBuilder output, String value) {
    output.append('"');
    for (int index = 0; index < value.length(); ) {
      int codePoint = value.codePointAt(index);
      index += Character.charCount(codePoint);
      switch (codePoint) {
        case '"' -> output.append("\\\"");
        case '\\' -> output.append("\\\\");
        case '\b' -> output.append("\\b");
        case '\f' -> output.append("\\f");
        case '\n' -> output.append("\\n");
        case '\r' -> output.append("\\r");
        case '\t' -> output.append("\\t");
        default -> {
          if (codePoint < 0x20) {
            output
                .append("\\u00")
                .append(HEX[(codePoint >>> 4) & 0xf])
                .append(HEX[codePoint & 0xf]);
          } else {
            output.appendCodePoint(codePoint);
          }
        }
      }
    }
    output.append('"');
  }

  private sealed interface Action permits TextAction, StringAction, ValueAction {}

  private record TextAction(String text) implements Action {}

  private record StringAction(String value) implements Action {}

  private record ValueAction(JsonValue value) implements Action {}
}
