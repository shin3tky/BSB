package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BindingParserDiagnosticTest {
  @ParameterizedTest
  @MethodSource("syntaxFailures")
  void matchesEveryBindingSyntaxFailure(
      String sourceName, DiagnosticCode code, int line, int column) throws IOException {
    var parsed = ParserTestSupport.parseBindingResource(sourceName);

    assertFalse(parsed.parseResult().successful(), sourceName);
    assertEquals(1, parsed.diagnostics().diagnostics().size(), sourceName);
    var diagnostic = parsed.diagnostics().diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line(), sourceName);
    assertEquals(
        column, diagnostic.location().displayPosition().orElseThrow().column(), sourceName);
    assertThrows(IllegalStateException.class, parsed.parseResult()::programForAnalysis);
  }

  @org.junit.jupiter.api.Test
  void missingLocalDeclarationEndDoesNotConsumeTheWordEndOrNextDefinition() {
    String source = "壊れたとは （--）\n" + "    値は 定数 1\n" + "こと。\n\n" + "次とは （--）\n" + "こと。\n";
    var parsed = ParserTestSupport.parseText("回復.bsb", source);

    assertFalse(parsed.parseResult().successful());
    assertEquals(
        List.of("壊れた", "次"),
        parsed.parseResult().partialProgram().definitions().stream()
            .map(word -> word.name())
            .toList());
    assertEquals(1, parsed.diagnostics().diagnostics().size());
    assertEquals(
        DiagnosticCode.E_EXPECTED_DECLARATION_END,
        parsed.diagnostics().diagnostics().getFirst().code());
  }

  private static Stream<Arguments> syntaxFailures() {
    return Stream.of(
        Arguments.of("BIND-F001.bsb", DiagnosticCode.E_DECLARATION_ADJACENCY, 1, 3),
        Arguments.of("BIND-F002.bsb", DiagnosticCode.E_EXPECTED_DECLARATION_KIND, 1, 4),
        Arguments.of(
            "BIND-F003.bsb", DiagnosticCode.E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED, 1, 4),
        Arguments.of("BIND-F004.bsb", DiagnosticCode.E_EXPECTED_DECLARATION_END, 2, 1),
        Arguments.of("BIND-F005.bsb", DiagnosticCode.E_UNEXPECTED_DECLARATION_END, 1, 1),
        Arguments.of("BIND-F006.bsb", DiagnosticCode.E_EXPECTED_ASSIGNMENT_VALUE_PARTICLE, 4, 7),
        Arguments.of("BIND-F007.bsb", DiagnosticCode.E_EXPECTED_ASSIGNMENT_TARGET, 4, 9),
        Arguments.of("BIND-F008.bsb", DiagnosticCode.E_EXPECTED_ASSIGNMENT_TARGET_PARTICLE, 4, 12),
        Arguments.of("BIND-F012.bsb", DiagnosticCode.E_INITIALIZER_ELEMENT_NOT_ALLOWED, 1, 10));
  }
}
