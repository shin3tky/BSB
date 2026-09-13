package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.bsb.conformance.ResultConformanceData;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.Call;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.SymbolId;
import jp.bsb.json.JsonNull;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

/** RESULT-R014: 512 MiB内で深さ・幅・JSON作業・トレースを同時に通す統合試験です。 */
class ResultIntegratedResourceTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void resultR014IntegratedResultFitsIn512MiBAndIsDeterministic() throws Exception {
    var recipe = ResultConformanceData.loadIntegratedRecipe();
    assertEquals(256, recipe.depth());
    assertEquals(65_535, recipe.stackWidth());
    assertTrue(recipe.sharedPayload());
    assertEquals(2, recipe.jsonComparisons());
    assertTrue(recipe.trace());
    assertEquals(512, recipe.heapMiB());
    assertTrue(Runtime.getRuntime().maxMemory() <= recipe.heapMiB() * 1024L * 1024L);

    JsonRuntimeValue sharedPayload = new JsonRuntimeValue(JsonNull.INSTANCE);
    RuntimeValue first = wrap(sharedPayload, recipe.depth());
    RuntimeValue second = wrap(sharedPayload, recipe.depth());
    assertSame(sharedPayload, terminalPayload(first));
    assertSame(sharedPayload, terminalPayload(second));
    assertEquals(recipe.depth(), ValueType.constructorDepth(first.type()));
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertEquals(first.displayText(), second.displayText());
    assertTrue(first.displayText().endsWith("）".repeat(recipe.depth())));

    var budget = new ExecutionBudget("RESULT-R014-json.bsb", () -> 0L);
    for (int index = 0; index < recipe.jsonComparisons(); index++) {
      var values = new ArrayList<RuntimeValue>(List.of(first, second));
      new BuiltinExecutor(
              "RESULT-R014-json.bsb",
              new BoundedOutput("RESULT-R014-json.bsb", new MemoryOutputSink()),
              budget)
          .execute(BuiltinDictionary.find("等しい").orElseThrow(), values, SPAN);
      assertEquals(List.of(new BooleanValue(true)), values);
    }
    assertEquals(recipe.jsonComparisons() * 2L, budget.jsonWorkUnits());

    ExecutionResult wide = execute(recipe.stackWidth(), first, false).result();
    assertEquals(0, wide.exitCode(), wide.diagnostic().toString());
    assertEquals(65_536, wide.finalDataStack().size());
    assertSame(first, wide.finalDataStack().get(recipe.stackWidth() - 1));
    assertEquals(new BooleanValue(true), wide.finalDataStack().getLast());

    Observation normalObservation = execute(1, first, false);
    Observation tracedObservation = execute(1, first, true);
    ExecutionResult normal = normalObservation.result();
    ExecutionResult traced = tracedObservation.result();
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertEquals(normal.executedInstructions(), traced.executedInstructions());
    assertEquals(normal.jsonWorkUnits(), traced.jsonWorkUnits());
    assertEquals(normal.outputBytes(), traced.outputBytes());
    assertFalse(tracedObservation.events().isEmpty());
    String trace = TraceTsvFormatter.formatResult(tracedObservation.events());
    assertTrue(trace.contains("<redacted>"));
    assertFalse(trace.contains(":成功("));
    assertEquals(trace, TraceTsvFormatter.formatResult(tracedObservation.events()));
  }

  private static RuntimeValue wrap(JsonRuntimeValue payload, int depth) {
    RuntimeValue value = payload;
    for (int level = 1; level <= depth; level++) {
      if ((level & 1) == 1) {
        value = OptionalValue.present(value);
      } else {
        ResultType type = ValueType.resultOf(value.type(), ValueType.BOOLEAN);
        value = ResultValue.success(type, value);
      }
    }
    return value;
  }

  private static RuntimeValue terminalPayload(RuntimeValue root) {
    RuntimeValue value = root;
    while (true) {
      if (value instanceof OptionalValue optional) {
        value = optional.value().orElseThrow();
      } else if (value instanceof ResultValue result) {
        value = result.value();
      } else {
        return value;
      }
    }
  }

  private static Observation execute(int count, RuntimeValue value, boolean tracing) {
    var main = new SymbolId(0);
    var predicate = new SymbolId(1);
    var instructions = new ArrayList<IrInstruction>(count + 2);
    for (int index = 0; index < count; index++) {
      instructions.add(new PushConst(value, SPAN));
    }
    instructions.add(new Call(predicate, "結果が成功である", List.of(), SPAN));
    instructions.add(new Return(SPAN));
    var builtins = new LinkedHashMap<SymbolId, jp.bsb.stdlib.BuiltinWord>();
    builtins.put(predicate, BuiltinDictionary.find("結果が成功である").orElseThrow());
    IrProgram program =
        new IrProgram(
            "RESULT-R014.bsb",
            main,
            Map.of(main, new IrWord(main, "メイン", instructions)),
            builtins,
            instructions.size());
    var events = new ArrayList<TraceEvent>();
    ExecutionResult result =
        new Interpreter()
            .execute(
                program,
                new ExecutionContext(
                    new MemoryOutputSink(), () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, List.copyOf(events));
  }

  private record Observation(ExecutionResult result, List<TraceEvent> events) {}
}
