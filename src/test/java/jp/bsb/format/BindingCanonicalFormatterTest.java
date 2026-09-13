package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BindingCanonicalFormatterTest {
  @ParameterizedTest
  @MethodSource("specifiedCanonicalCases")
  void matchesEverySpecifiedBindingCanonicalOutput(String sourcePath, String expectedPath)
      throws IOException {
    FormatResult result =
        new SourceFormatter()
            .format(sourcePath, FormatTestSupport.bindingsResourceBytes(sourcePath));

    assertTrue(result.successful(), sourcePath + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourcePath);
    assertArrayEquals(
        FormatTestSupport.bindingsResourceBytes(expectedPath),
        result.outputForStandardOutput().getBytes(StandardCharsets.UTF_8),
        sourcePath);
  }

  @ParameterizedTest
  @MethodSource("allSyntaxValidSources")
  void isIdempotentForEverySyntaxValidBindingSource(String sourceName) throws IOException {
    var formatter = new SourceFormatter();
    FormatResult first = FormatTestSupport.formatBindingResource(sourceName);
    FormatResult second =
        formatter.format(
            "formatted-" + sourceName,
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), sourceName + first.diagnostics());
    assertTrue(second.successful(), sourceName + second.diagnostics());
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput(), sourceName);
  }

  @Test
  void formatsDeclarationsAtEveryDepthAndEndsEachAssignmentLine() {
    String source =
        "大域は　定数、1。\n"
            + "メインとは (--)\n"
            + "局所は 変数 0。 はい ならば\n"
            + "2，を 局所，に 入れる 局所 を 一行表示する\n"
            + "つぎに\n"
            + "こと。\n";
    String expected =
        "大域は 定数 1。\n"
            + "\n"
            + "メインとは （--）\n"
            + "    局所は 変数 0。\n"
            + "    はい ならば\n"
            + "        2 を 局所 に 入れる\n"
            + "        局所 を 一行表示する\n"
            + "    つぎに\n"
            + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("配置.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void preservesInitializerAndTrailingCommentsWithoutCrossingTheirBoundaries() {
    String source =
        "値は 定数 1 と # 前半  \n"
            + "2 を 足す # 後半\n"
            + "。 # 宣言\n"
            + "\n"
            + "メインとは （--）\n"
            + "3 を 値 に 入れる # 代入\n"
            + "こと。\n";
    String expected =
        "値は 定数\n"
            + "    1 と # 前半  \n"
            + "    2 を 足す # 後半\n"
            + "。 # 宣言\n"
            + "\n"
            + "メインとは （--）\n"
            + "    3 を 値 に 入れる # 代入\n"
            + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("コメント.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void keepsACommentOnlyInitializerOneLevelDeeperThanItsHeader() {
    String source = "値は 定数 # 初期値\n。\n\nメインとは （--）\nこと。\n";
    String expected = "値は 定数\n    # 初期値\n。\n\nメインとは （--）\nこと。\n";

    FormatResult result =
        new SourceFormatter().format("コメント初期値.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void keepsTopLevelCommentsOnTheirOriginalSideOfStructuralBoundaries() {
    String source =
        "一つは 定数 1。\n"
            + "# 連続宣言\n"
            + "二つは 定数 2。\n"
            + "# 宣言側\n"
            + "メインとは （--）\n"
            + "こと。 # 定義側\n"
            + "三つは 定数 3。\n";
    String expected =
        "一つは 定数 1。\n"
            + "# 連続宣言\n"
            + "二つは 定数 2。\n"
            + "# 宣言側\n"
            + "\n"
            + "メインとは （--）\n"
            + "こと。 # 定義側\n"
            + "\n"
            + "三つは 定数 3。\n";

    FormatResult result =
        new SourceFormatter().format("トップレベル.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void formatsTheNormativeChapterProgramIdempotently() throws IOException {
    byte[] source = FormatTestSupport.bindingsResourceBytes("chapter/bindings-chapter.bsb");
    var formatter = new SourceFormatter();

    FormatResult first = formatter.format("bindings-chapter.bsb", source);
    FormatResult second =
        formatter.format(
            "formatted-bindings-chapter.bsb",
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertArrayEquals(source, first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r", "\r\n"})
  void normalizesBindingLineEndingsAndOmitsBom(String lineEnding) throws IOException {
    String source = FormatTestSupport.bindingsResourceText("sources/BIND-N018.bsb");
    byte[] plain = source.replace("\n", lineEnding).getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[plain.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(plain, 0, withBom, 3, plain.length);

    FormatResult result = new SourceFormatter().format("BIND-N018.bsb", withBom);

    assertEquals(
        FormatTestSupport.bindingsResourceText("canonical/BIND-N018.bsb"),
        result.outputForStandardOutput());
    assertFalse(result.outputForStandardOutput().contains("\r"));
    assertFalse(result.outputForStandardOutput().startsWith("\uFEFF"));
  }

  private static Stream<Arguments> specifiedCanonicalCases() {
    Stream<Arguments> normalCases =
        Stream.of(
            Arguments.of("sources/BIND-N001.bsb", "canonical/BIND-N001.bsb"),
            Arguments.of("sources/BIND-N002.bsb", "canonical/BIND-N002.bsb"),
            Arguments.of("sources/BIND-N016.bsb", "canonical/BIND-N016.bsb"),
            Arguments.of("sources/BIND-N017.bsb", "canonical/BIND-N017.bsb"),
            Arguments.of("sources/BIND-N018.bsb", "canonical/BIND-N018.bsb"),
            Arguments.of("chapter/bindings-chapter.bsb", "chapter/bindings-chapter.bsb"));
    Stream<Arguments> staticFailureCases =
        IntStream.concat(IntStream.rangeClosed(9, 11), IntStream.rangeClosed(13, 29))
            .mapToObj(
                number -> {
                  String name = "BIND-F%03d.bsb".formatted(number);
                  String expectedDirectory =
                      number == 19 || number == 27 ? "canonical/" : "sources/";
                  return Arguments.of("sources/" + name, expectedDirectory + name);
                });
    return Stream.concat(normalCases, staticFailureCases);
  }

  private static Stream<String> allSyntaxValidSources() {
    return Stream.concat(
        IntStream.rangeClosed(1, 19).mapToObj(number -> "BIND-N%03d.bsb".formatted(number)),
        Stream.concat(
            Stream.of("BIND-F009.bsb", "BIND-F010.bsb", "BIND-F011.bsb"),
            IntStream.rangeClosed(13, 30).mapToObj(number -> "BIND-F%03d.bsb".formatted(number))));
  }
}
