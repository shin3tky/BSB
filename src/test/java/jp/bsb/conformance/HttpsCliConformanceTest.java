package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.tools.ToolProvider;
import jp.bsb.cli.BsbCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** HTTPSの章末と参照サンプルを公開CLI・Java埋込み境界で検査します。 */
class HttpsCliConformanceTest {
  private static final String EMPTY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @TempDir Path temporaryDirectory;

  @Test
  void chapterAndReferenceSampleCommandsAreByteStableAndMatchFixedHashes() throws Exception {
    for (Expected expected : expectations()) {
      byte[] sourceBefore = Files.readAllBytes(Path.of(expected.source()));
      Invocation first = invoke(expected.arguments());
      Invocation second = invoke(expected.arguments());
      assertEquals(expected.exitCode(), first.exitCode(), expected.label());
      assertEquals(first.exitCode(), second.exitCode(), expected.label());
      assertArrayEquals(first.stdout(), second.stdout(), expected.label() + "/stdout");
      assertArrayEquals(first.stderr(), second.stderr(), expected.label() + "/stderr");
      assertBytes(expected.stdout(), first.stdout(), expected.label() + "/stdout");
      assertBytes(expected.stderr(), first.stderr(), expected.label() + "/stderr");
      assertArrayEquals(
          sourceBefore, Files.readAllBytes(Path.of(expected.source())), expected.label());
    }

    assertEquals(
        "inputUnavailable\n",
        new String(
            invoke(List.of("run", "samples/20-minimal-https.bsb")).stdout(),
            StandardCharsets.UTF_8));
    assertArrayEquals(
        HttpsConformanceData.resourceBytes("canonical/https-chapter.bsb"),
        invoke(List.of("format", "tests/conformance/https/chapter/https-chapter.bsb")).stdout());
    for (String source :
        List.of(
            "tests/conformance/https/chapter/https-chapter.bsb", "samples/20-minimal-https.bsb")) {
      String explain =
          new String(invoke(List.of("explain", "--json", source)).stdout(), StandardCharsets.UTF_8);
      assertTrue(explain.contains("\"name\":\"HTTP要求を送信する\""), source);
      assertTrue(explain.contains("\"http.send\""), source);
      assertTrue(explain.contains("\"connection.resolve\""), source);
    }
  }

  @Test
  void bothEmbeddingExamplesCompileAgainstTheDistributedPublicApi() {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertEquals(
        0,
        compiler.run(
            null,
            null,
            null,
            "-encoding",
            "UTF-8",
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            temporaryDirectory.toString(),
            "samples/MinimalHttpsFakeEmbedding.java",
            "samples/MinimalHttpsJdkEmbedding.java"));
  }

  private static List<Expected> expectations() {
    String chapter = "tests/conformance/https/chapter/https-chapter.bsb";
    String sample = "samples/20-minimal-https.bsb";
    return List.of(
        expected(chapter, "check", 0, bytes(0, EMPTY_SHA256), bytes(0, EMPTY_SHA256)),
        expected(
            chapter,
            "check-json",
            0,
            bytes(144, "dc72742dd6aa67e6b8a46ddb18c8d19db6c71a8d09b5b2d413e8d15ebff24cb9"),
            bytes(0, EMPTY_SHA256)),
        expected(
            chapter,
            "run",
            10,
            bytes(0, EMPTY_SHA256),
            bytes(369, "da2203570f151bda3ce1436b8cd7d5d4adadef492bf74a9aa4918794e8164b42")),
        expected(
            chapter,
            "format",
            0,
            bytes(801, "220000db2ba98209ab23ad29f2329659092eea4dedd19dfa4d9d53b5cd20e48d"),
            bytes(0, EMPTY_SHA256)),
        expected(
            chapter,
            "explain-json",
            0,
            bytes(70_617, "552b01204aa791cf78495814aaa8e6aae1f4a39f6a8e462b97ed6a634d42a5f2"),
            bytes(0, EMPTY_SHA256)),
        expected(sample, "check", 0, bytes(0, EMPTY_SHA256), bytes(0, EMPTY_SHA256)),
        expected(
            sample,
            "check-json",
            0,
            bytes(123, "7ca4176e4fe4b133ad2c11a644e4bcc80fa98bf9c5090830d00cbd00b3291993"),
            bytes(0, EMPTY_SHA256)),
        expected(
            sample,
            "run",
            0,
            bytes(17, "fd11efcd5375698c00a808356e2cd5cec2a43df434241cb604dd8b13d1e73cc3"),
            bytes(0, EMPTY_SHA256)),
        expected(
            sample,
            "format",
            0,
            bytes(2_848, "624e448c5ee60a675f79df8935ae7b3ef5f8b42bc18a4653cb2637ddc79db5a9"),
            bytes(0, EMPTY_SHA256)),
        expected(
            sample,
            "explain-json",
            0,
            bytes(76_088, "124c1886bad896078e75e44fe4f1e2f518728241843ca38f51ea3b8e65d872b3"),
            bytes(0, EMPTY_SHA256)));
  }

  private static Expected expected(
      String source, String command, int exitCode, ExpectedBytes stdout, ExpectedBytes stderr) {
    List<String> arguments =
        switch (command) {
          case "check-json" -> List.of("check", "--json", source);
          case "explain-json" -> List.of("explain", "--json", source);
          default -> List.of(command, source);
        };
    return new Expected(source + '/' + command, source, arguments, exitCode, stdout, stderr);
  }

  private static ExpectedBytes bytes(int length, String sha256) {
    return new ExpectedBytes(length, sha256);
  }

  private static void assertBytes(ExpectedBytes expected, byte[] actual, String label)
      throws Exception {
    assertEquals(expected.length(), actual.length, label);
    assertEquals(expected.sha256(), sha256(actual), label);
    assertFalse(
        actual.length >= 3
            && actual[0] == (byte) 0xef
            && actual[1] == (byte) 0xbb
            && actual[2] == (byte) 0xbf,
        label);
    assertArrayEquals(
        actual, new String(actual, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
  }

  private static Invocation invoke(List<String> arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments.toArray(String[]::new), stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private record Expected(
      String label,
      String source,
      List<String> arguments,
      int exitCode,
      ExpectedBytes stdout,
      ExpectedBytes stderr) {}

  private record ExpectedBytes(int length, String sha256) {}

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
