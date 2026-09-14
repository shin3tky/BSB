package jp.bsb.runtime;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jp.bsb.json.JsonArray;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonValue;

/** RFC 6901の文字列表現を検証し、JSON値を決定的に参照します。 */
final class JsonPointer {
  private JsonPointer() {}

  static List<String> parse(String pointer) throws SyntaxException {
    if (pointer.isEmpty()) {
      return List.of();
    }
    if (pointer.charAt(0) != '/') {
      throw new SyntaxException("missingLeadingSlash", 0);
    }
    var tokens = new ArrayList<String>();
    int start = 1;
    for (int index = 1; index <= pointer.length(); index++) {
      if (index == pointer.length() || pointer.charAt(index) == '/') {
        tokens.add(decode(pointer, start, index));
        start = index + 1;
      }
    }
    return List.copyOf(tokens);
  }

  static Optional<JsonValue> resolve(JsonValue root, List<String> tokens) {
    JsonValue current = root;
    for (String token : tokens) {
      if (current instanceof JsonObject object) {
        Optional<JsonValue> found = object.find(token);
        if (found.isEmpty()) {
          return Optional.empty();
        }
        current = found.orElseThrow();
      } else if (current instanceof JsonArray array) {
        Integer index = arrayIndex(token, array.size());
        if (index == null) {
          return Optional.empty();
        }
        current = array.get(index);
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(current);
  }

  private static String decode(String pointer, int start, int end) throws SyntaxException {
    var result = new StringBuilder(end - start);
    for (int index = start; index < end; index++) {
      char current = pointer.charAt(index);
      if (current != '~') {
        result.append(current);
        continue;
      }
      if (index + 1 >= end) {
        throw new SyntaxException("trailingTilde", index);
      }
      char escaped = pointer.charAt(++index);
      if (escaped == '0') {
        result.append('~');
      } else if (escaped == '1') {
        result.append('/');
      } else {
        throw new SyntaxException("invalidEscape", index - 1);
      }
    }
    return result.toString();
  }

  private static Integer arrayIndex(String token, int size) {
    if (token.isEmpty() || token.equals("-") || (token.length() > 1 && token.charAt(0) == '0')) {
      return null;
    }
    for (int index = 0; index < token.length(); index++) {
      char character = token.charAt(index);
      if (character < '0' || character > '9') {
        return null;
      }
    }
    try {
      BigInteger value = new BigInteger(token);
      return value.compareTo(BigInteger.valueOf(size)) < 0 ? value.intValueExact() : null;
    } catch (ArithmeticException ignored) {
      return null;
    }
  }

  static final class SyntaxException extends Exception {
    private final int offset;

    SyntaxException(String reason, int offset) {
      super(reason);
      this.offset = offset;
    }

    int offset() {
      return offset;
    }
  }
}
