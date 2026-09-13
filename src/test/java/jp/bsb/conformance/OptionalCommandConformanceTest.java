package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import jp.bsb.cli.BsbCli;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptionalCommandConformanceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void executesEveryCataloguedPublicCommandAndMatchesBytes() throws Exception {
    int invocations = 0;
    for (var spec : OptionalConformanceData.loadCases()) {
      if (spec.source().equals("-")) {
        assertEquals(java.util.List.of("internal"), spec.commands(), spec.id());
        continue;
      }
      byte[] source = OptionalConformanceData.resourceBytes(spec.source());
      Path copied = temporaryDirectory.resolve(spec.id() + ".bsb");
      Files.write(copied, source);
      for (String command : spec.commands()) {
        Path path =
            command.equals("explain")
                ? Path.of("tests/conformance/optional-values").resolve(spec.source())
                : copied;
        Invocation actual =
            command.equals("explain")
                ? invoke("explain", "--json", path.toString())
                : invoke(command, path.toString());
        assertEquals(spec.exitFor(command), actual.exitCode(), spec.id() + '/' + command);
        byte[] comparableStdout =
            command.equals("explain")
                ? ExplainBuiltinCompatibility.withoutByteSequenceWords(actual.stdout())
                : actual.stdout();
        assertArrayEquals(
            expectedStdout(spec, command), comparableStdout, spec.id() + '/' + command);
        boolean diagnosticExpected =
            actual.exitCode() != 0 || spec.id().equals("OPT-F024") && !command.equals("format");
        if (diagnosticExpected) {
          assertTrue(actual.stderr().length > 0, spec.id() + '/' + command + " stderr");
        } else {
          assertEquals(0, actual.stderr().length, spec.id() + '/' + command + " stderr");
        }
        String error = new String(actual.stderr(), StandardCharsets.UTF_8);
        assertFalse(error.contains("Exception"), spec.id() + '/' + command);
        assertFalse(error.contains("Error"), spec.id() + '/' + command);
        invocations++;
      }
      assertArrayEquals(source, Files.readAllBytes(copied), spec.id() + ": input changed");
      if (!spec.canonical().equals("-")) {
        Invocation first = invoke("format", copied.toString());
        Path twice = temporaryDirectory.resolve(spec.id() + "-twice.bsb");
        Files.write(twice, first.stdout());
        Invocation second = invoke("format", twice.toString());
        assertEquals(0, second.exitCode(), spec.id());
        assertArrayEquals(first.stdout(), second.stdout(), spec.id() + ": format idempotence");
      }
    }
    assertEquals(125, invocations);
  }

  @Test
  void matchesIndependentFinalStateAndBudgetsForEveryExecutedCase() throws Exception {
    for (var spec : OptionalConformanceData.loadCases()) {
      if (spec.source().equals("-") || spec.finalStack().equals("-")) {
        continue;
      }
      byte[] source = OptionalConformanceData.resourceBytes(spec.source());
      var stdout = new MemoryOutputSink();
      var stderr = new MemoryOutputSink();
      var environment =
          ExecutionEnvironment.builder(() -> 0L)
              .consoleInput(new jp.bsb.runtime.MemoryConsoleInput(java.util.List.of()))
              .consoleOutput(stdout::write)
              .consoleError(stderr::write)
              .wallTime(() -> new jp.bsb.runtime.WallTimeReading(0L, 0))
              .build();
      var result =
          new ProgramRunner()
              .run(
                  spec.id() + ".bsb",
                  source,
                  new ExecutionContext(stdout, () -> 0L, TraceSink.none(), environment));
      assertEquals(spec.exitFor("run"), result.exitCode(), spec.id());
      assertEquals(Long.parseLong(spec.finalStack()), result.finalDataStack().size(), spec.id());
      assertEquals(
          Long.parseLong(spec.finalGlobals()), result.finalGlobalValues().size(), spec.id());
      assertEquals(Long.parseLong(spec.instructions()), result.executedInstructions(), spec.id());
      assertEquals(Long.parseLong(spec.outputBytes()), result.outputBytes(), spec.id());
      assertEquals(
          Long.parseLong(spec.jsonConstruction()), result.jsonConstructionUnits(), spec.id());
      assertEquals(Long.parseLong(spec.jsonWork()), result.jsonWorkUnits(), spec.id());
      assertEquals(0, result.errorOutputBytes(), spec.id());
      assertArrayEquals(expectedRunStdout(spec), stdout.bytes(), spec.id());
      assertArrayEquals(new byte[0], stderr.bytes(), spec.id());
    }
  }

  private static byte[] expectedStdout(OptionalConformanceData.CaseSpec spec, String command)
      throws Exception {
    return switch (command) {
      case "check" -> new byte[0];
      case "run" -> expectedRunStdout(spec);
      case "format" ->
          spec.canonical().equals("-")
              ? new byte[0]
              : OptionalConformanceData.resourceBytes(spec.canonical());
      case "explain" -> OptionalConformanceData.resourceBytes("explain/" + spec.id() + ".json");
      default -> throw new IllegalArgumentException(command);
    };
  }

  private static byte[] expectedRunStdout(OptionalConformanceData.CaseSpec spec) throws Exception {
    return spec.stdout().equals("-")
        ? new byte[0]
        : OptionalConformanceData.resourceBytes(spec.stdout());
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(exit, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
