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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecimalTextCommandConformanceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void executesAllNormalAndFailureCommandsWithAtomicChannelsAndIdempotentFormat() throws Exception {
    var catalog = DecimalTextConformanceData.loadCatalog();
    int invocations = 0;
    for (String id : catalog.normalIds()) {
      byte[] source = normalSource(id, catalog);
      byte[] expectedOutput = normalOutput(id, catalog);
      invocations += assertCommands(id, source, 0, expectedOutput);
    }
    for (DecimalTextConformanceData.DiagnosticSpec spec :
        DecimalTextConformanceData.loadDiagnostics()) {
      String input = DecimalTextConformanceData.diagnosticInput(spec);
      byte[] source = failureSource(input, spec.word());
      invocations += assertCommands(spec.id(), source, 10, new byte[0]);
    }
    assertEquals(60, invocations);
  }

  private int assertCommands(String id, byte[] source, int runExit, byte[] expectedRunOutput)
      throws Exception {
    Path path = temporaryDirectory.resolve(id + ".bsb");
    Files.write(path, source);

    Invocation check = invoke("check", path);
    assertEquals(0, check.exitCode(), id + "/check");
    assertEquals(0, check.stdout().length, id + "/check stdout");
    assertEquals(0, check.stderr().length, id + "/check stderr");

    Invocation run = invoke("run", path);
    assertEquals(runExit, run.exitCode(), id + "/run");
    assertArrayEquals(expectedRunOutput, run.stdout(), id + "/run stdout");
    if (runExit == 0) {
      assertEquals(0, run.stderr().length, id + "/run stderr");
    } else {
      assertTrue(run.stderr().length > 0, id + "/run stderr");
      String error = new String(run.stderr(), StandardCharsets.UTF_8);
      assertFalse(error.contains("NumberFormatException"), id);
      assertFalse(error.contains("ArithmeticException"), id);
    }

    Invocation format = invoke("format", path);
    assertEquals(0, format.exitCode(), id + "/format");
    assertEquals(0, format.stderr().length, id + "/format stderr");
    Path secondPath = temporaryDirectory.resolve(id + "-twice.bsb");
    Files.write(secondPath, format.stdout());
    Invocation second = invoke("format", secondPath);
    assertEquals(0, second.exitCode(), id + "/format twice");
    assertArrayEquals(format.stdout(), second.stdout(), id + "/format idempotence");
    assertArrayEquals(source, Files.readAllBytes(path), id + "/input unchanged");
    return 3;
  }

  private static byte[] normalSource(String id, DecimalTextConformanceData.Catalog catalog)
      throws Exception {
    if (id.equals("DTXT-N001")) {
      StringBuilder source = new StringBuilder("メインとは （--）\n");
      for (var spec : DecimalTextConformanceData.loadDecimalTexts()) {
        if (spec.id().equals(id)) {
          source.append("    「").append(spec.input()).append("」 を 文字列を小数に変換する を 一行表示する\n");
        }
      }
      return source.append("こと。\n").toString().getBytes(StandardCharsets.UTF_8);
    }
    String path = catalog.normalCases().get(id).attributes().get("source");
    return DecimalTextConformanceData.resourceBytes(path);
  }

  private static byte[] normalOutput(String id, DecimalTextConformanceData.Catalog catalog)
      throws Exception {
    if (id.equals("DTXT-N001")) {
      String output =
          DecimalTextConformanceData.loadDecimalTexts().stream()
              .filter(spec -> spec.id().equals(id))
              .map(DecimalTextConformanceData.DecimalTextSpec::valueDisplay)
              .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
      return output.getBytes(StandardCharsets.UTF_8);
    }
    String path = catalog.normalCases().get(id).attributes().get("stdout");
    return DecimalTextConformanceData.resourceBytes(path);
  }

  private static byte[] failureSource(String input, String word) {
    return ("メインとは （--）\n    「" + input + "」 を " + word + " を 一行表示する\nこと。\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static Invocation invoke(String command, Path source) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(new String[] {command, source.toString()}, stdout, stderr);
    return new Invocation(exit, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
