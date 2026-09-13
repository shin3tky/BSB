package jp.bsb.json;

/** 厳密JSON解析と決定的直列化の公開入口です。 */
public final class JsonCodec {
  private JsonCodec() {}

  /** JSON文字列全体を第一級JSON値へ解析します。 */
  public static JsonValue parse(String source) throws JsonParseException {
    return JsonParser.parse(source);
  }

  /** JSON値を規範のコンパクトJSONへ直列化します。 */
  public static String serialize(JsonValue value) throws JsonWriteException {
    return JsonWriter.write(value);
  }
}
