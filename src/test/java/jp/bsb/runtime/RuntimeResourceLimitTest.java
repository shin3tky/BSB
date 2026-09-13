package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.Call;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.Jump;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.SymbolId;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class RuntimeResourceLimitTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void acceptsAndRejectsIntegerResultDigitBoundaryBeforeOversizedMultiplication() {
    BigInteger largest = BigInteger.TEN.pow(RuntimeLimits.INTEGER_DIGITS - 1);
    ExecutionResult accepted = executeArithmetic(largest, BigInteger.ONE);
    ExecutionResult rejected = executeArithmetic(largest, BigInteger.TEN);

    assertTrue(accepted.successful());
    assertEquals(
        RuntimeLimits.INTEGER_DIGITS,
        ((IntegerValue) accepted.finalDataStack().getFirst()).displayText().length());
    assertEquals(DiagnosticCode.E_INTEGER_RESULT_LIMIT, rejected.diagnostic().orElseThrow().code());
    assertEquals("65537", rejected.diagnostic().orElseThrow().observed().orElseThrow());
  }

  @Test
  void accepts65536DataValuesAndRejectsTheNextPush() {
    var value = new IntegerValue(BigInteger.ZERO);
    var exactInstructions = new ArrayList<IrInstruction>(RuntimeLimits.DATA_STACK_VALUES + 1);
    for (int index = 0; index < RuntimeLimits.DATA_STACK_VALUES; index++) {
      exactInstructions.add(new PushConst(value, SPAN));
    }
    exactInstructions.add(new Return(SPAN));
    ExecutionResult accepted = execute(singleWord(exactInstructions));

    var overInstructions = new ArrayList<>(exactInstructions);
    overInstructions.add(overInstructions.size() - 1, new PushConst(value, SPAN));
    ExecutionResult rejected = execute(singleWord(overInstructions));

    assertTrue(accepted.successful());
    assertEquals(RuntimeLimits.DATA_STACK_VALUES, accepted.finalDataStack().size());
    assertEquals(DiagnosticCode.E_DATA_STACK_LIMIT, rejected.diagnostic().orElseThrow().code());
    assertEquals("メイン", rejected.diagnostic().orElseThrow().fields().get("currentWord"));
    assertTrue(rejected.diagnostic().orElseThrow().fields().get("dataStack").startsWith("[整数:0"));
    assertEquals("メイン", rejected.diagnostic().orElseThrow().fields().get("callStack"));
  }

  @Test
  void usesAnExplicitCallStackAt1024AndRejectsFrame1025() {
    ExecutionResult accepted = execute(callChain(RuntimeLimits.CALL_STACK_FRAMES));
    ExecutionResult rejected = execute(callChain(RuntimeLimits.CALL_STACK_FRAMES + 1));

    assertTrue(accepted.successful());
    assertEquals(DiagnosticCode.E_CALL_STACK_LIMIT, rejected.diagnostic().orElseThrow().code());
    assertEquals("1025", rejected.diagnostic().orElseThrow().observed().orElseThrow());
  }

  @Test
  void acceptsTenMillionInstructionsAndRejectsTheNextBeforeExecution() throws Exception {
    var budget =
        new ExecutionBudget("synthetic.bsb", () -> 0L, RuntimeLimits.EXECUTED_INSTRUCTIONS - 1, 0L);

    budget.beforeInstruction(SPAN);
    assertEquals(RuntimeLimits.EXECUTED_INSTRUCTIONS, budget.executed());
    RuntimeFailure failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> budget.beforeInstruction(SPAN));
    assertEquals(DiagnosticCode.E_INSTRUCTION_LIMIT, failure.diagnostic().code());
  }

  @Test
  void appliesTheInstructionLimitToAnActuallyExecutedBackEdge() {
    var main = new SymbolId(0);
    List<IrInstruction> instructions = List.of(new Jump(0, 0, SPAN), new Return(SPAN));
    var program =
        new IrProgram(
            "loop.bsb",
            main,
            Map.of(main, new IrWord(main, "メイン", instructions)),
            Map.of(),
            instructions.size());

    ExecutionResult result = execute(program);

    assertEquals(DiagnosticCode.E_INSTRUCTION_LIMIT, result.diagnostic().orElseThrow().code());
    assertEquals(RuntimeLimits.EXECUTED_INSTRUCTIONS, result.executedInstructions());
    assertEquals("10000001", result.diagnostic().orElseThrow().observed().orElseThrow());
  }

  @Test
  void fakeClockAcceptsThirtySecondsAndRejectsOneNanosecondMore() throws Exception {
    var now = new AtomicLong(RuntimeLimits.ELAPSED_NANOS);
    var budget = new ExecutionBudget("synthetic.bsb", now::get, 0, 0);

    budget.beforeInstruction(SPAN);
    now.incrementAndGet();
    RuntimeFailure failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> budget.beforeInstruction(SPAN));
    assertEquals(DiagnosticCode.E_EXECUTION_TIMEOUT, failure.diagnostic().code());
  }

  @Test
  void outputLimitIsAtomicPerBuiltinCall() throws Exception {
    var sink = new MemoryOutputSink();
    var output = new BoundedOutput("synthetic.bsb", sink, RuntimeLimits.OUTPUT_UTF8_BYTES - 1);
    output.write(new byte[] {1}, SPAN);

    assertEquals(RuntimeLimits.OUTPUT_UTF8_BYTES, output.writtenBytes());
    assertEquals(1, sink.bytes().length);
    RuntimeFailure failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> output.write(new byte[] {2, 3}, SPAN));
    assertEquals(DiagnosticCode.E_OUTPUT_LIMIT, failure.diagnostic().code());
    assertEquals(1, sink.bytes().length);
  }

  private static ExecutionResult executeArithmetic(BigInteger first, BigInteger second) {
    var builtin = BuiltinDictionary.find("掛ける").orElseThrow();
    var main = new SymbolId(0);
    var multiply = new SymbolId(1);
    var instructions =
        List.<IrInstruction>of(
            new PushConst(new IntegerValue(first), SPAN),
            new PushConst(new IntegerValue(second), SPAN),
            new Call(multiply, "掛ける", List.of(), SPAN),
            new Return(SPAN));
    var program =
        new IrProgram(
            "synthetic.bsb",
            main,
            Map.of(main, new IrWord(main, "メイン", instructions)),
            Map.of(multiply, builtin),
            instructions.size());
    return execute(program);
  }

  private static IrProgram singleWord(List<IrInstruction> instructions) {
    var main = new SymbolId(0);
    return new IrProgram(
        "synthetic.bsb",
        main,
        Map.of(main, new IrWord(main, "メイン", instructions)),
        Map.of(),
        instructions.size());
  }

  private static IrProgram callChain(int frameCount) {
    var words = new LinkedHashMap<SymbolId, IrWord>();
    int instructionCount = 0;
    for (int index = 0; index < frameCount; index++) {
      var id = new SymbolId(index);
      List<IrInstruction> instructions;
      if (index + 1 < frameCount) {
        var target = new SymbolId(index + 1);
        instructions =
            List.of(new Call(target, "語" + (index + 1), List.of(), SPAN), new Return(SPAN));
      } else {
        instructions = List.of(new Return(SPAN));
      }
      instructionCount += instructions.size();
      words.put(id, new IrWord(id, index == 0 ? "メイン" : "語" + index, instructions));
    }
    return new IrProgram("synthetic.bsb", new SymbolId(0), words, Map.of(), instructionCount);
  }

  private static ExecutionResult execute(IrProgram program) {
    return new Interpreter()
        .execute(program, new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));
  }
}
