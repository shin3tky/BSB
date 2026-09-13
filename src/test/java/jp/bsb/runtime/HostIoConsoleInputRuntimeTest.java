package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import org.junit.jupiter.api.Test;

class HostIoConsoleInputRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void streamAdapterPreservesAllNormativeLineBoundaries() throws Exception {
    byte[] raw = hex("4C460A43524C460D0A43520D4E4558540A454F46");
    Run run = run("IO-N001", new StreamConsoleInput(new ByteArrayInputStream(raw)), () -> 0L);

    assertTrue(run.result().successful(), run.result().diagnostics().toString());
    assertEquals("LF\nCRLF\nCR\nNEXT\nEOF\n", run.output().utf8Text());
  }

  @Test
  void lineEndCancelAndEmptyLineRemainDistinct() throws Exception {
    Run states =
        run(
            "IO-N002",
            new MemoryConsoleInput(
                List.of(
                    MemoryConsoleInput.rawLine("値".getBytes(StandardCharsets.UTF_8), 0),
                    new InputEvent.End(),
                    new InputEvent.Cancel())),
            () -> 0L);
    Run empty =
        run(
            "IO-N008",
            new MemoryConsoleInput(
                List.of(MemoryConsoleInput.rawLine(new byte[0], 1), new InputEvent.End())),
            () -> 0L);

    assertEquals("行\n終端\n取消\n", states.output().utf8Text());
    assertEquals("0\n終端\n", empty.output().utf8Text());
    assertTrue(states.result().successful(), states.result().diagnostics().toString());
    assertTrue(empty.result().successful(), empty.result().diagnostics().toString());
  }

  @Test
  void preservesBomCombiningAndSupplementaryScalars() throws Exception {
    Run run =
        run(
            "IO-N009",
            new StreamConsoleInput(new ByteArrayInputStream(hex("EFBBBFE3818BE38299F0A0AEB70A"))),
            () -> 0L);

    assertTrue(run.result().successful(), run.result().diagnostics().toString());
    assertEquals("4\n", run.output().utf8Text());
  }

  @Test
  void composesInputWithStringsAndExistingNumericConversions() throws Exception {
    Run stringInput =
        run(
            "IO-N003",
            new MemoryConsoleInput(
                List.of(MemoryConsoleInput.rawLine("山田".getBytes(StandardCharsets.UTF_8), 0))),
            () -> 0L);
    Run numericInput =
        run(
            "IO-N004",
            new MemoryConsoleInput(
                List.of(
                    MemoryConsoleInput.rawLine("-42".getBytes(StandardCharsets.UTF_8), 0),
                    MemoryConsoleInput.rawLine("1.2300".getBytes(StandardCharsets.UTF_8), 0))),
            () -> 0L);

    assertTrue(stringInput.result().successful(), stringInput.result().diagnostics().toString());
    assertTrue(numericInput.result().successful(), numericInput.result().diagnostics().toString());
    assertEquals("こんにちは、山田さん\n", stringInput.output().utf8Text());
    assertEquals("-42\n1.23\n", numericInput.output().utf8Text());
  }

  @Test
  void cancellationCanBeHandledAsANormalValue() throws Exception {
    Run run = run("IO-N010", new MemoryConsoleInput(List.of(new InputEvent.Cancel())), () -> 0L);

    assertTrue(run.result().successful(), run.result().diagnostics().toString());
    assertEquals("取消\n", run.output().utf8Text());
  }

  @Test
  void nonLineExtractionFailsAtomically() throws Exception {
    Run end = run("IO-F006", new MemoryConsoleInput(List.of(new InputEvent.End())), () -> 0L);
    Run cancel = run("IO-F007", new MemoryConsoleInput(List.of(new InputEvent.Cancel())), () -> 0L);

    assertFailure(end, DiagnosticCode.E_INPUT_RESULT_NOT_LINE);
    assertFailure(cancel, DiagnosticCode.E_INPUT_RESULT_NOT_LINE);
    assertEquals(InputResultValue.end(), end.result().finalDataStack().getLast());
    assertEquals(InputResultValue.cancel(), cancel.result().finalDataStack().getLast());
    assertEquals("", end.output().utf8Text());
    assertEquals("", cancel.output().utf8Text());
  }

  @Test
  void distinguishesUnavailableFailureAndStrictUtf8WithoutLeakingHostText() throws Exception {
    Run unavailable = run("IO-F008", null, () -> 0L);
    ConsoleInput failed =
        () -> {
          throw CapabilityException.failure(RuntimeCapability.CONSOLE_INPUT, "readLine");
        };
    Run failure = run("IO-F010", failed, () -> 0L);
    Run invalid =
        run(
            "IO-F011",
            new StreamConsoleInput(new ByteArrayInputStream(new byte[] {'a', (byte) 0xff, '\n'})),
            () -> 0L);

    assertFailure(unavailable, DiagnosticCode.E_CAPABILITY_UNAVAILABLE);
    assertFailure(failure, DiagnosticCode.E_CAPABILITY_FAILURE);
    assertFailure(invalid, DiagnosticCode.E_INPUT_UTF8);
    Diagnostic invalidDiagnostic = invalid.result().diagnostics().getFirst();
    assertEquals("1", invalidDiagnostic.fields().get("byteOffset"));
    assertEquals("FF", invalidDiagnostic.fields().get("bytes"));
    assertTrue(failure.result().finalDataStack().isEmpty());
    assertFalse(failure.result().diagnostics().getFirst().fields().toString().contains("java"));
  }

  @Test
  void enforcesLineAndCumulativeRawByteLimitsBeforeCreatingAValue() throws Exception {
    byte[] maximumLine = new byte[RuntimeLimits.INPUT_LINE_UTF8_BYTES];
    java.util.Arrays.fill(maximumLine, (byte) 'a');
    var oversized = new byte[RuntimeLimits.INPUT_LINE_UTF8_BYTES + 1];
    java.util.Arrays.fill(oversized, (byte) 'a');
    BoundedInput lineInput =
        boundedInput(new MemoryConsoleInput(List.of(MemoryConsoleInput.rawLine(oversized, 0))));
    RuntimeFailure lineFailure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> lineInput.readLine("一行を入力する", SPAN));
    assertEquals(DiagnosticCode.E_INPUT_LINE_LIMIT, lineFailure.diagnostic().code());

    ConsoleInput generated =
        new ConsoleInput() {
          private int calls;

          @Override
          public InputEvent readLine() {
            calls++;
            return calls <= 4
                ? new InputEvent.Line(maximumLine, maximumLine.length)
                : new InputEvent.Line(new byte[] {'x'}, 1);
          }
        };
    BoundedInput totalInput = boundedInput(generated);
    for (int index = 0; index < 4; index++) {
      assertEquals(
          RuntimeLimits.INPUT_LINE_UTF8_BYTES,
          totalInput.readLine("一行を入力する", SPAN).line().orElseThrow().length());
    }
    RuntimeFailure totalFailure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> totalInput.readLine("一行を入力する", SPAN));
    assertEquals(DiagnosticCode.E_INPUT_TOTAL_LIMIT, totalFailure.diagnostic().code());
    assertEquals("67108865", totalFailure.diagnostic().observed().orElseThrow());
  }

  @Test
  void inputDelegationTimeIsExcludedFromTheActiveTimeout() throws Exception {
    var now = new AtomicLong();
    ConsoleInput delayed =
        () -> {
          now.set(RuntimeLimits.ELAPSED_NANOS + 10_000_000_000L);
          return new InputEvent.End();
        };

    Run run = run("IO-F008", delayed, now::get);

    assertTrue(run.result().successful(), run.result().diagnostics().toString());
    assertTrue(run.result().finalDataStack().isEmpty());
  }

  @Test
  void activeTimeoutBoundaryRemainsExactAfterAnExcludedInputInterval() throws Exception {
    var now = new AtomicLong();
    var budget = new ExecutionBudget("input.bsb", now::get, 0, 0);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(now::get)
            .consoleInput(
                () -> {
                  now.set(3_600_000_000_000L);
                  return new InputEvent.End();
                })
            .build();
    var input = new BoundedInput("input.bsb", environment, budget);

    input.readLine("一行を入力する", SPAN);
    now.addAndGet(RuntimeLimits.ELAPSED_NANOS);
    budget.beforeInstruction(SPAN);
    now.incrementAndGet();
    RuntimeFailure failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> budget.beforeInstruction(SPAN));
    assertEquals(DiagnosticCode.E_EXECUTION_TIMEOUT, failure.diagnostic().code());
  }

  private static BoundedInput boundedInput(ConsoleInput input) {
    MonotonicClock clock = () -> 0L;
    ExecutionBudget budget = new ExecutionBudget("input.bsb", clock);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(clock).consoleInput(input).build();
    return new BoundedInput("input.bsb", environment, budget);
  }

  private static void assertFailure(Run run, DiagnosticCode code) {
    assertEquals(10, run.result().exitCode());
    assertEquals(code, run.result().diagnostics().getFirst().code());
  }

  private static Run run(String caseId, ConsoleInput input, MonotonicClock clock) throws Exception {
    var output = new MemoryOutputSink();
    ExecutionEnvironment.Builder builder =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(ConsoleOutput.fromOutputSink(output))
            .programControl(ProgramControl.accepting());
    if (input != null) {
      builder.consoleInput(input);
    }
    ExecutionEnvironment environment = builder.build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                caseId + ".bsb",
                source(caseId),
                new ExecutionContext(output, clock, TraceSink.none(), environment));
    return new Run(result, output);
  }

  private static byte[] source(String caseId) throws Exception {
    String resource = "/conformance/host-io/sources/" + caseId + ".bsb";
    try (var input = HostIoConsoleInputRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static byte[] hex(String text) {
    return java.util.HexFormat.of().parseHex(text);
  }

  private record Run(ProgramRunResult result, MemoryOutputSink output) {}
}
