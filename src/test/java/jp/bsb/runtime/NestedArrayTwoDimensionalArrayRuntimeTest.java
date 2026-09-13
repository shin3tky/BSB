package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 多次元配列の不規則二次元配列、操作、入れ子ループ、ラッパー通過を実行します。 */
class NestedArrayTwoDimensionalArrayRuntimeTest {
  @ParameterizedTest
  @MethodSource("normalCases")
  void runsNormativeTwoDimensionalArrayPrograms(String sourceName, String expected)
      throws IOException {
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                sourceName,
                resourceBytes(sourceName),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertEquals(java.util.List.of(), result.finalDataStack(), sourceName);
    assertEquals(expected, output.utf8Text(), sourceName);
  }

  private static Stream<Arguments> normalCases() {
    return Stream.of(
        Arguments.of("NARRAY-N-ragged.bsb", "【【1、2】、【3】、【】】\n"),
        Arguments.of("NARRAY-N-loops.bsb", "1\n2\n3\n"),
        Arguments.of(
            "NARRAY-N-operations.bsb",
            "【【9、8】、【2】】\n" + "【【1】、【2】、【3、4】】\n" + "2\n2\n【1】\n【1、9】\n【1、2、3】\n"),
        Arguments.of("NARRAY-N-wrappers.bsb", "【【1】】\n【【1】】\n【【「a」】】\n"),
        Arguments.of("NARRAY-N-types.bsb", "0\n0\n0\n0\n0\n0\n"));
  }

  private static byte[] resourceBytes(String sourceName) throws IOException {
    String resource = "/conformance/nested-arrays/sources/" + sourceName;
    try (var input =
        NestedArrayTwoDimensionalArrayRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
