package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultResultCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void checkRunFormatAndExplainExposeExecutableResultWords() throws Exception {
    Path source =
        source(
            "result-cli.bsb",
            "メインとは （--）\n"
                + "    42 を 成功にする<整数,文字列> 結果が成功である\n"
                + "    ならば\n"
                + "        結果から成功値を取り出す 一行表示する\n"
                + "    さもなければ\n"
                + "        結果から失敗値を取り出す 一行表示する\n"
                + "    つぎに\n"
                + "    「not-found」を 失敗にする<整数,文字列> 結果が失敗である\n"
                + "    ならば\n"
                + "        結果から失敗値を取り出す 一行表示する\n"
                + "    さもなければ\n"
                + "        結果を捨てる\n"
                + "    つぎに\n"
                + "こと。\n");

    Invocation checked = invoke("check", source.toString());
    Invocation run = invoke("run", source.toString());
    Invocation formatted = invoke("format", source.toString());
    Invocation explained = invoke("explain", "--json", source.toString());

    assertEquals(0, checked.exitCode());
    assertEquals("", checked.stdout());
    assertEquals("", checked.stderr());
    assertEquals(0, run.exitCode());
    assertEquals("42\nnot-found\n", run.stdout());
    assertEquals("", run.stderr());
    assertEquals(0, formatted.exitCode());
    assertTrue(formatted.stdout().contains("成功にする<整数,文字列>"));
    assertEquals(0, explained.exitCode());
    assertEquals("", explained.stderr());
    assertTrue(explained.stdout().contains("\"typeRule\":\"resultSuccessWrap\""));
    assertTrue(explained.stdout().contains("\"typeRule\":\"resultFailureUnwrap\""));
    assertTrue(explained.stdout().contains("\"featureGroup\":\"RESULT\""));
  }

  @Test
  void bothStateMismatchesUseRuntimeExitStableMessageAndNoPayloadLeak() throws Exception {
    for (Mismatch mismatch :
        java.util.List.of(
            new Mismatch(
                "failure-success.bsb",
                "「credential-failure-secret」を 失敗にする<整数,文字列>",
                "結果から成功値を取り出す",
                "success",
                "failure",
                "credential-failure-secret"),
            new Mismatch(
                "success-failure.bsb",
                "98765432109876543210987654321 を 成功にする<整数,文字列>",
                "結果から失敗値を取り出す",
                "failure",
                "success",
                "98765432109876543210987654321"))) {
      Path source =
          source(
              mismatch.file,
              "メインとは （--）\n    "
                  + mismatch.construction
                  + " "
                  + mismatch.unwrap
                  + " 一行表示する\nこと。\n");

      Invocation checked = invoke("check", source.toString());
      Invocation run = invoke("run", source.toString());

      assertEquals(0, checked.exitCode(), mismatch.file);
      assertEquals(10, run.exitCode(), mismatch.file);
      assertEquals("", run.stdout(), mismatch.file);
      assertTrue(run.stderr().contains("E_RESULT_STATE_MISMATCH"), mismatch.file);
      assertTrue(run.stderr().contains("結果値の状態が取り出す側と一致しません"), mismatch.file);
      assertTrue(
          run.stderr().contains(mismatch.expectedState.equals("success") ? "成功の結果値" : "失敗の結果値"),
          mismatch.file);
      assertFalse(run.stderr().contains(mismatch.secret), mismatch.file);
      assertFalse(run.stderr().contains("java."), mismatch.file);
    }
  }

  @Test
  void existingJsonFailuresAreNotCapturedAsResultValues() throws Exception {
    Path source =
        source(
            "json-not-captured.bsb",
            "メインとは （--）\n"
                + "    「{」を JSONを解析する\n"
                + "    成功にする<JSON,文字列> 結果から成功値を取り出す 一行表示する\n"
                + "こと。\n");

    Invocation result = invoke("run", source.toString());

    assertEquals(10, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("E_JSON_SYNTAX"));
    assertFalse(result.stderr().contains("E_RESULT_STATE_MISMATCH"));
  }

  private Path source(String name, String text) throws IOException {
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

  private record Mismatch(
      String file,
      String construction,
      String unwrap,
      String expectedState,
      String actualState,
      String secret) {}
}
