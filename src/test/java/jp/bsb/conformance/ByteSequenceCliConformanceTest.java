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
import jp.bsb.cli.BsbCli;
import org.junit.jupiter.api.Test;

/** 章末と利用者サンプルの公開5コマンドを各2回、固定バイト列とSHA-256で検証します。 */
class ByteSequenceCliConformanceTest {
  private static final String EMPTY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @Test
  void chapterAndSampleCommandsAreByteStableAndMatchFixedHashes() throws Exception {
    for (Expected expected : expectations()) {
      byte[] inputBefore = Files.readAllBytes(Path.of(expected.source()));
      Invocation first = invoke(expected.arguments());
      Invocation second = invoke(expected.arguments());
      assertEquals(expected.exitCode(), first.exitCode(), expected.label());
      assertEquals(first.exitCode(), second.exitCode(), expected.label());
      assertArrayEquals(first.stdout(), second.stdout(), expected.label() + "/stdout");
      assertArrayEquals(first.stderr(), second.stderr(), expected.label() + "/stderr");
      assertEquals(expected.stdoutBytes(), first.stdout().length, expected.label());
      assertEquals(expected.stdoutSha256(), sha256(first.stdout()), expected.label());
      assertEquals(0, first.stderr().length, expected.label());
      assertEquals(EMPTY_SHA256, sha256(first.stderr()), expected.label());
      assertArrayEquals(
          inputBefore, Files.readAllBytes(Path.of(expected.source())), expected.label());
      assertBomlessUtf8(first.stdout(), expected.label());
    }

    Invocation chapterRun =
        invoke(
            List.of("run", "tests/conformance/byte-sequences/chapter/byte-sequences-chapter.bsb"));
    assertEquals(
        "19\n44GT44KT44Gr44Gh44Gv8J+MjQ==\nこんにちは🌍\nはい\ninvalidLeadingByte\n0\ninvalidLength\n2\n",
        new String(chapterRun.stdout(), StandardCharsets.UTF_8));
    Invocation sampleRun = invoke(List.of("run", "samples/19-byte-sequences.bsb"));
    assertEquals(
        "19\n44GT44KT44Gr44Gh44Gv8J+MjQ==\nこんにちは🌍\nはい\ninvalidLeadingByte\ninvalidLength\n",
        new String(sampleRun.stdout(), StandardCharsets.UTF_8));

    Invocation chapterFormat =
        invoke(
            List.of(
                "format", "tests/conformance/byte-sequences/chapter/byte-sequences-chapter.bsb"));
    assertArrayEquals(
        ByteSequenceConformanceData.resourceBytes("canonical/byte-sequences-chapter.bsb"),
        chapterFormat.stdout());
    Invocation sampleFormat = invoke(List.of("format", "samples/19-byte-sequences.bsb"));
    assertArrayEquals(
        Files.readAllBytes(Path.of("samples/19-byte-sequences.bsb")), sampleFormat.stdout());

    for (String source :
        List.of(
            "tests/conformance/byte-sequences/chapter/byte-sequences-chapter.bsb",
            "samples/19-byte-sequences.bsb")) {
      String explain =
          new String(invoke(List.of("explain", "--json", source)).stdout(), StandardCharsets.UTF_8);
      assertTrue(explain.startsWith("{\"schemaVersion\":1,"), source);
      assertTrue(explain.contains("\"name\":\"バイト列の長さ\""), source);
      assertTrue(explain.contains("\"name\":\"Base64復号失敗の文字位置を取り出す\""), source);
    }
  }

  private static List<Expected> expectations() {
    String chapter = "tests/conformance/byte-sequences/chapter/byte-sequences-chapter.bsb";
    String sample = "samples/19-byte-sequences.bsb";
    return List.of(
        expected(chapter, "check", 0, EMPTY_SHA256),
        expected(
            chapter,
            "check-json",
            162,
            "39a852356200a65eeb90a1791fa8c49363469c7fe27dff0118f5140247728fa7"),
        expected(
            chapter, "run", 96, "6f7a15414e0b18661c1915d2219cc38c72367f34a07e076f07617749b178bec2"),
        expected(
            chapter,
            "format",
            1615,
            "5f9779f710d50cbd1f723599a22fb31fc5c64f777bfb9c7c4ba55bb9147944ec"),
        expected(
            chapter,
            "explain-json",
            80_122,
            "4ea726ce934cd409376ca162476ada1b9134bdb30e1b4c29c059afc437981207"),
        expected(sample, "check", 0, EMPTY_SHA256),
        expected(
            sample,
            "check-json",
            124,
            "18dd3c1dd77e5e5313b7ec917b5e40f66b5a94ac5e74672af7e82c13a9f34280"),
        expected(
            sample, "run", 92, "a86a262facc3ca520f554009532a8a1756c8456e926583390ae831caee1f24d1"),
        expected(
            sample,
            "format",
            1130,
            "7265b56f4fb201ba8c17cf1ce06bbdbd83483195647220e2fb67792bc9e623bc"),
        expected(
            sample,
            "explain-json",
            80_035,
            "9b30867bf5ace606b0a53b95728a5f1464567f99e0a86fa1e0ab890e2f4201fa"));
  }

  private static Expected expected(String source, String command, int bytes, String hash) {
    List<String> arguments =
        switch (command) {
          case "check-json" -> List.of("check", "--json", source);
          case "explain-json" -> List.of("explain", "--json", source);
          default -> List.of(command, source);
        };
    return new Expected(source + '/' + command, source, arguments, 0, bytes, hash);
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

  private static void assertBomlessUtf8(byte[] bytes, String label) {
    assertFalse(
        bytes.length >= 3
            && bytes[0] == (byte) 0xef
            && bytes[1] == (byte) 0xbb
            && bytes[2] == (byte) 0xbf,
        label);
    String decoded = new String(bytes, StandardCharsets.UTF_8);
    assertArrayEquals(bytes, decoded.getBytes(StandardCharsets.UTF_8), label);
  }

  private record Expected(
      String label,
      String source,
      List<String> arguments,
      int exitCode,
      int stdoutBytes,
      String stdoutSha256) {}

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
