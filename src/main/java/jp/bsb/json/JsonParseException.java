package jp.bsb.json;

import java.util.Objects;
import java.util.Optional;

/** 実行時診断へ依存しない、JSON入力内位置つきの解析失敗です。 */
public final class JsonParseException extends Exception {
  private final JsonParseErrorKind kind;
  private final String reason;
  private final long utf8Offset;
  private final int line;
  private final int column;
  private final Optional<String> expected;

  JsonParseException(
      JsonParseErrorKind kind,
      String reason,
      long utf8Offset,
      int line,
      int column,
      String expected) {
    super(reason);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.reason = Objects.requireNonNull(reason, "reason");
    this.utf8Offset = utf8Offset;
    this.line = line;
    this.column = column;
    this.expected = Optional.ofNullable(expected);
  }

  public JsonParseErrorKind kind() {
    return kind;
  }

  public String reason() {
    return reason;
  }

  public long utf8Offset() {
    return utf8Offset;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public Optional<String> expected() {
    return expected;
  }
}
