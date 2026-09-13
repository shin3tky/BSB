package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArrayArrayLiteralAnalyzerTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "ARRAY-N001.bsb",
        "ARRAY-N002.bsb",
        "ARRAY-N004.bsb",
        "ARRAY-N010.bsb",
        "ARRAY-N018.bsb",
        "ARRAY-N019.bsb",
        "ARRAY-N020.bsb"
      })
  void acceptsEveryArrayCaseWhoseStaticRulesBelongToInitialRules(String sourceName)
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkArrayResource(sourceName);

    assertTrue(result.successful(), sourceName + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName);
  }

  @ParameterizedTest
  @MethodSource("literalAndTypeFailures")
  void matchesEveryNormativeArrayLiteralAndTypeFailure(
      String sourceName, ExpectedDiagnostic expected) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkArrayResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(8, result.exitCode(), sourceName);
    assertEquals(1, result.diagnostics().size(), sourceName + result.diagnostics());
    assertDiagnostic(result.diagnostics().getFirst(), expected, sourceName);
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  @Test
  void retainsConcreteTypesForEveryLiteralAndBindingByAstIdentity() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "整数列は 変数 【1、2】。\n"
                + "文字列列は 定数 【「甲」、「乙」】。\n\n"
                + "メインとは （--）\n"
                + "    【3、4】 を 整数列 に 入れる\n"
                + "    整数列 を 一行表示する\n"
                + "    文字列列 を 一行表示する\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    AnalyzedProgram analyzed = result.programForIrGeneration();
    assertEquals(
        List.of(ValueType.arrayOf(ValueType.INTEGER), ValueType.arrayOf(ValueType.STRING)),
        analyzed.nameResolution().bindings().stream()
            .map(binding -> binding.typeState().type().orElseThrow())
            .toList());
    assertTrue(
        analyzed.nameResolution().bindings().stream()
            .allMatch(binding -> binding.typeState().status() == BindingTypeState.Status.INFERRED));
    assertEquals(3, analyzed.arrayLiteralTypes().size());
    analyzed
        .arrayLiteralTypes()
        .forEach(
            (literal, type) ->
                assertEquals(type, analyzed.findArrayLiteralType(literal).orElseThrow()));
  }

  @Test
  void acceptsAllFourTypedEmptyArrayValuesWithoutReverseInferringAnUntypedLiteral() {
    AnalysisResult typed =
        AnalyzerTestSupport.checkText(
            "整数列は 定数 空の整数配列。\n"
                + "真偽列は 定数 空の真偽配列。\n"
                + "文字要素列は 定数 空の文字配列。\n"
                + "文字列列は 定数 空の文字列配列。\n\n"
                + "メインとは （--）\nこと。\n");
    AnalysisResult untyped = AnalyzerTestSupport.checkText("整数列は 定数 【】。\n\nメインとは （--）\nこと。\n");

    assertTrue(typed.successful(), typed.diagnostics().toString());
    assertEquals(
        List.of(
            ValueType.arrayOf(ValueType.INTEGER),
            ValueType.arrayOf(ValueType.BOOLEAN),
            ValueType.arrayOf(ValueType.CHARACTER),
            ValueType.arrayOf(ValueType.STRING)),
        typed.programForIrGeneration().nameResolution().bindings().stream()
            .map(binding -> binding.typeState().type().orElseThrow())
            .toList());
    assertEquals(List.of(DiagnosticCode.E_EMPTY_ARRAY_TYPE_REQUIRED), codes(untyped));
  }

  @Test
  void usesStructuralArrayTypesForBranchesAndUserWordEffects() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "選ぶとは （真偽 -- 配列<整数>）\n"
                + "    ならば\n"
                + "        【1】\n"
                + "    さもなければ\n"
                + "        【2】\n"
                + "    つぎに\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "    はい を 選ぶ を 一行表示する\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        List.of(ValueType.arrayOf(ValueType.INTEGER)),
        result.programForIrGeneration().findUserWord("選ぶ").orElseThrow().outputTypes());
  }

  @Test
  void isolatesElementStacksAndSuppressesDerivedDiagnosticsInUnreachableArrays()
      throws IOException {
    AnalysisResult isolated =
        AnalyzerTestSupport.checkText("メインとは （--）\n" + "    99 【を】 を 一行表示する\n" + "こと。\n");
    AnalysisResult unreachable = AnalyzerTestSupport.checkArrayResource("ARRAY-F033.bsb");

    assertEquals(List.of(DiagnosticCode.E_ARRAY_ELEMENT_VALUE_MISSING), codes(isolated));
    assertTrue(unreachable.successful(), unreachable.diagnostics().toString());
    assertEquals(List.of(DiagnosticCode.W_UNREACHABLE_CODE), codes(unreachable));
    assertTrue(unreachable.programForIrGeneration().arrayLiteralTypes().isEmpty());
  }

  @Test
  void acceptsDecimalArraysButRejectsRoundingModeArrays() {
    AnalysisResult decimal =
        AnalyzerTestSupport.checkText("処理とは （配列<小数> -- 配列<小数>）\nこと。\n\nメインとは （--）\nこと。\n");
    AnalysisResult roundingMode =
        AnalyzerTestSupport.checkText("処理とは （配列<丸め方法> --）\nこと。\n\nメインとは （--）\nこと。\n");

    assertTrue(decimal.successful(), decimal.diagnostics().toString());
    assertEquals(
        List.of(ValueType.arrayOf(ValueType.DECIMAL)),
        decimal.programForIrGeneration().findUserWord("処理").orElseThrow().inputTypes());
    assertEquals(
        List.of(ValueType.arrayOf(ValueType.DECIMAL)),
        decimal.programForIrGeneration().findUserWord("処理").orElseThrow().outputTypes());
    assertEquals(List.of(DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED), codes(roundingMode));
    assertEquals("丸め方法", roundingMode.diagnostics().getFirst().fields().get("actualType"));
    assertEquals(
        1, roundingMode.diagnostics().getFirst().location().displayPosition().orElseThrow().line());
    assertEquals(
        10,
        roundingMode.diagnostics().getFirst().location().displayPosition().orElseThrow().column());
  }

  private static Stream<Arguments> literalAndTypeFailures() {
    return Stream.of(
        failure(
            "ARRAY-F009.bsb",
            expected(
                DiagnosticCode.E_EMPTY_ARRAY_TYPE_REQUIRED,
                2,
                5,
                Map.of("candidates", "空の整数配列,空の真偽配列,空の文字配列,空の文字列配列"),
                "具体的な配列型",
                "【】",
                "型付き空配列値を使用してください",
                null)),
        failure(
            "ARRAY-F010.bsb",
            expected(
                DiagnosticCode.E_ARRAY_ELEMENT_VALUE_MISSING,
                2,
                7,
                Map.of("elementIndex", "1", "actualStack", "[]"),
                "[1値]",
                "[]",
                "要素式に値を1個追加してください",
                "2:5 【")),
        failure(
            "ARRAY-F011.bsb",
            expected(
                DiagnosticCode.E_ARRAY_ELEMENT_VALUE_COUNT,
                2,
                9,
                Map.of("elementIndex", "1", "actualCount", "2", "actualStack", "[整数,整数]"),
                "[1値]",
                "[整数, 整数]",
                "要素式に余分な値を残さないでください",
                "2:5 【")),
        failure(
            "ARRAY-F012.bsb",
            expected(
                DiagnosticCode.E_ARRAY_ELEMENT_TYPE_MISMATCH,
                2,
                8,
                Map.of("elementIndex", "2", "expectedType", "整数", "actualType", "文字列"),
                "整数",
                "文字列",
                "すべての要素を整数に揃えてください",
                "2:6 1")),
        failure(
            "ARRAY-F013.bsb",
            expected(
                DiagnosticCode.E_NESTED_ARRAY_NOT_AVAILABLE,
                2,
                6,
                Map.of(
                    "context", "配列要素",
                    "maximumDimensions", "2",
                    "actualDimensions", "3"),
                "最大2次元の配列値",
                "配列<配列<整数>>",
                "配列リテラルの入れ子を2段までにしてください",
                null)),
        failure(
            "ARRAY-F014.bsb",
            expected(
                DiagnosticCode.E_ARRAY_ELEMENT_CALL_NOT_ALLOWED,
                6,
                6,
                Map.of("elementIndex", "1", "word", "作る", "callKind", "利用者定義単語"),
                "副作用のない組み込み単語",
                "利用者定義単語 作る",
                "要素値を配列の外で作ってください",
                "1:1 作る")),
        failure(
            "ARRAY-F015.bsb",
            expected(
                DiagnosticCode.E_ARRAY_ELEMENT_CALL_NOT_ALLOWED,
                2,
                12,
                Map.of(
                    "elementIndex", "1",
                    "word", "一行表示する",
                    "callKind", "副作用を持つ組み込み単語"),
                "副作用のない要素式",
                "一行表示する",
                "出力を配列リテラルの外へ移動してください",
                null)),
        failure(
            "ARRAY-F017.bsb",
            expected(
                DiagnosticCode.E_ARRAY_ELEMENT_TYPE_REQUIRED,
                1,
                7,
                Map.of("context", "スタック効果", "allowedTypes", "整数,真偽,文字,文字列"),
                "配列<要素型>",
                "配列",
                "具体的な要素型を追加してください",
                null)),
        failure(
            "ARRAY-F018.bsb",
            expected(
                DiagnosticCode.E_NESTED_ARRAY_NOT_AVAILABLE,
                1,
                10,
                Map.of(
                    "context", "配列型",
                    "maximumDimensions", "2",
                    "actualDimensions", "3"),
                "最大2次元の配列型",
                "配列<配列<整数>>",
                "配列型の入れ子を2段までにしてください",
                null)),
        failure(
            "ARRAY-F019.bsb",
            expected(
                DiagnosticCode.E_ASSIGNMENT_TYPE_MISMATCH,
                4,
                18,
                Map.of(
                    "target", "値",
                    "expectedType", "配列<整数>",
                    "actualType", "配列<文字列>",
                    "declarationLine", "1",
                    "declarationColumn", "1"),
                "配列<整数>",
                "配列<文字列>",
                "整数配列を代入してください",
                "1:1 値")));
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
