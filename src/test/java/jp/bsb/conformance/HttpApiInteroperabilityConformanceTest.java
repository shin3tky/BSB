package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import jp.bsb.cli.BsbCli;
import org.junit.jupiter.api.Test;

class HttpApiInteroperabilityConformanceTest {
  private static final Path ROOT = Path.of("tests/conformance/http-api-interoperability");

  @Test
  void centralCatalogRunsEveryCaseAndCoversBothPublicWords() throws Exception {
    List<String> lines = Files.readAllLines(ROOT.resolve("catalog.tsv"), StandardCharsets.UTF_8);
    assertEquals("case_id\tkind\tsource\tcommand\texit_code\tstdout", lines.getFirst());
    assertEquals(3, lines.size());
    var ids = new HashSet<String>();
    var sources = new ArrayList<String>();
    for (String line : lines.subList(1, lines.size())) {
      String[] columns = line.split("\t", -1);
      assertEquals(6, columns.length);
      assertTrue(ids.add(columns[0]));
      assertEquals("normal", columns[1]);
      Path source = ROOT.resolve(columns[2]);
      sources.add(Files.readString(source, StandardCharsets.UTF_8));
      Invocation result = invoke(columns[3], source);
      assertEquals(Integer.parseInt(columns[4]), result.exitCode(), columns[0]);
      assertEquals(columns[5].replace("\\n", "\n"), result.stdout(), columns[0]);
      assertEquals("", result.stderr(), columns[0]);
    }
    String joined = String.join("\n", sources);
    assertTrue(joined.contains("文字列表をフォームURL符号化する"));
    assertTrue(joined.contains("HTTP応答を成功状態として検査する"));
  }

  private static Invocation invoke(String command, Path source) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(new String[] {command, source.toString()}, stdout, stderr);
    return new Invocation(
        exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
