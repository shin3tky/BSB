package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jp.bsb.cli.BsbCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecimalTextResourceConformanceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void executesAllGeneratedBoundariesWithFiniteAtomicResults() throws Exception {
    Map<String, DecimalTextConformanceData.GeneratedLiteral> generated =
        DecimalTextConformanceData.loadGeneratedLiterals();
    int consumed = 0;
    for (var spec : DecimalTextConformanceData.loadResources()) {
      String literal = generated.get(spec.generatorKey()).literal();
      byte[] source = source(literal);
      Invocation result = invoke(spec.command(), source, spec.id() + '-' + spec.target() + ".bsb");
      assertEquals(spec.exit(), result.exitCode(), spec.id() + '/' + spec.target());
      String error = new String(result.stderr(), StandardCharsets.UTF_8);
      assertFalse(error.contains("NumberFormatException"), spec.id());
      assertFalse(error.contains("ArithmeticException"), spec.id());

      if (spec.target().equals("javaFailureDisclosure")) {
        assertTrue(error.contains("65537"), spec.id());
        consumed++;
        continue;
      }
      if (spec.exit() == 0) {
        assertEquals(0, result.stderr().length, spec.id() + '/' + spec.variant());
      } else {
        assertEquals(0, result.stdout().length, spec.id() + '/' + spec.variant());
        assertTrue(error.contains(spec.code()), spec.id() + '/' + spec.variant());
        assertTrue(error.contains(spec.limit()), spec.id() + '/' + spec.variant());
        assertTrue(error.contains(spec.observed()), spec.id() + '/' + spec.variant());
      }
      if (spec.command().equals("format")) {
        Invocation second = invoke("format", result.stdout(), spec.id() + "-twice.bsb");
        assertEquals(0, second.exitCode(), spec.id());
        assertArrayEquals(result.stdout(), second.stdout(), spec.id());
      }
      if (spec.target().equals("fixedDisplayCodePoints")) {
        String output = new String(result.stdout(), StandardCharsets.UTF_8);
        assertEquals(
            Integer.parseInt(spec.observed()) + 1, output.codePointCount(0, output.length()));
        assertTrue(output.endsWith(".0\n"));
        assertTrue(output.chars().noneMatch(character -> character == 'e' || character == 'E'));
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
    return ("メインとは （--）\n    「" + literal + "」 を 文字列を小数に変換する を 一行表示する\nこと。\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
