package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class HostIoTimeRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void delegatesZeroAndTheOneDayBoundaryExactlyOnce() throws Exception {
    var calls = new ArrayList<Long>();
    Direct direct =
        direct(
            milliseconds -> {
              calls.add(milliseconds);
              return SleepCapability.Result.COMPLETED;
            });
    var zero = stack(0);
    var day = stack(RuntimeLimits.WAIT_CALL_MILLISECONDS);

    direct.executor().execute(BuiltinDictionary.find("待つ").orElseThrow(), zero, SPAN);
    direct.executor().execute(BuiltinDictionary.find("待つ").orElseThrow(), day, SPAN);

    assertEquals(List.of(0L, RuntimeLimits.WAIT_CALL_MILLISECONDS), calls);
    assertEquals(List.of(), zero);
    assertEquals(List.of(), day);
    assertEquals("time.sleep:" + RuntimeLimits.WAIT_CALL_MILLISECONDS, direct.executor().effect());
  }

  @Test
  void acceptsSevenDaysAndRejectsTheNextMillisecondBeforeDelegation() {
    StringBuilder source = new StringBuilder("メインとは （--）\n");
    for (int index = 0; index < 7; index++) {
      source.append("    86400000 を 待つ\n");
    }
    source.append("    1 を 待つ\nこと。\n");
    var calls = new AtomicInteger();
    Run run =
        run(
            source.toString(),
            milliseconds -> {
              calls.incrementAndGet();
              return SleepCapability.Result.COMPLETED;
            },
            null,
            () -> 0L,
            false);

    assertEquals(10, run.result().exitCode());
    assertEquals(DiagnosticCode.E_WAIT_TOTAL_LIMIT, run.result().diagnostics().getFirst().code());
    assertEquals(7, calls.get());
    assertEquals(new IntegerValue(BigInteger.ONE), run.result().finalDataStack().getLast());
  }

  @Test
  void cancellationDoesNotConsumeTheInputOrIncreaseTheSuccessfulTotal() throws Exception {
    var calls = new AtomicInteger();
    Direct direct =
        direct(
            milliseconds ->
                calls.getAndIncrement() == 0
                    ? SleepCapability.Result.CANCELLED
                    : SleepCapability.Result.COMPLETED);
    var cancelled = stack(1000);

    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                direct
                    .executor()
                    .execute(BuiltinDictionary.find("待つ").orElseThrow(), cancelled, SPAN));
    assertEquals(DiagnosticCode.E_WAIT_CANCELLED, failure.diagnostic().code());
    assertEquals(stack(1000), cancelled);
    for (int index = 0; index < 7; index++) {
      direct
          .executor()
          .execute(
              BuiltinDictionary.find("待つ").orElseThrow(),
              stack(RuntimeLimits.WAIT_CALL_MILLISECONDS),
              SPAN);
    }
    assertEquals(8, calls.get());
  }

  @Test
  void sleepTimeIsExcludedFromActiveTimeWhilePublicTimeIncludesIt() throws Exception {
    var now = new AtomicLong();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(now::get)
            .sleepCapability(
                milliseconds -> {
                  now.addAndGet(86_400_000_000_000L);
                  return SleepCapability.Result.COMPLETED;
                })
            .build();
    var budget = new ExecutionBudget("time.bsb", now::get, 0, 0);
    var executor =
        new BuiltinExecutor(
            "time.bsb", new BoundedOutput("time.bsb", new MemoryOutputSink()), budget, environment);

    executor.execute(
        BuiltinDictionary.find("待つ").orElseThrow(),
        stack(RuntimeLimits.WAIT_CALL_MILLISECONDS),
        SPAN);
    now.addAndGet(RuntimeLimits.ELAPSED_NANOS);
    budget.beforeInstruction(SPAN);
    now.incrementAndGet();
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> budget.beforeInstruction(SPAN));
    assertEquals(DiagnosticCode.E_EXECUTION_TIMEOUT, failure.diagnostic().code());
  }

  @Test
  void tracingAddsNoClockOrCapabilityReads() {
    Observation normal = observe(false);
    Observation traced = observe(true);

    assertEquals(0, normal.result().exitCode());
    assertEquals(normal.calls(), traced.calls());
    assertEquals(normal.resourceReads(), traced.resourceReads());
    assertEquals(normal.output(), traced.output());
  }

  @Test
  void decreasingPublicClockAndResourceArithmeticOverflowAreInternalContractFailures() {
    assertThrows(
        IllegalStateException.class,
        () ->
            run(
                "メインとは （--）\n    単調ミリ秒を得る を 一行表示する\n" + "    単調ミリ秒を得る を 一行表示する\nこと。\n",
                null,
                new MonotonicTime() {
                  private long value = 2;

                  @Override
                  public long milliseconds() {
                    return value--;
                  }
                },
                () -> 0L,
                false));

    var clock = new AtomicLong(Long.MAX_VALUE);
    var budget = new ExecutionBudget("overflow.bsb", clock::get, 0, Long.MIN_VALUE);
    assertThrows(IllegalStateException.class, () -> budget.beforeInstruction(SPAN));
  }

  private static Observation observe(boolean tracing) {
    var resourceReads = new AtomicInteger();
    var calls = new ArrayList<String>();
    MonotonicClock resourceClock =
        () -> {
          resourceReads.incrementAndGet();
          return 0L;
        };
    var publicValues = new java.util.ArrayDeque<>(List.of(0L, 2500L));
    Run run =
        run(
            "メインとは （--）\n"
                + "    単調ミリ秒を得る を 一行表示する\n"
                + "    0 を 待つ\n"
                + "    単調ミリ秒を得る を 一行表示する\n"
                + "こと。\n",
            milliseconds -> {
              calls.add("sleep:" + milliseconds);
              return SleepCapability.Result.COMPLETED;
            },
            () -> {
              calls.add("monotonic");
              return publicValues.removeFirst();
            },
            resourceClock,
            tracing);
    return new Observation(
        run.result(), List.copyOf(calls), resourceReads.get(), run.output().utf8Text());
  }

  private static Direct direct(SleepCapability sleep) {
    MonotonicClock clock = () -> 0L;
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(clock).sleepCapability(sleep).build();
    var budget = new ExecutionBudget("time.bsb", clock);
    return new Direct(
        new BuiltinExecutor(
            "time.bsb",
            new BoundedOutput("time.bsb", new MemoryOutputSink()),
            budget,
            environment));
  }

  private static ArrayList<RuntimeValue> stack(long milliseconds) {
    return new ArrayList<>(List.of(new IntegerValue(BigInteger.valueOf(milliseconds))));
  }

  private static Run run(
      String source,
      SleepCapability sleep,
      MonotonicTime monotonic,
      MonotonicClock resourceClock,
      boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ExecutionEnvironment.Builder builder =
        ExecutionEnvironment.builder(resourceClock).consoleOutput(output::write);
    if (sleep != null) {
      builder.sleepCapability(sleep);
    }
    if (monotonic != null) {
      builder.monotonicTime(monotonic);
    }
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "time.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(
                    output,
                    resourceClock,
                    tracing ? events::add : TraceSink.none(),
                    builder.build()));
    return new Run(result, output);
  }

  private record Direct(BuiltinExecutor executor) {}

  private record Run(ProgramRunResult result, MemoryOutputSink output) {}

  private record Observation(
      ProgramRunResult result, List<String> calls, int resourceReads, String output) {}
}
