package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.binding.LexicalScopeKind;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArrayArrayOperationAndLoopAnalyzerTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "ARRAY-N003.bsb",
        "ARRAY-N005.bsb",
        "ARRAY-N006.bsb",
        "ARRAY-N007.bsb",
        "ARRAY-N008.bsb",
        "ARRAY-N009.bsb",
        "ARRAY-N011.bsb",
        "ARRAY-N012.bsb",
        "ARRAY-N013.bsb",
        "ARRAY-N014.bsb",
        "ARRAY-N015.bsb",
        "ARRAY-N016.bsb",
        "ARRAY-N017.bsb"
      })
  void acceptsEveryArrayCaseWhoseStaticRulesBelongToOperationRules(String sourceName)
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkArrayResource(sourceName);

    assertTrue(result.successful(), sourceName + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName + result.diagnostics());
  }

  @ParameterizedTest
  @MethodSource("operationAndLoopFailures")
  void matchesEveryNormativeArrayOperationAndLoopFailure(
      String sourceName, ExpectedDiagnostic expected) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkArrayResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(8, result.exitCode(), sourceName);
    assertEquals(1, result.diagnostics().size(), sourceName + result.diagnostics());
    assertDiagnostic(result.diagnostics().getFirst(), expected, sourceName);
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  @Test
  void createsAChildScopeForArrayLoopLocals() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n"
                + "    【1】を 各要素について\n"
                + "        一時値は 変数 1。\n"
                + "        一行表示する\n"
                + "        一時値 を 一行表示する\n"
                + "    繰り返す\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertTrue(
        result.programForIrGeneration().nameResolution().scopes().stream()
            .anyMatch(scope -> scope.kind() == LexicalScopeKind.ARRAY_LOOP_BODY));
  }

  @Test
  void checksTheBodyOfATypedEmptyArrayAndTheBreakPath() {
    AnalysisResult emptyBodyMismatch =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n" + "    空の整数配列 を 各要素について\n" + "    繰り返す\n" + "こと。\n");
    AnalysisResult breakMismatch =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n" + "    【1】を 各要素について\n" + "        打ち切る\n" + "    繰り返す\n" + "こと。\n");

    assertEquals(List.of(DiagnosticCode.E_LOOP_STACK_MISMATCH), codes(emptyBodyMismatch));
    assertEquals("normal", emptyBodyMismatch.diagnostics().getFirst().fields().get("path"));
    assertEquals(List.of(DiagnosticCode.E_LOOP_STACK_MISMATCH), codes(breakMismatch));
    assertEquals("break", breakMismatch.diagnostics().getFirst().fields().get("path"));
    assertEquals(List.of("打ち切る前に現在要素を消費してください"), breakMismatch.diagnostics().getFirst().fixes());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ARRAY-N021.bsb",
        "ARRAY-F028.bsb",
        "ARRAY-F029.bsb",
        "ARRAY-F030.bsb",
        "ARRAY-F031.bsb",
        "ARRAY-F032.bsb"
      })
  void acceptsRemainingSourcesWhoseFailuresAreNotStatic(String sourceName) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkArrayResource(sourceName);

    assertTrue(result.successful(), sourceName + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName + result.diagnostics());
  }

  @Test
  void acceptsTheArrayChapterAndKeepsInnerLoopTransfersInnermost() throws IOException {
    AnalysisResult chapter = AnalyzerTestSupport.checkArrayChapterResource();
    AnalysisResult nested =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n"
                + "    【1】を 各要素について\n"
                + "        1 回だけ\n"
                + "            続ける\n"
                + "        繰り返す\n"
                + "        一行表示する\n"
                + "    繰り返す\n"
                + "こと。\n");

    assertTrue(chapter.successful(), chapter.diagnostics().toString());
    assertTrue(chapter.diagnostics().isEmpty(), chapter.diagnostics().toString());
    assertTrue(nested.successful(), nested.diagnostics().toString());
    assertTrue(nested.diagnostics().isEmpty(), nested.diagnostics().toString());
  }

  @Test
  void rejectsAnArrayLoopLocalAfterTheLoop() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n"
                + "    【1】を 各要素について\n"
                + "        一時値は 定数 1。\n"
                + "        一行表示する\n"
                + "    繰り返す\n"
                + "    一時値 を 一行表示する\n"
                + "こと。\n");

    assertEquals(List.of(DiagnosticCode.E_BINDING_OUT_OF_SCOPE), codes(result));
  }

  @Test
  void permitsPureArrayOperationsInsideElementsAndConcreteGenericTypes() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n"
                + "    【【「甲」】と 0 を 配列から取り出す】を 一行表示する\n"
                + "    【はい】と いいえ を 配列の末尾へ追加する を 一行表示する\n"
                + "    【'甲'、'乙'】と 1 と '丙' を 配列の要素を置き換える を 一行表示する\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
  }

  private static Stream<Arguments> operationAndLoopFailures() {
    return Stream.of(
        failure(
            "ARRAY-F020.bsb",
            expected(
                DiagnosticCode.E_STACK_UNDERFLOW,
                2,
                11,
                Map.of("word", "配列から取り出す", "requiredCount", "2", "actualCount", "1"),
                "[配列<T>, 整数]",
                "[配列<整数>]",
                "整数の添字を追加してください",
                null)),
        failure(
            "ARRAY-F021.bsb",
            expected(
                DiagnosticCode.E_TYPE_MISMATCH,
                2,
                9,
                Map.of(
                    "word", "配列の長さ",
                    "inputIndex", "1",
                    "expectedType", "配列<T>",
                    "actualType", "整数"),
                "配列<T>",
                "整数",
                "配列値を渡してください",
                null)),
        failure(
            "ARRAY-F022.bsb",
            expected(
                DiagnosticCode.E_TYPE_MISMATCH,
                2,
                22,
                Map.of(
                    "word", "配列の要素を置き換える",
                    "inputIndex", "3",
                    "expectedType", "整数",
                    "actualType", "文字列"),
                "整数",
                "文字列",
                "置換値を整数にしてください",
                null)),
        failure(
            "ARRAY-F023.bsb",
            expected(
                DiagnosticCode.E_TYPE_MISMATCH,
                2,
                18,
                Map.of(
                    "word", "配列の末尾へ追加する",
                    "inputIndex", "2",
                    "expectedType", "整数",
                    "actualType", "文字列"),
                "整数",
                "文字列",
                "追加値を整数にしてください",
                null)),
        failure(
            "ARRAY-F024.bsb",
            expected(
                DiagnosticCode.E_ARRAY_LOOP_INPUT_UNDERFLOW,
                2,
                5,
                Map.of("expectedType", "配列<T>", "actualStack", "[]"),
                "[配列<T>]",
                "[]",
                "反復する配列を先に置いてください",
                null)),
        failure(
            "ARRAY-F025.bsb",
            expected(
                DiagnosticCode.E_ARRAY_LOOP_INPUT_TYPE_MISMATCH,
                2,
                9,
                Map.of("expectedType", "配列<T>", "actualType", "整数", "actualStack", "[整数]"),
                "配列<T>",
                "整数",
                "反復する配列を渡してください",
                null)),
        failure(
            "ARRAY-F026.bsb",
            expected(
                DiagnosticCode.E_LOOP_STACK_MISMATCH,
                3,
                5,
                Map.of("loopKind", "配列", "path", "normal", "base", "[]", "actual", "[整数]"),
                "[]",
                "[整数]",
                "現在要素を本体で消費してください",
                "2:11 各要素について")),
        failure(
            "ARRAY-F027.bsb",
            expected(
                DiagnosticCode.E_LOOP_STACK_MISMATCH,
                3,
                9,
                Map.of("loopKind", "配列", "path", "continue", "base", "[]", "actual", "[整数]"),
                "[]",
                "[整数]",
                "続ける前に現在要素を消費してください",
                "2:11 各要素について")));
  }

  private static Arguments failure(String sourceName, ExpectedDiagnostic expected) {
    return Arguments.of(sourceName, expected);
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

  private static void assertDiagnostic(
      Diagnostic diagnostic, ExpectedDiagnostic expected, String sourceName) {
    assertEquals(expected.code(), diagnostic.code(), sourceName);
    assertEquals(DiagnosticStage.TYPE_AND_STACK, diagnostic.stage(), sourceName);
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(expected.line(), position.line(), sourceName);
    assertEquals(expected.column(), position.column(), sourceName);
    assertEquals(expected.fields(), diagnostic.fields(), sourceName);
    assertEquals(expected.expected(), diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(expected.actual(), diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(List.of(expected.fix()), diagnostic.fixes(), sourceName);
    String actualRelated =
        diagnostic.relatedLocations().isEmpty()
            ? null
            : diagnostic.relatedLocations().getFirst().position().line()
                + ":"
                + diagnostic.relatedLocations().getFirst().position().column()
                + " "
                + diagnostic.relatedLocations().getFirst().description();
    assertEquals(expected.related(), actualRelated, sourceName);
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(Diagnostic::code).toList();
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
