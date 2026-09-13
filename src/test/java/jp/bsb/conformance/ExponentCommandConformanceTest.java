package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jp.bsb.cli.BsbCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExponentCommandConformanceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void executesEveryCataloguedCommandWithAtomicStandardChannels() throws Exception {
    Map<String, ExponentConformanceData.LiteralSpec> literals =
        ExponentConformanceData.loadLiterals().stream()
            .collect(
                Collectors.toMap(
                    ExponentConformanceData.LiteralSpec::id,
                    Function.identity(),
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    int invocations = 0;
    for (var spec : ExponentConformanceData.loadCatalog().cases()) {
      if (spec.commands().isEmpty()) {
        continue;
      }
      byte[] source = sourceFor(spec, literals);
      Path path = temporaryDirectory.resolve(spec.id() + ".bsb");
      Files.write(path, source);
      for (String command : spec.commands()) {
        Invocation actual = invoke(command, path);
        int expectedExit = Integer.parseInt(spec.attributes().getOrDefault(command + ".exit", "0"));
        assertEquals(expectedExit, actual.exitCode(), spec.id() + '/' + command);
        assertArrayEquals(
            expectedStdout(spec, command, source), actual.stdout(), spec.id() + '/' + command);
        if (expectedExit == 0 || isTrue(spec, command + ".stderr.empty")) {
          assertEquals(0, actual.stderr().length, spec.id() + '/' + command);
        } else {
          assertTrue(actual.stderr().length > 0, spec.id() + '/' + command);
        }
        String error = new String(actual.stderr(), StandardCharsets.UTF_8);
        assertFalse(error.contains("NumberFormatException"), spec.id() + '/' + command);
        assertFalse(error.contains("ArithmeticException"), spec.id() + '/' + command);
        invocations++;
      }
    }
    assertEquals(51, invocations);
  }

  private static byte[] sourceFor(
      ExponentConformanceData.CaseSpec spec,
      Map<String, ExponentConformanceData.LiteralSpec> literals)
      throws Exception {
    String path = spec.attributes().get("source");
    if (path != null) {
      return ExponentConformanceData.resourceBytes(path);
    }
    String input = literals.get(spec.id()).input();
    if (spec.id().equals("EXP-F007")) {
      return ("メインとは （--）\n    " + input + "\n    を 一行表示する\nこと。\n")
          .getBytes(StandardCharsets.UTF_8);
    }
    return ("メインとは （--）\n    " + input + " を 一行表示する\nこと。\n").getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] expectedStdout(
      ExponentConformanceData.CaseSpec spec, String command, byte[] source) throws Exception {
    String expectedPath = spec.attributes().get(command + ".stdout.source");
    if (expectedPath != null) {
      return ExponentConformanceData.resourceBytes(expectedPath);
    }
    if (isTrue(spec, command + ".stdout.empty") || command.equals("check")) {
      return new byte[0];
    }
    if (command.equals("format")) {
      return source;
    }
    throw new IllegalArgumentException(spec.id() + ": missing stdout expectation for " + command);
  }

  private static boolean isTrue(ExponentConformanceData.CaseSpec spec, String key) {
    return Boolean.parseBoolean(spec.attributes().getOrDefault(key, "false"));
  }

  private static Invocation invoke(String command, Path source) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(new String[] {command, source.toString()}, stdout, stderr);
    return new Invocation(exit, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
