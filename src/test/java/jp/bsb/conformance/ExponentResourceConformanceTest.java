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

class ExponentResourceConformanceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void executesAllGeneratedBoundariesWithFiniteAtomicResults() throws Exception {
    var generator = ExponentConformanceData.loadGenerator();
    int consumed = 0;
    for (var spec : ExponentConformanceData.loadResources()) {
      String key = generatorKey(spec);
      String literal = generator.literals().get(key).literal();
      if (spec.target().equals("javaFailureDisclosure")) {
        Invocation result = invoke("check", source(literal), spec.id() + "-disclosure.bsb");
        String error = new String(result.stderr(), StandardCharsets.UTF_8);
        assertFalse(error.contains("NumberFormatException"));
        assertFalse(error.contains("ArithmeticException"));
        assertTrue(error.contains(Integer.toString(generator.expectedObserved())));
        assertEquals(0, Integer.parseInt(spec.observed()));
        consumed++;
        continue;
      }

      byte[] source = source(literal);
      Invocation result = invoke(spec.command(), source, spec.id() + '-' + spec.variant() + ".bsb");
      assertEquals(spec.exit(), result.exitCode(), spec.id() + '/' + spec.variant());
      if (spec.exit() == 0) {
        assertEquals(0, result.stderr().length, spec.id() + '/' + spec.variant());
      } else {
        assertEquals(0, result.stdout().length, spec.id() + '/' + spec.variant());
        String error = new String(result.stderr(), StandardCharsets.UTF_8);
        assertTrue(error.contains(spec.code()), spec.id() + '/' + spec.variant());
        assertTrue(error.contains(spec.limit()), spec.id() + '/' + spec.variant());
        assertTrue(error.contains(spec.observed()), spec.id() + '/' + spec.variant());
      }
      if (spec.command().equals("run")) {
        String output = new String(result.stdout(), StandardCharsets.UTF_8);
        assertEquals(
            generator.expectedCodePoints() + 1,
            output.codePointCount(0, output.length()),
            spec.id());
        assertTrue(output.endsWith(".0\n"));
        assertTrue(output.chars().noneMatch(character -> character == 'e' || character == 'E'));
      } else if (spec.command().equals("format") && spec.exit() == 0) {
        assertArrayEquals(source, result.stdout(), spec.id());
        Invocation second = invoke("format", result.stdout(), spec.id() + "-twice.bsb");
        assertEquals(0, second.exitCode());
        assertArrayEquals(result.stdout(), second.stdout(), spec.id());
      } else if (spec.exit() == 0) {
        assertEquals(0, result.stdout().length, spec.id() + '/' + spec.variant());
      }
      consumed++;
    }
    assertEquals(16, consumed);
  }

  private Invocation invoke(String command, byte[] source, String name) throws Exception {
    Path path = temporaryDirectory.resolve(name);
    Files.write(path, source);
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(new String[] {command, path.toString()}, stdout, stderr);
    return new Invocation(exit, stdout.toByteArray(), stderr.toByteArray());
  }

  private static byte[] source(String literal) {
    return ("メインとは （--）\n    " + literal + " を 一行表示する\nこと。\n").getBytes(StandardCharsets.UTF_8);
  }

  private static String generatorKey(ExponentConformanceData.ResourceSpec spec) {
    return switch (spec.id()) {
      case "EXP-R001", "EXP-R002", "EXP-R003", "EXP-R004", "EXP-R005", "EXP-R006" ->
          spec.id() + '.' + spec.variant() + ".literal";
      case "EXP-R007" -> "EXP-R007.literal";
      case "EXP-R008" ->
          spec.target().equals("fixedDisplayCodePoints")
              ? "EXP-R008.run.literal"
              : "EXP-R008.format.literal";
      default -> throw new IllegalArgumentException("unknown resource: " + spec.id());
    };
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
