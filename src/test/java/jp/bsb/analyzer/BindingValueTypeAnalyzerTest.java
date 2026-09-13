package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BindingValueTypeAnalyzerTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "BIND-N001.bsb", "BIND-N002.bsb", "BIND-N003.bsb", "BIND-N004.bsb", "BIND-N005.bsb",
        "BIND-N006.bsb", "BIND-N007.bsb", "BIND-N008.bsb", "BIND-N009.bsb", "BIND-N010.bsb",
        "BIND-N011.bsb", "BIND-N012.bsb", "BIND-N013.bsb", "BIND-N014.bsb", "BIND-N015.bsb",
        "BIND-N016.bsb", "BIND-N017.bsb", "BIND-N018.bsb", "BIND-N019.bsb"
      })
  void acceptsEveryTextBackedBindingNormalCaseWithInferredBindingTypes(String sourceName)
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkBindingResource(sourceName);

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertTrue(
        result.programForIrGeneration().nameResolution().bindings().stream()
            .allMatch(binding -> binding.typeState().status() == BindingTypeState.Status.INFERRED),
        sourceName);
  }

  @ParameterizedTest
  @MethodSource("typeFailures")
  void matchesEveryBindingInitializerAndAssignmentTypeFailure(
      String sourceName, List<ExpectedDiagnostic> expectedDiagnostics) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkBindingResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(8, result.exitCode(), sourceName);
    assertEquals(expectedDiagnostics.size(), result.diagnostics().size(), sourceName);
    IntStream.range(0, expectedDiagnostics.size())
        .forEach(
            index ->
                assertDiagnostic(
                    result.diagnostics().get(index), expectedDiagnostics.get(index), sourceName));
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  @Test
  void infersAllFourCurrentTypesForConstantsAndVariables() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "整数定数は 定数 1。\n"
                + "真偽定数は 定数 はい。\n"
                + "文字定数は 定数 '字'。\n"
                + "文字列定数は 定数 「列」。\n\n"
                + "メインとは （--）\n"
                + "    整数変数は 変数 1。\n"
                + "    真偽変数は 変数 はい。\n"
                + "    文字変数は 変数 '字'。\n"
                + "    文字列変数は 変数 「列」。\n"
                + "    2 を 整数変数 に 入れる\n"
                + "    いいえ を 真偽変数 に 入れる\n"
                + "    '別' を 文字変数 に 入れる\n"
                + "    「別」を 文字列変数 に 入れる\n"
                + "    整数変数 を 表示する\n"
                + "    真偽変数 を 表示する\n"
                + "    文字変数 を 表示する\n"
                + "    文字列変数 を 表示する\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        List.of(
            ValueType.INTEGER,
            ValueType.BOOLEAN,
            ValueType.CHARACTER,
            ValueType.STRING,
            ValueType.INTEGER,
            ValueType.BOOLEAN,
            ValueType.CHARACTER,
            ValueType.STRING),
        result.programForIrGeneration().nameResolution().bindings().stream()
            .map(binding -> binding.typeState().type().orElseThrow())
            .toList());
  }

  @Test
  void infersGlobalsBeforeLocalsEvenWhenTheGlobalIsWrittenAfterTheWord() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "使うとは （--）\n"
                + "    局所は 定数 後方大域。\n"
                + "こと。\n\n"
                + "後方大域は 定数 42。\n\n"
                + "メインとは （--）\n"
                + "    使う\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertTrue(
        result.programForIrGeneration().nameResolution().bindings().stream()
            .allMatch(binding -> binding.typeState().type().orElseThrow() == ValueType.INTEGER));
  }

  @Test
  void checksALocalInitializerFromAnEmptyStackInsteadOfTheWordInputStack() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "処理とは （整数 整数 -- 整数 整数）\n" + "    値は 定数 足す。\n" + "こと。\n\n" + "メインとは （--）\n" + "こと。\n");

    assertEquals(List.of(DiagnosticCode.E_STACK_UNDERFLOW), codes(result));
    assertEquals("0", result.diagnostics().getFirst().fields().get("actualCount"));
    assertEquals("2", result.diagnostics().getFirst().fields().get("requiredCount"));
  }

  @Test
  void acceptsTheNormativeChapterProgramAndInfersEveryBinding() throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkBindingChapterResource();

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(4, result.programForIrGeneration().nameResolution().bindings().size());
    assertTrue(
        result.programForIrGeneration().nameResolution().bindings().stream()
            .allMatch(binding -> binding.typeState().status() == BindingTypeState.Status.INFERRED));
  }

  @Test
  void infersDecimalInitializersAndChecksBuiltinTypesInsideInitializers() {
    AnalysisResult decimal =
        AnalyzerTestSupport.checkText("値は 定数 1.5。\n\n" + "メインとは （--）\n" + "こと。\n");
    assertTrue(decimal.successful(), decimal.diagnostics().toString());
    assertEquals(
        ValueType.DECIMAL,
        decimal
            .programForIrGeneration()
            .nameResolution()
            .bindings()
            .getFirst()
            .typeState()
            .type()
            .orElseThrow());

    AnalysisResult mismatch =
        AnalyzerTestSupport.checkText("値は 定数 「1」と 2 を 足す。\n\n" + "メインとは （--）\n" + "こと。\n");
    assertEquals(List.of(DiagnosticCode.E_TYPE_MISMATCH), codes(mismatch));
    assertEquals(DiagnosticStage.TYPE_AND_STACK, mismatch.diagnostics().getFirst().stage());
    assertEquals("文字列", mismatch.diagnostics().getFirst().fields().get("actualType"));
    assertEquals("整数", mismatch.diagnostics().getFirst().fields().get("expectedType"));
  }

  private static void assertDiagnostic(
      jp.bsb.diagnostics.Diagnostic diagnostic, ExpectedDiagnostic expected, String sourceName) {
    assertEquals(expected.code(), diagnostic.code(), sourceName);
    assertEquals(DiagnosticStage.TYPE_AND_STACK, diagnostic.stage(), sourceName);
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(expected.line(), position.line(), sourceName);
    assertEquals(expected.column(), position.column(), sourceName);
    assertEquals(expected.fields(), diagnostic.fields(), sourceName);
    assertEquals(expected.expected(), diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(expected.actual(), diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(List.of(expected.fix()), diagnostic.fixes(), sourceName);
    if (expected.related() == null) {
      assertTrue(diagnostic.relatedLocations().isEmpty(), sourceName);
    } else {
      var related = diagnostic.relatedLocations().getFirst();
      assertEquals(
          expected.related(),
          related.position().line()
              + ":"
              + related.position().column()
              + " "
              + related.description(),
          sourceName);
    }
  }

  private static Stream<Arguments> typeFailures() {
    return Stream.of(
        failure(
            "BIND-F009.bsb",
            expected(
                DiagnosticCode.E_INITIALIZER_VALUE_MISSING,
                1,
                6,
                Map.of("name", "空", "actualStack", "[]"),
                "[1値]",
                "[]",
                "初期値を1個追加してください",
                "1:1 空")),
        failure(
            "BIND-F010.bsb",
            expected(
                DiagnosticCode.E_INITIALIZER_VALUE_COUNT,
                1,
                11,
                Map.of("name", "二つ", "actualCount", "2", "actualStack", "[整数,整数]"),
                "[1値]",
                "[整数, 整数]",
                "余分な値を残さないでください",
                "1:1 二つ")),
        Arguments.of(
            "BIND-F011.bsb",
            List.of(
                expected(
                    DiagnosticCode.E_INITIALIZER_CALL_NOT_ALLOWED,
                    5,
                    8,
                    Map.of("word", "作る", "callKind", "利用者定義単語"),
                    "初期値で許可された組み込み単語",
                    "利用者定義単語 作る",
                    "リテラル、名前参照、または許可された組み込み単語を使ってください",
                    "1:1 作る"),
                expected(
                    DiagnosticCode.E_INITIALIZER_CALL_NOT_ALLOWED,
                    6,
                    12,
                    Map.of("word", "一行表示する", "callKind", "副作用を持つ組み込み単語"),
                    "副作用のない初期値",
                    "一行表示する",
                    "出力は宣言の後へ移動してください",
                    null))),
        failure(
            "BIND-F026.bsb",
            expected(
                DiagnosticCode.E_ASSIGNMENT_STACK_UNDERFLOW,
                4,
                12,
                Map.of("target", "合計", "expectedType", "整数", "actualStack", "[]"),
                "[整数]",
                "[]",
                "代入する整数を先に置いてください",
                "1:1 合計")),
        failure(
            "BIND-F027.bsb",
            expected(
                DiagnosticCode.E_ASSIGNMENT_TYPE_MISMATCH,
                4,
                16,
                Map.of(
                    "target",
                    "合計",
                    "expectedType",
                    "整数",
                    "actualType",
                    "文字列",
                    "declarationLine",
                    "1",
                    "declarationColumn",
                    "1"),
                "整数",
                "文字列",
                "整数を代入してください",
                "1:1 合計")),
        failure(
            "BIND-F029.bsb",
            expected(
                DiagnosticCode.E_STACK_UNDERFLOW,
                1,
                11,
                Map.of("word", "足す", "requiredCount", "2", "actualCount", "1"),
                "[整数, 整数]",
                "[整数]",
                "整数をもう1つ置いてください",
                null)));
  }

  private static Arguments failure(String sourceName, ExpectedDiagnostic expected) {
    return Arguments.of(sourceName, List.of(expected));
  }

  private static ExpectedDiagnostic expected(
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      String related) {
    return new ExpectedDiagnostic(code, line, column, fields, expected, actual, fix, related);
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
  }

  private record ExpectedDiagnostic(
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      String related) {}
}
