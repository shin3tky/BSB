package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.IrStackEffect;
import jp.bsb.json.JsonLimits;
import jp.bsb.json.JsonNull;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ResultRuntimeResourceConformanceTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final ResultType INTEGER_STRING =
      ValueType.resultOf(ValueType.INTEGER, ValueType.STRING);

  @Test
  void runtimeExpectationTableIsCompleteUniqueAndUsesEveryPlannedId() throws Exception {
    List<Row> rows = rows();
    assertEquals(
        rows.size(), rows.stream().map(row -> row.caseId + "/" + row.variant).distinct().count());
    assertEquals(
        Set.of(
            "RESULT-N003",
            "RESULT-N004",
            "RESULT-N005",
            "RESULT-N006",
            "RESULT-N007",
            "RESULT-N008",
            "RESULT-N009",
            "RESULT-N010",
            "RESULT-N011",
            "RESULT-N012",
            "RESULT-N013",
            "RESULT-N014",
            "RESULT-N015",
            "RESULT-N016",
            "RESULT-N018",
            "RESULT-N019",
            "RESULT-F022",
            "RESULT-F023",
            "RESULT-F036",
            "RESULT-R005",
            "RESULT-R006",
            "RESULT-R007",
            "RESULT-R008",
            "RESULT-R009",
            "RESULT-R010",
            "RESULT-R011",
            "RESULT-R012"),
        rows.stream().map(row -> row.caseId).collect(Collectors.toSet()));
    assertTrue(rows.stream().anyMatch(row -> row.state.equals("success")));
    assertTrue(rows.stream().anyMatch(row -> row.state.equals("failure")));
    assertTrue(rows.stream().anyMatch(row -> row.outcome.equals("diagnostic")));
  }

  @Test
  void resultR005PredicatesReachExactlyTheDataStackBoundaryAtomically() throws Exception {
    Row exact = row("RESULT-R005", "successPredicate-exact");
    Row over = row("RESULT-R005", "successPredicate-over");
    ResultValue result = ResultValue.success(INTEGER_STRING, new IntegerValue(BigInteger.ONE));
    var stack = filledStack(Integer.parseInt(exact.observed) - 1, result);
    execute("結果が成功である", stack, new ExecutionBudget("RESULT-R005.bsb", () -> 0L));
    assertEquals(Integer.parseInt(exact.limit), stack.size());
    assertEquals(new BooleanValue(true), stack.getLast());

    stack = filledStack(Integer.parseInt(over.limit), result);
    List<RuntimeValue> before = List.copyOf(stack);
    ArrayList<RuntimeValue> rejected = stack;
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("結果が成功である", rejected, new ExecutionBudget("RESULT-R005.bsb", () -> 0L)));
    assertEquals(DiagnosticCode.valueOf(over.code), failure.diagnostic().code());
    assertEquals(over.limit, failure.diagnostic().limit().orElseThrow());
    assertEquals(over.observed, failure.diagnostic().observed().orElseThrow());
    assertEquals(before, rejected);
  }

  @Test
  void resultR006AndResultR007AcceptExactResultBindingCountsAndRejectOneMore() throws Exception {
    assertBindingBoundary("RESULT-R006", false);
    assertBindingBoundary("RESULT-R007", true);
  }

  @Test
  void resultR008WrappingAddsNoArrayOrJsonConstructionWorkAndInstructionLimitStaysExisting()
      throws Exception {
    row("RESULT-R008", "wrapper-no-construction");
    var budget =
        new ExecutionBudget(
            "RESULT-R008.bsb",
            () -> 0L,
            RuntimeLimits.EXECUTED_INSTRUCTIONS - 1,
            0,
            ArrayLimits.MAX_CONSTRUCTION_UNITS,
            ArrayLimits.MAX_ELEMENT_OPERATION_UNITS,
            0,
            JsonLimits.CONSTRUCTION_UNITS,
            JsonLimits.WORK_UNITS);
    var stack = new ArrayList<RuntimeValue>(List.of(new JsonRuntimeValue(JsonNull.INSTANCE)));
    ResultType type = ValueType.resultOf(ValueType.JSON, ValueType.STRING);

    budget.beforeInstruction(SPAN);
    executeConstruction("成功にする", stack, type, budget);

    assertEquals(RuntimeLimits.EXECUTED_INSTRUCTIONS, budget.executed());
    assertEquals(ArrayLimits.MAX_CONSTRUCTION_UNITS, budget.arrayConstructionUnits());
    assertEquals(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, budget.arrayElementOperationUnits());
    assertEquals(JsonLimits.CONSTRUCTION_UNITS, budget.jsonConstructionUnits());
    assertEquals(JsonLimits.WORK_UNITS, budget.jsonWorkUnits());
    assertThrows(RuntimeFailure.class, () -> budget.beforeInstruction(SPAN));
  }

  @Test
  void resultR009WrapperBytesCountAndRejectedOutputIsAtomic() throws Exception {
    Row exact = row("RESULT-R009", "stdout-exact");
    Row over = row("RESULT-R009", "stdout-over");
    ResultValue displayed = ResultValue.success(INTEGER_STRING, new IntegerValue(BigInteger.ONE));
    byte[] expected = "成功（1）".getBytes(StandardCharsets.UTF_8);
    assertEquals(Integer.parseInt(exact.outputBytes), expected.length);

    var exactSink = new MemoryOutputSink();
    var exactOutput =
        new BoundedOutput(
            "RESULT-R009.bsb", exactSink, Long.parseLong(exact.limit) - expected.length);
    var exactStack = new ArrayList<RuntimeValue>(List.of(displayed));
    new BuiltinExecutor(
            "RESULT-R009.bsb", exactOutput, new ExecutionBudget("RESULT-R009.bsb", () -> 0L))
        .execute(BuiltinDictionary.find("表示する").orElseThrow(), exactStack, SPAN);
    assertEquals("成功（1）", exactSink.utf8Text());
    assertTrue(exactStack.isEmpty());

    var overSink = new MemoryOutputSink();
    var overOutput =
        new BoundedOutput(
            "RESULT-R009.bsb", overSink, Long.parseLong(over.limit) - expected.length + 1);
    var overStack = new ArrayList<RuntimeValue>(List.of(displayed));
    List<RuntimeValue> before = List.copyOf(overStack);
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                new BuiltinExecutor(
                        "RESULT-R009.bsb",
                        overOutput,
                        new ExecutionBudget("RESULT-R009.bsb", () -> 0L))
                    .execute(BuiltinDictionary.find("表示する").orElseThrow(), overStack, SPAN));
    assertEquals(DiagnosticCode.valueOf(over.code), failure.diagnostic().code());
    assertEquals(over.observed, failure.diagnostic().observed().orElseThrow());
    assertEquals(before, overStack);
    assertEquals("", overSink.utf8Text());
  }

  @Test
  void resultR010SelectedPayloadInheritsJsonAndArrayWorkWhileDifferentStatesCostZero()
      throws Exception {
    Row jsonOver = row("RESULT-R010", "json-over");
    Row arrayOver = row("RESULT-R010", "array-over");
    ResultType jsonType = ValueType.resultOf(ValueType.JSON, ValueType.JSON);
    ResultValue json = ResultValue.success(jsonType, new JsonRuntimeValue(JsonNull.INSTANCE));
    var exactBudget = budget(JsonLimits.WORK_UNITS - 2, 0);
    var exact = new ArrayList<RuntimeValue>(List.of(json, json));
    execute("等しい", exact, exactBudget);
    assertEquals(JsonLimits.WORK_UNITS, exactBudget.jsonWorkUnits());

    var overBudget = budget(JsonLimits.WORK_UNITS - 1, 0);
    var overStack = new ArrayList<RuntimeValue>(List.of(json, json));
    List<RuntimeValue> before = List.copyOf(overStack);
    RuntimeFailure jsonFailure =
        assertThrows(RuntimeFailure.class, () -> execute("等しい", overStack, overBudget));
    assertEquals(DiagnosticCode.valueOf(jsonOver.code), jsonFailure.diagnostic().code());
    assertEquals(jsonOver.observed, jsonFailure.diagnostic().observed().orElseThrow());
    assertEquals(before, overStack);
    assertEquals(JsonLimits.WORK_UNITS - 1, overBudget.jsonWorkUnits());

    var different =
        new ArrayList<RuntimeValue>(
            List.of(
                ResultValue.success(jsonType, new JsonRuntimeValue(JsonNull.INSTANCE)),
                ResultValue.failure(jsonType, new JsonRuntimeValue(JsonNull.INSTANCE))));
    execute("等しい", different, overBudget);
    assertEquals(List.of(new BooleanValue(false)), different);
    assertEquals(JsonLimits.WORK_UNITS - 1, overBudget.jsonWorkUnits());

    ResultType arrayType =
        ValueType.resultOf(ValueType.arrayOf(ScalarType.INTEGER), ValueType.BOOLEAN);
    ResultValue array =
        ResultValue.success(
            arrayType,
            new ArrayValue(ScalarType.INTEGER, List.of(new IntegerValue(BigInteger.ONE))));
    var arrayBudget = budget(0, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 1);
    var arrays = new ArrayList<RuntimeValue>(List.of(array, array));
    execute("等しい", arrays, arrayBudget);
    assertEquals(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, arrayBudget.arrayElementOperationUnits());

    var rejectedArrayBudget = budget(0, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS);
    var rejectedArrays = new ArrayList<RuntimeValue>(List.of(array, array));
    List<RuntimeValue> arrayBefore = List.copyOf(rejectedArrays);
    RuntimeFailure arrayFailure =
        assertThrows(
            RuntimeFailure.class, () -> execute("等しい", rejectedArrays, rejectedArrayBudget));
    assertEquals(DiagnosticCode.valueOf(arrayOver.code), arrayFailure.diagnostic().code());
    assertEquals(arrayOver.observed, arrayFailure.diagnostic().observed().orElseThrow());
    assertEquals(arrayBefore, rejectedArrays);
    assertEquals(
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, rejectedArrayBudget.arrayElementOperationUnits());
  }

  @Test
  void resultR011AndResultR012KeepFailuresAtomicAndTraceRedactionTransitive() throws Exception {
    row("RESULT-R011", "success-unwrap");
    row("RESULT-R012", "inactive-secret");
    ResultValue secret = ResultValue.failure(INTEGER_STRING, new StringValue("credential-secret"));
    var stack = new ArrayList<RuntimeValue>(List.of(secret));
    List<RuntimeValue> before = List.copyOf(stack);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("結果から成功値を取り出す", stack, budget(0, 0)));
    assertEquals(DiagnosticCode.E_RESULT_STATE_MISMATCH, failure.diagnostic().code());
    assertEquals(before, stack);

    String trace = TraceValueFormatter.format(secret, TraceValuePolicy.result());
    assertEquals("結果<整数,文字列>:<redacted>", trace);
    assertFalse(trace.contains("credential"));
    assertFalse(trace.contains("failure"));
  }

  private static void assertBindingBoundary(String caseId, boolean local) throws Exception {
    Row exact = row(caseId, "exact");
    Row over = row(caseId, "over");
    var checker = new SourceChecker();
    var accepted =
        checker.check(
            caseId + "-exact.bsb", bindingSource(Integer.parseInt(exact.observed), local));
    var rejected =
        checker.check(caseId + "-over.bsb", bindingSource(Integer.parseInt(over.observed), local));
    assertTrue(accepted.successful(), accepted.diagnostics().toString());
    assertEquals(DiagnosticCode.valueOf(over.code), rejected.diagnostics().getFirst().code());
    assertEquals(over.limit, rejected.diagnostics().getFirst().limit().orElseThrow());
    assertEquals(over.observed, rejected.diagnostics().getFirst().observed().orElseThrow());
  }

  private static byte[] bindingSource(int count, boolean local) {
    var source = new StringBuilder(count * 60 + 64);
    if (local) {
      source.append("メインとは （--）\n");
    }
    for (int index = 0; index < count; index++) {
      if (local) {
        source.append("    ");
      }
      source
          .append(local ? "局所" : "大域")
          .append(index)
          .append("は 定数 ")
          .append(index % 2 == 0 ? "0 成功にする<整数,文字列>" : "「x」 失敗にする<整数,文字列>")
          .append("。\n");
    }
    if (!local) {
      source.append("\nメインとは （--）\n");
    }
    return source.append("こと。\n").toString().getBytes(StandardCharsets.UTF_8);
  }

  private static ArrayList<RuntimeValue> filledStack(int totalBeforePredicate, ResultValue result) {
    var stack = new ArrayList<RuntimeValue>(totalBeforePredicate);
    for (int index = 1; index < totalBeforePredicate; index++) {
      stack.add(new IntegerValue(BigInteger.ZERO));
    }
    stack.add(result);
    return stack;
  }

  private static ExecutionBudget budget(long jsonWork, long arrayWork) {
    return new ExecutionBudget("Result-resource.bsb", () -> 0L, 0, 0, 0, arrayWork, 0, 0, jsonWork);
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(
            "Result-resource.bsb",
            new BoundedOutput("Result-resource.bsb", new MemoryOutputSink()),
            budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private static void executeConstruction(
      String name, ArrayList<RuntimeValue> stack, ResultType type, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(
            "Result-resource.bsb",
            new BoundedOutput("Result-resource.bsb", new MemoryOutputSink()),
            budget)
        .execute(
            BuiltinDictionary.find(name).orElseThrow(),
            stack,
            SPAN,
            java.util.Optional.of(
                new IrStackEffect(List.of(stack.getLast().type()), List.of(type))));
  }

  private static Row row(String caseId, String variant) throws IOException {
    return rows().stream()
        .filter(value -> value.caseId.equals(caseId) && value.variant.equals(variant))
        .findFirst()
        .orElseThrow();
  }

  private static List<Row> rows() throws IOException {
    Path path = Path.of("tests/conformance/result-values/runtime/runtime-cases.tsv");
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    String expectedHeader =
        "case_id\tvariant\ttarget\tstate\tresult_type\tpayload\toutcome\tcode\tlimit_name\tlimit\tobserved\tstack_delta\toutput_bytes\tarray_work\tjson_work\ttrace_policy";
    assertEquals(expectedHeader, lines.getFirst());
    var result = new ArrayList<Row>();
    for (String line : lines.subList(1, lines.size())) {
      String[] fields = line.split("\\t", -1);
      assertEquals(16, fields.length, line);
      result.add(new Row(fields));
    }
    return List.copyOf(result);
  }

  private record Row(
      String caseId,
      String variant,
      String target,
      String state,
      String resultType,
      String payload,
      String outcome,
      String code,
      String limitName,
      String limit,
      String observed,
      String stackDelta,
      String outputBytes,
      String arrayWork,
      String jsonWork,
      String tracePolicy) {
    Row(String[] fields) {
      this(
          fields[0],
          fields[1],
          fields[2],
          fields[3],
          fields[4],
          fields[5],
          fields[6],
          fields[7],
          fields[8],
          fields[9],
          fields[10],
          fields[11],
          fields[12],
          fields[13],
          fields[14],
          fields[15]);
    }
  }
}
