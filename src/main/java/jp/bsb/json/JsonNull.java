package jp.bsb.json;

/** JSONのnull値です。 */
public enum JsonNull implements JsonValue {
  INSTANCE;

  private static final JsonMetrics METRICS = new JsonMetrics(1, 0, 0, 4);

  @Override
  public JsonKind kind() {
    return JsonKind.NULL;
  }

  @Override
  public JsonMetrics metrics() {
    return METRICS;
  }
}
