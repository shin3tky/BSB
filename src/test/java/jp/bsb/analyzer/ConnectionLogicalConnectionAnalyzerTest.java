package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.binding.DeclaredNameKind;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class ConnectionLogicalConnectionAnalyzerTest {
  @Test
  void resolvesForwardReferencesWithoutCreatingBindings() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n" + "    論理接続を確認する<顧客管理API>\n" + "こと。\n\n" + "顧客管理APIは 論理接続。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    var analyzed = result.programForIrGeneration();
    assertTrue(analyzed.nameResolution().bindings().isEmpty());
    assertEquals(
        DeclaredNameKind.LOGICAL_CONNECTION,
        analyzed.nameResolution().findGlobalDeclaration("顧客管理API").orElseThrow().declarationKind());
    assertEquals(1, analyzed.logicalConnectionResolution().uses().size());
    assertEquals(
        "顧客管理API", analyzed.logicalConnectionResolution().uses().getFirst().declaration().name());
  }

  @Test
  void rejectsUndeclaredAndOrdinaryReferences() {
    assertSingle(
        "メインとは （--）\n論理接続を確認する<未宣言API>\nこと。\n", DiagnosticCode.E_UNDECLARED_LOGICAL_CONNECTION);
    assertSingle(
        "顧客管理APIは 論理接続。\nメインとは （--）\n顧客管理API\nこと。\n",
        DiagnosticCode.E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED);
    assertSingle(
        "顧客管理APIは 論理接続。\n値は 定数 顧客管理API。\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED);
  }

  @Test
  void sharesTheGlobalNamespaceAndPreventsLocalShadowing() {
    assertSingle(
        "顧客管理APIは 論理接続。\n顧客管理APIとは （--）\nこと。\nメインとは （--）\nこと。\n", DiagnosticCode.E_DUPLICATE_NAME);
    assertSingle(
        "顧客管理APIは 論理接続。\nメインとは （--）\n顧客管理APIは 定数 1。\nこと。\n", DiagnosticCode.E_NAME_SHADOWING);
    assertSingle("論理接続は 論理接続。\nメインとは （--）\nこと。\n", DiagnosticCode.E_RESERVED_NAME);
  }

  @Test
  void reportsOnlyTheFirstDeclarationBeyondTheLimit() {
    var source = new StringBuilder();
    for (int index = 1; index <= 10_001; index++) {
      source.append("接続").append(index).append("は 論理接続。\n");
    }
    source.append("メインとは （--）\nこと。\n");

    AnalysisResult result = AnalyzerTestSupport.checkText(source.toString());

    assertFalse(result.successful());
    assertEquals(
        List.of(DiagnosticCode.E_LOGICAL_CONNECTION_LIMIT),
        result.diagnostics().stream().map(d -> d.code()).toList());
    assertEquals("10001", result.diagnostics().getFirst().fields().get("observed"));
  }

  private static void assertSingle(String source, DiagnosticCode code) {
    AnalysisResult result = AnalyzerTestSupport.checkText(source);
    assertEquals(
        List.of(code),
        result.diagnostics().stream().map(d -> d.code()).toList(),
        result.diagnostics().toString());
  }
}
