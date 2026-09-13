package jp.bsb.runtime;

import java.util.Objects;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonValue;
import jp.bsb.json.JsonWriteException;
import jp.bsb.stdlib.ValueType;

/** JSON内部の7値種別を1個のBSB実行時値として包みます。 */
public record JsonRuntimeValue(JsonValue value) implements RuntimeValue {
  public JsonRuntimeValue {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public ValueType type() {
    return ValueType.JSON;
  }

  @Override
  public String displayText() {
    try {
      return JsonCodec.serialize(value);
    } catch (JsonWriteException exception) {
      throw new IllegalStateException("oversized JSON must be rejected before display", exception);
    }
  }

  @Override
  public String traceText() {
    return "JSON:<redacted>";
  }

  @Override
  public String toString() {
    return displayText();
  }
}
