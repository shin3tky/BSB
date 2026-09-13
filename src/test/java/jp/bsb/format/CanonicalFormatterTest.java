package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CanonicalFormatterTest {
  @ParameterizedTest
  @MethodSource("canonicalCases")
  void matchesEverySpecifiedCanonicalFile(String sourceName, String expectedPath)
      throws IOException {
    FormatResult result = FormatTestSupport.formatResource(sourceName);

    assertTrue(result.successful(), sourceName);
    assertTrue(result.diagnostics().isEmpty(), sourceName);
    assertArrayEquals(
        FormatTestSupport.resourceBytes(expectedPath),
        result.outputForStandardOutput().getBytes(StandardCharsets.UTF_8),
        sourceName);
  }

  @ParameterizedTest
  @MethodSource("successfulFormatCases")
  void isIdempotentForEverySuccessfulTextBackedFormatCase(String sourceName) throws IOException {
    var formatter = new SourceFormatter();
    FormatResult first =
        formatter.format(sourceName, FormatTestSupport.resourceBytes("sources/" + sourceName));
    FormatResult second =
        formatter.format(
            "formatted-" + sourceName,
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), sourceName);
    assertTrue(second.successful(), sourceName);
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput(), sourceName);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r", "\r\n"})
  void normalizesEveryPhysicalLineEndingToLf(String lineEnding) throws IOException {
    String template = FormatTestSupport.resourceText("sources/CORE-N001.bsb");
    byte[] source = template.replace("\n", lineEnding).getBytes(StandardCharsets.UTF_8);

    FormatResult result = new SourceFormatter().format("CORE-N007.bsb", source);

    assertEquals(template, result.outputForStandardOutput());
    assertFalse(result.outputForStandardOutput().contains("\r"));
  }

  @Test
  void acceptsLeadingBomButNeverEmitsIt() throws IOException {
    byte[] source = FormatTestSupport.resourceBytes("sources/CORE-N001.bsb");
    byte[] withBom = new byte[source.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(source, 0, withBom, 3, source.length);

    FormatResult result = new SourceFormatter().format("CORE-N021.bsb", withBom);

    assertEquals(
        FormatTestSupport.resourceText("sources/CORE-N001.bsb"), result.outputForStandardOutput());
    assertFalse(result.outputForStandardOutput().startsWith("\uFEFF"));
  }

  @Test
  void returnsNoBytesForAnEmptyOrSeparatorOnlySource() {
    var formatter = new SourceFormatter();

    assertEquals("", formatter.format("empty.bsb", new byte[0]).outputForStandardOutput());
    assertEquals(
        "",
        formatter
            .format("separators.bsb", " \t　、，\r\n".getBytes(StandardCharsets.UTF_8))
            .outputForStandardOutput());
  }

  @Test
  void preservesACommentOnlyFileWithoutAddingBlankLines() {
    String source = "\n# first  \r\n\r\n# second\r";
    String expected = "# first  \n# second\n";

    FormatResult result =
        new SourceFormatter().format("comments-only.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void preservesCommentTextAndPlacesCommentsUsingOriginalPhysicalLines() {
    String source =
        "# top  \n"
            + "メインとは （--） # header  \n"
            + "42 # pending  \n"
            + "# body only  \n"
            + "を 表示する # call  \n"
            + "こと。\n";
    String expected =
        "# top  \n"
            + "メインとは （--） # header  \n"
            + "    42 # pending  \n"
            + "    # body only  \n"
            + "    を 表示する # call  \n"
            + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("comments.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void reconstructsControlAndInvisibleCharactersWithCanonicalEscapes() {
    String source =
        "メインとは （--）\n" + "    \"\\u{0}\\u{7f}\\u{200d}\" を 表示する\n" + "    '\\'' を 表示する\n" + "こと。\n";
    String expected =
        "メインとは （--）\n" + "    「\\u{0}\\u{7F}\\u{200D}」 を 表示する\n" + "    '\\'' を 表示する\n" + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("escapes.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertEquals(expected, result.outputForStandardOutput());
  }

  @Test
  void keepsOutputCandidateSeparateFromLexicalAndSyntaxDiagnostics() throws IOException {
    FormatResult lexical = FormatTestSupport.formatResource("CORE-F004.bsb");
    FormatResult syntax = FormatTestSupport.formatResource("CORE-F011.bsb");

    assertFalse(lexical.successful());
    assertTrue(lexical.standardOutputCandidate().isEmpty());
    assertEquals(DiagnosticCode.E_MISSING_SEPARATOR, lexical.diagnostics().getFirst().code());
    assertThrows(IllegalStateException.class, lexical::outputForStandardOutput);

    assertFalse(syntax.successful());
    assertTrue(syntax.standardOutputCandidate().isEmpty());
    assertEquals(DiagnosticCode.E_MIXED_STACK_PARENTHESES, syntax.diagnostics().getFirst().code());
    assertThrows(IllegalStateException.class, syntax::outputForStandardOutput);
  }

  private static Stream<Arguments> canonicalCases() {
    return Stream.of(
        Arguments.of("CORE-N001.bsb", "sources/CORE-N001.bsb"),
        Arguments.of("CORE-N005.bsb", "canonical/CORE-N005.bsb"),
        Arguments.of("CORE-N006.bsb", "canonical/CORE-N006.bsb"),
        Arguments.of("CORE-N008.bsb", "canonical/CORE-N008.bsb"),
        Arguments.of("CORE-N009.bsb", "canonical/CORE-N009.bsb"),
        Arguments.of("CORE-N010.bsb", "canonical/CORE-N010.bsb"),
        Arguments.of("CORE-N012.bsb", "canonical/CORE-N012.bsb"),
        Arguments.of("CORE-N013.bsb", "canonical/CORE-N013.bsb"),
        Arguments.of("CORE-N014.bsb", "canonical/CORE-N014.bsb"),
        Arguments.of("CORE-N017.bsb", "canonical/CORE-N017.bsb"),
        Arguments.of("CORE-N019.bsb", "sources/CORE-N019.bsb"),
        Arguments.of("CORE-N020.bsb", "canonical/CORE-N020.bsb"),
        Arguments.of("CORE-N022.bsb", "canonical/CORE-N022.bsb"),
        Arguments.of("CORE-F016.bsb", "sources/CORE-F016.bsb"),
        Arguments.of("CORE-F017.bsb", "canonical/CORE-F017.bsb"),
        Arguments.of("CORE-F018.bsb", "canonical/CORE-F018.bsb"),
        Arguments.of("CORE-F019.bsb", "sources/CORE-F019.bsb"),
        Arguments.of("CORE-F020.bsb", "sources/CORE-F020.bsb"),
        Arguments.of("CORE-F020b.bsb", "sources/CORE-F020b.bsb"),
        Arguments.of("CORE-F021.bsb", "sources/CORE-F021.bsb"),
        Arguments.of("CORE-F022.bsb", "sources/CORE-F022.bsb"),
        Arguments.of("CORE-F023.bsb", "sources/CORE-F023.bsb"),
        Arguments.of("CORE-F024.bsb", "sources/CORE-F024.bsb"),
        Arguments.of("CORE-F025.bsb", "sources/CORE-F025.bsb"),
        Arguments.of("CORE-F025b.bsb", "sources/CORE-F025b.bsb"),
        Arguments.of("CORE-F026.bsb", "sources/CORE-F026.bsb"));
  }

  private static Stream<Arguments> successfulFormatCases() {
    return Stream.concat(
            Stream.of(
                "CORE-N001.bsb",
                "CORE-N005.bsb",
                "CORE-N006.bsb",
                "CORE-N008.bsb",
                "CORE-N009.bsb",
                "CORE-N010.bsb",
                "CORE-N012.bsb",
                "CORE-N013.bsb",
                "CORE-N014.bsb",
                "CORE-N017.bsb",
                "CORE-N019.bsb",
                "CORE-N020.bsb",
                "CORE-N022.bsb"),
            List.of(
                "CORE-F016.bsb",
                "CORE-F017.bsb",
                "CORE-F018.bsb",
                "CORE-F019.bsb",
                "CORE-F020.bsb",
                "CORE-F020b.bsb",
                "CORE-F021.bsb",
                "CORE-F022.bsb",
                "CORE-F023.bsb",
                "CORE-F024.bsb",
                "CORE-F025.bsb",
                "CORE-F025b.bsb",
                "CORE-F026.bsb")
                .stream())
        .map(Arguments::of);
  }
}
