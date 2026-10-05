package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class IntegerRandomRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void exhaustiveCandidateCyclesGiveEachOutcomeExactlyOncePerCycle() throws Exception {
    for (int width : new int[] {2, 3, 6, 7, 8, 127, 128, 129, 255, 256, 257, 1000}) {
      int bits = BigInteger.valueOf(width - 1).bitLength();
      int cycle = 1 << bits;
      var calls = new AtomicInteger();
      BuiltinExecutor executor =
          executor(bytes -> put(bytes, BigInteger.valueOf(calls.getAndIncrement() % cycle)));
      // Completing a second cycle forces every rejected candidate at the end of the first cycle.
      for (int index = 0; index <= 2 * width; index++) {
        var stack = stack(BigInteger.valueOf(-50), BigInteger.valueOf(width - 51));
        execute(executor, stack);
        assertEquals(List.of(integer(index % width - 50)), stack, "width=" + width);
      }
      assertEquals(2 * cycle + 1, calls.get(), "width=" + width);
    }
  }

  @Test
  void discardsSixAndSevenInsteadOfReducingThemModuloSix() throws Exception {
    var candidates = new java.util.ArrayDeque<>(List.of(6, 7, 5));
    BuiltinExecutor executor = executor(bytes -> bytes[0] = candidates.removeFirst().byteValue());
    var stack = stack(BigInteger.ONE, BigInteger.valueOf(6));
    stack.addFirst(integer(99));
    execute(executor, stack);
    assertEquals(List.of(integer(99), integer(6)), stack);
    assertTrue(candidates.isEmpty());
    assertEquals("random.bytes", executor.effect());
  }

  @Test
  void masksUnusedHighBitsAndTreatsBytesAsUnsigned() throws Exception {
    for (int bits : new int[] {1, 7, 8, 9, 64, 65, 128}) {
      BuiltinExecutor executor =
          executor(
              bytes -> {
                assertEquals((bits + 7) / 8, bytes.length);
                Arrays.fill(bytes, (byte) 0xff);
              });
      BigInteger upper = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
      var stack = stack(BigInteger.ZERO, upper);
      execute(executor, stack);
      assertEquals(List.of(new IntegerValue(upper)), stack);
    }
  }

  @Test
  void supportsBothEndpointsOfTheLargestLegalSignedInterval() throws Exception {
    BigInteger upper = BigInteger.TEN.pow(RuntimeLimits.INTEGER_DIGITS).subtract(BigInteger.ONE);
    BigInteger lower = upper.negate();
    BigInteger offset = upper.subtract(lower);
    for (BigInteger candidate : List.of(BigInteger.ZERO, offset)) {
      var stack = stack(lower, upper);
      execute(executor(bytes -> put(bytes, candidate)), stack);
      assertEquals(List.of(new IntegerValue(lower.add(candidate))), stack);
    }
  }

  @Test
  void equalBoundsConsumeNoRandomnessButStillRequireTheDeclaredCapability() throws Exception {
    var stack = stack(BigInteger.valueOf(-42), BigInteger.valueOf(-42));
    execute(executor(bytes -> fail("singleton must not read randomness")), stack);
    assertEquals(List.of(integer(-42)), stack);
    var absent = stack(BigInteger.ONE, BigInteger.ONE);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute(executor(null), absent));
    assertEquals(DiagnosticCode.E_CAPABILITY_UNAVAILABLE, failure.diagnostic().code());
    assertEquals("random.bytes", failure.diagnostic().fields().get("capability"));
    assertEquals(stack(BigInteger.ONE, BigInteger.ONE), absent);
  }

  @Test
  void invalidBoundsAreRejectedBeforeCapabilityLookupOrRandomness() {
    var stack = stack(BigInteger.valueOf(6), BigInteger.ONE);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute(executor(null), stack));
    assertEquals(DiagnosticCode.E_NUMERIC_RANGE_INVALID, failure.diagnostic().code());
    assertEquals("整数乱数を得る", failure.diagnostic().fields().get("word"));
    assertEquals(stack(BigInteger.valueOf(6), BigInteger.ONE), stack);
  }

  @Test
  void capabilityFailuresLeaveInputsIntactAndHideHostDetails() {
    for (RandomSource source :
        List.<RandomSource>of(
            bytes -> {
              throw CapabilityException.failure(RuntimeCapability.RANDOM_BYTES, "nextBytes");
            },
            bytes -> {
              bytes[0] = 0;
              throw new IllegalArgumentException("host-private-secret");
            })) {
      var stack = stack(BigInteger.ONE, BigInteger.valueOf(6));
      RuntimeFailure failure =
          assertThrows(RuntimeFailure.class, () -> execute(executor(source), stack));
      assertEquals(DiagnosticCode.E_CAPABILITY_FAILURE, failure.diagnostic().code());
      assertFalse(failure.diagnostic().toString().contains("host-private-secret"));
      assertEquals(stack(BigInteger.ONE, BigInteger.valueOf(6)), stack);
    }
  }

  @Test
  void acceptsTheLastAllowedAttemptAndNeverFallsBackAfterExhaustion() throws Exception {
    var calls = new AtomicInteger();
    BuiltinExecutor executor =
        executor(
            bytes ->
                bytes[0] =
                    (byte) (calls.incrementAndGet() == RuntimeLimits.RANDOM_ATTEMPTS ? 0 : 7));
    var stack = stack(BigInteger.ONE, BigInteger.valueOf(6));
    execute(executor, stack);
    assertEquals(List.of(integer(1)), stack);
    assertEquals(RuntimeLimits.RANDOM_ATTEMPTS, calls.get());
    calls.set(0);
    executor =
        executor(
            bytes -> {
              calls.incrementAndGet();
              bytes[0] = 7;
            });
    BuiltinExecutor rejecting = executor;
    var unchanged = stack(BigInteger.ONE, BigInteger.valueOf(6));
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute(rejecting, unchanged));
    assertEquals(DiagnosticCode.E_CAPABILITY_FAILURE, failure.diagnostic().code());
    assertEquals("sample", failure.diagnostic().fields().get("operation"));
    assertEquals(RuntimeLimits.RANDOM_ATTEMPTS, calls.get());
    assertEquals(stack(BigInteger.ONE, BigInteger.valueOf(6)), unchanged);
    assertEquals("", rejecting.effect());
  }

  @Test
  void checksActiveTimeInsideSamplingWithoutCountingCandidatesAsIrInstructions() throws Exception {
    var now = new AtomicLong();
    MonotonicClock clock = now::get;
    var budget = new ExecutionBudget("random.bsb", clock);
    BuiltinExecutor executor =
        executor(
            bytes -> {
              bytes[0] = 0;
              now.set(RuntimeLimits.ELAPSED_NANOS + 1);
            },
            clock,
            budget);
    var stack = stack(BigInteger.ONE, BigInteger.valueOf(6));
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute(executor, stack));
    assertEquals(DiagnosticCode.E_EXECUTION_TIMEOUT, failure.diagnostic().code());
    assertEquals(0, budget.executed());
    assertEquals(stack(BigInteger.ONE, BigInteger.valueOf(6)), stack);
  }

  @Test
  void seedingAndTracingPreserveTheSequenceAndNumberOfSourceCalls() {
    assertEquals(observe(false), observe(true));
    RandomSource first = RandomSource.seeded(123);
    RandomSource second = RandomSource.seeded(123);
    byte[] a = new byte[2048];
    byte[] b = new byte[2048];
    for (int index = 0; index < 3; index++) {
      firstBytes(first, a);
      firstBytes(second, b);
      assertArrayEquals(a, b);
    }
    assertTrue(
        ExecutionContext.standard(new MemoryOutputSink())
            .environment()
            .capabilities()
            .contains(RuntimeCapability.RANDOM_BYTES));
  }

  private static void firstBytes(RandomSource source, byte[] bytes) {
    assertDoesNotThrow(() -> source.nextBytes(bytes));
  }

  private static Observation observe(boolean tracing) {
    var output = new MemoryOutputSink();
    var calls = new AtomicInteger();
    RandomSource source = RandomSource.seeded(9876);
    var events = new ArrayList<TraceEvent>();
    MonotonicClock clock = () -> 0L;
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .randomSource(
                bytes -> {
                  calls.incrementAndGet();
                  source.nextBytes(bytes);
                })
            .build();
    String program = "メインとは （--）\n" + "    -100 と 100 から 整数乱数を得る 一行表示する\n".repeat(20) + "こと。\n";
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "random.bsb",
                program.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(
                    output, clock, tracing ? events::add : TraceSink.none(), environment));
    assertEquals(0, result.exitCode(), result.diagnostics().toString());
    if (tracing)
      assertTrue(events.stream().anyMatch(event -> event.effect().equals("random.bytes")));
    return new Observation(output.utf8Text(), calls.get(), result.executedInstructions());
  }

  private record Observation(String output, int calls, long instructions) {}

  private static BuiltinExecutor executor(RandomSource source) {
    MonotonicClock clock = () -> 0L;
    return executor(source, clock, new ExecutionBudget("random.bsb", clock));
  }

  private static BuiltinExecutor executor(
      RandomSource source, MonotonicClock clock, ExecutionBudget budget) {
    ExecutionEnvironment.Builder builder = ExecutionEnvironment.builder(clock);
    if (source != null) builder.randomSource(source);
    return new BuiltinExecutor(
        "random.bsb",
        new BoundedOutput("random.bsb", new MemoryOutputSink()),
        budget,
        builder.build());
  }

  private static void execute(BuiltinExecutor executor, ArrayList<RuntimeValue> stack)
      throws RuntimeFailure {
    executor.execute(BuiltinDictionary.find("整数乱数を得る").orElseThrow(), stack, SPAN);
  }

  private static IntegerValue integer(long value) {
    return new IntegerValue(BigInteger.valueOf(value));
  }

  private static ArrayList<RuntimeValue> stack(BigInteger lower, BigInteger upper) {
    return new ArrayList<>(List.of(new IntegerValue(lower), new IntegerValue(upper)));
  }

  private static void put(byte[] bytes, BigInteger value) {
    Arrays.fill(bytes, (byte) 0);
    byte[] encoded = value.toByteArray();
    int length = Math.min(encoded.length, bytes.length);
    System.arraycopy(encoded, encoded.length - length, bytes, bytes.length - length, length);
  }
}
