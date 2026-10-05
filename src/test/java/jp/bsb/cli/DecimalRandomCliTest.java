package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecimalRandomCliTest {
  @TempDir Path directory;

  @Test
  void checkRunFormatAndExplainExposeTheDecimalRandomWord() throws Exception {
    Path source = source("0.0 と 1.0 と 2 で 小数乱数を得る 一行表示する");
    assertEquals(0, invoke("check", source.toString()).exitCode());
    Invocation run = invoke("run", source.toString());
    assertEquals(0, run.exitCode(), run.stderr());
    var value = new java.math.BigDecimal(run.stdout().strip());
    assertTrue(value.signum() >= 0 && value.compareTo(java.math.BigDecimal.ONE) <= 0);
    assertTrue(value.scale() <= 2);
    assertEquals("", run.stderr());
    Invocation formatted = invoke("format", source.toString());
    assertEquals(0, formatted.exitCode());
    assertTrue(formatted.stdout().contains("小数乱数を得る"));
    Invocation explained = invoke("explain", "--json", source.toString());
    assertEquals(0, explained.exitCode(), explained.stderr());
    assertTrue(explained.stdout().contains("\"featureGroup\":\"RNG\""));
    assertTrue(explained.stdout().contains("\"capabilities\":[\"random.bytes\"]"));
    assertTrue(explained.stdout().contains("\"effects\":[\"random.bytes\"]"));
  }

  @Test
  void staticAndRuntimeFailuresUseThePublicDiagnosticsWithoutHostExceptions() throws Exception {
    for (String body : new String[] {"0 1.0 2 小数乱数を得る 一行表示する", "0.0 2 小数乱数を得る 一行表示する"}) {
      Invocation checked = invoke("check", source(body).toString());
      assertEquals(8, checked.exitCode());
      assertTrue(
          checked
              .stderr()
              .contains(body.startsWith("0 ") ? "E_TYPE_MISMATCH" : "E_STACK_UNDERFLOW"));
    }
    for (var failure :
        java.util.List.of(
            new Failure("0.0 1.0 -1", "E_RANDOM_DIGITS_OUT_OF_RANGE"),
            new Failure("0.005 0.015 2", "E_RANDOM_BOUND_NOT_ALIGNED"),
            new Failure("0.0 2.0 65536", "E_RANDOM_RANGE_PRECISION_LIMIT"),
            new Failure("1.0 0.0 2", "E_NUMERIC_RANGE_INVALID"))) {
      Path path = source(failure.inputs() + " 小数乱数を得る 一行表示する");
      assertEquals(0, invoke("check", path.toString()).exitCode());
      Invocation run = invoke("run", path.toString());
      assertEquals(10, run.exitCode(), run.stderr());
      assertTrue(run.stderr().contains(failure.code()));
      assertTrue(!run.stderr().contains("java.") && !run.stderr().contains("Exception"));
      assertEquals("", run.stdout());
    }
  }

  private record Failure(String inputs, String code) {}

  private Path source(String body) throws Exception {
    Path path = directory.resolve("random.bsb");
    Files.writeString(path, "メインとは （--）\n    " + body + "\nこと。\n");
    return path;
  }

  private static Invocation invoke(String... args) {
    var output = new ByteArrayOutputStream();
    var error = new ByteArrayOutputStream();
    int code = new BsbCli().run(args, output, error);
    return new Invocation(
        code, output.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
