package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.conformance.RecoverableJsonConformanceData;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonLimits;
import jp.bsb.json.JsonNull;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class RecoverableJsonRecoverableJsonRuntimeTest {
  private static final String SOURCE_PATH = "RecoverableJson-runtime.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final ResultType RESULT_TYPE =
      ValueType.resultOf(ValueType.JSON, ValueType.JSON_PARSE_FAILURE);

  @Test
  void mapsExactlyTheThirteenRecoverableKindsWithUtf8ScalarAndCrlfPositions() throws Exception {
    List<FailureCase> cases =
        List.of(
            new FailureCase("", "emptyInput", 0, 1, 1),
            new FailureCase("x", "unexpectedToken", 0, 1, 1),
            new FailureCase("null x", "trailingContent", 5, 1, 6),
            new FailureCase("{", "expectedObjectKey", 1, 1, 2),
            new FailureCase("{\"x\"}", "expectedColon", 4, 1, 5),
            new FailureCase("[1 2]", "expectedCommaOrEnd", 3, 1, 4),
            new FailureCase("\"x", "unterminatedString", 2, 1, 3),
            new FailureCase("\"x\n\"", "unescapedControl", 2, 1, 3),
            new FailureCase("\"\\q\"", "invalidEscape", 1, 1, 2),
            new FailureCase("\"\\u12G4\"", "invalidUnicodeEscape", 1, 1, 2),
            new FailureCase("\"\\uD800\"", "isolatedSurrogate", 1, 1, 2),
            new FailureCase("01", "invalidNumber", 1, 1, 2),
            new FailureCase("{\"x\":1,\"x\":2}", "duplicateKey", 7, 1, 8),
            new FailureCase("[\"😀\",x]", "unexpectedToken", 8, 1, 6),
            new FailureCase("[\r\nx]", "unexpectedToken", 3, 2, 1));

    for (FailureCase expected : cases) {
      var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
      var stack = stack(new StringValue(expected.input()));
      execute("JSONを解析して結果を返す", stack, budget);

      ResultValue result = (ResultValue) stack.getFirst();
      assertTrue(result.isFailure(), expected.kind());
      assertEquals(RESULT_TYPE, result.type(), expected.kind());
      JsonParseFailureValue failure = (JsonParseFailureValue) result.value();
      assertEquals(expected.kind(), failure.kind());
      assertEquals(expected.offset(), failure.utf8Offset(), expected.kind());
      assertEquals(expected.line(), failure.line(), expected.kind());
      assertEquals(expected.column(), failure.column(), expected.kind());
      assertEquals(
          expected.input().getBytes(StandardCharsets.UTF_8).length, budget.jsonWorkUnits());
      assertEquals(0, budget.jsonConstructionUnits());
    }
  }

  @Test
  void everyCentralCaseMatchesItsIndependentResultPayloadPositionBudgetAndAtomicity()
      throws Exception {
    for (var expected : RecoverableJsonConformanceData.loadCases()) {
      String input = new String(expected.input(), StandardCharsets.UTF_8);
      var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
      StringValue original = new StringValue(input);
      var stack = stack(original);

      if (expected.outcome().equals("diagnostic")) {
        RuntimeFailure failure =
            assertThrows(RuntimeFailure.class, () -> execute("JSONを解析する", stack, budget));
        assertEquals(expected.diagnostic(), failure.diagnostic().code().name(), expected.variant());
        assertSame(original, stack.getFirst(), expected.variant());
      } else {
        execute("JSONを解析して結果を返す", stack, budget);
        ResultValue result = (ResultValue) stack.getFirst();
        if (expected.outcome().equals("success")) {
          assertTrue(result.isSuccess(), expected.variant());
          byte[] serialized =
              JsonCodec.serialize(((JsonRuntimeValue) result.value()).value())
                  .getBytes(StandardCharsets.UTF_8);
          assertArrayEquals(expected.payload(), serialized, expected.variant());
        } else {
          assertTrue(result.isFailure(), expected.variant());
          JsonParseFailureValue failure = (JsonParseFailureValue) result.value();
          assertEquals(expected.failureKind(), failure.kind(), expected.variant());
          assertEquals(expected.utf8Offset().longValue(), failure.utf8Offset(), expected.variant());
          assertEquals(expected.line().intValue(), failure.line(), expected.variant());
          assertEquals(expected.column().intValue(), failure.column(), expected.variant());
        }
      }
      assertEquals(expected.jsonWorkDelta(), budget.jsonWorkUnits(), expected.variant());
      assertEquals(
          expected.jsonConstructionDelta(), budget.jsonConstructionUnits(), expected.variant());
    }
  }

  @Test
  void returnsSuccessAndAccessorsReplaceOneValueWithoutAdditionalJsonBudget() throws Exception {
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var stack = stack(new StringValue("null"));

    execute("JSONを解析して結果を返す", stack, budget);

    ResultValue result = (ResultValue) stack.getFirst();
    assertTrue(result.isSuccess());
    assertEquals(new JsonRuntimeValue(JsonNull.INSTANCE), result.value());
    assertEquals(4, budget.jsonWorkUnits());
    assertEquals(1, budget.jsonConstructionUnits());

    JsonParseFailureValue failure = new JsonParseFailureValue("unexpectedToken", 8, 2, 3);
    long work = budget.jsonWorkUnits();
    long construction = budget.jsonConstructionUnits();
    assertAccessor("JSON解析失敗の種類を取り出す", failure, new StringValue("unexpectedToken"), budget);
    assertAccessor("JSON解析失敗のバイト位置を取り出す", failure, new IntegerValue(BigInteger.valueOf(8)), budget);
    assertAccessor("JSON解析失敗の行を取り出す", failure, new IntegerValue(BigInteger.valueOf(2)), budget);
    assertAccessor("JSON解析失敗の列を取り出す", failure, new IntegerValue(BigInteger.valueOf(3)), budget);
    assertEquals(work, budget.jsonWorkUnits());
    assertEquals(construction, budget.jsonConstructionUnits());
  }

  @Test
  void leavesAllSixParserLimitClassesAndBothCumulativeBudgetsAsDiagnostics() {
    assertUncapturable(
        "9".repeat(JsonLimits.INPUT_NUMBER_DIGITS + 1), DiagnosticCode.E_JSON_NUMBER_LIMIT);
    assertUncapturable(
        "[".repeat(JsonLimits.DEPTH + 1) + "0" + "]".repeat(JsonLimits.DEPTH + 1),
        DiagnosticCode.E_JSON_DEPTH_LIMIT);
    assertUncapturable(overNodeDocument(), DiagnosticCode.E_JSON_NODE_LIMIT);
    assertUncapturable(overArrayDocument(), DiagnosticCode.E_JSON_ARRAY_LENGTH_LIMIT);
    assertUncapturable(overObjectDocument(), DiagnosticCode.E_JSON_OBJECT_MEMBER_LIMIT);
    assertUncapturable(
        " ".repeat((int) JsonLimits.INPUT_UTF8_BYTES) + "x", DiagnosticCode.E_STRING_UTF8_LIMIT);

    var fullWork =
        new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, 0, 0, 0, 0, JsonLimits.WORK_UNITS);
    var workStack = stack(new StringValue("secret-marker"));
    RuntimeFailure work =
        assertThrows(RuntimeFailure.class, () -> execute("JSONを解析して結果を返す", workStack, fullWork));
    assertEquals(DiagnosticCode.E_JSON_WORK_LIMIT, work.diagnostic().code());
    assertEquals("JSONを解析して結果を返す", work.diagnostic().fields().get("word"));
    assertEquals(List.of(new StringValue("secret-marker")), workStack);
    assertEquals(JsonLimits.WORK_UNITS, fullWork.jsonWorkUnits());

    var fullConstruction =
        new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, 0, 0, 0, JsonLimits.CONSTRUCTION_UNITS, 0);
    var constructionStack = stack(new StringValue("null"));
    RuntimeFailure construction =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("JSONを解析して結果を返す", constructionStack, fullConstruction));
    assertEquals(DiagnosticCode.E_JSON_CONSTRUCTION_LIMIT, construction.diagnostic().code());
    assertEquals(List.of(new StringValue("null")), constructionStack);
    assertEquals(4, fullConstruction.jsonWorkUnits());
    assertEquals(JsonLimits.CONSTRUCTION_UNITS, fullConstruction.jsonConstructionUnits());
  }

  @Test
  void continuesAfterRecoverableFailureAndNeverPublishesFailureOrDiagnosticInput() {
    String source =
        "メインとは （--）\n"
            + "    「secret-marker」を JSONを解析して結果を返す 結果が失敗である\n"
            + "    ならば\n"
            + "        結果から失敗値を取り出す JSON解析失敗の種類を取り出す 一行表示する\n"
            + "    さもなければ\n"
            + "        結果から成功値を取り出す JSONを文字列に変換する 一行表示する\n"
            + "    つぎに\n"
            + "    「continued」を 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("unexpectedToken\ncontinued\n", output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
    String trace = TraceTsvFormatter.formatRecoverableJson(events);
    assertFalse(trace.contains("secret-marker"));
    assertTrue(trace.contains("結果<JSON,JSON解析失敗>:<redacted>"));
    assertTrue(trace.contains("JSON解析失敗:<redacted>"));

    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_JSON_WORK_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                SOURCE_PATH,
                SPAN)
            .field("word", "JSONを解析して結果を返す")
            .build();
    assertEquals(
        "[文字列:visible, 文字列:<redacted>]",
        Interpreter.safeDataStack(
            diagnostic, List.of(new StringValue("visible"), new StringValue("secret-marker"))));
    assertFalse(
        Interpreter.safeDataStack(diagnostic, List.of(new StringValue("secret-marker")))
            .contains("secret"));
  }

  @Test
  void appliesInstructionTimeAndFullStackBoundariesBeforeOrWithoutGrowingTheParserInput()
      throws Exception {
    var exactInstructions =
        new ExecutionBudget(SOURCE_PATH, () -> 0L, RuntimeLimits.EXECUTED_INSTRUCTIONS - 1, 0L);
    var exactInstructionStack = stack(new StringValue("x"));
    exactInstructions.beforeInstruction(SPAN);
    execute("JSONを解析して結果を返す", exactInstructionStack, exactInstructions);
    assertEquals(RuntimeLimits.EXECUTED_INSTRUCTIONS, exactInstructions.executed());
    assertTrue(((ResultValue) exactInstructionStack.getFirst()).isFailure());

    var overInstructions =
        new ExecutionBudget(SOURCE_PATH, () -> 0L, RuntimeLimits.EXECUTED_INSTRUCTIONS, 0L);
    var overInstructionStack = stack(new StringValue("x"));
    RuntimeFailure instructionFailure =
        assertThrows(RuntimeFailure.class, () -> overInstructions.beforeInstruction(SPAN));
    assertEquals(DiagnosticCode.E_INSTRUCTION_LIMIT, instructionFailure.diagnostic().code());
    assertEquals(List.of(new StringValue("x")), overInstructionStack);
    assertEquals(0, overInstructions.jsonWorkUnits());

    var now = new AtomicLong();
    var exactTime = new ExecutionBudget(SOURCE_PATH, now::get);
    now.set(RuntimeLimits.ELAPSED_NANOS);
    exactTime.beforeInstruction(SPAN);
    var exactTimeStack = stack(new StringValue("x"));
    execute("JSONを解析して結果を返す", exactTimeStack, exactTime);
    assertTrue(((ResultValue) exactTimeStack.getFirst()).isFailure());

    var overNow = new AtomicLong();
    var overTime = new ExecutionBudget(SOURCE_PATH, overNow::get);
    overNow.set(RuntimeLimits.ELAPSED_NANOS + 1);
    RuntimeFailure timeFailure =
        assertThrows(RuntimeFailure.class, () -> overTime.beforeInstruction(SPAN));
    assertEquals(DiagnosticCode.E_EXECUTION_TIMEOUT, timeFailure.diagnostic().code());
    assertEquals(0, overTime.jsonWorkUnits());

    var fullStack =
        new ArrayList<RuntimeValue>(
            Collections.nCopies(
                RuntimeLimits.DATA_STACK_VALUES - 1, new IntegerValue(BigInteger.ZERO)));
    fullStack.add(new StringValue("x"));
    execute("JSONを解析して結果を返す", fullStack, new ExecutionBudget(SOURCE_PATH, () -> 0L));
    assertEquals(RuntimeLimits.DATA_STACK_VALUES, fullStack.size());
    assertTrue(((ResultValue) fullStack.getLast()).isFailure());
  }

  private static void assertAccessor(
      String name, JsonParseFailureValue input, RuntimeValue expected, ExecutionBudget budget)
      throws RuntimeFailure {
    var stack = stack(input);
    execute(name, stack, budget);
    assertEquals(List.of(expected), stack);
  }

  private static void assertUncapturable(String input, DiagnosticCode code) {
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    StringValue original = new StringValue(input);
    var stack = stack(original);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("JSONを解析して結果を返す", stack, budget));
    assertEquals(code, failure.diagnostic().code());
    assertSame(original, stack.getFirst());
    assertFalse(failure.diagnostic().toString().contains(input));
  }

  private static String overArrayDocument() {
    return "[" + "0,".repeat(JsonLimits.ARRAY_LENGTH) + "0]";
  }

  private static String overObjectDocument() {
    var result = new StringBuilder("{");
    for (int index = 0; index <= JsonLimits.OBJECT_MEMBERS; index++) {
      if (index > 0) {
        result.append(',');
      }
      result.append('"').append(index).append("\":0");
    }
    return result.append('}').toString();
  }

  private static String overNodeDocument() {
    var result = new StringBuilder("[");
    int remaining = (int) JsonLimits.VALUE_NODES;
    while (remaining > 0) {
      int width = Math.min(JsonLimits.ARRAY_LENGTH, remaining);
      if (result.length() > 1) {
        result.append(',');
      }
      result.append('[');
      for (int index = 0; index < width; index++) {
        if (index > 0) {
          result.append(',');
        }
        result.append('0');
      }
      result.append(']');
      remaining -= width;
    }
    return result.append(']').toString();
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()), budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private record FailureCase(String input, String kind, long offset, int line, int column) {}
}
