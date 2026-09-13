package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.conformance.BindingConformanceData.DiagnosticSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;

/** 束縛のCLI表示試験と構造化診断試験で共有する期待診断支援です。 */
final class BindingDiagnosticSupport {
  private BindingDiagnosticSupport() {}

  /** TSVの各列から表示器へ渡せる期待診断を組み立てます。 */
  static Diagnostic expectedDiagnostic(DiagnosticSpec spec, String sourcePath) {
    DiagnosticCode code = DiagnosticCode.valueOf(spec.code());
    var builder =
        Diagnostic.builder(
            code,
            severity(spec.severity()),
            stageFor(code),
            sourcePath,
            new SourcePosition(0, spec.line(), spec.column()));
    spec.fields().forEach(builder::field);
    if (spec.expected() != null) {
      builder.expected(spec.expected());
    }
    if (spec.actual() != null) {
      builder.actual(spec.actual());
    }
    if (spec.fix() != null) {
      builder.fix(spec.fix());
    }
    for (ExpectedRelated related : parseRelated(spec.related())) {
      builder.relatedLocation(
          new RelatedLocation(
              sourcePath,
              new SourcePosition(0, related.line(), related.column()),
              related.description()));
    }
    return builder.build();
  }

  /** 実装が返した診断を、表示文ではなく独立した各フィールドとして比較します。 */
  static void assertStructuredDiagnostic(
      String label, DiagnosticSpec spec, Diagnostic actual, String sourcePath) {
    Diagnostic expected = expectedDiagnostic(spec, sourcePath);
    assertEquals(expected.code(), actual.code(), label + ": code");
    assertEquals(expected.severity(), actual.severity(), label + ": severity");
    assertEquals(expected.stage(), actual.stage(), label + ": stage");
    assertEquals(sourcePath, actual.sourcePath(), label + ": source path");
    var position = actual.location().displayPosition().orElseThrow();
    assertEquals(spec.line(), position.line(), label + ": line");
    assertEquals(spec.column(), position.column(), label + ": column");
    assertEquals(spec.fields(), actual.fields(), label + ": fields");
    assertEquals(spec.expected(), actual.expected().orElse(null), label + ": expected");
    assertEquals(spec.actual(), actual.actual().orElse(null), label + ": actual");
    assertEquals(
        spec.fix() == null ? List.of() : List.of(spec.fix()), actual.fixes(), label + ": fixes");

    List<ExpectedRelated> expectedRelated = parseRelated(spec.related());
    assertEquals(
        expectedRelated.size(), actual.relatedLocations().size(), label + ": related count");
    for (int index = 0; index < expectedRelated.size(); index++) {
      ExpectedRelated related = expectedRelated.get(index);
      RelatedLocation actualRelated = actual.relatedLocations().get(index);
      assertEquals(sourcePath, actualRelated.sourcePath(), label + ": related path " + index);
      assertEquals(
          related.line(), actualRelated.position().line(), label + ": related line " + index);
      assertEquals(
          related.column(), actualRelated.position().column(), label + ": related column " + index);
      assertEquals(
          related.description(),
          actualRelated.description(),
          label + ": related description " + index);
    }
  }

  private static Severity severity(String value) {
    return switch (value) {
      case "error" -> Severity.ERROR;
      case "warning" -> Severity.WARNING;
      default -> throw new IllegalArgumentException("unknown severity: " + value);
    };
  }

  private static DiagnosticStage stageFor(DiagnosticCode code) {
    return switch (code) {
      case E_DECLARATION_ADJACENCY,
          E_EXPECTED_DECLARATION_KIND,
          E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED,
          E_EXPECTED_DECLARATION_END,
          E_UNEXPECTED_DECLARATION_END,
          E_EXPECTED_ASSIGNMENT_VALUE_PARTICLE,
          E_EXPECTED_ASSIGNMENT_TARGET,
          E_EXPECTED_ASSIGNMENT_TARGET_PARTICLE,
          E_INITIALIZER_ELEMENT_NOT_ALLOWED ->
          DiagnosticStage.SYNTAX;
      case E_REFERENCE_BEFORE_INITIALIZATION,
          E_REFERENCE_BEFORE_DECLARATION,
          E_BINDING_OUT_OF_SCOPE,
          E_DUPLICATE_NAME,
          E_NAME_SHADOWING,
          E_ASSIGN_TO_CONSTANT,
          E_ASSIGNMENT_TARGET_NOT_VARIABLE,
          E_UNDEFINED_ASSIGNMENT_TARGET,
          E_GLOBAL_BINDING_LIMIT,
          E_LOCAL_BINDING_LIMIT,
          E_BINDING_LIMIT ->
          DiagnosticStage.NAME;
      case E_INITIALIZER_VALUE_MISSING,
          E_INITIALIZER_VALUE_COUNT,
          E_INITIALIZER_CALL_NOT_ALLOWED,
          E_ASSIGNMENT_STACK_UNDERFLOW,
          E_ASSIGNMENT_TYPE_MISMATCH,
          E_STACK_UNDERFLOW,
          W_UNREACHABLE_CODE ->
          DiagnosticStage.TYPE_AND_STACK;
      default -> throw new IllegalArgumentException("not a bindings diagnostic: " + code);
    };
  }

  private static List<ExpectedRelated> parseRelated(String value) {
    if (value == null) {
      return List.of();
    }
    var result = new ArrayList<ExpectedRelated>();
    for (String entry : value.split(";", -1)) {
      String[] positioned = entry.split(" ", 2);
      assertTrue(
          positioned.length == 2 && positioned[0].matches("[0-9]+:[0-9]+"),
          "invalid related location: " + entry);
      String[] coordinates = positioned[0].split(":", -1);
      result.add(
          new ExpectedRelated(
              Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), positioned[1]));
    }
    return List.copyOf(result);
  }

  private record ExpectedRelated(int line, int column, String description) {}
}
