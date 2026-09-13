package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ControlFlowCanonicalFormatterTest {
  @ParameterizedTest
  @MethodSource("specifiedCanonicalCases")
  void matchesEverySpecifiedControlFlowCanonicalOutput(String sourceName, String expectedPath)
      throws IOException {
    FormatResult result = FormatTestSupport.formatControlFlowResource(sourceName);

    assertTrue(result.successful(), sourceName + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName);
    assertArrayEquals(
        FormatTestSupport.ControlFlowResourceBytes(expectedPath),
        result.outputForStandardOutput().getBytes(StandardCharsets.UTF_8),
        sourceName);
  }

  @ParameterizedTest
  @MethodSource("allSyntaxValidSources")
  void isIdempotentForEverySyntaxValidControlFlowSource(String sourceName) throws IOException {
    var formatter = new SourceFormatter();
    FormatResult first = FormatTestSupport.formatControlFlowResource(sourceName);
    FormatResult second =
        formatter.format(
            "formatted-" + sourceName,
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), sourceName);
    assertTrue(second.successful(), sourceName);
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput(), sourceName);
  }

  @Test
  void joinsAControlStartToThePreviousCallButNeverAcrossAComment() {
    String source =
        "メインとは （--）\n"
            + "    1 と 2 を 足す\n"
            + "    回だけ\n"
            + "    繰り返す\n"
            + "    はい\n"
            + "    # 条件の説明\n"
            + "    ならば # 開始\n"
            + "    つぎに\n"
            + "こと。\n";
    String expected =
        "メインとは （--）\n"
            + "    1 と 2 を 足す 回だけ\n"
            + "    繰り返す\n"
            + "    はい\n"
            + "    # 条件の説明\n"
            + "    ならば # 開始\n"
            + "    つぎに\n"
            + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("開始語.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void putsEveryTransferOnItsOwnLine() {
    String source = "メインとは （--）\n" + "    3 回だけ 1 打ち切る 2 続ける 3 戻る\n" + "    繰り返す\n" + "こと。\n";
    String expected =
        "メインとは （--）\n"
            + "    3 回だけ\n"
            + "        1\n"
            + "        打ち切る\n"
            + "        2\n"
            + "        続ける\n"
            + "        3\n"
            + "        戻る\n"
            + "    繰り返す\n"
            + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("移行.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertEquals(expected, result.outputForStandardOutput());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r", "\r\n"})
  void normalizesLineEndingsInsideControlStructures(String lineEnding) throws IOException {
    String source = FormatTestSupport.ControlFlowResourceText("sources/FLOW-N004.bsb");
    String expected = FormatTestSupport.ControlFlowResourceText("canonical/FLOW-N004.bsb");
    byte[] bytes = source.replace("\n", lineEnding).getBytes(StandardCharsets.UTF_8);

    FormatResult result = new SourceFormatter().format("FLOW-N004.bsb", bytes);

    assertEquals(expected, result.outputForStandardOutput());
    assertFalse(result.outputForStandardOutput().contains("\r"));
  }

  @Test
  void acceptsLeadingBomButOmitsItFromControlStructureOutput() throws IOException {
    byte[] source = FormatTestSupport.ControlFlowResourceBytes("sources/FLOW-N004.bsb");
    byte[] withBom = new byte[source.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(source, 0, withBom, 3, source.length);

    FormatResult result = new SourceFormatter().format("FLOW-N004.bsb", withBom);

    assertEquals(
        FormatTestSupport.ControlFlowResourceText("canonical/FLOW-N004.bsb"),
        result.outputForStandardOutput());
    assertFalse(result.outputForStandardOutput().startsWith("\uFEFF"));
  }

  @Test
  void formats256NestedControlsWithAUniqueIndentAndTrailingLf() {
    var formatter = new SourceFormatter();
    String source = nestedConditionals(256);

    FormatResult first = formatter.format("深さ256.bsb", source.getBytes(StandardCharsets.UTF_8));
    String output = first.outputForStandardOutput();
    FormatResult second = formatter.format("再整形.bsb", output.getBytes(StandardCharsets.UTF_8));
    String[] lines = output.split("\n", -1);

    assertTrue(first.successful());
    assertEquals("    ".repeat(256) + "はい ならば", lines[256]);
    assertTrue(output.endsWith("\n"));
    assertFalse(output.endsWith("\n\n"));
    assertEquals(output, second.outputForStandardOutput());
  }

  private static String nestedConditionals(int depth) {
    var source = new StringBuilder("メインとは （--）\n");
    source.append("    はい ならば\n".repeat(depth));
    source.append("    つぎに\n".repeat(depth));
    return source.append("こと。\n").toString();
  }

  private static Stream<Arguments> specifiedCanonicalCases() {
    return Stream.concat(
        Stream.of(
            Arguments.of("FLOW-N001.bsb", "canonical/FLOW-N001.bsb"),
            Arguments.of("FLOW-N004.bsb", "canonical/FLOW-N004.bsb"),
            Arguments.of("FLOW-N009.bsb", "canonical/FLOW-N009.bsb"),
            Arguments.of("FLOW-N012.bsb", "canonical/FLOW-N012.bsb"),
            Arguments.of("FLOW-N020.bsb", "canonical/FLOW-N020.bsb")),
        java.util.stream.IntStream.rangeClosed(10, 26)
            .mapToObj(
                number -> {
                  String name = "FLOW-F%03d.bsb".formatted(number);
                  String expectedDirectory =
                      number == 19 || number == 24 ? "canonical/" : "sources/";
                  return Arguments.of(name, expectedDirectory + name);
                }));
  }

  private static Stream<String> allSyntaxValidSources() {
    return Stream.concat(
        java.util.stream.IntStream.rangeClosed(1, 20)
            .mapToObj(number -> "FLOW-N%03d.bsb".formatted(number)),
        java.util.stream.IntStream.rangeClosed(10, 26)
            .mapToObj(number -> "FLOW-F%03d.bsb".formatted(number)));
  }
}
