package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.bsb.conformance.ControlFlowConformanceData.DiagnosticSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;

/** 制御フローのCLI表示試験と構造化診断試験で共有する、期待診断の組立てと比較です。 */
final class ControlFlowDiagnosticSupport {
  private ControlFlowDiagnosticSupport() {}

  /** TSVの各列から、表示器へ渡せる期待診断を組み立てます。 */
  static Diagnostic expectedDiagnostic(DiagnosticSpec spec, String sourcePath) {
    DiagnosticCode code = DiagnosticCode.valueOf(spec.code());
    var builder =
        Diagnostic.builder(
            code,
            severity(spec.severity()),
            stageFor(code),
            sourcePath,
            new SourcePosition(0, spec.line(), spec.column()));
    for (Map.Entry<String, String> field : spec.fields().entrySet()) {
      if (!field.getKey().equals("limit") && !field.getKey().equals("observed")) {
        builder.field(field.getKey(), field.getValue());
      }
    }
    if (spec.fields().containsKey("limit")) {
      builder.limit("syntaxDepth", spec.fields().get("limit"), spec.fields().get("observed"));
    }
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

  /**
   * 実装が返した診断を、表示文ではなく独立した各フィールドとして比較します。
   *
   * <p>実行時診断はデバッグ用のスタック情報も持つため、規範TSVに指定されたフィールドがすべて一致することを検査します。
   */
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

    var ordinaryFields = new LinkedHashMap<>(spec.fields());
    String limit = ordinaryFields.remove("limit");
    String observed = ordinaryFields.remove("observed");
    ordinaryFields.forEach(
        (key, value) -> assertEquals(value, actual.fields().get(key), label + ": field " + key));
    if (actual.stage() != DiagnosticStage.RUNTIME) {
      assertEquals(ordinaryFields, actual.fields(), label + ": fields");
    }
    assertEquals(limit, actual.limit().orElse(null), label + ": limit");
    assertEquals(observed, actual.observed().orElse(null), label + ": observed");
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
      case E_UNEXPECTED_ELSE,
          E_DUPLICATE_ELSE,
          E_EXPECTED_IF_END,
          E_UNEXPECTED_BLOCK_END,
          E_UNEXPECTED_LOOP_END,
          E_EXPECTED_LOOP_END,
          E_EXPECTED_LOOP_CONDITION_SEPARATOR,
          E_UNEXPECTED_LOOP_SEPARATOR,
          E_SYNTAX_DEPTH_LIMIT ->
          DiagnosticStage.SYNTAX;
      case E_NEGATIVE_REPEAT_COUNT -> DiagnosticStage.RUNTIME;
      case E_CONDITION_STACK_UNDERFLOW,
          E_CONDITION_TYPE_MISMATCH,
          E_BRANCH_STACK_MISMATCH,
          E_REPEAT_COUNT_UNDERFLOW,
          E_REPEAT_COUNT_TYPE_MISMATCH,
          E_LOOP_STACK_MISMATCH,
          E_LOOP_CONDITION_MISMATCH,
          E_BREAK_OUTSIDE_LOOP,
          E_CONTINUE_OUTSIDE_LOOP,
          E_RETURN_EFFECT_MISMATCH,
          E_STACK_UNDERFLOW,
          E_TYPE_MISMATCH,
          W_UNREACHABLE_CODE ->
          DiagnosticStage.TYPE_AND_STACK;
      default -> throw new IllegalArgumentException("not a ControlFlow diagnostic: " + code);
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
