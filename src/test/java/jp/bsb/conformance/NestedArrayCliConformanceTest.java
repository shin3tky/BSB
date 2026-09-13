package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import jp.bsb.cli.BsbCli;
import org.junit.jupiter.api.Test;

/** 多次元配列の公開ソースと利用者サンプルを公開CLIから各2回検証します。 */
class NestedArrayCliConformanceTest {
  @Test
  void requiredPublicPathsAreByteStableAcrossTwoInvocations() throws Exception {
    Map<String, String> outputs =
        Map.of(
            "tests/conformance/nested-arrays/sources/NARRAY-N-ragged.bsb", "【【1、2】、【3】、【】】\n",
            "tests/conformance/nested-arrays/sources/NARRAY-N-operations.bsb",
                "【【9、8】、【2】】\n【【1】、【2】、【3、4】】\n2\n2\n【1】\n【1、9】\n【1、2、3】\n",
            "tests/conformance/nested-arrays/sources/NARRAY-N-loops.bsb", "1\n2\n3\n",
            "tests/conformance/nested-arrays/chapter/nested-arrays-chapter.bsb",
                "【【1、2】、【】、【3】】\n1\n2\n3\n",
            "samples/22-two-dimensional-arrays.bsb",
                "【【「商品」、「個数」】、【「りんご」、「3」】、【「みかん」、「2」】】\n商品\n個数\nりんご\n3\nみかん\n2\n");
    for (var entry : outputs.entrySet()) {
      byte[] sourceBefore = Files.readAllBytes(Path.of(entry.getKey()));
      for (List<String> arguments :
          List.of(
              List.of("check", entry.getKey()),
              List.of("check", "--json", entry.getKey()),
              List.of("run", entry.getKey()),
              List.of("format", entry.getKey()))) {
        Invocation first = invoke(arguments);
        Invocation second = invoke(arguments);
        assertEquals(0, first.exitCode(), arguments.toString());
        assertEquals(first.exitCode(), second.exitCode(), arguments.toString());
        assertArrayEquals(first.stdout(), second.stdout(), arguments.toString());
        assertArrayEquals(first.stderr(), second.stderr(), arguments.toString());
        assertEquals(0, first.stderr().length, arguments.toString());
        if (arguments.getFirst().equals("run")) {
          assertEquals(
              entry.getValue(),
              new String(first.stdout(), StandardCharsets.UTF_8),
              arguments.toString());
        }
      }
      assertArrayEquals(sourceBefore, Files.readAllBytes(Path.of(entry.getKey())));
    }

    assertArrayEquals(
        NestedArrayConformanceData.resourceBytes("canonical/NARRAY-N-ragged.bsb"),
        invoke(List.of("format", "tests/conformance/nested-arrays/sources/NARRAY-N-ragged.bsb"))
            .stdout());
    assertArrayEquals(
        NestedArrayConformanceData.resourceBytes("canonical/NARRAY-N-operations.bsb"),
        invoke(List.of("format", "tests/conformance/nested-arrays/sources/NARRAY-N-operations.bsb"))
            .stdout());
    assertArrayEquals(
        NestedArrayConformanceData.resourceBytes("canonical/NARRAY-N-loops.bsb"),
        invoke(List.of("format", "tests/conformance/nested-arrays/sources/NARRAY-N-loops.bsb"))
            .stdout());
    assertArrayEquals(
        NestedArrayConformanceData.resourceBytes("canonical/nested-arrays-chapter.bsb"),
        invoke(
                List.of(
                    "format", "tests/conformance/nested-arrays/chapter/nested-arrays-chapter.bsb"))
            .stdout());
  }

  @Test
  void concreteNestedTypesAndAllSixNewWordsAppearInStableExplainJson() {
    for (String source :
        List.of(
            "tests/conformance/nested-arrays/sources/NARRAY-N-types.bsb",
            "tests/conformance/nested-arrays/sources/NARRAY-N-wrappers.bsb",
            "samples/22-two-dimensional-arrays.bsb")) {
      Invocation first = invoke(List.of("explain", "--json", source));
      Invocation second = invoke(List.of("explain", "--json", source));
      assertEquals(0, first.exitCode(), source);
      assertArrayEquals(first.stdout(), second.stdout(), source);
      assertArrayEquals(first.stderr(), second.stderr(), source);
      String json = new String(first.stdout(), StandardCharsets.UTF_8);
      assertTrue(json.contains("配列<配列<"), source);
      for (String leaf : List.of("整数", "真偽", "文字", "文字列", "小数", "JSON")) {
        assertTrue(json.contains("空の" + leaf + "二次元配列"), source + leaf);
      }
    }
  }

  private static Invocation invoke(List<String> arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments.toArray(String[]::new), stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
