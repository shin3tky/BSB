package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ControlFlowParserDiagnosticTest {
  @ParameterizedTest
  @MethodSource("syntaxFailures")
  void matchesEveryTextBackedControlFlowSyntaxDiagnostic(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      String relatedDescription)
      throws IOException {
    var parsed = ParserTestSupport.parseControlFlowResource(sourceName);

    assertFalse(parsed.parseResult().successful(), sourceName);
    assertEquals(1, parsed.diagnostics().size(), sourceName);
    Diagnostic diagnostic = parsed.diagnostics().diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line(), sourceName);
    assertEquals(
        column, diagnostic.location().displayPosition().orElseThrow().column(), sourceName);
    assertEquals(fields, diagnostic.fields(), sourceName);
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(fix, diagnostic.fixes().getFirst(), sourceName);
    if (relatedDescription == null) {
      assertTrue(diagnostic.relatedLocations().isEmpty(), sourceName);
    } else {
      var related = diagnostic.relatedLocations().getFirst();
      String prefix = relatedDescription.startsWith("最初") ? "first" : "opening";
      assertEquals(relatedDescription, related.description(), sourceName);
      assertEquals(
          fields.get(prefix + "Line"), Integer.toString(related.position().line()), sourceName);
      assertEquals(
          fields.get(prefix + "Column"), Integer.toString(related.position().column()), sourceName);
    }
  }

  @Test
  void rejectsThe257thControlBeforeAddingTheDefinitionToTheAst() {
    var parsed =
        ParserTestSupport.parseText("FLOW-F009.bsb", ControlFlowParserTest.nestedConditionals(257));

    assertFalse(parsed.parseResult().successful());
    assertEquals(1, parsed.diagnostics().size());
    Diagnostic diagnostic = parsed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_SYNTAX_DEPTH_LIMIT, diagnostic.code());
    assertEquals(258, diagnostic.location().displayPosition().orElseThrow().line());
    assertEquals(8, diagnostic.location().displayPosition().orElseThrow().column());
    assertEquals("256", diagnostic.limit().orElseThrow());
    assertEquals("257", diagnostic.observed().orElseThrow());
    assertTrue(parsed.parseResult().partialProgram().definitions().isEmpty());
  }

  @Test
  void doesNotLetAnInnerMismatchedEndCloseAnOuterConstruct() {
    String source = "メインとは （--）\n    はい ならば\n    繰り返す\n    つぎに\nこと。\n";

    var parsed = ParserTestSupport.parseText("異種終端.bsb", source);

    assertEquals(
        List.of(DiagnosticCode.E_UNEXPECTED_LOOP_END),
        parsed.diagnostics().diagnostics().stream().map(Diagnostic::code).toList());
  }

  @Test
  void reportsAMissingShortCircuitEndWithTheOperatorAndOpeningLocation() {
    String source = "メインとは （--）\n    はい または\n        いいえ\nこと。\n";

    var parsed = ParserTestSupport.parseText("短絡終端不足.bsb", source);

    assertFalse(parsed.parseResult().successful());
    Diagnostic diagnostic = parsed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_EXPECTED_SHORT_CIRCUIT_END, diagnostic.code());
    assertEquals(
        Map.of("operator", "または", "openingLine", "2", "openingColumn", "8"), diagnostic.fields());
    assertEquals("つぎに", diagnostic.expected().orElseThrow());
    assertEquals("こと", diagnostic.actual().orElseThrow());
    assertEquals("または", diagnostic.relatedLocations().getFirst().description());
  }

  private static Stream<Arguments> syntaxFailures() {
    return Stream.of(
        Arguments.of(
            "FLOW-F001.bsb",
            DiagnosticCode.E_UNEXPECTED_ELSE,
            2,
            5,
            Map.of(),
            "ならばの内側",
            "さもなければ",
            "この語を削除してください",
            null),
        Arguments.of(
            "FLOW-F002.bsb",
            DiagnosticCode.E_DUPLICATE_ELSE,
            4,
            5,
            Map.of("firstLine", "3", "firstColumn", "5"),
            "さもなければは1個まで",
            "2個目のさもなければ",
            "この語を削除してください",
            "最初のさもなければ"),
        Arguments.of(
            "FLOW-F003.bsb",
            DiagnosticCode.E_EXPECTED_IF_END,
            4,
            1,
            Map.of("openingLine", "2", "openingColumn", "8"),
            "つぎに",
            "こと",
            "ここにつぎにを追加してください",
            "ならば"),
        Arguments.of(
            "FLOW-F004.bsb",
            DiagnosticCode.E_UNEXPECTED_BLOCK_END,
            2,
            5,
            Map.of(),
            "ならばの内側",
            "つぎに",
            "この語を削除してください",
            null),
        Arguments.of(
            "FLOW-F005.bsb",
            DiagnosticCode.E_UNEXPECTED_LOOP_END,
            2,
            5,
            Map.of(),
            "ループの内側",
            "繰り返す",
            "この語を削除してください",
            null),
        Arguments.of(
            "FLOW-F006.bsb",
            DiagnosticCode.E_EXPECTED_LOOP_END,
            4,
            1,
            Map.of("loopKind", "回数", "openingLine", "2", "openingColumn", "7"),
            "繰り返す",
            "こと",
            "ここに繰り返すを追加してください",
            "回だけ"),
        Arguments.of(
            "FLOW-F007.bsb",
            DiagnosticCode.E_EXPECTED_LOOP_CONDITION_SEPARATOR,
            4,
            1,
            Map.of("openingLine", "2", "openingColumn", "5"),
            "続く間",
            "こと",
            "ここに続く間を追加してください",
            "ここから"),
        Arguments.of(
            "FLOW-F008.bsb",
            DiagnosticCode.E_UNEXPECTED_LOOP_SEPARATOR,
            5,
            5,
            Map.of("firstLine", "4", "firstColumn", "5"),
            "続く間は1個",
            "2個目の続く間",
            "この語を削除してください",
            "最初の続く間"));
  }
}
