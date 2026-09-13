package jp.bsb.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class JsonParser {
  private final String source;
  private int index;
  private long nodeCount;

  private JsonParser(String source) {
    this.source = Objects.requireNonNull(source, "source");
  }

  static JsonValue parse(String source) throws JsonParseException {
    return new JsonParser(source).parseDocument();
  }

  private JsonValue parseDocument() throws JsonParseException {
    long inputBytes = validateAndMeasureInput();
    if (inputBytes > JsonLimits.INPUT_UTF8_BYTES) {
      throw failureAt(
          JsonParseErrorKind.INPUT_LIMIT, "inputLimit", source.length(), "16,777,216 UTF-8 bytes");
    }
    skipWhitespace();
    if (atEnd()) {
      throw failure(JsonParseErrorKind.SYNTAX, "emptyInput", "JSON value");
    }

    var frames = new ArrayDeque<Frame>();
    JsonValue completed = null;
    while (true) {
      if (completed == null) {
        completed = parseValueStart(frames);
        if (completed == null) {
          continue;
        }
      }

      if (frames.isEmpty()) {
        skipWhitespace();
        if (!atEnd()) {
          throw failure(JsonParseErrorKind.SYNTAX, "trailingContent", "end of input");
        }
        return completed;
      }

      Frame frame = frames.peek();
      if (frame instanceof ArrayFrame array) {
        array.elements.add(completed);
        completed = null;
        skipWhitespace();
        if (take(']')) {
          frames.pop();
          completed = new JsonArray(array.elements);
          continue;
        }
        if (!take(',')) {
          throw failure(JsonParseErrorKind.SYNTAX, "expectedCommaOrEnd", "',' or ']'");
        }
        skipWhitespace();
        if (!isValueStart()) {
          throw failure(JsonParseErrorKind.SYNTAX, "unexpectedToken", "JSON value");
        }
        if (array.elements.size() >= JsonLimits.ARRAY_LENGTH) {
          throw failure(
              JsonParseErrorKind.ARRAY_LENGTH_LIMIT,
              "arrayLengthLimit",
              Integer.toString(JsonLimits.ARRAY_LENGTH));
        }
        continue;
      }

      ObjectFrame object = (ObjectFrame) frame;
      object.members.add(new JsonMember(object.pendingKey, completed));
      object.pendingKey = null;
      completed = null;
      skipWhitespace();
      if (take('}')) {
        frames.pop();
        completed = new JsonObject(object.members);
        continue;
      }
      if (!take(',')) {
        throw failure(JsonParseErrorKind.SYNTAX, "expectedCommaOrEnd", "',' or '}'");
      }
      skipWhitespace();
      parseObjectKey(object);
    }
  }

  private JsonValue parseValueStart(ArrayDeque<Frame> frames) throws JsonParseException {
    if (atEnd()) {
      throw failure(JsonParseErrorKind.SYNTAX, "unexpectedToken", "JSON value");
    }
    int start = index;
    char current = source.charAt(index);
    return switch (current) {
      case 'n' -> {
        reserveNode(start);
        takeLiteral("null");
        yield JsonNull.INSTANCE;
      }
      case 't' -> {
        reserveNode(start);
        takeLiteral("true");
        yield new JsonBoolean(true);
      }
      case 'f' -> {
        reserveNode(start);
        takeLiteral("false");
        yield new JsonBoolean(false);
      }
      case '"' -> {
        reserveNode(start);
        yield new JsonString(parseString());
      }
      case '[' -> {
        reserveNode(start);
        checkDepth(frames.size() + 1, start);
        index++;
        skipWhitespace();
        if (take(']')) {
          yield new JsonArray(List.of());
        }
        if (!isValueStart()) {
          throw failure(JsonParseErrorKind.SYNTAX, "unexpectedToken", "JSON value or ']'");
        }
        frames.push(new ArrayFrame());
        yield null;
      }
      case '{' -> {
        reserveNode(start);
        checkDepth(frames.size() + 1, start);
        index++;
        skipWhitespace();
        if (take('}')) {
          yield new JsonObject(List.of());
        }
        var object = new ObjectFrame();
        frames.push(object);
        parseObjectKey(object);
        yield null;
      }
      default -> {
        if (current == '-' || isDigit(current)) {
          reserveNode(start);
          yield parseNumber();
        }
        throw failure(JsonParseErrorKind.SYNTAX, "unexpectedToken", "JSON value");
      }
    };
  }

  private void parseObjectKey(ObjectFrame object) throws JsonParseException {
    if (atEnd() || source.charAt(index) != '"') {
      throw failure(JsonParseErrorKind.SYNTAX, "expectedObjectKey", "JSON string key");
    }
    int keyStart = index;
    String key = parseString();
    skipWhitespace();
    if (!take(':')) {
      throw failure(JsonParseErrorKind.SYNTAX, "expectedColon", "':'");
    }
    if (object.members.size() >= JsonLimits.OBJECT_MEMBERS) {
      throw failureAt(
          JsonParseErrorKind.OBJECT_MEMBER_LIMIT,
          "objectMemberLimit",
          keyStart,
          Integer.toString(JsonLimits.OBJECT_MEMBERS));
    }
    if (!object.keys.add(key)) {
      throw failureAt(JsonParseErrorKind.DUPLICATE_KEY, "duplicateKey", keyStart, "unique key");
    }
    object.pendingKey = key;
    skipWhitespace();
    if (!isValueStart()) {
      throw failure(JsonParseErrorKind.SYNTAX, "unexpectedToken", "JSON value");
    }
  }

  private JsonValue parseNumber() throws JsonParseException {
    int start = index;
    take('-');
    if (atEnd()) {
      throw failureAt(JsonParseErrorKind.SYNTAX, "invalidNumber", start, "JSON number");
    }

    int mantissaDigits = 0;
    int fractionDigits = 0;
    if (take('0')) {
      mantissaDigits = 1;
      if (!atEnd() && isDigit(source.charAt(index))) {
        throw failure(JsonParseErrorKind.SYNTAX, "invalidNumber", "no leading zero");
      }
    } else if (!atEnd() && isNonZeroDigit(source.charAt(index))) {
      while (!atEnd() && isDigit(source.charAt(index))) {
        index++;
        mantissaDigits++;
      }
    } else {
      throw failureAt(JsonParseErrorKind.SYNTAX, "invalidNumber", start, "JSON integer part");
    }

    boolean decimal = false;
    if (take('.')) {
      decimal = true;
      int fractionStart = index;
      while (!atEnd() && isDigit(source.charAt(index))) {
        index++;
        mantissaDigits++;
        fractionDigits++;
      }
      if (index == fractionStart) {
        throw failure(JsonParseErrorKind.SYNTAX, "invalidNumber", "fraction digit");
      }
    }

    long exponent = 0;
    int exponentDigits = 0;
    if (!atEnd() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
      decimal = true;
      index++;
      boolean negativeExponent = take('-');
      if (!negativeExponent) {
        take('+');
      }
      int exponentStart = index;
      while (!atEnd() && isDigit(source.charAt(index))) {
        exponent = Math.min(1_000_000L, exponent * 10 + source.charAt(index) - '0');
        index++;
        exponentDigits++;
      }
      if (index == exponentStart) {
        throw failure(JsonParseErrorKind.SYNTAX, "invalidNumber", "exponent digit");
      }
      if (negativeExponent) {
        exponent = -exponent;
      }
    }

    int totalDigits = mantissaDigits + exponentDigits;
    if (totalDigits > JsonLimits.INPUT_NUMBER_DIGITS) {
      throw failureAt(
          JsonParseErrorKind.NUMBER_LIMIT,
          "inputDigits",
          start,
          Integer.toString(JsonLimits.INPUT_NUMBER_DIGITS));
    }

    String lexeme = source.substring(start, index);
    if (!decimal) {
      return new JsonInteger(new BigInteger(lexeme));
    }
    long scale = (long) fractionDigits - exponent;
    if (Math.abs(scale) > JsonLimits.DECIMAL_ABSOLUTE_SCALE) {
      throw failureAt(
          JsonParseErrorKind.NUMBER_LIMIT,
          "scale",
          start,
          Integer.toString(JsonLimits.DECIMAL_ABSOLUTE_SCALE));
    }
    BigDecimal value = JsonSupport.normalizeDecimal(new BigDecimal(lexeme));
    if (value.precision() > JsonLimits.DECIMAL_PRECISION) {
      throw failureAt(
          JsonParseErrorKind.NUMBER_LIMIT,
          "precision",
          start,
          Integer.toString(JsonLimits.DECIMAL_PRECISION));
    }
    if (JsonSupport.decimalAbsoluteScale(value) > JsonLimits.DECIMAL_ABSOLUTE_SCALE) {
      throw failureAt(
          JsonParseErrorKind.NUMBER_LIMIT,
          "scale",
          start,
          Integer.toString(JsonLimits.DECIMAL_ABSOLUTE_SCALE));
    }
    return new JsonDecimal(value);
  }

  private String parseString() throws JsonParseException {
    int opening = index;
    index++;
    var result = new StringBuilder();
    while (!atEnd()) {
      int currentIndex = index;
      char current = source.charAt(index++);
      if (current == '"') {
        return result.toString();
      }
      if (current < 0x20) {
        throw failureAt(
            JsonParseErrorKind.SYNTAX, "unescapedControl", currentIndex, "escaped control");
      }
      if (current == '\\') {
        if (atEnd()) {
          throw failureAt(
              JsonParseErrorKind.SYNTAX, "unterminatedString", index, "escape or closing quote");
        }
        char escape = source.charAt(index++);
        switch (escape) {
          case '"' -> result.append('"');
          case '\\' -> result.append('\\');
          case '/' -> result.append('/');
          case 'b' -> result.append('\b');
          case 'f' -> result.append('\f');
          case 'n' -> result.append('\n');
          case 'r' -> result.append('\r');
          case 't' -> result.append('\t');
          case 'u' -> appendUnicodeEscape(result, currentIndex);
          default ->
              throw failureAt(
                  JsonParseErrorKind.SYNTAX, "invalidEscape", currentIndex, "valid JSON escape");
        }
        continue;
      }
      if (Character.isHighSurrogate(current)) {
        if (atEnd() || !Character.isLowSurrogate(source.charAt(index))) {
          throw failureAt(
              JsonParseErrorKind.SYNTAX, "isolatedSurrogate", currentIndex, "Unicode scalar");
        }
        result.append(current).append(source.charAt(index++));
      } else if (Character.isLowSurrogate(current)) {
        throw failureAt(
            JsonParseErrorKind.SYNTAX, "isolatedSurrogate", currentIndex, "Unicode scalar");
      } else {
        result.append(current);
      }
    }
    throw failureAt(
        JsonParseErrorKind.SYNTAX, "unterminatedString", source.length(), "closing quote");
  }

  private void appendUnicodeEscape(StringBuilder result, int escapeStart)
      throws JsonParseException {
    int first = parseHexQuad(escapeStart);
    char firstChar = (char) first;
    if (Character.isHighSurrogate(firstChar)) {
      if (index + 6 > source.length()
          || source.charAt(index) != '\\'
          || source.charAt(index + 1) != 'u') {
        throw failureAt(
            JsonParseErrorKind.SYNTAX, "isolatedSurrogate", escapeStart, "low surrogate escape");
      }
      int lowStart = index;
      index += 2;
      char low = (char) parseHexQuad(lowStart);
      if (!Character.isLowSurrogate(low)) {
        throw failureAt(
            JsonParseErrorKind.SYNTAX, "isolatedSurrogate", escapeStart, "low surrogate escape");
      }
      result.appendCodePoint(Character.toCodePoint(firstChar, low));
      return;
    }
    if (Character.isLowSurrogate(firstChar)) {
      throw failureAt(
          JsonParseErrorKind.SYNTAX, "isolatedSurrogate", escapeStart, "high surrogate escape");
    }
    result.append(firstChar);
  }

  private int parseHexQuad(int escapeStart) throws JsonParseException {
    if (index + 4 > source.length()) {
      throw failureAt(
          JsonParseErrorKind.SYNTAX, "invalidUnicodeEscape", escapeStart, "four hex digits");
    }
    int value = 0;
    for (int count = 0; count < 4; count++) {
      int digit = hexValue(source.charAt(index++));
      if (digit < 0) {
        throw failureAt(
            JsonParseErrorKind.SYNTAX, "invalidUnicodeEscape", escapeStart, "four hex digits");
      }
      value = value * 16 + digit;
    }
    return value;
  }

  private void takeLiteral(String literal) throws JsonParseException {
    int start = index;
    if (!source.startsWith(literal, index)) {
      throw failureAt(JsonParseErrorKind.SYNTAX, "unexpectedToken", start, "'" + literal + "'");
    }
    index += literal.length();
  }

  private void reserveNode(int start) throws JsonParseException {
    nodeCount++;
    if (nodeCount > JsonLimits.VALUE_NODES) {
      throw failureAt(
          JsonParseErrorKind.NODE_LIMIT, "nodeLimit", start, Long.toString(JsonLimits.VALUE_NODES));
    }
  }

  private void checkDepth(int depth, int start) throws JsonParseException {
    if (depth > JsonLimits.DEPTH) {
      throw failureAt(
          JsonParseErrorKind.DEPTH_LIMIT, "depthLimit", start, Integer.toString(JsonLimits.DEPTH));
    }
  }

  private long validateAndMeasureInput() throws JsonParseException {
    long bytes = 0;
    for (int offset = 0; offset < source.length(); ) {
      int start = offset;
      char first = source.charAt(offset++);
      int codePoint;
      if (Character.isHighSurrogate(first)) {
        if (offset >= source.length() || !Character.isLowSurrogate(source.charAt(offset))) {
          throw failureAt(JsonParseErrorKind.SYNTAX, "isolatedSurrogate", start, "Unicode scalar");
        }
        codePoint = Character.toCodePoint(first, source.charAt(offset++));
      } else if (Character.isLowSurrogate(first)) {
        throw failureAt(JsonParseErrorKind.SYNTAX, "isolatedSurrogate", start, "Unicode scalar");
      } else {
        codePoint = first;
      }
      bytes =
          JsonSupport.saturatedAdd(
              bytes, codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4);
      if (bytes > JsonLimits.INPUT_UTF8_BYTES) {
        return bytes;
      }
    }
    return bytes;
  }

  private JsonParseException failure(JsonParseErrorKind kind, String reason, String expected) {
    return failureAt(kind, reason, index, expected);
  }

  private JsonParseException failureAt(
      JsonParseErrorKind kind, String reason, int errorIndex, String expected) {
    Position position = positionAt(errorIndex);
    return new JsonParseException(
        kind, reason, position.utf8Offset, position.line, position.column, expected);
  }

  private Position positionAt(int target) {
    long bytes = 0;
    int line = 1;
    int column = 1;
    for (int offset = 0; offset < target; ) {
      char first = source.charAt(offset);
      if (first == '\r') {
        bytes++;
        offset++;
        if (offset < target && offset < source.length() && source.charAt(offset) == '\n') {
          bytes++;
          offset++;
        }
        line++;
        column = 1;
        continue;
      }
      if (first == '\n') {
        bytes++;
        offset++;
        line++;
        column = 1;
        continue;
      }
      int codePoint;
      if (Character.isHighSurrogate(first)
          && offset + 1 < source.length()
          && Character.isLowSurrogate(source.charAt(offset + 1))) {
        codePoint = Character.toCodePoint(first, source.charAt(offset + 1));
        offset += 2;
      } else {
        codePoint = first;
        offset++;
      }
      bytes += codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
      column++;
    }
    return new Position(bytes, line, column);
  }

  private boolean take(char expected) {
    if (!atEnd() && source.charAt(index) == expected) {
      index++;
      return true;
    }
    return false;
  }

  private void skipWhitespace() {
    while (!atEnd()) {
      char current = source.charAt(index);
      if (current != ' ' && current != '\t' && current != '\r' && current != '\n') {
        return;
      }
      index++;
    }
  }

  private boolean isValueStart() {
    if (atEnd()) {
      return false;
    }
    char current = source.charAt(index);
    return current == 'n'
        || current == 't'
        || current == 'f'
        || current == '"'
        || current == '['
        || current == '{'
        || current == '-'
        || isDigit(current);
  }

  private boolean atEnd() {
    return index >= source.length();
  }

  private static boolean isDigit(char value) {
    return value >= '0' && value <= '9';
  }

  private static boolean isNonZeroDigit(char value) {
    return value >= '1' && value <= '9';
  }

  private static int hexValue(char value) {
    if (value >= '0' && value <= '9') {
      return value - '0';
    }
    if (value >= 'A' && value <= 'F') {
      return value - 'A' + 10;
    }
    if (value >= 'a' && value <= 'f') {
      return value - 'a' + 10;
    }
    return -1;
  }

  private sealed interface Frame permits ArrayFrame, ObjectFrame {}

  private static final class ArrayFrame implements Frame {
    private final List<JsonValue> elements = new ArrayList<>();
  }

  private static final class ObjectFrame implements Frame {
    private final List<JsonMember> members = new ArrayList<>();
    private final Set<String> keys = new HashSet<>();
    private String pendingKey;
  }

  private record Position(long utf8Offset, int line, int column) {}
}
