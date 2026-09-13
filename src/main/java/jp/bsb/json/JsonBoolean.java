package jp.bsb.json;

/** JSONの真偽値です。 */
public record JsonBoolean(boolean value) implements JsonValue {
  private static final JsonMetrics TRUE_METRICS = new JsonMetrics(1, 0, 0, 4);
  private static final JsonMetrics FALSE_METRICS = new JsonMetrics(1, 0, 0, 5);

  @Override
  public JsonKind kind() {
    return JsonKind.BOOLEAN;
  }

  @Override
  public JsonMetrics metrics() {
    return value ? TRUE_METRICS : FALSE_METRICS;
  }
}
