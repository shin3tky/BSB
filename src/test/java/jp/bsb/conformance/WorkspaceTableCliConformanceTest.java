package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** 作業領域・区切り表の公開コマンドと2つの作業領域設定形式を各2回検証します。 */
class WorkspaceTableCliConformanceTest {
  private static final Path FILE_SAMPLE = Path.of("samples/23-named-workspaces-files.bsb");
  private static final Path DELIMITED_SAMPLE = Path.of("samples/24-csv-tsv-files.bsb");
  private static final byte[] CSV_INPUT =
      "\uFEFF商品,個数\r\nりんご,3\r\nみかん,\r\n".getBytes(StandardCharsets.UTF_8);
  private static final byte[] TSV_INPUT =
      "\"注\n記\"\t値\r\n空\t\"\"\r\n".getBytes(StandardCharsets.UTF_8);
  private static final byte[] CSV_OUTPUT =
      "商品,個数\r\nりんご,3\r\nみかん,\r\n\"注\n記\",値\r\n".getBytes(StandardCharsets.UTF_8);
  private static final String CSV_OUTPUT_SHA256 =
      "0fd2705b0f29001f8364d4c54d62a9fadfa5ec77e28c5084e8b303a9fa0628e7";
  @TempDir Path temporaryDirectory;

  @Test
  void staticPublicCommandsAreByteStableAcrossTwoInvocations() throws Exception {
    for (Path sample : List.of(FILE_SAMPLE, DELIMITED_SAMPLE)) {
      byte[] sourceBefore = Files.readAllBytes(sample);
      for (List<String> arguments :
          List.of(
              List.of("check", sample.toString()),
              List.of("check", "--json", sample.toString()),
              List.of("format", sample.toString()),
              List.of("explain", "--json", sample.toString()))) {
        Invocation first = invoke(arguments);
        Invocation second = invoke(arguments);
        assertEquals(0, first.exitCode(), arguments.toString());
        assertEquals(first.exitCode(), second.exitCode(), arguments.toString());
        assertArrayEquals(first.stdout(), second.stdout(), arguments.toString());
        assertArrayEquals(first.stderr(), second.stderr(), arguments.toString());
        assertEquals(0, first.stderr().length, arguments.toString());
        if (arguments.getFirst().equals("format") && sample.equals(FILE_SAMPLE)) {
          assertArrayEquals(sourceBefore, first.stdout());
        }
        if (arguments.getFirst().equals("format") && sample.equals(DELIMITED_SAMPLE)) {
          assertArrayEquals(
              WorkspaceTableConformanceData.resourceBytes("canonical/delimited-tables-chapter.bsb"),
              first.stdout());
        }
        if (arguments.getFirst().equals("explain")) {
          String json = new String(first.stdout(), StandardCharsets.UTF_8);
          assertEquals(187, occurrences(json, "\"featureGroup\":"));
          assertTrue(json.contains("\"workspaceDeclarations\":["));
          assertTrue(json.contains("\"kind\":\"workspace\""));
          if (sample.equals(DELIMITED_SAMPLE)) {
            assertTrue(json.contains("\"name\":\"CSVを表として解析する\""));
            assertTrue(json.contains("\"name\":\"表をCSVに変換する\""));
          }
        }
      }
      assertArrayEquals(sourceBefore, Files.readAllBytes(sample));
    }
  }

  @Test
  void directAndTomlRunsAreStableWithMultipleWorkspacesAndConnections() throws Exception {
    Path source = temporaryDirectory.resolve("program.bsb");
    Files.copy(FILE_SAMPLE, source);
    Path first = writeBytes("first.dat", new byte[] {1, 2, 3});
    Path second = writeBytes("second.dat", new byte[] {4, 5, 6});
    Path directOutput = temporaryDirectory.resolve("direct-output.dat");
    Path tomlOutput = temporaryDirectory.resolve("toml-output.dat");
    Path unused = writeBytes("unused.dat", new byte[] {9});
    Path connections = writeText("connections.toml", connectionToml());
    List<String> direct =
        List.of(
            "run",
            "--file",
            "帳票",
            "入力A.dat",
            "read",
            first.toString(),
            "--file",
            "画像",
            "保管.dat",
            "read-write",
            unused.toString(),
            "--connections",
            connections.toString(),
            "--file",
            "帳票",
            "入力B.dat",
            "read",
            second.toString(),
            "--file",
            "帳票",
            "出力.dat",
            "write",
            directOutput.toString(),
            source.toString(),
            "--",
            "入力A.dat",
            "入力B.dat",
            "出力.dat");
    Path workspaceConfig =
        writeText("config/workspaces.toml", workspaceToml(first, second, tomlOutput, unused));
    List<String> toml =
        List.of(
            "run",
            "--workspaces",
            workspaceConfig.toString(),
            "--connections",
            connections.toString(),
            source.toString(),
            "--",
            "入力A.dat",
            "入力B.dat",
            "出力.dat");

    for (var entry : List.of(new RunCase(direct, directOutput), new RunCase(toml, tomlOutput))) {
      Invocation firstRun = invoke(entry.arguments());
      byte[] firstPublished = Files.readAllBytes(entry.output());
      Invocation secondRun = invoke(entry.arguments());
      assertEquals(0, firstRun.exitCode(), firstRun.stderrText());
      assertEquals(firstRun.exitCode(), secondRun.exitCode());
      assertArrayEquals(firstRun.stdout(), secondRun.stdout());
      assertArrayEquals(firstRun.stderr(), secondRun.stderr());
      assertArrayEquals("3\n".getBytes(StandardCharsets.UTF_8), firstRun.stdout());
      assertEquals(0, firstRun.stderr().length);
      assertArrayEquals(new byte[] {1, 2, 3}, firstPublished);
      assertArrayEquals(firstPublished, Files.readAllBytes(entry.output()));
    }
  }

