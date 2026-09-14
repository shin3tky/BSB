package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonArray;
import jp.bsb.json.JsonBoolean;
import jp.bsb.json.JsonDecimal;
import jp.bsb.json.JsonInteger;
import jp.bsb.json.JsonLimits;
import jp.bsb.json.JsonMember;
import jp.bsb.json.JsonNull;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonString;
import jp.bsb.json.JsonValue;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

class JsonJsonRuntimeTest {
  private static final String SOURCE_PATH = "json-runtime.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void parsesSerializesAndRoundTripsEveryBsbScalarEndToEnd() {
    String source =
        "メインとは （--）\n"
            + "    「{\"b\":true,\"i\":42,\"d\":2.50,\"s\":\"値\",\"a\":[null]}」を JSONを解析する\n"
            + "    JSONを文字列に変換する 一行表示する\n"
            + "    はい を 真偽をJSONに変換する JSONから真偽を取り出す 一行表示する\n"
            + "    42 を 整数をJSONに変換する JSONから整数を取り出す 一行表示する\n"
            + "    2.50 を 小数をJSONに変換する JSONから小数を取り出す 一行表示する\n"
            + "    「値」 を 文字列をJSONに変換する JSONから文字列を取り出す 一行表示する\n"
            + "    JSONヌル JSONヌルである 一行表示する JSONを文字列に変換する 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        "{\"b\":true,\"i\":42,\"d\":2.5,\"s\":\"値\",\"a\":[null]}\n" + "はい\n42\n2.5\n値\nはい\nnull\n",
        output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
    assertTrue(result.jsonConstructionUnits() > 0);
    assertTrue(result.jsonWorkUnits() > 0);
  }

  @Test
  void convertsAndUpdatesJsonArraysWithoutChangingTheirInputs() throws Exception {
    JsonArray original =
        new JsonArray(List.of(new JsonInteger(BigInteger.ONE), new JsonInteger(BigInteger.TWO)));
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    var converted = stack(new JsonRuntimeValue(original));
    execute("JSONから配列に変換する", converted, budget);
    ArrayValue bsbArray = (ArrayValue) converted.getFirst();
    assertEquals(ScalarType.JSON, bsbArray.elementType());
    assertEquals(original.elements(), unwrap(bsbArray));
    execute("JSON配列に変換する", converted, budget);
    JsonArray roundTrip = (JsonArray) ((JsonRuntimeValue) converted.getFirst()).value();
    assertEquals(original, roundTrip);
    assertNotSame(original, roundTrip);

    var got = stack(new JsonRuntimeValue(original), new IntegerValue(BigInteger.ONE));
    execute("JSON配列から取り出す", got, budget);
    assertEquals(new JsonInteger(BigInteger.TWO), ((JsonRuntimeValue) got.getFirst()).value());

    var sliced =
        stack(
            new JsonRuntimeValue(original),
            new IntegerValue(BigInteger.ZERO),
            new IntegerValue(BigInteger.ONE));
    execute("JSON配列の一部を取り出す", sliced, budget);
    assertEquals(
        new JsonArray(List.of(new JsonInteger(BigInteger.ONE))),
        ((JsonRuntimeValue) sliced.getFirst()).value());

    var replaced =
        stack(
            new JsonRuntimeValue(original),
            new IntegerValue(BigInteger.ZERO),
            new JsonRuntimeValue(JsonNull.INSTANCE));
    execute("JSON配列の要素を置き換える", replaced, budget);
    assertEquals(
        new JsonArray(List.of(JsonNull.INSTANCE, new JsonInteger(BigInteger.TWO))),
        ((JsonRuntimeValue) replaced.getFirst()).value());

    var appended =
        stack(new JsonRuntimeValue(original), new JsonRuntimeValue(new JsonBoolean(true)));
    execute("JSON配列の末尾へ追加する", appended, budget);
    assertEquals(
        new JsonArray(
            List.of(
                new JsonInteger(BigInteger.ONE),
                new JsonInteger(BigInteger.TWO),
                new JsonBoolean(true))),
        ((JsonRuntimeValue) appended.getFirst()).value());
    assertEquals(
        List.of(new JsonInteger(BigInteger.ONE), new JsonInteger(BigInteger.TWO)),
        original.elements());
  }

