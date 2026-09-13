package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonJsonCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void keepsNormalCommandChannelsAndExitCodesForJsonPrograms() throws IOException {
    String source =
        "値は 定数 「{\"x\":1}」 JSONを解析する。\n\n"
            + "JSONを保つとは （JSON -- JSON）\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    値 を JSONを保つ JSONを文字列に変換する を 一行表示する\n"
            + "こと。\n";
    String canonical =
        "値は 定数 「{\"x\":1}」 JSONを解析する。\n\n"
            + "JSONを保つとは （JSON -- JSON）\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    値 を JSONを保つ\n"
            + "    JSONを文字列に変換する\n"
            + "    を 一行表示する\n"
            + "こと。\n";
    Path path = temporaryDirectory.resolve("json-cli.bsb");
    Files.writeString(path, source, StandardCharsets.UTF_8);

    Invocation checked = invoke("check", path.toString());
    Invocation run = invoke("run", path.toString());
    Invocation formatted = invoke("format", path.toString());
    Invocation explained = invoke("explain", "--json", path.toString());

    assertEquals(0, checked.exitCode());
    assertEquals("", checked.stdout());
    assertEquals("", checked.stderr());
    assertEquals(0, run.exitCode());
    assertEquals("{\"x\":1}\n", run.stdout());
    assertEquals("", run.stderr());
    assertEquals(0, formatted.exitCode());
    assertEquals(canonical, formatted.stdout());
    assertEquals("", formatted.stderr());
    assertEquals(0, explained.exitCode());
    assertEquals("", explained.stderr());

    Map<String, Object> document = object(StrictJsonParser.parse(explained.stdout()));
    assertEquals(new java.math.BigDecimal("1"), document.get("schemaVersion"));
    assertTrue(explained.stdout().contains("\"featureGroup\":\"JSON\""));
    assertTrue(explained.stdout().contains("\"inputs\":[\"JSON\"]"));
    assertTrue(explained.stdout().contains("\"type\":\"JSON\""));
    assertFalse(explained.stdout().contains("{\"x\":1}"));
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(
        exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
