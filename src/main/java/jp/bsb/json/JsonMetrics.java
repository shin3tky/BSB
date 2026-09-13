package jp.bsb.json;

/** JSON値を構築せず再走査せず参照できる規範計測値です。 */
public record JsonMetrics(
    long nodeCount, long containerReferences, int depth, long serializedUtf8Bytes) {
  public JsonMetrics {
    if (nodeCount < 1 || containerReferences < 0 || depth < 0 || serializedUtf8Bytes < 1) {
      throw new IllegalArgumentException("JSON metrics must be non-negative");
    }
  }

  /** JSON構築単位を返します。 */
  public long constructionUnits() {
    return JsonSupport.saturatedAdd(nodeCount, containerReferences);
  }
}
