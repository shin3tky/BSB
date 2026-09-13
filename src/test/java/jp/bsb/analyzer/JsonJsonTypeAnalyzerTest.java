package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class JsonJsonTypeAnalyzerTest {
  @Test
  void acceptsJsonInSignaturesDisplayEqualityAndArrays() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "JSONを保つとは （JSON -- JSON）\n"
                + "こと。\n\n"
                + "JSONを表示するとは （JSON --）\n"
                + "    一行表示する\n"
                + "こと。\n\n"
                + "JSONを比較するとは （JSON JSON -- 真偽）\n"
                + "    等しい\n"
                + "こと。\n\n"
                + "JSON配列長とは （配列<JSON> -- 整数）\n"
                + "    配列の長さ\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        new WordSignature(List.of(ValueType.JSON), List.of(ValueType.JSON)),
        result.programForIrGeneration().findUserWord("JSONを保つ").orElseThrow());
    assertEquals(
        new WordSignature(List.of(ValueType.arrayOf(ValueType.JSON)), List.of(ValueType.INTEGER)),
        result.programForIrGeneration().findUserWord("JSON配列長").orElseThrow());
    assertTrue(new jp.bsb.ir.IrGenerator().generate(result.programForIrGeneration()).successful());
  }

  @Test
  void reservesTheJsonTypeThroughTheConcreteTypeRegistry() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText("JSONとは （--）\n" + "こと。\n\n" + "メインとは （--）\n" + "こと。\n");

    assertEquals(
        List.of(jp.bsb.diagnostics.DiagnosticCode.E_RESERVED_NAME),
        result.diagnostics().stream().map(jp.bsb.diagnostics.Diagnostic::code).toList());
  }

  @Test
  void resolvesJsonBuiltinsInInitializersArrayElementsAndMultiOutputEffects() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "既定値は 定数 JSONヌル。\n"
                + "JSON列は 定数 【JSONヌル、1 を 整数をJSONに変換する】。\n\n"
                + "種別判定とは （JSON -- JSON 真偽）\n"
                + "    JSON配列である\n"
                + "こと。\n\n"
                + "キー判定とは （JSON 文字列 -- JSON 真偽）\n"
                + "    JSONオブジェクトにキーがある\n"
                + "こと。\n\n"
                + "空列を作るとは （-- 配列<JSON>）\n"
                + "    空のJSON配列 JSONから配列に変換する\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    AnalyzedProgram analyzed = result.programForIrGeneration();
    assertEquals(
        List.of(ValueType.JSON, ValueType.arrayOf(ValueType.JSON)),
        analyzed.nameResolution().bindings().stream()
            .map(binding -> binding.typeState().type().orElseThrow())
            .toList());
    assertEquals(
        new WordSignature(List.of(ValueType.JSON), List.of(ValueType.JSON, ValueType.BOOLEAN)),
        analyzed.findUserWord("種別判定").orElseThrow());
    assertEquals(
        new WordSignature(
            List.of(ValueType.JSON, ValueType.STRING), List.of(ValueType.JSON, ValueType.BOOLEAN)),
        analyzed.findUserWord("キー判定").orElseThrow());
    assertEquals(
        new WordSignature(List.of(), List.of(ValueType.arrayOf(ValueType.JSON))),
        analyzed.findUserWord("空列を作る").orElseThrow());
  }
}
