package jp.bsb.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 挿入順を保つ不変JSONオブジェクトです。 */
public final class JsonObject implements JsonValue {
  private final List<JsonMember> members;
  private final Map<String, JsonValue> values;
  private final JsonMetrics metrics;

  public JsonObject(List<JsonMember> members) {
    Objects.requireNonNull(members, "members");
    if (members.size() > JsonLimits.OBJECT_MEMBERS) {
      throw new IllegalArgumentException("JSON object members exceed the normative limit");
    }
    var copiedMembers = new ArrayList<JsonMember>(members.size());
    var copiedValues = new LinkedHashMap<String, JsonValue>();
    for (JsonMember member : members) {
      JsonMember nonNull = Objects.requireNonNull(member, "member");
      if (copiedValues.putIfAbsent(nonNull.key(), nonNull.value()) != null) {
        throw new IllegalArgumentException("duplicate JSON object key");
      }
      copiedMembers.add(nonNull);
    }
    this.members = List.copyOf(copiedMembers);
    values = Collections.unmodifiableMap(copiedValues);
    metrics = measure(this.members);
  }

  public List<JsonMember> members() {
    return members;
  }

  public List<String> keys() {
    return List.copyOf(values.keySet());
  }

  public int size() {
    return members.size();
  }

  public boolean containsKey(String key) {
    return values.containsKey(Objects.requireNonNull(key, "key"));
  }

  public Optional<JsonValue> find(String key) {
    return Optional.ofNullable(values.get(Objects.requireNonNull(key, "key")));
  }

  @Override
  public JsonKind kind() {
    return JsonKind.OBJECT;
  }

  @Override
  public JsonMetrics metrics() {
    return metrics;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonObject object && values.equals(object.values);
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }

  private static JsonMetrics measure(List<JsonMember> members) {
    long nodes = 1;
    long references = members.size();
    long bytes = 2L + Math.max(0, members.size() - 1);
    int childDepth = 0;
    for (JsonMember member : members) {
      JsonMetrics child = member.value().metrics();
      nodes = JsonSupport.saturatedAdd(nodes, child.nodeCount());
      references = JsonSupport.saturatedAdd(references, child.containerReferences());
      bytes = JsonSupport.saturatedAdd(bytes, JsonSupport.escapedStringUtf8Length(member.key()));
      bytes = JsonSupport.saturatedAdd(bytes, 1);
      bytes = JsonSupport.saturatedAdd(bytes, child.serializedUtf8Bytes());
      childDepth = Math.max(childDepth, child.depth());
    }
    int depth = 1 + childDepth;
    JsonArray.validateStructure(nodes, depth);
    return new JsonMetrics(nodes, references, depth, bytes);
  }
}
