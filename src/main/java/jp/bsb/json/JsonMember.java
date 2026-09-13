package jp.bsb.json;

import java.util.Objects;

/** 挿入位置を保つJSONオブジェクトの1メンバーです。 */
public record JsonMember(String key, JsonValue value) {
  public JsonMember {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    JsonSupport.utf8Length(key);
  }
}
