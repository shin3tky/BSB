package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.Test;

class OptionalDiagnosticConformanceTest {
  @Test
  void matchesEverySourceBackedStructuredDiagnosticField() throws Exception {
    for (var caseSpec : OptionalConformanceData.loadCases()) {
      List<OptionalConformanceData.DiagnosticSpec> expected =
          OptionalConformanceData.loadDiagnostics().stream()
              .filter(spec -> spec.id().equals(caseSpec.id()))
              .toList();
      if (expected.isEmpty() || caseSpec.source().equals("-")) {
        continue;
      }
      byte[] source = OptionalConformanceData.resourceBytes(caseSpec.source());
      List<Diagnostic> actual =
          expected.getFirst().command().equals("run")
              ? new ProgramRunner()
                  .run(
                      caseSpec.id() + ".bsb",
                      source,
                      new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()))
                  .diagnostics()
              : new SourceChecker().check(caseSpec.id() + ".bsb", source).diagnostics();
      assertEquals(expected.size(), actual.size(), caseSpec.id());
      for (int index = 0; index < expected.size(); index++) {
        assertDiagnostic(expected.get(index), actual.get(index));
      }
    }
  }

  private static void assertDiagnostic(
      OptionalConformanceData.DiagnosticSpec expected, Diagnostic actual) {
    var position = actual.location().displayPosition().orElseThrow();
    assertEquals(expected.code(), actual.code().name(), expected.id());
    assertEquals(expected.severity(), actual.severity().name().toLowerCase(), expected.id());
    assertEquals(stageName(actual), expected.stage(), expected.id());
    assertEquals(expected.line(), position.line(), expected.id());
    assertEquals(expected.column(), position.column(), expected.id());
    assertEquals(expected.fields(), actual.fields(), expected.id());
    assertEquals(optional(expected.expected()), actual.expected(), expected.id());
    assertEquals(optional(expected.actual()), actual.actual(), expected.id());
    assertEquals(expected.fixes(), actual.fixes(), expected.id());
    assertEquals(
        expected.related(),
        actual.relatedLocations().stream()
            .map(
                related ->
                    related.position().line()
                        + ":"
                        + related.position().column()
                        + ":"
                        + related.description())
            .toList(),
        expected.id());
    assertEquals(optional(expected.limitName()), actual.limitName(), expected.id());
    assertEquals(optional(expected.limit()), actual.limit(), expected.id());
    assertEquals(optional(expected.observed()), actual.observed(), expected.id());
  }

  private static java.util.Optional<String> optional(String value) {
    return value.equals("-") ? java.util.Optional.empty() : java.util.Optional.of(value);
  }

  private static String stageName(Diagnostic diagnostic) {
    return switch (diagnostic.stage()) {
      case UTF8 -> "utf8";
      case LEXICAL -> "lexical";
      case SYNTAX -> "syntax";
      case NAME -> "name";
      case TYPE_AND_STACK -> "typeAndStack";
      case IR -> "ir";
      case RUNTIME -> "runtime";
    };
  }
}
