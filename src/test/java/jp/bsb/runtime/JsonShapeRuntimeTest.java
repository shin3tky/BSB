package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonArray;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonInteger;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class JsonShapeRuntimeTest {
  private static final String SOURCE = "json-shapes-test.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void distinguishesMissingNullKindsAndUsesStableDepthFirstPointerPaths() throws Exception {
    JsonShapeValue item =
        JsonShapeValue.emptyObject()
            .withMember("x", true, JsonShapeValue.leaf(JsonShapeValue.Kind.STRING));
    JsonShapeValue shape =
        JsonShapeValue.emptyObject()
            .withMember("a", true, JsonShapeValue.leaf(JsonShapeValue.Kind.STRING))
            .withMember("b", false, JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER))
            .withMember("items", true, JsonShapeValue.array(item))
            .withMember("~/", true, JsonShapeValue.leaf(JsonShapeValue.Kind.BOOLEAN))
            .withMember("missing", true, JsonShapeValue.leaf(JsonShapeValue.Kind.DECIMAL));
    JsonRuntimeValue input =
        new JsonRuntimeValue(
            JsonCodec.parse("{\"a\":1,\"b\":null,\"items\":[{\"x\":\"ok\"},{\"x\":2}],\"~/\":0}"));
    var budget = new ExecutionBudget(SOURCE, () -> 0L);
    var stack = stack(input, shape);

    execute("JSONの形状を検証する", stack, budget);

    ResultValue result = (ResultValue) stack.getFirst();
    assertTrue(result.isFailure());
    ArrayValue failures = (ArrayValue) result.value();
    assertEquals(ValueType.JSON_SHAPE_FAILURE, failures.elementType());
    assertEquals(5, failures.size());
    assertEquals(
        List.of("/a", "/b", "/items/1/x", "/~0~1", "/missing"),
        failures.elements().stream()
            .map(JsonShapeFailureValue.class::cast)
            .map(JsonShapeFailureValue::path)
            .toList());
    assertEquals(
        List.of(
            "kindMismatch", "nullNotAllowed", "kindMismatch", "kindMismatch", "missingRequiredKey"),
        failures.elements().stream()
            .map(JsonShapeFailureValue.class::cast)
            .map(JsonShapeFailureValue::kind)
            .toList());
    assertEquals(10, budget.jsonShapeWorkUnits());
  }

  @Test
  void validatesOptionalNullableAndReturnsTheOriginalJsonValue() throws Exception {
    JsonShapeValue shape =
        JsonShapeValue.emptyObject()
            .withMember(
                "next",
                false,
                JsonShapeValue.nullable(JsonShapeValue.leaf(JsonShapeValue.Kind.STRING)))
            .withMember("id", true, JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER));
    JsonRuntimeValue input = new JsonRuntimeValue(JsonCodec.parse("{\"next\":null,\"id\":1}"));
    var budget = new ExecutionBudget(SOURCE, () -> 0L);
    var stack = stack(input, shape);

    execute("JSONの形状を検証する", stack, budget);

    ResultValue result = (ResultValue) stack.getFirst();
    assertTrue(result.isSuccess());
    assertSame(input, result.value());
    assertEquals(3, budget.jsonShapeWorkUnits());

    var missingOptional = stack(new JsonRuntimeValue(JsonCodec.parse("{\"id\":1}")), shape);
    execute("JSONの形状を検証する", missingOptional, new ExecutionBudget(SOURCE, () -> 0L));
    assertTrue(((ResultValue) missingOptional.getFirst()).isSuccess());
  }

  @Test
  void capsFailuresAtTwoHundredFiftySixInArrayOrder() throws Exception {
    List<jp.bsb.json.JsonValue> elements =
        java.util.stream.IntStream.range(0, 300)
            .mapToObj(index -> new JsonString("x"))
            .map(jp.bsb.json.JsonValue.class::cast)
            .toList();
    JsonRuntimeValue input = new JsonRuntimeValue(new JsonArray(elements));
    JsonShapeValue shape = JsonShapeValue.array(JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER));
    var budget = new ExecutionBudget(SOURCE, () -> 0L);
    var stack = stack(input, shape);

    execute("JSONの形状を検証する", stack, budget);

    ArrayValue failures = (ArrayValue) ((ResultValue) stack.getFirst()).value();
    assertEquals(JsonShapeLimits.MAX_FAILURES, failures.size());
    assertEquals("/0", ((JsonShapeFailureValue) failures.get(0)).path());
    assertEquals("/255", ((JsonShapeFailureValue) failures.get(255)).path());
    assertEquals(257, budget.jsonShapeWorkUnits());
  }

  @Test
  void settersReplaceInPlaceAndRejectNonObjectWithoutChangingInputs() throws Exception {
    JsonShapeValue object = JsonShapeValue.emptyObject();
    JsonShapeValue integer = JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER);
    var first = stack(object, new StringValue("id"), integer);
    execute("JSON形状に必須キーを設定する", first, new ExecutionBudget(SOURCE, () -> 0L));
    JsonShapeValue withRequired = (JsonShapeValue) first.getFirst();
    assertTrue(withRequired.members().getFirst().required());

    var replace =
        stack(withRequired, new StringValue("id"), JsonShapeValue.leaf(JsonShapeValue.Kind.STRING));
    execute("JSON形状に任意キーを設定する", replace, new ExecutionBudget(SOURCE, () -> 0L));
    JsonShapeValue withOptional = (JsonShapeValue) replace.getFirst();
    assertFalse(withOptional.members().getFirst().required());
    assertEquals(JsonShapeValue.Kind.STRING, withOptional.members().getFirst().shape().kind());

    JsonShapeValue leaf = JsonShapeValue.leaf(JsonShapeValue.Kind.STRING);
    var invalid = stack(leaf, new StringValue("secret-key"), integer);
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("JSON形状に必須キーを設定する", invalid, new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_JSON_SHAPE_OBJECT_REQUIRED, failure.diagnostic().code());
    assertEquals(List.of(leaf, new StringValue("secret-key"), integer), invalid);
    assertFalse(failure.diagnostic().toString().contains("secret-key"));
  }

  @Test
  void enforcesDepthNodePathAndCumulativeWorkAtomically() throws Exception {
    JsonShapeValue deep = JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER);
    for (int depth = 1; depth < JsonShapeLimits.MAX_DEPTH; depth++) {
      deep = JsonShapeValue.array(deep);
    }
    var deepStack = stack(deep);
    RuntimeFailure depthFailure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("JSON配列の形状にする", deepStack, new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_JSON_SHAPE_DEPTH_LIMIT, depthFailure.diagnostic().code());
    assertSame(deep, deepStack.getFirst());

    JsonShapeValue wide = JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER);
    while (wide.nodeCount() <= JsonShapeLimits.MAX_NODES / 2) {
      wide = JsonShapeValue.emptyObject().withMember("a", true, wide).withMember("b", true, wide);
    }
    JsonShapeValue base = JsonShapeValue.emptyObject().withMember("first", true, wide);
    var nodeStack =
        stack(base, new StringValue("overflow"), JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER));
    RuntimeFailure nodeFailure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("JSON形状に必須キーを設定する", nodeStack, new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_JSON_SHAPE_NODE_LIMIT, nodeFailure.diagnostic().code());
    assertSame(base, nodeStack.getFirst());

    String longKey = "x".repeat((int) JsonShapeLimits.MAX_PATH_UTF8_BYTES);
    JsonShapeValue longPathShape =
        JsonShapeValue.emptyObject()
            .withMember(longKey, true, JsonShapeValue.leaf(JsonShapeValue.Kind.STRING));
    JsonRuntimeValue longPathInput =
        new JsonRuntimeValue(
            new JsonObject(
                List.of(
                    new jp.bsb.json.JsonMember(
                        longKey, new JsonInteger(java.math.BigInteger.ONE)))));
    var pathBudget = new ExecutionBudget(SOURCE, () -> 0L);
    var pathStack = stack(longPathInput, longPathShape);
    RuntimeFailure pathFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONの形状を検証する", pathStack, pathBudget));
    assertEquals(DiagnosticCode.E_JSON_SHAPE_PATH_LIMIT, pathFailure.diagnostic().code());
    assertEquals(List.of(longPathInput, longPathShape), pathStack);
    assertEquals(0, pathBudget.jsonShapeWorkUnits());

    var fullBudget = new ExecutionBudget(SOURCE, () -> 0L);
    fullBudget.beforeJsonShapeWork(JsonShapeLimits.MAX_WORK_UNITS, SPAN, "test");
    var workStack =
        stack(
            new JsonRuntimeValue(JsonCodec.parse("1")),
            JsonShapeValue.leaf(JsonShapeValue.Kind.INTEGER));
    RuntimeFailure workFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONの形状を検証する", workStack, fullBudget));
    assertEquals(DiagnosticCode.E_JSON_SHAPE_WORK_LIMIT, workFailure.diagnostic().code());
    assertEquals(JsonShapeLimits.MAX_WORK_UNITS, fullBudget.jsonShapeWorkUnits());
  }

  @Test
  void accessorsExposeOnlyClosedFields() throws Exception {
    JsonShapeFailureValue failure =
        new JsonShapeFailureValue("kindMismatch", "/items/2", "integer", "string");
    assertAccessor("JSON形状失敗の種類を取り出す", failure, "kindMismatch");
    assertAccessor("JSON形状失敗のパスを取り出す", failure, "/items/2");
    assertAccessor("JSON形状失敗の期待種類を取り出す", failure, "integer");
    assertAccessor("JSON形状失敗の実際種類を取り出す", failure, "string");
    assertFalse(TraceValuePolicy.byteSequence().mayReveal(failure));
  }

  private static void assertAccessor(String word, JsonShapeFailureValue failure, String expected)
      throws Exception {
    var stack = stack(failure);
    execute(word, stack, new ExecutionBudget(SOURCE, () -> 0L));
    assertEquals(List.of(new StringValue(expected)), stack);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(SOURCE, new BoundedOutput(SOURCE, new MemoryOutputSink()), budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }
}
