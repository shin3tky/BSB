package jp.bsb.regex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class TextRegexRegexCompilerTest {
  private final Re2RegexCompiler compiler = new Re2RegexCompiler();

  @Test
  void expandsUnicode16GeneralCategoriesAndScriptsWithoutEngineFallback() {
    assertTrue(program("\\p{Han}", "").matchesEntire("\uD87A\uDFF0"));
    assertTrue(program("\\p{Han}", "").matchesEntire("𠮷"));
    assertTrue(program("\\p{M}", "").matchesEntire("゙"));
    assertTrue(program("\\p{Hiragana}", "").matchesEntire("あ"));
    assertFalse(program("\\p{Hiragana}", "").matchesEntire("ア"));
  }

  @Test
  void reportsAnUnknownUnicodePropertyThroughThePublicCompilerBoundary() {
    RegexCompilationResult.Failure failure =
        assertInstanceOf(
            RegexCompilationResult.Failure.class,
            compiler.compile("a\\p{Definitely_Not_A_Unicode_Property}", ""));

    assertEquals(DiagnosticCode.E_REGEX_SYNTAX, failure.code());
    assertEquals("unknownUnicodeProperty", failure.fields().get("reason"));
    assertEquals("Definitely_Not_A_Unicode_Property", failure.fields().get("property"));
    assertEquals("1", failure.fields().get("patternOffset"));
  }

  @Test
  void appliesAsciiOnlyCaseFoldingAndNormativeLineAndDotFlags() {
    assertTrue(program("^abc$", "im").containsMatch("abc\nABC"));
    assertTrue(program("[a-z]+", "i").matchesEntire("AbZ"));
    assertFalse(program("é", "i").matchesEntire("É"));
    assertFalse(program(".", "").matchesEntire("\n"));
    assertTrue(program(".", "s").matchesEntire("\n"));
  }

  @Test
  void preservesCaptureCountAndSourceOrderedUniqueNames() {
    RegexProgram program = program("(?<year>[0-9]+)-(x)-(?<number>[0-9]+)", "");

    assertEquals(3, program.captureCount());
    assertEquals(List.of("year", "number"), program.namedCaptures());
    assertTrue(program.matchesEntire("2026-x-42"));
  }

  @Test
  void reportsAllFiveNormativeStaticRegexFailuresExactly() throws Exception {
    assertFailure(
        "TEXT-F009", DiagnosticCode.E_REGEX_UNSUPPORTED_CONSTRUCT, 2, 20, "backreference", "\\1");
    assertFailure(
        "TEXT-F010", DiagnosticCode.E_REGEX_UNSUPPORTED_CONSTRUCT, 2, 18, "lookahead", "(?=");
    assertFailure("TEXT-F011", DiagnosticCode.E_REGEX_SYNTAX, 2, 11, "quantifierLimit", "a{1001}");
    assertFailure("TEXT-F012", DiagnosticCode.E_REGEX_SYNTAX, 2, 20, "duplicateGroupName", "x");
    assertFailure("TEXT-F013", DiagnosticCode.E_REGEX_SYNTAX, 2, 13, "invalidGroupName", "年");
  }

  @Test
  void enforcesRawBytesProgramInstructionsAndCaptureBoundaries() {
    String maximumRaw = "(?:)".repeat(16_384);
    assertEquals(65_536, maximumRaw.length());
    assertInstanceOf(RegexCompilationResult.Success.class, compiler.compile(maximumRaw, ""));
    assertFailureCode(
        compiler.compile(maximumRaw + "a", ""), DiagnosticCode.E_REGEX_PATTERN_LIMIT, 65_537);

    RegexProgram maximumProgram = program("a".repeat(65_534), "");
    assertEquals(65_536, maximumProgram.instructionCount());
    assertFailureCode(
        compiler.compile("a".repeat(65_535), ""), DiagnosticCode.E_REGEX_PROGRAM_LIMIT, 65_537);

    assertEquals(64, program("()".repeat(64), "").captureCount());
    assertFailureCode(
        compiler.compile("()".repeat(65), ""), DiagnosticCode.E_REGEX_CAPTURE_LIMIT, 65);
  }

  @Test
  void acceptsQuantifier1000AndRejects1001BeforeCallingTheEngine() {
    assertInstanceOf(RegexCompilationResult.Success.class, compiler.compile("a{1000}", ""));
    RegexCompilationResult.Failure failure =
        assertInstanceOf(RegexCompilationResult.Failure.class, compiler.compile("a{1001}", ""));
    assertEquals(DiagnosticCode.E_REGEX_SYNTAX, failure.code());
    assertEquals("quantifierLimit", failure.fields().get("reason"));
    assertFalse(failure.fields().containsKey("engineDescription"));
  }

  private RegexProgram program(String pattern, String flags) {
    RegexCompilationResult.Success success =
        assertInstanceOf(RegexCompilationResult.Success.class, compiler.compile(pattern, flags));
    return success.program();
  }

  private static void assertFailureCode(
      RegexCompilationResult result, DiagnosticCode code, long observed) {
    RegexCompilationResult.Failure failure =
        assertInstanceOf(RegexCompilationResult.Failure.class, result);
    assertEquals(code, failure.code());
    String value =
        failure
            .fields()
            .getOrDefault(
                "patternUtf8Bytes",
                failure
                    .fields()
                    .getOrDefault("programInstructions", failure.fields().get("regexCaptures")));
    assertEquals(Long.toString(observed), value);
  }

  private static void assertFailure(
      String caseId,
      DiagnosticCode code,
      int line,
      int column,
      String classification,
      String actual)
      throws Exception {
    Diagnostic diagnostic =
        new SourceChecker().check(caseId + ".bsb", source(caseId)).diagnostics().getFirst();

    assertEquals(code, diagnostic.code(), caseId);
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line(), caseId);
    assertEquals(column, diagnostic.location().displayPosition().orElseThrow().column(), caseId);
    assertEquals(classification, diagnostic.fields().get(classificationField(caseId)), caseId);
    assertEquals(actual, diagnostic.actual().orElseThrow(), caseId);
    if (caseId.equals("TEXT-F012")) {
      assertEquals(1, diagnostic.relatedLocations().size());
      assertEquals(13, diagnostic.relatedLocations().getFirst().position().column());
    }
  }

  private static String classificationField(String caseId) {
    return caseId.equals("TEXT-F009") || caseId.equals("TEXT-F010") ? "construct" : "reason";
  }

  private static byte[] source(String caseId) throws Exception {
    String resource = "/conformance/text-regex/sources/" + caseId + ".bsb";
    try (var input = TextRegexRegexCompilerTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
