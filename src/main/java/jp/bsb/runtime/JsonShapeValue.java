package jp.bsb.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jp.bsb.json.JsonKind;
import jp.bsb.stdlib.ValueType;

/** JSON値へ適用する、有限で不変な形状定義です。 */
public final class JsonShapeValue implements RuntimeValue {
  /** 形状を構成する閉じたノード種類です。 */
  public enum Kind {
    NULL("null", JsonKind.NULL),
    BOOLEAN("boolean", JsonKind.BOOLEAN),
    INTEGER("integer", JsonKind.INTEGER),
    DECIMAL("decimal", JsonKind.DECIMAL),
    STRING("string", JsonKind.STRING),
    ARRAY("array", JsonKind.ARRAY),
    OBJECT("object", JsonKind.OBJECT),
    NULLABLE("nullable", null);

    private final String stableName;
    private final JsonKind jsonKind;

    Kind(String stableName, JsonKind jsonKind) {
      this.stableName = stableName;
      this.jsonKind = jsonKind;
    }

    public String stableName() {
      return stableName;
    }

    JsonKind jsonKind() {
      return jsonKind;
    }
  }

  /** オブジェクト形状の設定順を保持するメンバーです。 */
  public record Member(String name, boolean required, JsonShapeValue shape) {
    public Member {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(shape, "shape");
    }
  }

  private static final Map<Kind, JsonShapeValue> LEAVES = createLeaves();
  private static final JsonShapeValue EMPTY_OBJECT =
      new JsonShapeValue(Kind.OBJECT, null, List.of(), 1, 1);

  private final Kind kind;
  private final JsonShapeValue child;
  private final List<Member> members;
  private final Map<String, Integer> memberIndexes;
  private final long nodeCount;
  private final int depth;

  private JsonShapeValue(
      Kind kind, JsonShapeValue child, List<Member> members, long nodeCount, int depth) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.child = child;
    this.members = List.copyOf(members);
    this.nodeCount = nodeCount;
    this.depth = depth;
    var indexes = new LinkedHashMap<String, Integer>();
    for (int index = 0; index < this.members.size(); index++) {
      if (indexes.put(this.members.get(index).name(), index) != null) {
        throw new IllegalArgumentException("duplicate JSON shape member");
      }
    }
    memberIndexes = Collections.unmodifiableMap(indexes);
  }

  public static JsonShapeValue leaf(Kind kind) {
    JsonShapeValue result = LEAVES.get(Objects.requireNonNull(kind, "kind"));
    if (result == null) {
      throw new IllegalArgumentException("a leaf JSON shape kind is required");
    }
    return result;
  }

  public static JsonShapeValue emptyObject() {
    return EMPTY_OBJECT;
  }

  static JsonShapeValue array(JsonShapeValue child) {
    return wrapped(Kind.ARRAY, child);
  }

  static JsonShapeValue nullable(JsonShapeValue child) {
    Objects.requireNonNull(child, "child");
    return child.kind == Kind.NULLABLE || child.kind == Kind.NULL
        ? child
        : wrapped(Kind.NULLABLE, child);
  }

  static JsonShapeValue wrapped(Kind kind, JsonShapeValue child) {
    Objects.requireNonNull(child, "child");
    if (kind != Kind.ARRAY && kind != Kind.NULLABLE) {
      throw new IllegalArgumentException("a wrapper JSON shape kind is required");
    }
    return new JsonShapeValue(kind, child, List.of(), child.nodeCount + 1, child.depth + 1);
  }

  JsonShapeValue withMember(String name, boolean required, JsonShapeValue shape) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(shape, "shape");
    if (kind != Kind.OBJECT) {
      throw new IllegalStateException("an object JSON shape is required");
    }
    var result = new ArrayList<>(members);
    Integer existing = memberIndexes.get(name);
    long resultNodes;
    if (existing == null) {
      result.add(new Member(name, required, shape));
      resultNodes = saturatedAdd(nodeCount, shape.nodeCount);
    } else {
      JsonShapeValue previous = result.get(existing).shape();
      result.set(existing, new Member(name, required, shape));
      resultNodes = saturatedAdd(nodeCount - previous.nodeCount, shape.nodeCount);
    }
    int resultDepth = 1;
    for (Member member : result) {
      resultDepth = Math.max(resultDepth, member.shape.depth + 1);
    }
    return new JsonShapeValue(Kind.OBJECT, null, result, resultNodes, resultDepth);
  }

  public Kind kind() {
    return kind;
  }

  JsonShapeValue child() {
    if (child == null) {
      throw new IllegalStateException("this JSON shape has no child");
    }
    return child;
  }

  List<Member> members() {
    return members;
  }

  public long nodeCount() {
    return nodeCount;
  }

  public int depth() {
    return depth;
  }

  String expectedJsonKindName() {
    JsonShapeValue current = this;
    while (current.kind == Kind.NULLABLE) {
      current = current.child();
    }
    return current.kind.stableName();
  }

  boolean acceptsNull() {
    return kind == Kind.NULL || kind == Kind.NULLABLE;
  }

  JsonShapeValue nonNullShape() {
    return kind == Kind.NULLABLE ? child() : this;
  }

  @Override
  public ValueType type() {
    return ValueType.JSON_SHAPE;
  }

  @Override
  public String displayText() {
    return "<JSON形状>";
  }

  @Override
  public String toString() {
    return displayText();
  }

  private static Map<Kind, JsonShapeValue> createLeaves() {
    var result = new LinkedHashMap<Kind, JsonShapeValue>();
    for (Kind kind : List.of(Kind.NULL, Kind.BOOLEAN, Kind.INTEGER, Kind.DECIMAL, Kind.STRING)) {
      result.put(kind, new JsonShapeValue(kind, null, List.of(), 1, 1));
    }
    return Map.copyOf(result);
  }

  private static long saturatedAdd(long left, long right) {
    return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
  }
}
