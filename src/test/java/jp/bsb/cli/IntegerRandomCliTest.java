package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegerRandomCliTest {
  @TempDir Path directory;

  @Test
  void checkRunFormatAndExplainExposeTheIntegerRandomWord() throws Exception {
    Path source = source("1 と 6 から 整数乱数を得る 一行表示する");
    assertEquals(0, invoke("check", source.toString()).exitCode());
    Invocation run = invoke("run", source.toString());
    assertEquals(0, run.exitCode(), run.stderr());
    assertTrue(run.stdout().matches("[1-6]\\n"));
    assertEquals("", run.stderr());
    Invocation formatted = invoke("format", source.toString());
    assertEquals(0, formatted.exitCode());
    assertTrue(formatted.stdout().contains("整数乱数を得る"));
    Invocation explained = invoke("explain", "--json", source.toString());
    assertEquals(0, explained.exitCode(), explained.stderr());
    assertTrue(explained.stdout().contains("\"featureGroup\":\"RNG\""));
    assertTrue(explained.stdout().contains("\"capabilities\":[\"random.bytes\"]"));
    assertTrue(explained.stdout().contains("\"effects\":[\"random.bytes\"]"));
  }

  @Test
  void rejectsDecimalsAndMissingBoundsStaticallyAndReversedBoundsAtRuntime() throws Exception {
    for (String body : new String[] {"1.0 6 整数乱数を得る 一行表示する", "6 整数乱数を得る 一行表示する"}) {
      Invocation checked = invoke("check", source(body).toString());
      assertTrue(checked.exitCode() != 0);
      assertTrue(
          checked
              .stderr()
              .contains(body.startsWith("1.0") ? "E_TYPE_MISMATCH" : "E_STACK_UNDERFLOW"));
    }
    Path reversed = source("6 1 整数乱数を得る 一行表示する");
    assertEquals(0, invoke("check", reversed.toString()).exitCode());
    Invocation run = invoke("run", reversed.toString());
    assertEquals(10, run.exitCode());
    assertTrue(run.stderr().contains("E_NUMERIC_RANGE_INVALID"));
    assertEquals("", run.stdout());
  }

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
