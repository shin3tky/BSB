package jp.bsb.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticCollectorTest {
  @Test
  void sortsByStageOffsetAndCode() {
    var collector = new DiagnosticCollector();
    collector.add(diagnostic(DiagnosticCode.E_UNEXPECTED_TOP_LEVEL, DiagnosticStage.SYNTAX, 1));
    collector.add(diagnostic(DiagnosticCode.E_UNEXPECTED_CHARACTER, DiagnosticStage.LEXICAL, 10));
    collector.add(diagnostic(DiagnosticCode.E_INVALID_IDENTIFIER, DiagnosticStage.LEXICAL, 10));
    collector.add(diagnostic(DiagnosticCode.E_INVALID_UTF8, DiagnosticStage.UTF8, 20));

    assertEquals(
        List.of(
            DiagnosticCode.E_INVALID_UTF8,
            DiagnosticCode.E_INVALID_IDENTIFIER,
            DiagnosticCode.E_UNEXPECTED_CHARACTER,
            DiagnosticCode.E_UNEXPECTED_TOP_LEVEL),
        collector.diagnostics().stream().map(Diagnostic::code).toList());
  }

  @Test
  void keeps99DiagnosticsAndReplacesThe100thWithTheLimitNotice() {
    var collector = new DiagnosticCollector();
    for (int index = 0; index < 99; index++) {
      assertTrue(
          collector.add(
              diagnostic(DiagnosticCode.E_UNDEFINED_WORD, DiagnosticStage.NAME, (long) index * 2)));
    }

    assertFalse(
        collector.add(diagnostic(DiagnosticCode.E_UNDEFINED_WORD, DiagnosticStage.NAME, 198)));
    assertFalse(
        collector.add(diagnostic(DiagnosticCode.E_UNDEFINED_WORD, DiagnosticStage.NAME, 200)));

    assertEquals(100, collector.size());
    assertTrue(collector.limitReached());
    assertTrue(collector.hasErrors());
    assertEquals(
        99,
        collector.diagnostics().stream()
            .filter(value -> value.code() == DiagnosticCode.E_UNDEFINED_WORD)
            .count());
    Diagnostic limit = collector.diagnostics().get(99);
    assertEquals(DiagnosticCode.E_DIAGNOSTIC_LIMIT, limit.code());
    assertEquals("100", limit.limit().orElseThrow());
    assertEquals("100", limit.observed().orElseThrow());
  }

  @Test
  void warningsAloneDoNotCountAsErrors() {
    var collector = new DiagnosticCollector();
    collector.add(
        Diagnostic.builder(
                DiagnosticCode.W_PARTICLE_POSITION,
                Severity.WARNING,
                DiagnosticStage.TYPE_AND_STACK,
                "入力.bsb",
                new SourcePosition(0, 1, 1))
            .build());

    assertFalse(collector.hasErrors());
  }

  private static Diagnostic diagnostic(
      DiagnosticCode code, DiagnosticStage stage, long utf8Offset) {
    return Diagnostic.builder(code, Severity.ERROR, stage, "入力.bsb", new FileOffset(utf8Offset))
        .build();
  }
}