  @Test
  void inspectsAndUpdatesObjectsWhileDistinguishingNullFromMissing() throws Exception {
    JsonObject original =
        new JsonObject(
            List.of(
                new JsonMember("nullKey", JsonNull.INSTANCE),
                new JsonMember("value", new JsonString("old"))));
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    var contains = stack(new JsonRuntimeValue(original), new StringValue("nullKey"));
    execute("JSONオブジェクトにキーがある", contains, budget);
    assertEquals(new JsonRuntimeValue(original), contains.getFirst());
    assertEquals(new BooleanValue(true), contains.getLast());

    var required = stack(new JsonRuntimeValue(original), new StringValue("nullKey"));
    execute("JSONオブジェクトから必須値を取り出す", required, budget);
    assertEquals(new JsonRuntimeValue(JsonNull.INSTANCE), required.getFirst());

    var keys = stack(new JsonRuntimeValue(original));
    execute("JSONオブジェクトのキー一覧", keys, budget);
    assertEquals(
        List.of(new StringValue("nullKey"), new StringValue("value")),
        ((ArrayValue) keys.getFirst()).elements());

    var updated =
        stack(
            new JsonRuntimeValue(original),
            new StringValue("value"),
            new JsonRuntimeValue(new JsonString("new")));
    execute("JSONオブジェクトに設定する", updated, budget);
    JsonObject updatedObject = (JsonObject) ((JsonRuntimeValue) updated.getFirst()).value();
    assertEquals(List.of("nullKey", "value"), updatedObject.keys());
    assertEquals(new JsonString("new"), updatedObject.find("value").orElseThrow());

    var added =
        stack(
            new JsonRuntimeValue(original),
            new StringValue("last"),
            new JsonRuntimeValue(new JsonDecimal(new BigDecimal("1.0"))));
    execute("JSONオブジェクトに設定する", added, budget);
    assertEquals(
        List.of("nullKey", "value", "last"),
        ((JsonObject) ((JsonRuntimeValue) added.getFirst()).value()).keys());

    var deleted = stack(new JsonRuntimeValue(original), new StringValue("missing"));
    execute("JSONオブジェクトから削除する", deleted, budget);
    assertEquals(original, ((JsonRuntimeValue) deleted.getFirst()).value());
    assertNotSame(original, ((JsonRuntimeValue) deleted.getFirst()).value());
    assertEquals(new JsonString("old"), original.find("value").orElseThrow());
  }

  @Test
  void resolvesRfc6901PointersAndBuildsObjectsInOneOperation() throws Exception {
    JsonObject nested =
        new JsonObject(
            List.of(
                new JsonMember(
                    "a/b",
                    new JsonArray(
                        List.of(
                            new JsonObject(
                                List.of(new JsonMember("~key", new JsonString("found")))))))));
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    var found = stack(new JsonRuntimeValue(nested), new StringValue("/a~1b/0/~0key"));
    execute("JSONをポインターで任意参照する", found, budget);
    OptionalValue present = (OptionalValue) found.getFirst();
    assertTrue(present.isPresent());
    assertEquals(new JsonRuntimeValue(new JsonString("found")), present.value().orElseThrow());

    var root = stack(new JsonRuntimeValue(nested), new StringValue(""));
    execute("JSONをポインターで任意参照する", root, budget);
    assertEquals(
        new JsonRuntimeValue(nested), ((OptionalValue) root.getFirst()).value().orElseThrow());

    var missing = stack(new JsonRuntimeValue(nested), new StringValue("/a~1b/1"));
    execute("JSONをポインターで任意参照する", missing, budget);
    assertFalse(((OptionalValue) missing.getFirst()).isPresent());

    var invalid = stack(new JsonRuntimeValue(nested), new StringValue("/~2"));
    List<RuntimeValue> invalidBefore = List.copyOf(invalid);
    RuntimeFailure pointerFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONをポインターで任意参照する", invalid, budget));
    assertEquals(DiagnosticCode.E_JSON_POINTER_SYNTAX, pointerFailure.diagnostic().code());
    assertEquals("invalidEscape", pointerFailure.diagnostic().fields().get("reason"));
    assertEquals("1", pointerFailure.diagnostic().fields().get("offset"));
    assertEquals(invalidBefore, invalid);

    var keys =
        new ArrayValue(ScalarType.STRING, List.of(new StringValue("id"), new StringValue("name")));
    var values =
        new ArrayValue(
            ScalarType.JSON,
            List.of(
                new JsonRuntimeValue(new JsonInteger(BigInteger.ONE)),
                new JsonRuntimeValue(new JsonString("A"))));
    var built = stack(keys, values);
    execute("JSONオブジェクトを構築する", built, budget);
    JsonObject object = (JsonObject) ((JsonRuntimeValue) built.getFirst()).value();
    assertEquals(List.of("id", "name"), object.keys());
    assertEquals(new JsonInteger(BigInteger.ONE), object.find("id").orElseThrow());

    var mismatch =
        stack(
            keys,
            new ArrayValue(ScalarType.JSON, List.of(new JsonRuntimeValue(JsonNull.INSTANCE))));
    List<RuntimeValue> mismatchBefore = List.copyOf(mismatch);
    RuntimeFailure mismatchFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONオブジェクトを構築する", mismatch, budget));
    assertEquals(
        DiagnosticCode.E_JSON_OBJECT_BUILD_LENGTH_MISMATCH, mismatchFailure.diagnostic().code());
    assertEquals(mismatchBefore, mismatch);
  }

