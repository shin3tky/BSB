package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.SourceSpan;
import org.junit.jupiter.api.Test;

class RecoverableJsonDiagnosticConformanceTest {
  @Test
  void everyStaticFailureAndWarningMatchesItsCentralCodeStageSpanAndSelectedFields()
      throws Exception {
    Map<String, RecoverableJsonConformanceData.CatalogSpec> catalog =
        RecoverableJsonConformanceData.loadCatalog().stream()
            .collect(
                Collectors.toMap(RecoverableJsonConformanceData.CatalogSpec::id, item -> item));
    Map<String, List<RecoverableJsonConformanceData.DiagnosticSpec>> expectedById =
        RecoverableJsonConformanceData.loadDiagnostics().stream()
            .filter(item -> !item.id().equals("RJSON-F009"))
            .collect(Collectors.groupingBy(RecoverableJsonConformanceData.DiagnosticSpec::id));

    for (var entry : expectedById.entrySet()) {
      String id = entry.getKey();
      String evidence = catalog.get(id).evidence();
      byte[] source = RecoverableJsonConformanceData.resourceBytes(evidence);
      var analysis = new SourceChecker().check(evidence, source);
      List<Diagnostic> actual = analysis.diagnostics();
      assertEquals(entry.getValue().size(), actual.size(), id);
      for (var expected : entry.getValue()) {
        Diagnostic diagnostic =
            actual.stream()
                .filter(item -> item.code().name().equals(expected.code()))
                .filter(item -> lexeme(source, item).equals(expected.targetLexeme()))
                .findFirst()
                .orElse(null);
        assertNotNull(diagnostic, id + '/' + expected.variant());
        assertEquals(expected.stage(), publicStage(diagnostic), id + '/' + expected.variant());
        if (!expected.expectedType().equals("-")) {
          assertTrue(
              diagnostic.expected().orElse("").contains(expected.expectedType()),
              id + '/' + expected.variant() + "/expected");
        }
        if (!expected.actualType().equals("-")) {
          assertTrue(
              diagnostic.actual().orElse("").contains(expected.actualType()),
              id + '/' + expected.variant() + "/actual");
        }
        if (!expected.requiredCount().equals("-")) {
          assertEquals(
              expected.requiredCount(),
              diagnostic.fields().get("requiredCount"),
              id + '/' + expected.variant());
        }
        if (!expected.actualCount().equals("-")) {
          assertEquals(
              expected.actualCount(),
              diagnostic.fields().get("actualCount"),
              id + '/' + expected.variant());
        }
      }
    }
  }

  @Test
  void existingParserDiagnosticRowsMatchTheCentralCaseContrasts() throws Exception {
    var expected =
        RecoverableJsonConformanceData.loadDiagnostics().stream()
            .filter(item -> item.id().equals("RJSON-F009"))
            .toList();
    var cases =
        RecoverableJsonConformanceData.loadCases().stream()
            .filter(item -> item.id().equals("RJSON-F009"))
            .toList();
    assertEquals(2, expected.size());
    assertEquals(
        expected.stream().map(RecoverableJsonConformanceData.DiagnosticSpec::code).toList(),
        cases.stream().map(RecoverableJsonConformanceData.CaseSpec::diagnostic).toList());
    assertTrue(cases.stream().allMatch(RecoverableJsonConformanceData.CaseSpec::inputPreserved));
  }

  private static String lexeme(byte[] source, Diagnostic diagnostic) {
    SourceSpan span = (SourceSpan) diagnostic.location();
    return new String(
        source,
        Math.toIntExact(span.start().utf8Offset()),
        Math.toIntExact(span.end().utf8Offset() - span.start().utf8Offset()),
        StandardCharsets.UTF_8);
  }

  private static String publicStage(Diagnostic diagnostic) {
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
