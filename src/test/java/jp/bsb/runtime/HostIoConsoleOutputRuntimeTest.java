package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import org.junit.jupiter.api.Test;

class HostIoConsoleOutputRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void writesNormativeStandardOutputAndErrorBytesIndependently() throws Exception {
    Run stdout = run("IO-N005", accepting(true, true));
    Run stderr = run("IO-N006", accepting(true, true));
    Run interleaved = run("IO-N007", accepting(true, true));

    assertSuccessful(stdout, "AB\n\n", "");
    assertSuccessful(stderr, "", "AB\n\n");
    assertSuccessful(interleaved, "O1\nO2\n", "E1\nE2\n");
    assertEquals(6, interleaved.result().outputBytes());
    assertEquals(6, interleaved.result().errorOutputBytes());
  }

  @Test
  void delegatesEachValueAndLineFeedAsOneCompleteCall() throws Exception {
    var calls = new ArrayList<byte[]>();
    Environment environment = accepting(true, false, calls, new ArrayList<>());

    Run run = run("IO-N005", environment);

    assertTrue(run.result().successful());
    assertEquals(3, calls.size());
    assertArrayEquals("A".getBytes(StandardCharsets.UTF_8), calls.get(0));
    assertArrayEquals("B\n".getBytes(StandardCharsets.UTF_8), calls.get(1));
    assertArrayEquals("\n".getBytes(StandardCharsets.UTF_8), calls.get(2));
  }

  @Test
  void reportsUnavailableAndFailedCapabilitiesWithoutConsumingValues() throws Exception {
    Run unavailable = run("IO-F009", accepting(true, false));
    Run stdoutFailure =
        run(
            "IO-F014",
            environment(
                bytes -> {
                  throw CapabilityException.failure(RuntimeCapability.CONSOLE_OUTPUT, "write");
                },
                null));
    Run stderrFailure =
        run(
            "IO-F015",
            environment(
                bytes -> {},
                bytes -> {
                  throw CapabilityException.failure(RuntimeCapability.CONSOLE_ERROR, "write");
                }));

    assertDiagnostic(unavailable, DiagnosticCode.E_CAPABILITY_UNAVAILABLE, "console.error");
    assertDiagnostic(stdoutFailure, DiagnosticCode.E_CAPABILITY_FAILURE, "console.output");
    assertDiagnostic(stderrFailure, DiagnosticCode.E_CAPABILITY_FAILURE, "console.error");
    assertEquals(new StringValue("失敗"), stdoutFailure.result().finalDataStack().getLast());
    assertEquals(new StringValue("途中"), stderrFailure.result().finalDataStack().getLast());
    assertEquals(0, stdoutFailure.result().outputBytes());
    assertEquals(0, stderrFailure.result().errorOutputBytes());
  }

  @Test
  void stdoutAndStderrBudgetsAcceptTheLimitAndRejectOneMoreAtomically() throws Exception {
    var stdoutSink = new MemoryOutputSink();
    var stdout = new BoundedOutput("output.bsb", stdoutSink, RuntimeLimits.OUTPUT_UTF8_BYTES - 1);
    stdout.write(new byte[] {'x'}, "表示する", SPAN);
    RuntimeFailure stdoutFailure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> stdout.write(new byte[] {'y'}, "表示する", SPAN));

    var stderrCalls = new ArrayList<byte[]>();
    var stderr =
        BoundedOutput.error(
            "error.bsb",
            bytes -> stderrCalls.add(bytes.clone()),
            RuntimeLimits.OUTPUT_UTF8_BYTES - 1);
    stderr.write(new byte[] {'x'}, "エラー表示する", SPAN);
    RuntimeFailure stderrFailure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class, () -> stderr.write(new byte[] {'y'}, "エラー表示する", SPAN));

    assertEquals(DiagnosticCode.E_OUTPUT_LIMIT, stdoutFailure.diagnostic().code());
    assertEquals(DiagnosticCode.E_ERROR_OUTPUT_LIMIT, stderrFailure.diagnostic().code());
    assertEquals(1, stdoutSink.bytes().length);
    assertEquals(1, stderrCalls.size());
    assertEquals(RuntimeLimits.OUTPUT_UTF8_BYTES, stdout.writtenBytes());
    assertEquals(RuntimeLimits.OUTPUT_UTF8_BYTES, stderr.writtenBytes());
  }

  private static void assertSuccessful(Run run, String stdout, String stderr) {
    assertTrue(run.result().successful(), run.result().diagnostics().toString());
    assertEquals(stdout, run.stdout().utf8Text());
    assertEquals(stderr, run.stderr().utf8Text());
  }

  private static void assertDiagnostic(Run run, DiagnosticCode code, String capability) {
    assertEquals(10, run.result().exitCode());
    Diagnostic diagnostic = run.result().diagnostics().getLast();
    assertEquals(code, diagnostic.code());
    assertEquals(capability, diagnostic.fields().get("capability"));
  }

  private static Environment accepting(boolean stdout, boolean stderr) {
    return accepting(stdout, stderr, new ArrayList<>(), new ArrayList<>());
  }

  private static Environment accepting(
      boolean stdout, boolean stderr, List<byte[]> stdoutCalls, List<byte[]> stderrCalls) {
    return environment(
        stdout ? bytes -> stdoutCalls.add(bytes.clone()) : null,
        stderr ? bytes -> stderrCalls.add(bytes.clone()) : null);
  }

  private static Environment environment(ConsoleOutput stdout, ConsoleOutput stderr) {
    return new Environment(stdout, stderr);
  }

  private static Run run(String caseId, Environment supplied) throws Exception {
    var stdout = new MemoryOutputSink();
    var stderr = new MemoryOutputSink();
    ExecutionEnvironment.Builder builder = ExecutionEnvironment.builder(() -> 0L);
    if (supplied.stdout() != null) {
      builder.consoleOutput(
          bytes -> {
            supplied.stdout().write(bytes);
            stdout.write(bytes);
          });
    }
    if (supplied.stderr() != null) {
      builder.consoleError(
          bytes -> {
            supplied.stderr().write(bytes);
            stderr.write(bytes);
          });
    }
    builder.programControl(ProgramControl.accepting());
    ExecutionEnvironment environment = builder.build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                caseId + ".bsb",
                source(caseId),
                new ExecutionContext(stdout, () -> 0L, TraceSink.none(), environment));
    return new Run(result, stdout, stderr);
  }

  private static byte[] source(String caseId) throws Exception {
    String resource = "/conformance/host-io/sources/" + caseId + ".bsb";
    try (var input = HostIoConsoleOutputRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private record Environment(ConsoleOutput stdout, ConsoleOutput stderr) {}

  private record Run(ProgramRunResult result, MemoryOutputSink stdout, MemoryOutputSink stderr) {}
}
