package jp.bsb.json;

import java.util.Objects;

/** 正規化しないUnicodeスカラー値列のJSON文字列です。 */
public final class JsonString implements JsonValue {
  private final String value;
  private final JsonMetrics metrics;

  public JsonString(String value) {
    this.value = Objects.requireNonNull(value, "value");
    JsonSupport.utf8Length(value);
    metrics = new JsonMetrics(1, 0, 0, JsonSupport.escapedStringUtf8Length(value));
  }

  public String value() {
    return value;
  }

  @Override
  public JsonKind kind() {
    return JsonKind.STRING;
  }

  @Override
  public JsonMetrics metrics() {
    return metrics;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonString string && value.equals(string.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
