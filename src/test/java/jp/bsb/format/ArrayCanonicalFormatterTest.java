package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArrayCanonicalFormatterTest {
  @ParameterizedTest
  @MethodSource("specifiedCanonicalCases")
  void matchesEverySpecifiedArrayCanonicalOutput(String sourcePath, String expectedPath)
      throws IOException {
    FormatResult result =
        new SourceFormatter().format(sourcePath, FormatTestSupport.arraysResourceBytes(sourcePath));

    assertTrue(result.successful(), sourcePath + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourcePath);
    assertArrayEquals(
        FormatTestSupport.arraysResourceBytes(expectedPath),
        result.outputForStandardOutput().getBytes(StandardCharsets.UTF_8),
        sourcePath);
  }

  @ParameterizedTest
  @MethodSource("allSyntaxValidSources")
  void formatsEverySyntaxValidArraySourceIdempotently(String sourceName) throws IOException {
    var formatter = new SourceFormatter();
    FormatResult first = FormatTestSupport.formatArrayResource(sourceName);
    FormatResult second =
        formatter.format(
            "formatted-" + sourceName,
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), sourceName + first.diagnostics());
    assertTrue(first.diagnostics().isEmpty(), sourceName);
    assertTrue(second.successful(), sourceName + second.diagnostics());
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput(), sourceName);
  }

  @Test
  void formatsTheNormativeChapterProgramIdempotently() throws IOException {
    byte[] source = FormatTestSupport.arraysResourceBytes("chapter/arrays-chapter.bsb");
    var formatter = new SourceFormatter();

    FormatResult first = formatter.format("arrays-chapter.bsb", source);
    FormatResult second =
        formatter.format(
            "formatted-arrays-chapter.bsb",
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertArrayEquals(source, first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
  }

  @Test
  void keepsCommentFreeArraysOnOneLineAndNormalizesEverySeparator() {
    String source =
        "メインとは （--）\n" + "【\n" + " 1 ，\n" + " 2 と 3 を 足す 、\n" + " 4\n" + "】 を 一行表示する\n" + "こと。\n";
    String expected = "メインとは （--）\n    【1、2 と 3 を 足す、4】 を 一行表示する\nこと。\n";

    FormatResult result =
        new SourceFormatter().format("一行配列.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void distinguishesAHashInsideAStringFromAnArrayElementComment() {
    String source = "メインとは （--）\n" + "【「値 # 内部」，# 注釈\n" + "「次」】 を 一行表示する\n" + "こと。\n";
    String expected =
        "メインとは （--）\n"
            + "    【\n"
            + "        「値 # 内部」、 # 注釈\n"
            + "        「次」\n"
            + "    】 を 一行表示する\n"
            + "こと。\n";

    FormatResult first =
        new SourceFormatter().format("文字列.bsb", source.getBytes(StandardCharsets.UTF_8));
    FormatResult second =
        new SourceFormatter()
            .format("再整形.bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertEquals(expected, first.outputForStandardOutput());
    assertEquals(expected, second.outputForStandardOutput());
  }

  private static Stream<Arguments> specifiedCanonicalCases() {
    return Stream.of(1, 12, 13, 18, 19)
        .map(number -> "ARRAY-N%03d.bsb".formatted(number))
        .map(name -> Arguments.of("sources/" + name, "canonical/" + name));
  }

  private static Stream<String> allSyntaxValidSources() {
    Stream<String> normalCases =
        IntStream.rangeClosed(1, 21).mapToObj(number -> "ARRAY-N%03d.bsb".formatted(number));
    Stream<String> staticAndRuntimeCases =
        Stream.concat(
            IntStream.rangeClosed(9, 15).mapToObj(number -> "ARRAY-F%03d.bsb".formatted(number)),
            IntStream.rangeClosed(17, 33).mapToObj(number -> "ARRAY-F%03d.bsb".formatted(number)));
    return Stream.concat(normalCases, staticAndRuntimeCases);
  }
}
