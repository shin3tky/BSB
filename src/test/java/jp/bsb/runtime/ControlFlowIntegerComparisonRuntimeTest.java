package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 制御フローの整数比較が、符号や大きさにかかわらず数学上の大小を返すことを検証します。 */
class ControlFlowIntegerComparisonRuntimeTest {
  @ParameterizedTest
  @MethodSource("conformanceCases")
  void runsTheNormativeComparisonCases(String sourceName, String expectedOutput)
      throws IOException {
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                sourceName,
                resourceBytes(sourceName),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertEquals(0, result.exitCode(), sourceName);
    assertEquals(java.util.List.of(), result.finalDataStack(), sourceName);
    assertEquals(expectedOutput, output.utf8Text(), sourceName);
  }

  @ParameterizedTest
  @MethodSource("integerOrderCases")
  void comparesSignedAndArbitraryPrecisionIntegers(
      BigInteger first, BigInteger second, boolean expected) {
    String source =
        "メインとは （--）\n" + "    " + first + " と " + second + " を 比べて小さい\n" + "    一行表示する\n" + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "comparison.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(expected ? "はい\n" : "いいえ\n", output.utf8Text());
  }

  private static Stream<Arguments> conformanceCases() {
    return Stream.of(
        Arguments.of("FLOW-N017.bsb", "はい\n"), Arguments.of("FLOW-N018.bsb", "いいえ\nいいえ\n"));
  }

  private static Stream<Arguments> integerOrderCases() {
    BigInteger atTheSourceLiteralDigitLimit = BigInteger.TEN.pow(4_095);
    return Stream.of(
        Arguments.of(BigInteger.valueOf(-2), BigInteger.valueOf(-1), true),
        Arguments.of(BigInteger.valueOf(-1), BigInteger.valueOf(-2), false),
        Arguments.of(atTheSourceLiteralDigitLimit.negate(), atTheSourceLiteralDigitLimit, true));
  }

  @Test
  void comparesAnIntegerAtTheRuntimeResultDigitLimit() {
    String source = resultLimitComparisonSource();
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "result-limit-comparison.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("はい\n", output.utf8Text());
  }

  private static String resultLimitComparisonSource() {
    String base = "1" + "0".repeat(4_095);
    var source = new StringBuilder();
    source.append("基礎とは （-- 整数）\n    ").append(base).append("\nこと。\n\n");
    String previous = "基礎";
    for (int level = 1; level <= 4; level++) {
      String current = "二乗" + level;
      source
          .append(current)
          .append("とは （-- 整数）\n    ")
          .append(previous)
          .append(' ')
          .append(previous)
          .append(" 掛ける\nこと。\n\n");
      previous = current;
    }
    // 4回の二乗で65,521桁になり、10^15を掛けると実行結果上限ちょうどの65,536桁になります。
    source
        .append("上限整数とは （-- 整数）\n    ")
        .append(previous)
        .append(" 1000000000000000 掛ける\nこと。\n\n")
        .append("メインとは （--）\n    0 と 上限整数 を 比べて小さい\n    一行表示する\nこと。\n");
    return source.toString();
  }

  private static byte[] resourceBytes(String sourceName) throws IOException {
    String resource = "/conformance/control-flow/sources/" + sourceName;
    try (var input = ControlFlowIntegerComparisonRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
