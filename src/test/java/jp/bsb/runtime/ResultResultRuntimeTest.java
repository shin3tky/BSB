package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.IrStackEffect;
import jp.bsb.json.JsonNull;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ResultResultRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final ResultType INTEGER_STRING =
      ValueType.resultOf(ValueType.INTEGER, ValueType.STRING);

  @Test
  void runsConstructionPredicatesUnwrapDropDisplayEqualityAndStorage() {
    String source =
        "保存は 変数 42 を 成功にする<整数,文字列>。\n\n"
            + "メインとは （--）\n"
            + "    保存 結果が成功である\n"
            + "    ならば\n"
            + "        結果から成功値を取り出す 一行表示する\n"
            + "    さもなければ\n"
            + "        結果から失敗値を取り出す 一行表示する\n"
            + "    つぎに\n"
            + "    「bad」を 失敗にする<整数,文字列> 結果が失敗である\n"
            + "    ならば\n"
            + "        結果から失敗値を取り出す 一行表示する\n"
            + "    さもなければ\n"
            + "        結果を捨てる\n"
            + "    つぎに\n"
            + "    1 を 成功にする<整数,整数> 1 を 成功にする<整数,整数> 等しい 一行表示する\n"
            + "    1 を 成功にする<整数,整数> 1 を 失敗にする<整数,整数> 等しい 一行表示する\n"
            + "    7 を 成功にする<整数,文字列> 一行表示する\n"
            + "    「x」を 失敗にする<整数,文字列> 結果を捨てる\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "result.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("42\nbad\nはい\nいいえ\n成功（7）\n", output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
    ResultValue stored = (ResultValue) result.finalGlobalValues().getFirst().orElseThrow();
    assertTrue(stored.isSuccess());
    assertEquals(new IntegerValue(BigInteger.valueOf(42)), stored.value());
    assertEquals(INTEGER_STRING, stored.type());
  }

  @Test
  void constructionUsesConcreteCallOutputAndSharesOnlyTheSelectedPayload() throws Exception {
    RuntimeValue payload = new IntegerValue(BigInteger.valueOf(42));
    var stack = new ArrayList<RuntimeValue>(List.of(payload));

    execute("成功にする", stack, INTEGER_STRING);

    ResultValue result = (ResultValue) stack.getFirst();
    assertSame(payload, result.value());
    assertEquals(INTEGER_STRING, result.type());
    assertTrue(result.isSuccess());
    assertFalse(result.isFailure());
    assertThrows(
        IllegalArgumentException.class,
        () -> ResultValue.failure(INTEGER_STRING, new IntegerValue(BigInteger.ONE)));

    RuntimeValue failurePayload = new StringValue("bad");
    stack.set(0, failurePayload);
    execute("失敗にする", stack, INTEGER_STRING);
    ResultValue failure = (ResultValue) stack.getFirst();
    assertSame(failurePayload, failure.value());
    assertTrue(failure.isFailure());
    assertNotSame(result, failure);
  }

  @Test
  void mismatchedUnwrapIsAtomicAndDiagnosticNeverContainsThePayload() throws Exception {
    ResultValue secret = ResultValue.failure(INTEGER_STRING, new StringValue("credential-secret"));
    var stack = new ArrayList<RuntimeValue>(List.of(secret));
    List<RuntimeValue> before = List.copyOf(stack);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("結果から成功値を取り出す", stack));

    assertEquals(DiagnosticCode.E_RESULT_STATE_MISMATCH, failure.diagnostic().code());
    assertEquals("結果<整数,文字列>", failure.diagnostic().fields().get("resultType"));
    assertEquals("success", failure.diagnostic().fields().get("expectedState"));
    assertEquals("failure", failure.diagnostic().fields().get("actualState"));
    assertEquals("成功の結果値", failure.diagnostic().expected().orElseThrow());
    assertEquals("失敗の結果値", failure.diagnostic().actual().orElseThrow());
    assertFalse(failure.diagnostic().toString().contains("credential-secret"));
    assertEquals(before, stack);
  }

  @Test
  void runtimeContextAlwaysRedactsResultsAndOptionalsContainingResults() {
    String source =
        "メインとは （--）\n"
            + "    「credential-secret」を 失敗にする<整数,文字列>\n"
            + "    結果から成功値を取り出す 一行表示する\n"
            + "こと。\n";

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "result-secret.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));

    assertEquals(10, result.exitCode());
    var diagnostic = result.diagnostics().getLast();
    assertEquals(DiagnosticCode.E_RESULT_STATE_MISMATCH, diagnostic.code());
    assertEquals("[結果<整数,文字列>:<redacted>]", diagnostic.fields().get("dataStack"));
    assertFalse(diagnostic.toString().contains("credential-secret"));
  }

  @Test
  void equalityChargesOnlyTheSelectedPayloadAndDifferentStatesShortCircuit() throws Exception {
    var budget = new ExecutionBudget("result-work.bsb", () -> 0L);
    ResultType jsonString = ValueType.resultOf(ValueType.JSON, ValueType.STRING);
    ResultValue json = ResultValue.success(jsonString, new JsonRuntimeValue(JsonNull.INSTANCE));
    var equal = new ArrayList<RuntimeValue>(List.of(json, json));

    execute("等しい", equal, budget);

    assertEquals(List.of(new BooleanValue(true)), equal);
    assertEquals(2, budget.jsonWorkUnits());

    ResultType jsonJson = ValueType.resultOf(ValueType.JSON, ValueType.JSON);
    var differentStates =
        new ArrayList<RuntimeValue>(
            List.of(
                ResultValue.success(jsonJson, new JsonRuntimeValue(JsonNull.INSTANCE)),
                ResultValue.failure(jsonJson, new JsonRuntimeValue(JsonNull.INSTANCE))));
    execute("等しい", differentStates, budget);
    assertEquals(List.of(new BooleanValue(false)), differentStates);
    assertEquals(2, budget.jsonWorkUnits());
  }

  @Test
  void predicateChecksCapacityBeforeChangingTheStack() throws Exception {
    var budget = new ExecutionBudget("result-stack.bsb", () -> 0L);
    ResultValue result = ResultValue.success(INTEGER_STRING, new IntegerValue(BigInteger.ONE));
    var success = new ArrayList<RuntimeValue>(RuntimeLimits.DATA_STACK_VALUES);
    for (int index = 1; index < RuntimeLimits.DATA_STACK_VALUES; index++) {
      success.add(new IntegerValue(BigInteger.ZERO));
    }
    success.add(result);
    List<RuntimeValue> before = List.copyOf(success);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("結果が成功である", success, budget));

    assertEquals(DiagnosticCode.E_DATA_STACK_LIMIT, failure.diagnostic().code());
    assertEquals(before, success);

    success.removeFirst();
    execute("結果が失敗である", success, budget);
    assertEquals(RuntimeLimits.DATA_STACK_VALUES, success.size());
    assertEquals(new BooleanValue(false), success.getLast());
    assertSame(result, success.get(success.size() - 2));
  }

  @Test
  void deeplyAlternatingValuesDisplayCompareAndHashWithoutJavaRecursion() {
    RuntimeValue first = new IntegerValue(BigInteger.valueOf(42));
    RuntimeValue second = new IntegerValue(BigInteger.valueOf(42));
    for (int depth = 1; depth <= ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH; depth++) {
      if ((depth & 1) == 0) {
        first = OptionalValue.present(first);
        second = OptionalValue.present(second);
      } else {
        ResultType type = ValueType.resultOf(first.type(), ValueType.BOOLEAN);
        first = ResultValue.success(type, first);
        second = ResultValue.success(type, second);
      }
    }

    assertEquals(ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH, ValueType.constructorDepth(first.type()));
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertTrue(first.displayText().startsWith("ある（成功（"));
    assertTrue(first.displayText().endsWith("）".repeat(ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH)));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack) throws RuntimeFailure {
    execute(name, stack, new ExecutionBudget("result-runtime.bsb", () -> 0L));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(
            "result-runtime.bsb",
            new BoundedOutput("result-runtime.bsb", new MemoryOutputSink()),
            budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ResultType outputType)
      throws RuntimeFailure {
    new BuiltinExecutor(
            "result-runtime.bsb",
            new BoundedOutput("result-runtime.bsb", new MemoryOutputSink()),
            new ExecutionBudget("result-runtime.bsb", () -> 0L))
        .execute(
            BuiltinDictionary.find(name).orElseThrow(),
            stack,
            SPAN,
            Optional.of(new IrStackEffect(List.of(stack.getLast().type()), List.of(outputType))));
  }
}
