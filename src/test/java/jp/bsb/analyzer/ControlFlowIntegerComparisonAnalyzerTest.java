package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 制御フローで追加する整数比較の静的検査を、規範例と照合します。 */
class ControlFlowIntegerComparisonAnalyzerTest {
  @ParameterizedTest
  @ValueSource(strings = {"FLOW-N017.bsb", "FLOW-N018.bsb"})
  void acceptsNormalIntegerComparisonCases(String sourceName) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkControlFlowResource(sourceName);

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName);
  }

  @ParameterizedTest
  @MethodSource("comparisonFailureCases")
  void matchesSpecifiedComparisonFailure(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix)
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkControlFlowResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(8, result.exitCode(), sourceName);
    assertEquals(1, result.diagnostics().size(), sourceName);
    var diagnostic = result.diagnostics().getFirst();
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(Severity.ERROR, diagnostic.severity(), sourceName);
    assertEquals(DiagnosticStage.TYPE_AND_STACK, diagnostic.stage(), sourceName);
    assertEquals(line, position.line(), sourceName);
    assertEquals(column, position.column(), sourceName);
    assertEquals(fields, diagnostic.fields(), sourceName);
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(java.util.List.of(fix), diagnostic.fixes(), sourceName);
    assertTrue(diagnostic.relatedLocations().isEmpty(), sourceName);
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  private static Stream<Arguments> comparisonFailureCases() {
    return Stream.of(
        Arguments.of(
            "FLOW-F023.bsb",
            DiagnosticCode.E_STACK_UNDERFLOW,
            2,
            9,
            Map.of("word", "比べて小さい", "requiredCount", "2", "actualCount", "1"),
            "[整数, 整数]",
            "[整数]",
            "整数をもう1つ置いてください"),
        Arguments.of(
            "FLOW-F024.bsb",
            DiagnosticCode.E_TYPE_MISMATCH,
            2,
            14,
            Map.of(
                "word", "比べて小さい",
                "inputIndex", "1",
                "expectedType", "整数",
                "actualType", "文字列"),
            "[整数, 整数]",
            "[文字列, 整数]",
            "第1入力を整数にしてください"));
  }
}