  @Test
  void delimitedDirectAndTomlRunsAreStableAndPublishExactCsv() throws Exception {
    Path csv = writeBytes("products.csv", CSV_INPUT);
    Path tsv = writeBytes("notes.tsv", TSV_INPUT);
    Path directOutput = temporaryDirectory.resolve("direct-summary.csv");
    Path tomlOutput = temporaryDirectory.resolve("toml-summary.csv");
    Path connections = writeText("delimited-connections.toml", connectionToml());
    List<String> direct =
        List.of(
            "run",
            "--file",
            "入力",
            "商品.csv",
            "read",
            csv.toString(),
            "--connections",
            connections.toString(),
            "--file",
            "入力",
            "注記.tsv",
            "read",
            tsv.toString(),
            "--file",
            "出力",
            "集計.csv",
            "write",
            directOutput.toString(),
            DELIMITED_SAMPLE.toString(),
            "--",
            "商品.csv",
            "注記.tsv",
            "集計.csv");
    Path workspaceConfig =
        writeText("delimited/workspaces.toml", delimitedWorkspaceToml(csv, tsv, tomlOutput));
    List<String> toml =
        List.of(
            "run",
            "--workspaces",
            workspaceConfig.toString(),
            "--connections",
            connections.toString(),
            DELIMITED_SAMPLE.toString(),
            "--",
            "商品.csv",
            "注記.tsv",
            "集計.csv");

    for (var entry : List.of(new RunCase(direct, directOutput), new RunCase(toml, tomlOutput))) {
      Invocation firstRun = invoke(entry.arguments());
      byte[] firstPublished = Files.readAllBytes(entry.output());
      Invocation secondRun = invoke(entry.arguments());
      assertEquals(0, firstRun.exitCode(), firstRun.stderrText());
      assertEquals(firstRun.exitCode(), secondRun.exitCode());
      assertArrayEquals(firstRun.stdout(), secondRun.stdout());
      assertArrayEquals(firstRun.stderr(), secondRun.stderr());
      assertArrayEquals("55\n".getBytes(StandardCharsets.UTF_8), firstRun.stdout());
      assertEquals(0, firstRun.stderr().length);
      assertArrayEquals(CSV_OUTPUT, firstPublished);
      assertArrayEquals(firstPublished, Files.readAllBytes(entry.output()));
      assertEquals(CSV_OUTPUT_SHA256, sha256(firstPublished));
    }
  }

  @Test
  void bothWorkspaceTableEmbeddingExamplesCompileAgainstThePublicApi() {
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
            "samples/NamedWorkspaceFakeEmbedding.java",
            "samples/DelimitedTablesFakeEmbedding.java"));
  }

  private Path writeBytes(String relative, byte[] bytes) throws Exception {
    Path path = temporaryDirectory.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.write(path, bytes);
    return path;
  }

  private Path writeText(String relative, String text) throws Exception {
    Path path = temporaryDirectory.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return path;
  }

  private static String connectionToml() {
    return "schema-version = 1\n"
        + "[connections.API]\n"
        + "base-uri = \"https://api.example.test/\"\n"
        + "allowed-methods = [\"GET\"]\n"
        + "[connections.API.authentication]\n"
        + "kind = \"api-key\"\n"
        + "value = \"CONFORMANCE_API_KEY_SECRET\"\n";
  }

  private static String workspaceToml(Path first, Path second, Path output, Path unused) {
    return "schema-version = 1\n"
        + "[workspaces.\"帳票\"]\n"
        + "[workspaces.\"帳票\".files.\"入力A.dat\"]\npath = \""
        + first
        + "\"\naccess = \"read\"\n"
        + "[workspaces.\"帳票\".files.\"入力B.dat\"]\npath = \""
        + second
        + "\"\naccess = \"read\"\n"
        + "[workspaces.\"帳票\".files.\"出力.dat\"]\npath = \""
        + output
        + "\"\naccess = \"write\"\n"
        + "[workspaces.\"画像\"]\n"
        + "[workspaces.\"画像\".files.\"保管.dat\"]\npath = \""
        + unused
        + "\"\naccess = \"read-write\"\n";
  }

  private static String delimitedWorkspaceToml(Path csv, Path tsv, Path output) {
    return "schema-version = 1\n"
        + "[workspaces.\"入力\"]\n"
        + "[workspaces.\"入力\".files.\"商品.csv\"]\npath = \""
        + csv
        + "\"\naccess = \"read\"\n"
        + "[workspaces.\"入力\".files.\"注記.tsv\"]\npath = \""
        + tsv
        + "\"\naccess = \"read\"\n"
        + "[workspaces.\"出力\"]\n"
        + "[workspaces.\"出力\".files.\"集計.csv\"]\npath = \""
        + output
        + "\"\naccess = \"write\"\n";
  }

  private static Invocation invoke(List<String> arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments.toArray(String[]::new), stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private static int occurrences(String text, String needle) {
    int count = 0;
    for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length())
      count++;
    return count;
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {
    private String stderrText() {
      return new String(stderr, StandardCharsets.UTF_8);
    }
  }

  private record RunCase(List<String> arguments, Path output) {}
}
