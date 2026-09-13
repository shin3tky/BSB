package jp.bsb.cli;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 適合試験で期待値と実出力を独立に検証する、小さな厳密JSONパーサです。 */
final class StrictJsonParser {
  private final String input;
  private int index;

  private StrictJsonParser(String input) {
    this.input = input;
  }

  static Object parse(String input) {
    var parser = new StrictJsonParser(input);
    Object value = parser.value();
    parser.whitespace();
    if (parser.index != input.length()) {
      throw parser.error("trailing JSON content");
    }
    return value;
  }

  static Object parse(byte[] input) {
    try {
      String decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(input))
              .toString();
      return parse(decoded);
    } catch (java.nio.charset.CharacterCodingException exception) {
      throw new IllegalArgumentException("JSON is not valid UTF-8", exception);
    }
  }

  private Object value() {
    whitespace();
    if (index >= input.length()) {
      throw error("missing JSON value");
    }
    return switch (input.charAt(index)) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", null);
      default -> number();
    };
  }

  private Map<String, Object> object() {
    expect('{');
    var result = new LinkedHashMap<String, Object>();
    whitespace();
    if (take('}')) {
      return result;
    }
    while (true) {
      whitespace();
      if (index >= input.length() || input.charAt(index) != '"') {
        throw error("object member name must be a string");
      }
      String name = string();
      whitespace();
      expect(':');
      if (result.containsKey(name)) {
        throw error("duplicate object member: " + name);
      }
      result.put(name, value());
      whitespace();
      if (take('}')) {
        return result;
      }
      expect(',');
    }
  }

  private List<Object> array() {
    expect('[');
    var result = new ArrayList<>();
    whitespace();
    if (take(']')) {
      return result;
    }
    while (true) {
      result.add(value());
      whitespace();
      if (take(']')) {
        return result;
      }
      expect(',');
    }
  }

  private String string() {
    expect('"');
    var result = new StringBuilder();
    while (index < input.length()) {
      char character = input.charAt(index++);
      if (character == '"') {
        validateUnicodeScalars(result);
        return result.toString();
      }
      if (character < 0x20) {
        throw error("unescaped control in JSON string");
      }
      if (character != '\\') {
        result.append(character);
        continue;
      }
      if (index >= input.length()) {
        throw error("incomplete JSON escape");
      }
      char escaped = input.charAt(index++);
      switch (escaped) {
        case '"', '\\', '/' -> result.append(escaped);
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        case 'u' -> result.append(unicodeEscape());
        default -> throw error("unknown JSON escape");
      }
    }
    throw error("unterminated JSON string");
  }

  private char unicodeEscape() {
    if (index + 4 > input.length()) {
      throw error("incomplete Unicode escape");
    }
    int value = 0;
    for (int count = 0; count < 4; count++) {
      int digit = Character.digit(input.charAt(index++), 16);
      if (digit < 0) {
        throw error("invalid Unicode escape");
      }
      value = (value << 4) | digit;
    }
    return (char) value;
  }

  private Object number() {
    int start = index;
    take('-');
    if (take('0')) {
      if (index < input.length() && Character.isDigit(input.charAt(index))) {
        throw error("leading zero in JSON number");
      }
    } else {
      digits();
    }
    if (take('.')) {
      digits();
    }
    if (take('e') || take('E')) {
      if (!take('+')) {
        take('-');
      }
      digits();
    }
    if (start == index) {
      throw error("invalid JSON value");
    }
    try {
      return new BigDecimal(input.substring(start, index));
    } catch (NumberFormatException exception) {
      throw error("invalid JSON number");
    }
  }

  private void digits() {
    int start = index;
    while (index < input.length() && Character.isDigit(input.charAt(index))) {
      index++;
    }
    if (start == index) {
      throw error("JSON number requires digits");
    }
  }

  private Object literal(String text, Object value) {
    if (!input.startsWith(text, index)) {
      throw error("invalid JSON literal");
    }
    index += text.length();
    return value;
  }

  private void whitespace() {
    while (index < input.length()) {
      char character = input.charAt(index);
      if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
        return;
      }
      index++;
    }
  }

  private void expect(char expected) {
    if (!take(expected)) {
      throw error("expected '" + expected + "'");
    }
  }

  private boolean take(char expected) {
    if (index < input.length() && input.charAt(index) == expected) {
      index++;
      return true;
    }
    return false;
  }

  private static void validateUnicodeScalars(CharSequence value) {
    for (int position = 0; position < value.length(); ) {
      char character = value.charAt(position);
      if (Character.isHighSurrogate(character)) {
        if (position + 1 >= value.length()
            || !Character.isLowSurrogate(value.charAt(position + 1))) {
          throw new IllegalArgumentException("unpaired surrogate in JSON string");
        }
        position += 2;
      } else if (Character.isLowSurrogate(character)) {
        throw new IllegalArgumentException("unpaired surrogate in JSON string");
      } else {
        position++;
      }
    }
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at JSON index " + index);
  }
}
