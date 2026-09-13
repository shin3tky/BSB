package jp.bsb.json;

import java.util.List;
import java.util.Objects;

/** 順序つきの不変JSON配列です。 */
public final class JsonArray implements JsonValue {
  private final List<JsonValue> elements;
  private final JsonMetrics metrics;

  public JsonArray(List<? extends JsonValue> elements) {
    Objects.requireNonNull(elements, "elements");
    this.elements = List.copyOf(elements);
    if (this.elements.size() > JsonLimits.ARRAY_LENGTH) {
      throw new IllegalArgumentException("JSON array length exceeds the normative limit");
    }
    this.elements.forEach(value -> Objects.requireNonNull(value, "element"));
    metrics = measure(this.elements);
  }

  public List<JsonValue> elements() {
    return elements;
  }

  public int size() {
    return elements.size();
  }

  public JsonValue get(int index) {
    return elements.get(index);
  }

  @Override
  public JsonKind kind() {
    return JsonKind.ARRAY;
  }

  @Override
  public JsonMetrics metrics() {
    return metrics;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonArray array && elements.equals(array.elements);
  }

  @Override
  public int hashCode() {
    return elements.hashCode();
  }

  private static JsonMetrics measure(List<JsonValue> elements) {
    long nodes = 1;
    long references = elements.size();
    long bytes = 2L + Math.max(0, elements.size() - 1);
    int childDepth = 0;
    for (JsonValue element : elements) {
      JsonMetrics child = element.metrics();
      nodes = JsonSupport.saturatedAdd(nodes, child.nodeCount());
      references = JsonSupport.saturatedAdd(references, child.containerReferences());
      bytes = JsonSupport.saturatedAdd(bytes, child.serializedUtf8Bytes());
      childDepth = Math.max(childDepth, child.depth());
    }
    int depth = 1 + childDepth;
    validateStructure(nodes, depth);
    return new JsonMetrics(nodes, references, depth, bytes);
  }

  static void validateStructure(long nodes, int depth) {
    if (nodes > JsonLimits.VALUE_NODES) {
      throw new IllegalArgumentException("JSON value nodes exceed the normative limit");
    }
    if (depth > JsonLimits.DEPTH) {
      throw new IllegalArgumentException("JSON depth exceeds the normative limit");
    }
  }
}
