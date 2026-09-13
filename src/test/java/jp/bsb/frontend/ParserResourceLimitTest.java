package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class ParserResourceLimitTest {
  @Test
  void acceptsExactly10000Definitions() {
    var parsed =
        ParserTestSupport.parseText("CORE-R004-ok.bsb", definitions(Parser.MAX_DEFINITIONS));

    assertTrue(parsed.parseResult().successful());
    assertEquals(
        Parser.MAX_DEFINITIONS, parsed.parseResult().programForAnalysis().definitions().size());
  }

  @Test
  void rejectsThe10001stDefinitionBeforeAddingItToTheAst() {
    var parsed =
        ParserTestSupport.parseText("CORE-R004-error.bsb", definitions(Parser.MAX_DEFINITIONS + 1));

    var diagnostic = parsed.diagnostics().diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_DEFINITION_LIMIT, diagnostic.code());
    assertEquals("10000", diagnostic.limit().orElseThrow());
    assertEquals("10001", diagnostic.observed().orElseThrow());
    assertEquals(
        Parser.MAX_DEFINITIONS, parsed.parseResult().partialProgram().definitions().size());
  }

  private static String definitions(int count) {
    var source = new StringBuilder(count * 20);
    for (int index = 0; index < count - 1; index++) {
      source.append("単語").append(index).append("とは （--）\nこと。\n");
    }
    source.append("メインとは （--）\nこと。\n");
    return source.toString();
  }
}
