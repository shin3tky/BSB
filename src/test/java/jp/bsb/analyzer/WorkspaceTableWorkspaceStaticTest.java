package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import jp.bsb.binding.DeclaredNameKind;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.explain.ProgramExplainer;
import jp.bsb.format.SourceFormatter;
import jp.bsb.ir.Call;
import jp.bsb.ir.IrGenerator;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.BuiltinOperation;
import jp.bsb.stdlib.ValueType;
import jp.bsb.stdlib.ValueTypeTraits;
import org.junit.jupiter.api.Test;

/** 作業領域・区切り表の構文、型、静的資源参照、説明を本番経路で検証します。 */
class WorkspaceTableWorkspaceStaticTest {
  private static final String SOURCE =
      "帳票は 作業領域。\n"
          + "画像は 作業領域。\n\n"
          + "読むとは （文字列 -- 結果<バイト列,ファイル読取失敗>）\n"
          + "    ファイルを読む<帳票>\n"
          + "こと。\n\n"
          + "書くとは （文字列 バイト列 -- 結果<整数,ファイル書込失敗>）\n"
          + "    ファイルへ書く<画像>\n"
          + "こと。\n\n"
          + "メインとは （--）\n"
          + "    「入力.dat」を 読む 結果を捨てる\n"
          + "    「出力.dat」と 空のバイト列 を 書く 結果を捨てる\n"
          + "こと。\n";

  @Test
  void resolvesForwardCapableTypedWorkspaceCallsAndLowersThemToIr() {
    AnalysisResult result = check(SOURCE);

    assertTrue(
        result.successful(),
        result.diagnostics().stream()
            .map(diagnostic -> diagnostic.code() + ":" + diagnostic.fields())
            .toList()
            .toString());
    var analyzed = result.programForIrGeneration();
    assertTrue(analyzed.nameResolution().bindings().isEmpty());
    assertEquals(
        DeclaredNameKind.WORKSPACE,
        analyzed.nameResolution().findGlobalDeclaration("帳票").orElseThrow().declarationKind());
    assertEquals(
        List.of("read", "write"),
        analyzed.workspaceResolution().uses().stream().map(use -> use.operation()).toList());

    var ir = new IrGenerator().generate(analyzed).programForExecution();
    List<Call> fileCalls =
        ir.userWords().values().stream()
            .flatMap(word -> word.instructions().stream())
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.workspace().isPresent())
            .toList();
    assertEquals(
        List.of("read", "write"),
        fileCalls.stream().map(call -> call.workspace().orElseThrow().operation()).toList());
  }

  @Test
  void formatsDeclarationsAndStaticArgumentsIdempotently() {
    String source = "帳票は\n 作業領域。\n\nメインとは （--）\n「a」を ファイルを読む< 帳票 > 結果を捨てる\nこと。\n";
    String expected = "帳票は 作業領域。\n\nメインとは （--）\n    「a」 を ファイルを読む<帳票>\n    結果を捨てる\nこと。\n";
    var formatter = new SourceFormatter();
    var first = formatter.format("領域.bsb", source.getBytes(StandardCharsets.UTF_8));
    var second =
        formatter.format(
            "領域2.bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertEquals(expected, first.outputForStandardOutput());
    assertEquals(expected, second.outputForStandardOutput());
  }

  @Test
  void reportsWorkspaceSpecificSyntaxAndNameDiagnostics() {
    assertSingle("帳票は 作業領域 1。\nメインとは （--）\nこと。\n", DiagnosticCode.E_WORKSPACE_DECLARATION_VALUE);
    assertSingle("メインとは （--）\n帳票は 作業領域。\nこと。\n", DiagnosticCode.E_WORKSPACE_DECLARATION_SCOPE);
    assertSingle(
        "メインとは （--）\n「a」を ファイルを読む\nこと。\n", DiagnosticCode.E_EXPECTED_WORKSPACE_ARGUMENT_START);
    assertSingle("メインとは （--）\n「a」を ファイルを読む<未宣言>\nこと。\n", DiagnosticCode.E_UNDECLARED_WORKSPACE);
    assertSingle(
        "帳票は 作業領域。\nメインとは （--）\n帳票\nこと。\n", DiagnosticCode.E_WORKSPACE_REFERENCE_NOT_ALLOWED);
  }

  @Test
  void publishesFourWordsFailureTypesAndWorkspaceRequirements() {
    List<String> words =
        BuiltinDictionary.words().stream()
            .filter(word -> word.featureGroup().equals("WST"))
            .limit(4)
            .map(word -> word.canonicalName())
            .toList();
    assertEquals(List.of("ファイルを読む", "ファイルへ書く", "ファイル読取失敗の種類を取り出す", "ファイル書込失敗の種類を取り出す"), words);
    assertEquals(
        Set.of(BuiltinDictionary.WORKSPACE_RESOLVE, BuiltinDictionary.FILE_READ),
        BuiltinDictionary.find("ファイルを読む").orElseThrow().capabilities());
    assertEquals(
        BuiltinOperation.FILE_WRITE, BuiltinDictionary.find("ファイルへ書く").orElseThrow().operation());
    assertFalse(ValueType.FILE_READ_FAILURE.isArrayElementType());
    assertFalse(ValueTypeTraits.isDisplayable(ValueType.FILE_READ_FAILURE));
    assertFalse(ValueTypeTraits.isEqualityComparable(ValueType.FILE_WRITE_FAILURE));

    var explanation = new ProgramExplainer().explain(check(SOURCE).programForIrGeneration());
    assertEquals(
        List.of("帳票", "画像"),
        explanation.parameterizedCapabilities().workspaceDeclarations().stream()
            .map(entry -> entry.name())
            .toList());
    assertEquals(
        List.of("resolve", "read"),
        explanation.parameterizedCapabilities().summaryRequirements().getFirst().operations());
    assertEquals(
        "workspace",
        explanation.parameterizedCapabilities().summaryRequirements().getFirst().kind());
  }

  @Test
  void reportsOnlyTheFirstWorkspaceBeyondTheLimit() {
    var source = new StringBuilder();
    for (int index = 1; index <= 10_001; index++) {
      source.append("領域").append(index).append("は 作業領域。\n");
    }
    source.append("メインとは （--）\nこと。\n");

    AnalysisResult result = check(source.toString());

    assertFalse(result.successful());
    assertEquals(
        List.of(DiagnosticCode.E_WORKSPACE_DECLARATION_LIMIT),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals("10001", result.diagnostics().getFirst().fields().get("observed"));
  }

  private static AnalysisResult check(String source) {
    return new SourceChecker().check("作業領域.bsb", source.getBytes(StandardCharsets.UTF_8));
  }

  private static void assertSingle(String source, DiagnosticCode code) {
    AnalysisResult result = check(source);
    assertEquals(
        List.of(code),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList(),
        result.diagnostics().toString());
  }
}
