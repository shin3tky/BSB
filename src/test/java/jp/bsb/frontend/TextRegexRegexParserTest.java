package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LiteralKind;
import org.junit.jupiter.api.Test;

class TextRegexRegexParserTest {
  @Test
  void carriesRawPatternFlagsAndPositionsIntoTheAst() throws Exception {
    var parsed = ParserTestSupport.parseTextRegexResource("TEXT-N012.bsb");

    assertFalse(parsed.diagnostics().hasErrors(), parsed.diagnostics().diagnostics().toString());
    Literal regex =
        parsed.parseResult().programForAnalysis().definitions().getFirst().body().stream()
            .filter(Literal.class::isInstance)
            .map(Literal.class::cast)
            .filter(literal -> literal.kind() == LiteralKind.REGEX)
            .findFirst()
            .orElseThrow();

    assertEquals("^abc$", regex.value());
    assertEquals("mi", regex.regexMetadata().orElseThrow().flags());
    assertEquals(6, regex.regexMetadata().orElseThrow().patternPositions().size());
  }
}
