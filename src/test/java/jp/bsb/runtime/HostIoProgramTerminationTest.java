package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class HostIoProgramTerminationTest {
  @Test
  void acceptsZeroAndPreservesPriorOutputForCode42() throws Exception {
    Run zero = runResource("IO-N011", ProgramControl.accepting());
    Run fortyTwo = runResource("IO-N012", ProgramControl.accepting());

    assertProgramExit(zero, 0);
    assertProgramExit(fortyTwo, 42);
    assertEquals("", zero.stdout().utf8Text());
    assertEquals("前\n", fortyTwo.stdout().utf8Text());
    assertEquals("記録\n", fortyTwo.stderr().utf8Text());
  }

  @Test
  void partialExitPathsAndUnreachableCapabilityCallsKeepTheirNormalPath() throws Exception {
    Run partial = runResource("IO-N013", ProgramControl.accepting());
    Run unreachableInput = runResource("IO-N014", ProgramControl.accepting());

    assertEquals(ExecutionTermination.Kind.COMPLETED, partial.result().termination().kind());
    assertEquals(
        ExecutionTermination.Kind.COMPLETED, unreachableInput.result().termination().kind());
    assertEquals("継続\n", partial.stdout().utf8Text());
    assertEquals("到達\n", unreachableInput.stdout().utf8Text());
  }

  @Test
  void acceptsOneAnd255AndDoesNotDiagnoseLowerStackValues() {
    Run one = runSource("メインとは （--）\n    1 を 終了する\nこと。\n", ProgramControl.accepting());
    Run maximum = runSource("メインとは （--）\n    99 255 を 終了する\nこと。\n", ProgramControl.accepting());

    assertProgramExit(one, 1);
    assertProgramExit(maximum, 255);
    assertEquals(
        new IntegerValue(BigInteger.valueOf(99)), maximum.result().finalDataStack().getLast());

    Run throughUserWord =
        runSource(
            "終えるとは （整数 --）\n"
                + "    終了する\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "    99 7 を 終える\n"
                + "こと。\n",
            ProgramControl.accepting());
    assertProgramExit(throughUserWord, 7);
    assertEquals(
        new IntegerValue(BigInteger.valueOf(99)),
        throughUserWord.result().finalDataStack().getLast());
  }

  @Test
  void rejectsOutOfRangeCodesWithoutConsumingThem() throws Exception {
    Run negative = runResource("IO-F016", ProgramControl.accepting());
    Run tooLarge = runResource("IO-F017", ProgramControl.accepting());

    assertFailure(negative, DiagnosticCode.E_EXIT_CODE_RANGE, "-1");
    assertFailure(tooLarge, DiagnosticCode.E_EXIT_CODE_RANGE, "256");
    assertEquals(
        new IntegerValue(BigInteger.valueOf(-1)), negative.result().finalDataStack().getLast());
    assertEquals(
        new IntegerValue(BigInteger.valueOf(256)), tooLarge.result().finalDataStack().getLast());
  }

  @Test
  void capabilityFailureDoesNotConsumeTheCodeOrLeakHostDetails() {
    ProgramControl failed =
        code -> {
          throw CapabilityException.failure(RuntimeCapability.PROCESS_EXIT, "exit");
        };
    Run run = runSource("メインとは （--）\n    42 を 終了する\nこと。\n", failed);

    assertFailure(run, DiagnosticCode.E_CAPABILITY_FAILURE, "能力失敗");
    assertEquals(new IntegerValue(BigInteger.valueOf(42)), run.result().finalDataStack().getLast());
    assertEquals(ExecutionTermination.Kind.DIAGNOSTIC_FAILURE, run.result().termination().kind());
  }

  @Test
  void unavailableExitCapabilityIsReportedBeforeChangingState() {
    Run run = runSource("メインとは （--）\n    42 を 終了する\nこと。\n", null);

    assertFailure(run, DiagnosticCode.E_CAPABILITY_UNAVAILABLE, "能力なし");
    assertEquals(new IntegerValue(BigInteger.valueOf(42)), run.result().finalDataStack().getLast());
  }

  @Test
  void delegatesExactlyOnceWithTheValidatedCode() {
    var calls = new AtomicInteger();
    var observed = new AtomicInteger(-1);
    Run run =
        runSource(
            "メインとは （--）\n    255 を 終了する\nこと。\n",
            code -> {
              calls.incrementAndGet();
              observed.set(code);
            });

    assertProgramExit(run, 255);
    assertEquals(1, calls.get());
    assertEquals(255, observed.get());
  }

  @Test
  void exitKeepsSavedValuesAndSkipsEveryFollowingInstruction() {
    String source =
        "保存先は 変数 0。\n\n"
            + "メインとは （--）\n"
            + "    99 を 保存先 に 入れる\n"
            + "    23 を 終了する\n"
            + "    「後」を 一行表示する\n"
            + "こと。\n";

    Run run = runSource(source, ProgramControl.accepting());

    assertProgramExit(run, 23);
    assertEquals(
        new IntegerValue(BigInteger.valueOf(99)),
        run.result().finalGlobalValues().getFirst().orElseThrow());
    assertEquals("", run.stdout().utf8Text());
  }

  private static void assertProgramExit(Run run, int code) {
    assertEquals(code, run.result().exitCode());
    assertEquals(ExecutionTermination.Kind.PROGRAM_EXIT, run.result().termination().kind());
    assertEquals(code, run.result().termination().programExitCode().orElseThrow());
    assertTrue(
        run.result().diagnostics().stream()
            .noneMatch(diagnostic -> diagnostic.severity() == jp.bsb.diagnostics.Severity.ERROR));
  }

  private static void assertFailure(Run run, DiagnosticCode code, String actual) {
    assertEquals(10, run.result().exitCode());
    assertEquals(code, run.result().diagnostics().getLast().code());
    assertEquals(actual, run.result().diagnostics().getLast().actual().orElseThrow());
  }

  private static Run runResource(String caseId, ProgramControl control) throws Exception {
    return run(caseId + ".bsb", source(caseId), control);
  }

  private static Run runSource(String source, ProgramControl control) {
    return run("exit.bsb", source.getBytes(java.nio.charset.StandardCharsets.UTF_8), control);
  }

  private static Run run(String sourceName, byte[] source, ProgramControl control) {
    var stdout = new MemoryOutputSink();
    var stderr = new MemoryOutputSink();
    ExecutionEnvironment.Builder builder =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleOutput(ConsoleOutput.fromOutputSink(stdout))
            .consoleError(ConsoleOutput.fromOutputSink(stderr));
    if (control != null) {
      builder.programControl(control);
    }
    ExecutionEnvironment environment = builder.build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                sourceName,
                source,
                new ExecutionContext(stdout, () -> 0L, TraceSink.none(), environment));
    return new Run(result, stdout, stderr);
  }

  private static byte[] source(String caseId) throws Exception {
    String resource = "/conformance/host-io/sources/" + caseId + ".bsb";
    try (var input = HostIoProgramTerminationTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private record Run(ProgramRunResult result, MemoryOutputSink stdout, MemoryOutputSink stderr) {}
}
