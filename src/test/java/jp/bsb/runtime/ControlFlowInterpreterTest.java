package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 制御フローの分岐、ループ、制御移行を、公開実行パイプラインから検証します。 */
class ControlFlowInterpreterTest {
  @Test
  void evaluatesAllShortCircuitTruthTableRows() {
    String source =
        "メインとは （--）\n"
            + "    いいえ または\n        いいえ\n    つぎに 一行表示する\n"
            + "    いいえ または\n        はい\n    つぎに 一行表示する\n"
            + "    はい かつ\n        いいえ\n    つぎに 一行表示する\n"
            + "    はい かつ\n        はい\n    つぎに 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "short-circuit-truth-table.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                deterministic(output, TraceSink.none()));

    assertTrue(
        result.successful(),
        result.diagnostics().stream()
            .map(d -> d.code() + " " + d.fields() + " " + d.actual())
            .toList()
            .toString());
    assertEquals("いいえ\nはい\nいいえ\nはい\n", output.utf8Text());
  }

  @Test
  void skipsRightHandSideEffectsWhenLeftDeterminesTheResult() {
    String source =
        "メインとは （--）\n"
            + "    はい または\n"
            + "        「OR右辺」 一行表示する いいえ\n"
            + "    つぎに 一行表示する\n"
            + "    いいえ かつ\n"
            + "        「AND右辺」 一行表示する はい\n"
            + "    つぎに 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "short-circuit-side-effects.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                deterministic(output, TraceSink.none()));

    assertTrue(
        result.successful(),
        result.diagnostics().stream()
            .map(d -> d.code() + " " + d.fields() + " " + d.actual())
            .toList()
            .toString());
    assertEquals("はい\nいいえ\n", output.utf8Text());
  }

  @ParameterizedTest
  @MethodSource("normalControlCases")
  void runsEveryControlCaseAndTracingDoesNotChangeTheResult(
      String sourceName, String expectedOutput) throws IOException {
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
    assertEquals(normal.executedInstructions(), traced.executedInstructions(), sourceName);
    assertEquals(expectedOutput, normalOutput.utf8Text(), sourceName);
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes(), sourceName);
    assertFalse(events.isEmpty(), sourceName);
  }

  @Test
  void matchesTheNormativeControlFlowChapterTraceExactly() throws IOException {
    byte[] source = resourceBytes("chapter/control-flow-chapter.bsb");
    byte[] expectedOutput = resourceBytes("chapter/control-flow-chapter.stdout");
    String expectedTrace = resourceText("chapter/control-flow-chapter.trace.tsv");
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult result =
        new ProgramRunner()
            .run("control-flow-chapter.bsb", source, deterministic(output, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(33, result.executedInstructions());
    assertArrayEquals(expectedOutput, output.bytes());
    assertEquals(expectedTrace, TraceTsvFormatter.formatControlFlow(events));
  }

  @Test
  void reportsTheSpecifiedNegativeRepeatCountWithoutStartingTheLoop() throws IOException {
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "FLOW-F025.bsb",
                resourceBytes("sources/FLOW-F025.bsb"),
                deterministic(output, TraceSink.none()));

    assertFalse(result.successful());
    assertEquals(10, result.exitCode());
    assertEquals(1, result.diagnostics().size());
    var diagnostic = result.diagnostics().getFirst();
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(DiagnosticCode.E_NEGATIVE_REPEAT_COUNT, diagnostic.code());
    assertEquals(DiagnosticStage.RUNTIME, diagnostic.stage());
    assertEquals(2, position.line());
    assertEquals(8, position.column());
    assertEquals("-1", diagnostic.fields().get("count"));
    assertEquals("0以上", diagnostic.expected().orElseThrow());
    assertEquals("-1", diagnostic.actual().orElseThrow());
    assertEquals(List.of("反復回数を0以上にしてください"), diagnostic.fixes());
    assertTrue(diagnostic.fields().get("dataStack").contains("整数:-1"));
    assertEquals("", output.utf8Text());
  }

  @Test
  void doesNotExecuteTheSideEffectInAnUnselectedBranch() throws IOException {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "FLOW-N002.bsb",
                resourceBytes("sources/FLOW-N002.bsb"),
                deterministic(output, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("", output.utf8Text());
    assertTrue(events.stream().noneMatch(event -> event.opcode().equals("Call:一行表示する")));
  }

  @Test
  void tracesNestedCountedLoopsFromOuterToInner() {
    String source =
        "メインとは （--）\n"
            + "    2 回だけ\n"
            + "        2 回だけ\n"
            + "        繰り返す\n"
            + "    繰り返す\n"
            + "こと。\n";
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "nested.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                deterministic(new MemoryOutputSink(), events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    TraceEvent innerStart =
        events.stream()
            .filter(event -> event.opcode().equals("CountedLoopStart"))
            .skip(1)
            .findFirst()
            .orElseThrow();
    assertEquals(
        List.of(new CountedLoopTraceState(BigInteger.TWO, BigInteger.TWO)),
        innerStart.controlBefore());
    assertEquals(
        List.of(
            new CountedLoopTraceState(BigInteger.TWO, BigInteger.TWO),
            new CountedLoopTraceState(BigInteger.TWO, BigInteger.TWO)),
        innerStart.controlAfter());
  }

  @Test
  void discardsCountedLoopStateOnBreakAndReturn() throws IOException {
    var breakEvents = new ArrayList<TraceEvent>();
    ProgramRunResult broken =
        new ProgramRunner()
            .run(
                "FLOW-N014.bsb",
                resourceBytes("sources/FLOW-N014.bsb"),
                deterministic(new MemoryOutputSink(), breakEvents::add));

    assertTrue(broken.successful(), broken.diagnostics().toString());
    TraceEvent breakJump =
        breakEvents.stream()
            .filter(event -> event.opcode().equals("Jump"))
            .findFirst()
            .orElseThrow();
    assertEquals(1, breakJump.controlBefore().size());
    assertTrue(breakJump.controlAfter().isEmpty());

    String returningSource =
        "早く戻るとは （--）\n"
            + "    2 回だけ\n"
            + "        戻る\n"
            + "    繰り返す\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    早く戻る\n"
            + "こと。\n";
    var returnEvents = new ArrayList<TraceEvent>();
    ProgramRunResult returned =
        new ProgramRunner()
            .run(
                "return-in-loop.bsb",
                returningSource.getBytes(StandardCharsets.UTF_8),
                deterministic(new MemoryOutputSink(), returnEvents::add));

    assertTrue(returned.successful(), returned.diagnostics().toString());
    TraceEvent earlyReturn =
        returnEvents.stream()
            .filter(event -> event.word().equals("早く戻る") && event.opcode().equals("Return"))
            .findFirst()
            .orElseThrow();
    assertEquals(1, earlyReturn.controlBefore().size());
    assertTrue(earlyReturn.controlAfter().isEmpty());
  }

  @Test
  void stopsAnInfiniteBackEdgeByTheMonotonicTimeBudget() {
    String source =
        "メインとは （--）\n" + "    ここから\n" + "        はい\n" + "    続く間\n" + "    繰り返す\n" + "こと。\n";
    var clockCalls = new AtomicInteger();
    MonotonicClock clock =
        () -> clockCalls.getAndIncrement() < 4 ? 0L : RuntimeLimits.ELAPSED_NANOS + 1;

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "infinite.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(new MemoryOutputSink(), clock, TraceSink.none()));

    assertFalse(result.successful());
    assertEquals(DiagnosticCode.E_EXECUTION_TIMEOUT, result.diagnostics().getFirst().code());
    assertEquals(3, result.executedInstructions());
  }

  @Test
  void executesTheMaximumControlNestingWithoutJavaMethodRecursion() {
    var source = new StringBuilder("メインとは （--）\n");
    source.append("    はい ならば\n".repeat(256));
    source.append("    つぎに\n".repeat(256));
    source.append("こと。\n");

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "nested-256.bsb",
                source.toString().getBytes(StandardCharsets.UTF_8),
                deterministic(new MemoryOutputSink(), TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(513, result.executedInstructions());
  }

  private static ExecutionContext deterministic(OutputSink output, TraceSink trace) {
    return new ExecutionContext(output, () -> 0L, trace);
  }

  private static Stream<Arguments> normalControlCases() {
    return Stream.of(
        Arguments.of("FLOW-N001.bsb", "真\n"),
        Arguments.of("FLOW-N002.bsb", ""),
        Arguments.of("FLOW-N003.bsb", "真\n"),
        Arguments.of("FLOW-N004.bsb", "偽\n"),
        Arguments.of("FLOW-N005.bsb", "内側は偽\n"),
        Arguments.of("FLOW-N006.bsb", "2\n"),
        Arguments.of("FLOW-N007.bsb", ""),
        Arguments.of("FLOW-N008.bsb", "1回\n"),
        Arguments.of("FLOW-N009.bsb", "反復\n反復\n反復\n"),
        Arguments.of("FLOW-N010.bsb", "\n\n\n"),
        Arguments.of("FLOW-N011.bsb", ""),
        Arguments.of("FLOW-N012.bsb", "1回だけ\n"),
        Arguments.of("FLOW-N013.bsb", ""),
        Arguments.of("FLOW-N014.bsb", "最初\n"),
        Arguments.of("FLOW-N015.bsb", "完了\n"),
        Arguments.of("FLOW-N016.bsb", "通常\n早期\n"),
        Arguments.of("FLOW-N019.bsb", "X\nX\nX\nX\n"));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = "/conformance/control-flow/" + relativePath;
    try (var input = ControlFlowInterpreterTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }
}
