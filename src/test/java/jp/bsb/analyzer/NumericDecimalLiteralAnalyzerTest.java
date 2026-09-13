package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class NumericDecimalLiteralAnalyzerTest {
  @Test
  void acceptsNormativeDisplayCasesWithoutChangingTheirLiteralLexemes() throws Exception {
    for (String sourceName : List.of("NUM-N001.bsb", "NUM-N018.bsb")) {
      AnalysisResult result = AnalyzerTestSupport.checkText(numericsText("sources/" + sourceName));

      assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
      assertTrue(result.diagnostics().isEmpty(), sourceName);
    }
  }

  @Test
  void infersDecimalGlobalsLocalsAssignmentsUserEffectsAndArrays() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "大域値は 変数 1.50。\n\n"
                + "そのままとは （小数 -- 小数）\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "    局所値は 定数 2.500。\n"
                + "    3.75 を 大域値 に 入れる\n"
                + "    大域値 を そのまま を 一行表示する\n"
                + "    局所値 を 一行表示する\n"
                + "    【1.0、2.00】 を 一行表示する\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    AnalyzedProgram analyzed = result.programForIrGeneration();
    assertTrue(
        analyzed.nameResolution().bindings().stream()
            .allMatch(binding -> binding.typeState().type().orElseThrow() == ValueType.DECIMAL));
    assertEquals(
        List.of(ValueType.DECIMAL), analyzed.findUserWord("そのまま").orElseThrow().inputTypes());
    assertEquals(
        List.of(ValueType.DECIMAL), analyzed.findUserWord("そのまま").orElseThrow().outputTypes());
    assertEquals(
        List.of(ValueType.arrayOf(ValueType.DECIMAL)),
        analyzed.arrayLiteralTypes().values().stream().toList());
  }

  @Test
  void preservesThe4096DigitLexicalGateBeforeDecimalValueConstruction() {
    String acceptedLiteral = "1." + "0".repeat(4_095);
    String rejectedLiteral = "1." + "0".repeat(4_096);
    AnalysisResult accepted = AnalyzerTestSupport.checkText(mainDisplaying(acceptedLiteral));
    AnalysisResult rejected = AnalyzerTestSupport.checkText(mainDisplaying(rejectedLiteral));

    assertTrue(accepted.successful(), accepted.diagnostics().toString());
    assertFalse(rejected.successful());
    assertEquals(List.of(DiagnosticCode.E_NUMBER_LIMIT), codes(rejected));
    assertEquals("4097", rejected.diagnostics().getFirst().observed().orElseThrow());
  }

  @Test
  void reportsNumericFeatureTypeFailuresInsteadOfTheFormerFeatureBoundary() throws Exception {
    AnalysisResult mixedArithmetic =
        AnalyzerTestSupport.checkText(numericsText("sources/NUM-F001.bsb"));
    AnalysisResult mixedArray = AnalyzerTestSupport.checkText(numericsText("sources/NUM-F005.bsb"));

    assertEquals(List.of(DiagnosticCode.E_TYPE_MISMATCH), codes(mixedArithmetic));
    assertEquals("小数", mixedArithmetic.diagnostics().getFirst().fields().get("actualType"));
    assertEquals(List.of(DiagnosticCode.E_ARRAY_ELEMENT_TYPE_MISMATCH), codes(mixedArray));
    assertEquals("小数", mixedArray.diagnostics().getFirst().fields().get("actualType"));
  }

  private static String mainDisplaying(String literal) {
    return "メインとは （--）\n    " + literal + " を 一行表示する\nこと。\n";
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
  }

  private static String numericsText(String relativePath) throws Exception {
    String resource = "/conformance/numerics/" + relativePath;
    try (var input = NumericDecimalLiteralAnalyzerTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}
