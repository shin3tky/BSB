package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SemanticAnalyzerTest {
  @ParameterizedTest
  @ValueSource(strings = {"足す", "引く", "掛ける", "等しい", "表示する", "一行表示する"})
  void reportsUnderflowForEveryBuiltinThatConsumesInput(String word) {
    AnalysisResult result = AnalyzerTestSupport.checkText(mainWithBody(word));

    assertFalse(result.successful());
    assertEquals(DiagnosticCode.E_STACK_UNDERFLOW, result.diagnostics().getFirst().code());
  }

  @ParameterizedTest
  @MethodSource("fixedTypeMismatchCases")
  void reportsTypeMismatchForConstrainedBuiltins(String body, int inputIndex) {
    AnalysisResult result = AnalyzerTestSupport.checkText(mainWithBody(body));

    assertFalse(result.successful());
    var diagnostic = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_TYPE_MISMATCH, diagnostic.code());
    assertEquals(Integer.toString(inputIndex), diagnostic.fields().get("inputIndex"));
  }

  @Test
  void acceptsEveryDisplayableTypeAndTheZeroInputBuiltin() {
    String source =
        "メインとは （--）\n"
            + "    1 を 表示する\n"
            + "    はい を 表示する\n"
            + "    '字' を 一行表示する\n"
            + "    「列」を 一行表示する\n"
            + "    改行する\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @Test
  void classifiesReservedFutureNamesAndDictionaryConstraints() {
    String source =
        "配列とは （T -- T）\n"
            + "こと。\n\n"
            + "制約とは （T -- T）\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    JSON\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertFalse(result.successful());
    assertEquals(
        List.of(
            DiagnosticCode.E_RESERVED_NAME,
            DiagnosticCode.E_NAME_NOT_CALLABLE,
            DiagnosticCode.E_TYPE_CONSTRAINT_NOT_ALLOWED),
        codes(result));
  }

  @Test
  void acceptsTheFormerNumericLeadingFutureNameAsARoundingModeValue() {
    AnalysisResult result = AnalyzerTestSupport.checkText(mainWithBody("0方向へ丸め を 一行表示する"));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertTrue(result.diagnostics().isEmpty());
  }

  @Test
  void warnsForConfusableDefinitionsWithoutMergingTheirNames() {
    String source =
        "Alphaとは （--）\n" + "こと。\n\n" + "Аlphaとは （--）\n" + "こと。\n\n" + "メインとは （--）\n" + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(List.of(DiagnosticCode.W_CONFUSABLE_IDENTIFIER), codes(result));
    assertEquals(3, result.programForIrGeneration().userWordSignatures().size());
  }

  @Test
  void reportsOneWarningPerBadParticleAndIgnoresCommentsForAdjacency() {
    String source = "メインとは （--）\n" + "    10 を # between\n" + "    と 表示する から\n" + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful());
    assertEquals(
        List.of(
            DiagnosticCode.W_PARTICLE_POSITION,
            DiagnosticCode.W_PARTICLE_POSITION,
            DiagnosticCode.W_PARTICLE_POSITION),
        codes(result));
  }

  @Test
  void ordersNameDiagnosticsBeforeTypeDiagnosticsAndSuppressesEffectCascades() {
    String source =
        "不正とは （金額 -- 金額）\n"
            + "こと。\n\n"
            + "呼ぶとは （--）\n"
            + "    未定義\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertEquals(
        List.of(DiagnosticCode.E_UNDEFINED_WORD, DiagnosticCode.E_UNKNOWN_TYPE), codes(result));
    assertEquals(
        List.of(DiagnosticStage.NAME, DiagnosticStage.TYPE_AND_STACK),
        result.diagnostics().stream().map(diagnostic -> diagnostic.stage()).toList());
    assertFalse(codes(result).contains(DiagnosticCode.E_WORD_EFFECT_MISMATCH));
  }

  private static Stream<Arguments> fixedTypeMismatchCases() {
    return Stream.of(
        Arguments.of("「1」 2 足す", 1),
        Arguments.of("1 「2」 引く", 2),
        Arguments.of("はい 2 掛ける", 1),
        Arguments.of("「1」 1 等しい", 2));
  }

  private static String mainWithBody(String body) {
    return "メインとは （--）\n    " + body + "\nこと。\n";
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
  }
}
