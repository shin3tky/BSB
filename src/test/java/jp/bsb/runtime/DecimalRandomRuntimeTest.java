package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
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
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class DecimalRandomRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final int D = RuntimeLimits.DECIMAL_PRECISION;

  @Test
  void exhaustiveCandidateCyclesPreserveEachDecimalGridPointWithEqualMultiplicity()
      throws Exception {
    for (var range :
        List.of(
            new Range("0.0", "1.0", 1, 11), new Range("0.0", "1.0", 2, 101),
            new Range("0.0", "0.99", 2, 100), new Range("-1.0", "1.0", 2, 201),
            new Range("-2.0", "2.0", 0, 5), new Range("-0.009", "-0.001", 3, 9))) {
      var calls = new AtomicInteger();
      int cycle = 1 << BigInteger.valueOf(range.count() - 1).bitLength();
      var executor =
          executor(bytes -> put(bytes, BigInteger.valueOf(calls.getAndIncrement() % cycle)));
      for (int index = 0; index <= 2 * range.count(); index++) {
        var stack = stack(range.lower(), range.upper(), range.digits());
        stack.addFirst(new IntegerValue(BigInteger.valueOf(99)));
        execute(executor, stack);
        var expected =
            new DecimalValue(
                new BigDecimal(range.lower())
                    .add(
                        new BigDecimal(BigInteger.valueOf(index % range.count()), range.digits())));
        assertEquals(List.of(new IntegerValue(BigInteger.valueOf(99)), expected), stack);
        assertEquals(ValueType.DECIMAL, stack.getLast().type());
      }
      assertEquals(2 * cycle + 1, calls.get(), range.toString());
    }
  }

  @Test
  void rejectsCandidatesOutsideTheGridInsteadOfRoundingOrReducingThem() throws Exception {
    var candidates = new java.util.ArrayDeque<>(List.of(101, 127, 50));
    var stack = stack("0.0", "1.0", 2);
    var executor = executor(bytes -> bytes[0] = candidates.removeFirst().byteValue());
    execute(executor, stack);
    assertEquals(List.of(decimal("0.5")), stack);
    assertTrue(candidates.isEmpty());
    assertEquals("random.bytes", executor.effect());
  }

  @Test
  void handlesMaximumPrecisionEndpointsZeroSmallestStepAndAllNinesExactly() throws Exception {
    BigInteger power = BigInteger.TEN.pow(D);
    for (BigInteger offset : List.of(BigInteger.ZERO, power, power.shiftLeft(1))) {
      var stack = stack("-1.0", "1.0", D);
      execute(executor(bytes -> put(bytes, offset)), stack);
      assertEquals(List.of(new DecimalValue(new BigDecimal(offset.subtract(power), D))), stack);
      assertEquals(ValueType.DECIMAL, stack.getLast().type());
    }
    var smallest = stack("0.0", "1.0", D);
    execute(executor(bytes -> put(bytes, BigInteger.ONE)), smallest);
    var step = (DecimalValue) smallest.getLast();
    assertEquals(BigInteger.ONE, step.coefficient());
    assertEquals(D, step.scale());
    var nines = stack("0.0", "1.0", D);
    execute(executor(bytes -> put(bytes, power.subtract(BigInteger.ONE))), nines);
    var result = (DecimalValue) nines.getLast();
    assertEquals(D, result.precision());
    assertEquals(D, result.scale());
    assertEquals(power.subtract(BigInteger.ONE), result.coefficient());
  }

  @Test
  void checksTheRangeLimitBeforeDrawingAndAcceptsItsBoundary() throws Exception {
    var accepted = stack("-10.0", "10.0", D - 1);
    execute(executor(bytes -> put(bytes, BigInteger.ZERO)), accepted);
    assertEquals(List.of(decimal("-10.0")), accepted);
    for (var range :
        List.of(
            new Range("0.0", "1.1", D, 0),
            new Range("-1.1", "0.0", D, 0),
            new Range("10.1", "10.2", D - 1, 0))) {
      var inputs = stack(range.lower(), range.upper(), range.digits());
      assertFailure(
          executor(bytes -> fail("invalid range must not draw")),
          inputs,
          DiagnosticCode.E_RANDOM_RANGE_PRECISION_LIMIT);
    }
  }

  @Test
  void singletonUsesTheExistingValueWithoutConstructingAnOversizedCoefficient() throws Exception {
    var huge = new DecimalValue(BigInteger.TEN.pow(D).subtract(BigInteger.ONE), -D);
    var inputs =
        new ArrayList<RuntimeValue>(List.of(huge, huge, new IntegerValue(BigInteger.valueOf(D))));
    execute(executor(bytes -> fail("singleton must not draw")), inputs);
    assertSame(huge, inputs.getLast());
    var normal = stack("0.2500", "0.25", 2);
    execute(executor(bytes -> fail("normalized singleton must not draw")), normal);
    assertEquals(List.of(decimal("0.25")), normal);
    assertFailure(
        executor(null), stack("0.25", "0.25", 2), DiagnosticCode.E_CAPABILITY_UNAVAILABLE);
    assertFailure(
        executor(null), stack("0.005", "0.005", 2), DiagnosticCode.E_RANDOM_BOUND_NOT_ALIGNED);
  }

  @Test
  void preservesTheSpecifiedDiagnosticPriorityBeforeCapabilityLookup() {
    assertFailure(
        executor(null), stack("0.015", "0.005", -1), DiagnosticCode.E_RANDOM_DIGITS_OUT_OF_RANGE);
    assertFailure(
        executor(null), stack("0.015", "0.005", 2), DiagnosticCode.E_NUMERIC_RANGE_INVALID);
    RuntimeFailure lower =
        assertFailure(
            executor(null), stack("0.005", "0.015", 2), DiagnosticCode.E_RANDOM_BOUND_NOT_ALIGNED);
    assertEquals("lower", lower.diagnostic().fields().get("bound"));
    assertEquals("3", lower.diagnostic().fields().get("scale"));
    RuntimeFailure upper =
        assertFailure(
            executor(null), stack("0.0", "0.015", 2), DiagnosticCode.E_RANDOM_BOUND_NOT_ALIGNED);
    assertEquals("upper", upper.diagnostic().fields().get("bound"));
    RuntimeFailure precision =
        assertFailure(
            executor(null), stack("0.0", "2.0", D), DiagnosticCode.E_RANDOM_RANGE_PRECISION_LIMIT);
    assertEquals("0", precision.diagnostic().fields().get("maximumAbsoluteBoundExponent"));
    assertFailure(executor(null), stack("0.0", "1.0", 2), DiagnosticCode.E_CAPABILITY_UNAVAILABLE);
  }

  @Test
  void invalidDigitsAndGiantBoundaryDiagnosticsUseBoundedPreviews() {
    for (BigInteger digits :
        List.of(BigInteger.valueOf(-1), BigInteger.valueOf(D + 1), BigInteger.TEN.pow(D - 1))) {
      var inputs = stack("0.0", "1.0", 0);
      inputs.set(2, new IntegerValue(digits));
      RuntimeFailure failure =
          assertFailure(executor(null), inputs, DiagnosticCode.E_RANDOM_DIGITS_OUT_OF_RANGE);
      assertEquals("0", failure.diagnostic().fields().get("minimum"));
      assertEquals("65536", failure.diagnostic().fields().get("maximum"));
      assertTrue(failure.diagnostic().fields().get("digitsPreview").length() < 80);
      if (digits.bitLength() > 1000)
        assertEquals("65536", failure.diagnostic().fields().get("digitsCodePoints"));
    }
    var tiny = new DecimalValue(BigInteger.ONE, D);
    var misaligned =
        new ArrayList<RuntimeValue>(
            List.of(tiny, decimal("1.0"), new IntegerValue(BigInteger.ZERO)));
    var grid = assertFailure(executor(null), misaligned, DiagnosticCode.E_RANDOM_BOUND_NOT_ALIGNED);
    assertEquals("65538", grid.diagnostic().fields().get("valueCodePoints"));
    var huge = new DecimalValue(BigInteger.ONE, -D);
    var inputs =
        new ArrayList<RuntimeValue>(
            List.of(decimal("0.0"), huge, new IntegerValue(BigInteger.ONE)));
    var precision =
        assertFailure(executor(null), inputs, DiagnosticCode.E_RANDOM_RANGE_PRECISION_LIMIT);
    assertEquals("65539", precision.diagnostic().fields().get("upperCodePoints"));
    assertTrue(precision.diagnostic().fields().get("upperPreview").length() < 80);
  }

  @Test
  void exhaustionAndTheLastAcceptedCandidateShareTheIntegerAttemptBudget() throws Exception {
    var calls = new AtomicInteger();
    var executor =
        executor(
            bytes ->
                bytes[0] =
                    (byte) (calls.incrementAndGet() == RuntimeLimits.RANDOM_ATTEMPTS ? 100 : 127));
    var inputs = stack("0.0", "1.0", 2);
    execute(executor, inputs);
    assertEquals(List.of(decimal("1.0")), inputs);
    assertEquals(RuntimeLimits.RANDOM_ATTEMPTS, calls.get());
    calls.set(0);
    RuntimeFailure failure =
        assertFailure(
            executor(
                bytes -> {
                  calls.incrementAndGet();
                  bytes[0] = 127;
                }),
            stack("0.0", "1.0", 2),
            DiagnosticCode.E_CAPABILITY_FAILURE);
    assertEquals("sample", failure.diagnostic().fields().get("operation"));
    assertEquals(RuntimeLimits.RANDOM_ATTEMPTS, calls.get());
  }

  @Test
  void sourceFailuresKeepAllInputsAndHideHostDetails() {
    for (RandomSource source :
        List.<RandomSource>of(
            bytes -> {
              throw CapabilityException.failure(RuntimeCapability.RANDOM_BYTES, "nextBytes");
            },
            bytes -> {
              bytes[0] = 0;
              throw new IllegalArgumentException("host-private-secret");
            })) {
      RuntimeFailure failure =
          assertFailure(
              executor(source), stack("0.0", "1.0", 2), DiagnosticCode.E_CAPABILITY_FAILURE);
      assertEquals("nextBytes", failure.diagnostic().fields().get("operation"));
      assertFalse(failure.diagnostic().toString().contains("host-private-secret"));
    }
  }

  @Test
  void checksTimeAfterCoefficientConstructionDrawingAndNormalizationWithoutCountingIr() {
    for (int expiresAt : new int[] {1, 3, 4}) {
      var reads = new AtomicInteger();
      MonotonicClock clock =
          () -> reads.incrementAndGet() >= expiresAt ? RuntimeLimits.ELAPSED_NANOS + 1 : 0L;
      var budget = new ExecutionBudget("decimal-random.bsb", clock, 0, 0);
      var calls = new AtomicInteger();
      var executor =
          executor(
              bytes -> {
                calls.incrementAndGet();
                bytes[0] = 0;
              },
              clock,
              budget);
      assertFailure(executor, stack("0.0", "1.0", 2), DiagnosticCode.E_EXECUTION_TIMEOUT);
      assertEquals(expiresAt == 1 ? 0 : 1, calls.get());
      assertEquals(0, budget.executed());
    }
    var now = new AtomicLong();
    MonotonicClock clock = now::get;
    var budget = new ExecutionBudget("decimal-random.bsb", clock, 0, 0);
    assertFailure(
        executor(
            bytes -> {
              bytes[0] = 127;
              now.set(RuntimeLimits.ELAPSED_NANOS + 1);
            },
            clock,
            budget),
        stack("0.0", "1.0", 2),
        DiagnosticCode.E_EXECUTION_TIMEOUT);
  }

  @Test
  void mixedIntegerAndDecimalCallsUseOneStateAndTracingDoesNotChangeIt() {
    assertEquals(observe(false), observe(true));
    var expected = RandomSource.seeded(31415);
    byte[] bytes = new byte[1];
    var expectedOutput = new StringBuilder();
    for (int index = 0; index < 20; index++) {
      try {
        expected.nextBytes(bytes);
        expectedOutput.append(bytes[0] & 7).append('\n');
        expected.nextBytes(bytes);
        expectedOutput
            .append(new BigDecimal(BigInteger.valueOf((bytes[0] & 7) - 4), 1).toPlainString())
            .append('\n');
      } catch (CapabilityException failure) {
        throw new AssertionError(failure);
      }
    }
    assertEquals(expectedOutput.toString(), observe(false).output());
  }

  private static Observation observe(boolean tracing) {
    var output = new MemoryOutputSink();
    var calls = new AtomicInteger();
    var events = new ArrayList<TraceEvent>();
    RandomSource source = RandomSource.seeded(31415);
    MonotonicClock clock = () -> 0L;
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .randomSource(
                bytes -> {
                  calls.incrementAndGet();
                  source.nextBytes(bytes);
                })
            .build();
    String program =
        "メインとは （--）\n"
            + ("    0 7 整数乱数を得る 一行表示する\n" + "    -0.4 0.3 1 小数乱数を得る 一行表示する\n").repeat(20)
            + "こと。\n";
    var result =
        new ProgramRunner()
            .run(
                "decimal-random.bsb",
                program.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(
                    output, clock, tracing ? events::add : TraceSink.none(), environment));
    assertEquals(0, result.exitCode(), result.diagnostics().toString());
    assertEquals(40, calls.get());
    if (tracing)
      assertEquals(
          40, events.stream().filter(event -> event.effect().equals("random.bytes")).count());
    return new Observation(output.utf8Text(), calls.get(), result.executedInstructions());
  }

  private record Observation(String output, int calls, long instructions) {}

  private record Range(String lower, String upper, int digits, int count) {}

  private static RuntimeFailure assertFailure(
      BuiltinExecutor executor, ArrayList<RuntimeValue> inputs, DiagnosticCode code) {
    var before = List.copyOf(inputs);
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> execute(executor, inputs));
    assertEquals(code, failure.diagnostic().code());
    assertEquals(before, inputs);
    assertEquals("", executor.effect());
    return failure;
  }

  private static DecimalValue decimal(String value) {
    return new DecimalValue(new BigDecimal(value));
  }

  private static ArrayList<RuntimeValue> stack(String lower, String upper, int digits) {
    return new ArrayList<>(
        List.of(decimal(lower), decimal(upper), new IntegerValue(BigInteger.valueOf(digits))));
  }

  private static BuiltinExecutor executor(RandomSource source) {
    MonotonicClock clock = () -> 0L;
    return executor(source, clock, new ExecutionBudget("decimal-random.bsb", clock));
  }

  private static BuiltinExecutor executor(
      RandomSource source, MonotonicClock clock, ExecutionBudget budget) {
    var builder = ExecutionEnvironment.builder(clock);
    if (source != null) builder.randomSource(source);
    return new BuiltinExecutor(
        "decimal-random.bsb",
        new BoundedOutput("decimal-random.bsb", new MemoryOutputSink()),
        budget,
        builder.build());
  }

  private static void execute(BuiltinExecutor executor, ArrayList<RuntimeValue> stack)
      throws RuntimeFailure {
    executor.execute(BuiltinDictionary.find("小数乱数を得る").orElseThrow(), stack, SPAN);
  }

  private static void put(byte[] bytes, BigInteger value) {
    Arrays.fill(bytes, (byte) 0);
    byte[] encoded = value.toByteArray();
    int length = Math.min(encoded.length, bytes.length);
    System.arraycopy(encoded, encoded.length - length, bytes, bytes.length - length, length);
  }
}
