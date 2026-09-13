package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoverableJsonRecoverableJsonCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void checkRunFormatAndExplainExposeRecoveryWithoutChangingJsonSchemaVersion() throws Exception {
    Path source =
        source(
            "RecoverableJson-cli.bsb",
            "メインとは （--）\n"
                + "    「{」を JSONを解析して結果を返す 結果が失敗である\n"
                + "    ならば\n"
                + "        結果から失敗値を取り出す JSON解析失敗の種類を取り出す 一行表示する\n"
                + "    さもなければ\n"
                + "        結果から成功値を取り出す JSONを文字列に変換する 一行表示する\n"
                + "    つぎに\n"
                + "こと。\n");

    Invocation checked = invoke("check", "--json", source.toString());
    Invocation run = invoke("run", source.toString());
    Invocation formatted = invoke("format", source.toString());
    Invocation explained = invoke("explain", "--json", source.toString());

    assertEquals(0, checked.exitCode());
    assertTrue(checked.stdout().contains("\"schemaVersion\":1"));
    assertEquals("", checked.stderr());
    assertEquals(0, run.exitCode());
    assertEquals("expectedObjectKey\n", run.stdout());
    assertEquals("", run.stderr());
    assertEquals(0, formatted.exitCode());
    assertTrue(formatted.stdout().contains("JSONを解析して結果を返す"));
    assertEquals(0, explained.exitCode());
    assertTrue(explained.stdout().contains("\"schemaVersion\":1"));
    assertTrue(explained.stdout().contains("\"featureGroup\":\"RJSON\""));
    assertTrue(explained.stdout().contains("結果<JSON,JSON解析失敗>"));
    assertFalse(explained.stdout().contains("expectedObjectKey"));
  }

  @Test
  void existingParserStillTerminatesWithItsOriginalDiagnostic() throws Exception {
    Path source =
        source(
            "existing-json-cli.bsb",
            "メインとは （--）\n" + "    「secret-marker」を JSONを解析する JSONを文字列に変換する 一行表示する\n" + "こと。\n");

    Invocation result = invoke("run", source.toString());

    assertEquals(10, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("E_JSON_SYNTAX"));
    assertTrue(result.stderr().contains("unexpectedToken"));
    assertFalse(result.stderr().contains("secret-marker"));
    assertFalse(result.stderr().contains("java."));
  }

  private Path source(String name, String text) throws Exception {
    Path path = temporaryDirectory.resolve(name);
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return path;
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