  @Test
  void keepsPointerAbsenceNullAndSyntaxFailureDistinct() throws Exception {
    JsonValue root =
        new JsonObject(
            List.of(
                new JsonMember("", JsonNull.INSTANCE),
                new JsonMember("array", new JsonArray(List.of(JsonNull.INSTANCE)))));
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    var nullMember = stack(new JsonRuntimeValue(root), new StringValue("/"));
    execute("JSONをポインターで任意参照する", nullMember, budget);
    OptionalValue present = (OptionalValue) nullMember.getFirst();
    assertTrue(present.isPresent());
    assertEquals(new JsonRuntimeValue(JsonNull.INSTANCE), present.value().orElseThrow());

    for (String pointer :
        List.of(
            "/array/-", "/array/00", "/array/1", "/array/x", "/array/999999999999999999999999")) {
      var absent = stack(new JsonRuntimeValue(root), new StringValue(pointer));
      execute("JSONをポインターで任意参照する", absent, budget);
      assertFalse(((OptionalValue) absent.getFirst()).isPresent(), pointer);
    }

    var invalidAfterMissing = stack(new JsonRuntimeValue(root), new StringValue("/missing/😀/~2"));
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> execute("JSONをポインターで任意参照する", invalidAfterMissing, budget));
    assertEquals(DiagnosticCode.E_JSON_POINTER_SYNTAX, failure.diagnostic().code());
    assertEquals("invalidEscape", failure.diagnostic().fields().get("reason"));
    assertEquals("11", failure.diagnostic().fields().get("offset"));
  }

  @Test
  void buildsEmptyObjectsAndRejectsDuplicateKeysAtomically() throws Exception {
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var empty =
        stack(
            new ArrayValue(ScalarType.STRING, List.of()),
            new ArrayValue(ScalarType.JSON, List.of()));
    execute("JSONオブジェクトを構築する", empty, budget);
    assertEquals(new JsonObject(List.of()), ((JsonRuntimeValue) empty.getFirst()).value());

    var duplicate =
        stack(
            new ArrayValue(
                ScalarType.STRING, List.of(new StringValue("same"), new StringValue("same"))),
            new ArrayValue(
                ScalarType.JSON,
                List.of(
                    new JsonRuntimeValue(JsonNull.INSTANCE),
                    new JsonRuntimeValue(new JsonBoolean(true)))));
    List<RuntimeValue> before = List.copyOf(duplicate);
    long workBefore = budget.jsonWorkUnits();
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONオブジェクトを構築する", duplicate, budget));
    assertEquals(DiagnosticCode.E_JSON_DUPLICATE_KEY, failure.diagnostic().code());
    assertEquals(before, duplicate);
    assertEquals(workBefore, budget.jsonWorkUnits());
  }

  @Test
  void reportsKindKeyIndexAndSyntaxFailuresWithoutChangingInputs() throws Exception {
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var wrongKind = stack(new JsonRuntimeValue(new JsonString("x")));
    List<RuntimeValue> wrongKindBefore = List.copyOf(wrongKind);
    RuntimeFailure kindFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSON配列の長さ", wrongKind, budget));
    assertEquals(DiagnosticCode.E_JSON_KIND_MISMATCH, kindFailure.diagnostic().code());
    assertEquals("array", kindFailure.diagnostic().fields().get("expectedKind"));
    assertEquals("string", kindFailure.diagnostic().fields().get("actualKind"));
    assertEquals(wrongKindBefore, wrongKind);

    JsonArray array = new JsonArray(List.of(JsonNull.INSTANCE));
    var badIndex = stack(new JsonRuntimeValue(array), new IntegerValue(BigInteger.ONE));
    List<RuntimeValue> badIndexBefore = List.copyOf(badIndex);
    RuntimeFailure indexFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSON配列から取り出す", badIndex, budget));
    assertEquals(DiagnosticCode.E_JSON_INDEX_OUT_OF_BOUNDS, indexFailure.diagnostic().code());
    assertEquals(badIndexBefore, badIndex);

    JsonObject object = new JsonObject(List.of());
    var missing = stack(new JsonRuntimeValue(object), new StringValue("x"));
    List<RuntimeValue> missingBefore = List.copyOf(missing);
    RuntimeFailure keyFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONオブジェクトから必須値を取り出す", missing, budget));
    assertEquals(DiagnosticCode.E_JSON_KEY_NOT_FOUND, keyFailure.diagnostic().code());
    assertEquals(missingBefore, missing);

    var invalid = stack(new StringValue("{]"));
    List<RuntimeValue> invalidBefore = List.copyOf(invalid);
    long workBefore = budget.jsonWorkUnits();
    RuntimeFailure syntaxFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONを解析する", invalid, budget));
    assertEquals(DiagnosticCode.E_JSON_SYNTAX, syntaxFailure.diagnostic().code());
    assertEquals("expectedObjectKey", syntaxFailure.diagnostic().fields().get("reason"));
    assertEquals(invalidBefore, invalid);
    assertEquals(workBefore + 2, budget.jsonWorkUnits());
  }

  @Test
  void keepsConstructionAndWorkReservationsAtomicAtBothLimits() {
    var constructionLimit =
        new ExecutionBudget(
            SOURCE_PATH,
            () -> 0L,
            0,
            0,
            0,
            0,
            0,
            JsonLimits.CONSTRUCTION_UNITS,
            JsonLimits.WORK_UNITS - 1);
    var stack = stack(new StringValue("x"));

    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> execute("文字列をJSONに変換する", stack, constructionLimit));

    assertEquals(DiagnosticCode.E_JSON_CONSTRUCTION_LIMIT, failure.diagnostic().code());
    assertEquals(JsonLimits.CONSTRUCTION_UNITS, constructionLimit.jsonConstructionUnits());
    assertEquals(JsonLimits.WORK_UNITS - 1, constructionLimit.jsonWorkUnits());
    assertEquals(List.of(new StringValue("x")), stack);
    assertFalse(stack.getFirst() instanceof JsonRuntimeValue);
  }

  @Test
  void constructsAllEmptyValuesAndChecksEveryJsonKindWithoutConsumingTheInput() throws Exception {
    var constructionBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var constants = stack();
    execute("JSONヌル", constants, constructionBudget);
    execute("空のJSON配列", constants, constructionBudget);
    execute("空のJSONオブジェクト", constants, constructionBudget);
    assertEquals(
        List.of(
            new JsonRuntimeValue(JsonNull.INSTANCE),
            new JsonRuntimeValue(new JsonArray(List.of())),
            new JsonRuntimeValue(new JsonObject(List.of()))),
        constants);
    assertEquals(3, constructionBudget.jsonConstructionUnits());

    Map<String, JsonValue> predicates =
        Map.of(
            "JSONヌルである", JsonNull.INSTANCE,
            "JSON真偽である", new JsonBoolean(true),
            "JSON整数である", new JsonInteger(BigInteger.ONE),
            "JSON小数である", new JsonDecimal(new BigDecimal("1.0")),
            "JSON文字列である", new JsonString("x"),
            "JSON配列である", new JsonArray(List.of()),
            "JSONオブジェクトである", new JsonObject(List.of()));
    var predicateBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    for (Map.Entry<String, JsonValue> entry : predicates.entrySet()) {
      JsonRuntimeValue original = new JsonRuntimeValue(entry.getValue());
      var values = stack(original);
      execute(entry.getKey(), values, predicateBudget);
      assertEquals(List.of(original, new BooleanValue(true)), values, entry.getKey());
    }
    var falsePredicate = stack(new JsonRuntimeValue(JsonNull.INSTANCE));
    execute("JSON配列である", falsePredicate, predicateBudget);
    assertEquals(new BooleanValue(false), falsePredicate.getLast());
    assertEquals(8, predicateBudget.jsonWorkUnits());

    var arrayLength = stack(new JsonRuntimeValue(new JsonArray(List.of(JsonNull.INSTANCE))));
    execute("JSON配列の長さ", arrayLength, predicateBudget);
    assertEquals(new IntegerValue(BigInteger.ONE), arrayLength.getFirst());
    var objectSize =
        stack(
            new JsonRuntimeValue(new JsonObject(List.of(new JsonMember("x", JsonNull.INSTANCE)))));
    execute("JSONオブジェクトの要素数", objectSize, predicateBudget);
    assertEquals(new IntegerValue(BigInteger.ONE), objectSize.getFirst());
  }

  @Test
  void preflightsSerializationAndDisplayOutputBeforeChangingStackOrBudget() {
    String contents = "\0".repeat((int) (JsonLimits.OUTPUT_UTF8_BYTES / 6 + 1));
    JsonRuntimeValue value = new JsonRuntimeValue(new JsonString(contents));
    var serializeBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var serializeStack = stack(value);

    RuntimeFailure serializeFailure =
        assertThrows(
            RuntimeFailure.class, () -> execute("JSONを文字列に変換する", serializeStack, serializeBudget));
    assertEquals(DiagnosticCode.E_JSON_OUTPUT_LIMIT, serializeFailure.diagnostic().code());
    assertEquals(List.of(value), serializeStack);
    assertEquals(0, serializeBudget.jsonWorkUnits());

    var output = new MemoryOutputSink();
    var displayBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var displayStack = stack(value);
    var executor =
        new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, output), displayBudget);
    RuntimeFailure displayFailure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executor.execute(
                    BuiltinDictionary.find("一行表示する").orElseThrow(), displayStack, SPAN));
    assertEquals(DiagnosticCode.E_JSON_OUTPUT_LIMIT, displayFailure.diagnostic().code());
    assertEquals(List.of(value), displayStack);
    assertEquals(0, displayBudget.jsonWorkUnits());
    assertEquals("", output.utf8Text());
  }

  @Test
  void displaysJsonArraysWithBothArrayAndJsonWorkAccounting() throws Exception {
    var output = new MemoryOutputSink();
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var values =
        stack(
            new ArrayValue(
                ScalarType.JSON,
                List.of(
                    new JsonRuntimeValue(JsonNull.INSTANCE),
                    new JsonRuntimeValue(new JsonString("x")))));
    var executor = new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, output), budget);

    executor.execute(BuiltinDictionary.find("一行表示する").orElseThrow(), values, SPAN);

    assertEquals("【null、\"x\"】\n", output.utf8Text());
    assertTrue(values.isEmpty());
    assertEquals(2, budget.arrayElementOperationUnits());
    assertEquals(9, budget.jsonWorkUnits());
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()), budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private static List<JsonValue> unwrap(ArrayValue array) {
    return array.elements().stream()
        .map(JsonRuntimeValue.class::cast)
        .map(JsonRuntimeValue::value)
        .toList();
  }
}
