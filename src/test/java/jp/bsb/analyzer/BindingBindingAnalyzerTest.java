package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.binding.BindingStorage;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.binding.BindingUseKind;
import jp.bsb.binding.LexicalScopeKind;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BindingBindingAnalyzerTest {
  @ParameterizedTest
  @MethodSource("nameFailures")
  void matchesEveryBindingNameFailure(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      String related)
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkBindingResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(8, result.exitCode(), sourceName);
    assertEquals(1, result.diagnostics().size(), sourceName);
    var diagnostic = result.diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(DiagnosticStage.NAME, diagnostic.stage(), sourceName);
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(line, position.line(), sourceName);
    assertEquals(column, position.column(), sourceName);
    assertEquals(fields, diagnostic.fields(), sourceName);
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(List.of(fix), diagnostic.fixes(), sourceName);
    if (related == null) {
      assertTrue(diagnostic.relatedLocations().isEmpty(), sourceName);
    } else {
      var location = diagnostic.relatedLocations().getFirst();
      assertEquals(
          related,
          location.position().line()
              + ":"
              + location.position().column()
              + " "
              + location.description(),
          sourceName);
    }
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  @Test
  void resolvesStableIdsSiblingScopesAndInnerWritesToOuterBindings() {
    String source =
        "大域は 変数 0。\n"
            + "\n"
            + "メインとは （--）\n"
            + "    局所は 変数 1。\n"
            + "    局所\n"
            + "    を 局所 に 入れる\n"
            + "    はい ならば\n"
            + "        一時は 定数 2。\n"
            + "        大域\n"
            + "        を 大域 に 入れる\n"
            + "        一時 を 一行表示する\n"
            + "    さもなければ\n"
            + "        一時は 変数 3。\n"
            + "        一時 を 一行表示する\n"
            + "    つぎに\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
    var resolution = result.programForIrGeneration().nameResolution();
    assertEquals(
        List.of("B1", "B2", "B3", "B4"),
        resolution.bindings().stream().map(binding -> binding.id().displayName()).toList());
    assertEquals(
        List.of(
            BindingStorage.GLOBAL,
            BindingStorage.LOCAL,
            BindingStorage.LOCAL,
            BindingStorage.LOCAL),
        resolution.bindings().stream().map(binding -> binding.storage()).toList());
    assertTrue(
        resolution.bindings().stream()
            .allMatch(binding -> binding.typeState().status() == BindingTypeState.Status.INFERRED));
    assertEquals(
        List.of(
            LexicalScopeKind.GLOBAL,
            LexicalScopeKind.WORD_BODY,
            LexicalScopeKind.CONDITIONAL_TRUE,
            LexicalScopeKind.CONDITIONAL_FALSE),
        resolution.scopes().stream().map(scope -> scope.kind()).toList());
    assertEquals(
        List.of(
            BindingUseKind.READ,
            BindingUseKind.WRITE,
            BindingUseKind.READ,
            BindingUseKind.WRITE,
            BindingUseKind.READ,
            BindingUseKind.READ),
        resolution.uses().stream().map(use -> use.kind()).toList());
    assertEquals("B2", resolution.uses().get(0).bindingId().displayName());
    assertEquals("B2", resolution.uses().get(1).bindingId().displayName());
    assertEquals("B1", resolution.uses().get(2).bindingId().displayName());
    assertEquals("B1", resolution.uses().get(3).bindingId().displayName());
    assertEquals("B3", resolution.uses().get(4).bindingId().displayName());
    assertEquals("B4", resolution.uses().get(5).bindingId().displayName());
  }

  @Test
  void buildsCountedAndConditionLoopScopesAndResolvesTheirLocalReads() {
    String source =
        "メインとは （--）\n"
            + "    1 回だけ\n"
            + "        回数値は 変数 0。\n"
            + "        回数値 を 表示する\n"
            + "    繰り返す\n"
            + "    ここから\n"
            + "        条件値は 定数 はい。\n"
            + "        条件値 を 表示する\n"
            + "        いいえ\n"
            + "    続く間\n"
            + "        本体値は 変数 1。\n"
            + "        本体値 を 表示する\n"
            + "    繰り返す\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
    var resolution = result.programForIrGeneration().nameResolution();
    assertEquals(
        List.of(
            LexicalScopeKind.GLOBAL,
            LexicalScopeKind.WORD_BODY,
            LexicalScopeKind.COUNTED_LOOP_BODY,
            LexicalScopeKind.CONDITION_LOOP_CONDITION,
            LexicalScopeKind.CONDITION_LOOP_BODY),
        resolution.scopes().stream().map(scope -> scope.kind()).toList());
    assertEquals(
        List.of("B1", "B2", "B3"),
        resolution.bindings().stream().map(binding -> binding.id().displayName()).toList());
    assertEquals(
        List.of("B1", "B2", "B3"),
        resolution.uses().stream().map(use -> use.bindingId().displayName()).toList());
  }

  @Test
  void unreachableDeclarationsAndUsesDoNotConsumeBindingIdsOrAddNameDiagnostics()
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkBindingResource("BIND-F030.bsb");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        List.of(DiagnosticCode.W_UNREACHABLE_CODE),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertTrue(result.programForIrGeneration().nameResolution().bindings().isEmpty());
    assertTrue(result.programForIrGeneration().nameResolution().uses().isEmpty());
  }

  @Test
  void rejectsReservedValueNamesAndWordValueCollisionsInTheCommonNamespace() {
    AnalysisResult reserved =
        AnalyzerTestSupport.checkText("足すは 定数 1。\n\n" + "メインとは （--）\n" + "こと。\n");
    assertEquals(List.of(DiagnosticCode.E_RESERVED_NAME), codes(reserved));
    assertEquals(
        "組み込み単語 足す", reserved.diagnostics().getFirst().relatedLocations().getFirst().description());

    AnalysisResult valueThenWord =
        AnalyzerTestSupport.checkText(
            "共有は 定数 1。\n\n" + "共有とは （--）\n" + "こと。\n\n" + "メインとは （--）\n" + "こと。\n");
    assertEquals(List.of(DiagnosticCode.E_DUPLICATE_NAME), codes(valueThenWord));

    AnalysisResult wordThenValue =
        AnalyzerTestSupport.checkText(
            "共有とは （--）\n" + "こと。\n\n" + "共有は 変数 1。\n\n" + "メインとは （--）\n" + "こと。\n");
    assertEquals(List.of(DiagnosticCode.E_DUPLICATE_NAME), codes(wordThenValue));
  }

  @Test
  void preservesConfusableWarningsForValueDeclarationsWithoutMergingNames() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "Alphaは 定数 1。\n" + "Аlphaは 定数 2。\n\n" + "メインとは （--）\n" + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(List.of(DiagnosticCode.W_CONFUSABLE_IDENTIFIER), codes(result));
    assertEquals(2, result.programForIrGeneration().nameResolution().bindings().size());
  }

  @Test
  void treatsAReferenceInItsOwnLocalInitializerAsBeforeDeclaration() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText("メインとは （--）\n" + "    値は 定数 値。\n" + "こと。\n");

    assertEquals(List.of(DiagnosticCode.E_REFERENCE_BEFORE_DECLARATION), codes(result));
    assertEquals("read", result.diagnostics().getFirst().fields().get("access"));
    assertEquals("値", result.diagnostics().getFirst().fields().get("name"));
  }

  @Test
  void doesNotTreatALocalFromAnotherWordAsAnOutOfScopeAssignmentCandidate() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "準備とは （--）\n"
                + "    値は 変数 1。\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "    1 を 値 に 入れる\n"
                + "こと。\n");

    assertEquals(List.of(DiagnosticCode.E_UNDEFINED_ASSIGNMENT_TARGET), codes(result));
  }

  @Test
  void rejectsABuiltinWordAsAnAssignmentTarget() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText("メインとは （--）\n" + "    1 を 足す に 入れる\n" + "こと。\n");

    assertEquals(List.of(DiagnosticCode.E_ASSIGNMENT_TARGET_NOT_VARIABLE), codes(result));
    assertEquals("組み込み単語", result.diagnostics().getFirst().fields().get("targetKind"));
    assertTrue(result.diagnostics().getFirst().relatedLocations().isEmpty());
  }

  private static Stream<Arguments> nameFailures() {
    return Stream.of(
        failure(
            "BIND-F013.bsb",
            DiagnosticCode.E_REFERENCE_BEFORE_INITIALIZATION,
            1,
            7,
            Map.of(
                "name",
                "値",
                "declarationLine",
                "1",
                "declarationColumn",
                "1",
                "initializationOrder",
                "1"),
            "初期化済みの名前",
            "未初期化の値",
            "別の初期値を使用してください",
            "1:1 値"),
        failure(
            "BIND-F014.bsb",
            DiagnosticCode.E_REFERENCE_BEFORE_INITIALIZATION,
            1,
            7,
            Map.of(
                "name",
                "先",
                "declarationLine",
                "2",
                "declarationColumn",
                "1",
                "initializationOrder",
                "2"),
            "先に初期化される名前",
            "2番目の大域宣言",
            "宣言順を入れ替えてください",
            "2:1 先"),
        failure(
            "BIND-F015.bsb",
            DiagnosticCode.E_REFERENCE_BEFORE_DECLARATION,
            2,
            5,
            Map.of("name", "値", "access", "read", "declarationLine", "3", "declarationColumn", "5"),
            "宣言後の参照",
            "宣言前の参照",
            "宣言をこの参照より前へ移動してください",
            "3:5 値"),
        failure(
            "BIND-F016.bsb",
            DiagnosticCode.E_BINDING_OUT_OF_SCOPE,
            5,
            5,
            Map.of(
                "name", "内値", "scope", "条件分岐の真側", "declarationLine", "3", "declarationColumn", "9"),
            "可視な名前",
            "真側だけの局所名",
            "参照を宣言と同じスコープへ移動してください",
            "3:9 内値"),
        failure(
            "BIND-F017.bsb",
            DiagnosticCode.E_DUPLICATE_NAME,
            2,
            1,
            Map.of("actualName", "値", "previousName", "値"),
            "一意な名前",
            "値",
            "別の名前に変更してください",
            "1:1 値"),
        failure(
            "BIND-F018.bsb",
            DiagnosticCode.E_DUPLICATE_NAME,
            3,
            5,
            Map.of("actualName", "値", "previousName", "値"),
            "一意な名前",
            "値",
            "別の名前に変更してください",
            "2:5 値"),
        failure(
            "BIND-F019.bsb",
            DiagnosticCode.E_DUPLICATE_NAME,
            2,
            1,
            Map.of("actualName", "請求A1", "previousName", "請求Ａ１"),
            "一意な名前",
            "請求A1",
            "別の名前に変更してください",
            "1:1 請求Ａ１"),
        failure(
            "BIND-F020.bsb",
            DiagnosticCode.E_NAME_SHADOWING,
            4,
            5,
            Map.of("name", "値", "outerKind", "大域定数"),
            "外側と異なる局所名",
            "値",
            "別の局所名に変更してください",
            "1:1 値"),
        failure(
            "BIND-F021.bsb",
            DiagnosticCode.E_NAME_SHADOWING,
            5,
            5,
            Map.of("name", "処理", "outerKind", "大域単語"),
            "外側と異なる局所名",
            "処理",
            "別の局所名に変更してください",
            "1:1 処理"),
        failure(
            "BIND-F022.bsb",
            DiagnosticCode.E_NAME_SHADOWING,
            4,
            9,
            Map.of("name", "値", "outerKind", "局所定数"),
            "外側と異なる局所名",
            "値",
            "別の局所名に変更してください",
            "2:5 値"),
        failure(
            "BIND-F023.bsb",
            DiagnosticCode.E_ASSIGN_TO_CONSTANT,
            4,
            9,
            Map.of("name", "値", "type", "整数", "declarationLine", "1", "declarationColumn", "1"),
            "変数",
            "定数 値",
            "宣言を変数にするか代入を削除してください",
            "1:1 値"),
        failure(
            "BIND-F024.bsb",
            DiagnosticCode.E_ASSIGNMENT_TARGET_NOT_VARIABLE,
            5,
            9,
            Map.of("name", "処理", "targetKind", "利用者定義単語"),
            "変数",
            "利用者定義単語 処理",
            "変数名を指定してください",
            "1:1 処理"),
        failure(
            "BIND-F025.bsb",
            DiagnosticCode.E_UNDEFINED_ASSIGNMENT_TARGET,
            2,
            9,
            Map.of("name", "未定義"),
            "宣言済み変数",
            "未定義",
            "変数を宣言してください",
            null),
        failure(
            "BIND-F028.bsb",
            DiagnosticCode.E_REFERENCE_BEFORE_DECLARATION,
            2,
            9,
            Map.of(
                "name", "値", "access", "write", "declarationLine", "3", "declarationColumn", "5"),
            "宣言後の代入",
            "宣言前の代入",
            "宣言をこの代入より前へ移動してください",
            "3:5 値"));
  }

  private static Arguments failure(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      String related) {
    return Arguments.of(sourceName, code, line, column, fields, expected, actual, fix, related);
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
  }
}
