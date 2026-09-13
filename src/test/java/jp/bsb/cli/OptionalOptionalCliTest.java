package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptionalOptionalCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void checkRunFormatAndExplainExposeTheOptionalContract() throws IOException {
    Path source = temporaryDirectory.resolve("optional-cli.bsb");
    Files.writeString(
        source,
        "メインとは （--）\n"
            + "  「{\"x\":1}」を JSONを解析する 「x」を JSONオブジェクトから任意値を取り出す\n"
            + "  任意から値を取り出す 一行表示する\n"
            + "こと。\n",
        StandardCharsets.UTF_8);

    Invocation checked = invoke("check", source.toString());
    Invocation run = invoke("run", source.toString());
    Invocation formatted = invoke("format", source.toString());
    Invocation explained = invoke("explain", "--json", source.toString());

    assertEquals(0, checked.exitCode());
    assertEquals("1\n", run.stdout());
    assertEquals("", run.stderr());
    assertEquals(0, formatted.exitCode());
    assertTrue(formatted.stdout().contains("任意値を取り出す\n"));
    assertEquals(0, explained.exitCode());
    assertTrue(explained.stdout().contains("\"featureGroup\":\"OPT\""));
    assertTrue(explained.stdout().contains("\"typeRule\":\"optionalWrap\""));
    assertTrue(explained.stdout().contains("\"outputs\":[\"任意<JSON>\"]"));
  }

  @Test
  void absentUnwrapUsesRuntimeExitAndTheStableHumanMessage() throws IOException {
    Path source = temporaryDirectory.resolve("absent-cli.bsb");
    Files.writeString(
        source,
        "メインとは （--）\n"
            + "    空のJSONオブジェクト 「x」を JSONオブジェクトから任意値を取り出す\n"
            + "    任意から値を取り出す 一行表示する\n"
            + "こと。\n",
        StandardCharsets.UTF_8);

    Invocation result = invoke("run", source.toString());

    assertEquals(10, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("E_OPTIONAL_VALUE_ABSENT"));
    assertTrue(result.stderr().contains("任意値には値がありません"));
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(
        exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
