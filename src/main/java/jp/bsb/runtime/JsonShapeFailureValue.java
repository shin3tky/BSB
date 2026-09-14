package jp.bsb.runtime;

import java.util.Objects;
import java.util.Set;
import jp.bsb.stdlib.ValueType;

/** 原JSONを保持しない、1件のJSON形状不一致です。 */
public record JsonShapeFailureValue(
    String kind, String path, String expectedKind, String actualKind) implements RuntimeValue {
  private static final Set<String> KINDS =
      Set.of("missingRequiredKey", "nullNotAllowed", "kindMismatch");
  private static final Set<String> JSON_KINDS =
      Set.of("null", "boolean", "integer", "decimal", "string", "array", "object");

  public JsonShapeFailureValue {
    if (!KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown JSON shape failure kind");
    }
    Objects.requireNonNull(path, "path");
    if (!JSON_KINDS.contains(expectedKind)) {
      throw new IllegalArgumentException("unknown expected JSON kind");
    }
    if (!(JSON_KINDS.contains(actualKind) || actualKind.equals("missing"))) {
      throw new IllegalArgumentException("unknown actual JSON kind");
    }
  }

  @Override
  public ValueType type() {
    return ValueType.JSON_SHAPE_FAILURE;
  }

  @Override
  public String displayText() {
    return "<JSON形状失敗>";
  }

  @Override
  public String toString() {
    return displayText();
  }
}
