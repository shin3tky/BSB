package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonLimits;
import jp.bsb.json.JsonMember;
import jp.bsb.json.JsonNull;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class OptionalOptionalRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsWrapPredicateUnwrapDropDisplayEqualityAndStorage() {
    String source =
        "保存は 変数 42 任意にする。\n\n"
            + "メインとは （--）\n"
            + "    保存 任意に値がある\n"
            + "    ならば\n"
            + "        任意から値を取り出す 一行表示する\n"
            + "    さもなければ\n"
            + "        任意を捨てる\n"
            + "    つぎに\n"
            + "    7 任意にする 一行表示する\n"
            + "    1 任意にする 1 任意にする 等しい 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "optional.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("42\nある（7）\nはい\n", output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
    assertEquals(
        OptionalValue.present(new IntegerValue(BigInteger.valueOf(42))),
        result.finalGlobalValues().getFirst().orElseThrow());
  }

  @Test
  void distinguishesJsonValueNullAndMissingWithEqualLookupWork() throws Exception {
    JsonObject object =
        new JsonObject(
            List.of(
                new JsonMember("value", new JsonString("x")),
                new JsonMember("null", JsonNull.INSTANCE)));
    var budget = new ExecutionBudget("optional-json.bsb", () -> 0L);

    OptionalValue value = lookup(object, "value", budget);
    OptionalValue jsonNull = lookup(object, "null", budget);
    OptionalValue missing = lookup(object, "missing", budget);

    assertTrue(value.isPresent());
    assertEquals(new JsonRuntimeValue(JsonNull.INSTANCE), jsonNull.value().orElseThrow());
    assertFalse(missing.isPresent());
    assertEquals(ValueType.optionalOf(ValueType.JSON), missing.type());
    assertEquals(3, budget.jsonWorkUnits());
  }

  @Test
  void absentUnwrapAndWrongJsonKindLeaveStackAndBudgetsUnchanged() throws Exception {
    var budget = new ExecutionBudget("optional-failure.bsb", () -> 0L);
    var absent = new ArrayList<RuntimeValue>(List.of(OptionalValue.absent(ValueType.JSON)));
    List<RuntimeValue> before = List.copyOf(absent);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("任意から値を取り出す", absent, budget));
    assertEquals(DiagnosticCode.E_OPTIONAL_VALUE_ABSENT, failure.diagnostic().code());
    assertEquals("任意<JSON>", failure.diagnostic().fields().get("optionalType"));
    assertEquals(before, absent);
    assertEquals(0, budget.jsonWorkUnits());

    var wrong =
        new ArrayList<RuntimeValue>(
            List.of(new JsonRuntimeValue(new JsonString("x")), new StringValue("key")));
    List<RuntimeValue> wrongBefore = List.copyOf(wrong);
    RuntimeFailure kindFailure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONオブジェクトから任意値を取り出す", wrong, budget));
    assertEquals(DiagnosticCode.E_JSON_KIND_MISMATCH, kindFailure.diagnostic().code());
    assertEquals(wrongBefore, wrong);
    assertEquals(0, budget.jsonWorkUnits());
  }

  @Test
  void inheritsJsonEqualityAndDisplayWorkButStopsAtAbsentState() throws Exception {
    var budget = new ExecutionBudget("optional-work.bsb", () -> 0L);
    OptionalValue json = OptionalValue.present(new JsonRuntimeValue(JsonNull.INSTANCE));
    var equal = new ArrayList<RuntimeValue>(List.of(json, json));
    execute("等しい", equal, budget);
    assertEquals(List.of(new BooleanValue(true)), equal);
    assertEquals(2, budget.jsonWorkUnits());

    OptionalValue absent = OptionalValue.absent(ValueType.JSON);
    var absentEqual = new ArrayList<RuntimeValue>(List.of(absent, absent));
    execute("等しい", absentEqual, budget);
    assertEquals(List.of(new BooleanValue(true)), absentEqual);
    assertEquals(2, budget.jsonWorkUnits());

    var output = new MemoryOutputSink();
    var displayStack = new ArrayList<RuntimeValue>(List.of(json));
    new BuiltinExecutor("optional-work.bsb", new BoundedOutput("optional-work.bsb", output), budget)
        .execute(BuiltinDictionary.find("一行表示する").orElseThrow(), displayStack, SPAN);
    assertEquals("ある（null）\n", output.utf8Text());
    assertEquals(7, budget.jsonWorkUnits());
  }

  @Test
  void optionalLookupWorkLimitIsAtomicForPresentAndMissingKeys() {
    var budget =
        new ExecutionBudget(
            "optional-limit.bsb", () -> 0L, 0, 0, 0, 0, 0, 0, JsonLimits.WORK_UNITS);
    JsonObject object = new JsonObject(List.of());
    var stack =
        new ArrayList<RuntimeValue>(
            List.of(new JsonRuntimeValue(object), new StringValue("missing")));
    List<RuntimeValue> before = List.copyOf(stack);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONオブジェクトから任意値を取り出す", stack, budget));

    assertEquals(DiagnosticCode.E_JSON_WORK_LIMIT, failure.diagnostic().code());
    assertEquals("objectGetOptional", failure.diagnostic().fields().get("operation"));
    assertEquals(before, stack);
    assertEquals(JsonLimits.WORK_UNITS, budget.jsonWorkUnits());
  }

  private static OptionalValue lookup(JsonObject object, String key, ExecutionBudget budget)
      throws RuntimeFailure {
    var stack =
        new ArrayList<RuntimeValue>(List.of(new JsonRuntimeValue(object), new StringValue(key)));
    execute("JSONオブジェクトから任意値を取り出す", stack, budget);
    return (OptionalValue) stack.getFirst();
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(
            "optional-runtime.bsb",
            new BoundedOutput("optional-runtime.bsb", new MemoryOutputSink()),
            budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }
}
