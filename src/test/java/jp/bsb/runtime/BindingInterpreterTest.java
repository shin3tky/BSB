package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.Call;
import jp.bsb.ir.InitializeGlobal;
import jp.bsb.ir.InitializeLocal;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrStorageSlot;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.LoadGlobal;
import jp.bsb.ir.LoadLocal;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.SymbolId;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 束縛の大域・局所保存領域を反復型VMで実行できることを検証します。 */
class BindingInterpreterTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @ParameterizedTest
  @MethodSource("normalCases")
  void runsEveryNormalCaseAndTracingDoesNotChangeStorage(String sourceName, String expectedOutput)
      throws IOException {
    byte[] source = resourceBytes("sources/" + sourceName);
    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();

    ProgramRunResult normal =
        runner.run(sourceName, source, deterministic(normalOutput, TraceSink.none()));
    ProgramRunResult traced =
        runner.run(sourceName, source, deterministic(tracedOutput, events::add));

    assertTrue(normal.successful(), sourceName + ": " + normal.diagnostics());
    assertTrue(traced.successful(), sourceName + ": " + traced.diagnostics());
    assertEquals(normal.exitCode(), traced.exitCode(), sourceName);
    assertEquals(normal.finalDataStack(), traced.finalDataStack(), sourceName);
    assertEquals(normal.finalGlobalValues(), traced.finalGlobalValues(), sourceName);
    assertEquals(normal.executedInstructions(), traced.executedInstructions(), sourceName);
    assertEquals(expectedOutput, normalOutput.utf8Text(), sourceName);
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes(), sourceName);
    assertFalse(events.isEmpty(), sourceName);
  }

  @Test
  void executesTheGlobalInitializerOnceBeforeMainAndKeepsChapterGlobals() throws IOException {
    byte[] source = resourceBytes("chapter/bindings-chapter.bsb");
    byte[] expectedOutput = resourceBytes("chapter/bindings-chapter.stdout");
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult result =
        new ProgramRunner().run("bindings-chapter.bsb", source, deterministic(output, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(34, result.executedInstructions());
    assertArrayEquals(expectedOutput, output.bytes());
    assertEquals(
        List.of(
            Optional.of(new IntegerValue(java.math.BigInteger.valueOf(120))),
            Optional.of(new IntegerValue(java.math.BigInteger.valueOf(3))),
            Optional.of(new IntegerValue(java.math.BigInteger.valueOf(720)))),
        result.finalGlobalValues());
    assertEquals("<大域初期化>", events.getFirst().word());
    TraceEvent initializerReturn = events.get(6);
    assertEquals("Return", initializerReturn.opcode());
    assertEquals(1, initializerReturn.callDepthBefore());
    assertEquals(0, initializerReturn.callDepthAfter());
    TraceEvent firstMain = events.get(7);
    assertEquals("メイン", firstMain.word());
    assertEquals(1, firstMain.callDepthBefore());
  }

  @Test
  void storeConsumesOnlyTheTopValue() {
    String source =
        "保存先は 変数 0。\n\n" + "メインとは （--）\n" + "    99 7 を 保存先 に 入れる\n" + "    一行表示する\n" + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "store-top.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                deterministic(output, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("99\n", output.utf8Text());
    assertEquals(
        List.of(Optional.of(new IntegerValue(java.math.BigInteger.valueOf(7)))),
        result.finalGlobalValues());
    assertTrue(result.finalDataStack().isEmpty());
  }

  @Test
  void keepsLocalSlotsIndependentAcross1024ExplicitFrames() {
    IrProgram program = localCallChain(RuntimeLimits.CALL_STACK_FRAMES);

    ExecutionResult result =
        new Interpreter().execute(program, deterministic(new MemoryOutputSink(), TraceSink.none()));

    assertTrue(result.successful(), result.diagnostic().toString());
    assertEquals(RuntimeLimits.CALL_STACK_FRAMES, result.finalDataStack().size());
    for (int index = 0; index < RuntimeLimits.CALL_STACK_FRAMES; index++) {
      int expected = RuntimeLimits.CALL_STACK_FRAMES - index - 1;
      assertEquals(
          new IntegerValue(java.math.BigInteger.valueOf(expected)),
          result.finalDataStack().get(index));
    }
  }

  @Test
  void accepts65536LoadsAndRejectsTheNextBeforePushingIt() {
    ExecutionResult accepted = execute(loadProgram(RuntimeLimits.DATA_STACK_VALUES));
    ExecutionResult rejected = execute(loadProgram(RuntimeLimits.DATA_STACK_VALUES + 1));

    assertTrue(accepted.successful());
    assertEquals(RuntimeLimits.DATA_STACK_VALUES, accepted.finalDataStack().size());
    assertFalse(rejected.successful());
    assertEquals(DiagnosticCode.E_DATA_STACK_LIMIT, rejected.diagnostic().orElseThrow().code());
    assertEquals(RuntimeLimits.DATA_STACK_VALUES, rejected.finalDataStack().size());
    assertEquals(
        Long.toString((long) RuntimeLimits.DATA_STACK_VALUES + 1),
        rejected.diagnostic().orElseThrow().observed().orElseThrow());
  }

  private static ExecutionResult execute(IrProgram program) {
    return new Interpreter()
        .execute(program, deterministic(new MemoryOutputSink(), TraceSink.none()));
  }

  private static IrProgram loadProgram(int loadCount) {
    var global = globalSlot(1, 0);
    var mainSymbol = new SymbolId(0);
    var initializer =
        new IrWord(
            new SymbolId(1),
            "<大域初期化>",
            List.of(
                new PushConst(new IntegerValue(java.math.BigInteger.ONE), SPAN),
                new InitializeGlobal(global, SPAN),
                new Return(SPAN)));
    var instructions = new ArrayList<IrInstruction>(loadCount + 1);
    for (int index = 0; index < loadCount; index++) {
      instructions.add(new LoadGlobal(global, SPAN));
    }
    instructions.add(new Return(SPAN));
    var main = new IrWord(mainSymbol, "メイン", instructions);
    return new IrProgram(
        "load-stack.bsb",
        mainSymbol,
        Map.of(mainSymbol, main),
        Map.of(),
        initializer.instructions().size() + instructions.size(),
        Optional.of(initializer),
        List.of(global));
  }

  private static IrProgram localCallChain(int frameCount) {
    var words = new LinkedHashMap<SymbolId, IrWord>();
    int instructionCount = 0;
    for (int index = 0; index < frameCount; index++) {
      String name = index == 0 ? "メイン" : "語" + index;
      var id = new SymbolId(index);
      var slot = localSlot(index + 1, name);
      var instructions = new ArrayList<IrInstruction>();
      instructions.add(new PushConst(new IntegerValue(java.math.BigInteger.valueOf(index)), SPAN));
      instructions.add(new InitializeLocal(slot, SPAN));
      if (index + 1 < frameCount) {
        instructions.add(new Call(new SymbolId(index + 1), "語" + (index + 1), List.of(), SPAN));
      }
      instructions.add(new LoadLocal(slot, SPAN));
      instructions.add(new Return(SPAN));
      instructionCount += instructions.size();
      words.put(id, new IrWord(id, name, instructions, List.of(slot)));
    }
    return new IrProgram("local-frames.bsb", new SymbolId(0), words, Map.of(), instructionCount);
  }

  private static IrStorageSlot globalSlot(int bindingId, int index) {
    return new IrStorageSlot(
        new BindingId(bindingId),
        "大域" + bindingId,
        BindingKind.CONSTANT,
        BindingStorage.GLOBAL,
        ValueType.INTEGER,
        index,
        Optional.empty());
  }

  private static IrStorageSlot localSlot(int bindingId, String owner) {
    return new IrStorageSlot(
        new BindingId(bindingId),
        "局所" + bindingId,
        BindingKind.VARIABLE,
        BindingStorage.LOCAL,
        ValueType.INTEGER,
        0,
        Optional.of(owner));
  }

  private static ExecutionContext deterministic(OutputSink output, TraceSink trace) {
    return new ExecutionContext(output, () -> 0L, trace);
  }

  private static Stream<Arguments> normalCases() {
    return Stream.of(
        Arguments.of("BIND-N001.bsb", "42\n"),
        Arguments.of("BIND-N002.bsb", "100\n"),
        Arguments.of("BIND-N003.bsb", "360\n"),
        Arguments.of("BIND-N004.bsb", "局所\n"),
        Arguments.of("BIND-N005.bsb", "7\n"),
        Arguments.of("BIND-N006.bsb", "1\nはい\nA\n文\n"),
        Arguments.of("BIND-N007.bsb", "2\n"),
        Arguments.of("BIND-N008.bsb", "1\n1\n"),
        Arguments.of("BIND-N009.bsb", "2\n1\n0\n"),
        Arguments.of("BIND-N010.bsb", "真側\n"),
        Arguments.of("BIND-N011.bsb", "真\n偽\n"),
        Arguments.of("BIND-N012.bsb", "外側\n"),
        Arguments.of("BIND-N013.bsb", "3\n"),
        Arguments.of("BIND-N014.bsb", "1\n1\n1\n"),
        Arguments.of("BIND-N015.bsb", "戻る\n"),
        Arguments.of("BIND-N016.bsb", "16\n"),
        Arguments.of("BIND-N017.bsb", "注釈\n"),
        Arguments.of("BIND-N018.bsb", "7\n"),
        Arguments.of("BIND-N019.bsb", "はい\nはい\n"));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = "/conformance/bindings/" + relativePath;
    try (var input = BindingInterpreterTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
