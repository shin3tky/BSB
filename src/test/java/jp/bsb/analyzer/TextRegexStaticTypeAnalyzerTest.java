package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class TextRegexStaticTypeAnalyzerTest {
  @Test
  void matchesTheFiveNormativeStaticDiagnostics() throws Exception {
    assertDiagnostic(
        "TEXT-F005.bsb",
        DiagnosticCode.E_TYPE_MISMATCH,
        2,
        15,
        Map.of("word", "つなぐ", "inputIndex", "2", "expectedType", "文字列", "actualType", "整数"),
        "[文字列, 文字列]",
        "[文字列, 整数]",
        "第2入力を明示的に文字列へ変換してください");
    assertDiagnostic(
        "TEXT-F006.bsb",
        DiagnosticCode.E_TYPE_MISMATCH,
        2,
        19,
        Map.of(
            "word",
            "文字列からコードポイントを取り出す",
            "inputIndex",
            "2",
            "expectedType",
            "整数",
            "actualType",
            "文字列"),
        "[文字列, 整数]",
        "[文字列, 文字列]",
        "0始まりの整数位置を渡してください");
    assertDiagnostic(
        "TEXT-F007.bsb",
        DiagnosticCode.E_TYPE_MISMATCH,
        2,
        19,
        Map.of(
            "word", "正規表現に完全一致する", "inputIndex", "2", "expectedType", "正規表現", "actualType", "文字列"),
        "[文字列, 正規表現]",
        "[文字列, 文字列]",
        "正規表現リテラルを使用してください");
    assertDiagnostic(
        "TEXT-F008.bsb",
        DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
        1,
        10,
        Map.of(
            "actualType",
            "正規表現",
            "allowedTypes",
            "整数,真偽,文字,文字列,小数,JSON",
            "plannedFeature",
            "配列要素の対象外"),
        "配列要素にできる型",
        "正規表現",
        "正規表現を個別の値として使用してください");
    assertDiagnostic(
        "TEXT-F014.bsb",
        DiagnosticCode.E_TYPE_MISMATCH,
        2,
        17,
        Map.of("word", "一行表示する", "inputIndex", "1", "expectedType", "表示可能", "actualType", "正規表現"),
        "表示可能",
        "正規表現",
        "正規表現を照合操作に使用してください");
  }

  @Test
  void carriesRegexBindingsUserEffectsAndConcreteCallsToIrMetadata() throws Exception {
    AnalysisResult result = AnalyzerTestSupport.checkTextRegexResource("TEXT-N019.bsb");

    assertTrue(result.successful(), result.diagnostics().toString());
    AnalyzedProgram analyzed = result.programForIrGeneration();
    assertTrue(
        analyzed.nameResolution().bindings().stream()
            .allMatch(binding -> binding.typeState().type().orElseThrow().equals(ValueType.REGEX)));
    assertEquals(
        new WordSignature(List.of(ValueType.STRING, ValueType.REGEX), List.of(ValueType.BOOLEAN)),
        analyzed.findUserWord("一致する").orElseThrow());
    assertTrue(new jp.bsb.ir.IrGenerator().generate(analyzed).successful());
  }

  @Test
  void emitsConcreteStringEffectsForTheUserWordCase() throws Exception {
    AnalysisResult result = AnalyzerTestSupport.checkTextRegexResource("TEXT-N021.bsb");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertTrue(new jp.bsb.ir.IrGenerator().generate(result.programForIrGeneration()).successful());
  }

  @Test
  void keepsRegexOutsideGenericEqualityAsWellAsDisplayability() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText("メインとは （--）\n" + "    正規表現「a」 と 正規表現「a」 を 等しい\n" + "こと。\n");

    assertFalse(result.successful());
    assertEquals(List.of(DiagnosticCode.E_TYPE_MISMATCH), codes(result));
    assertEquals("等値比較可能", result.diagnostics().getFirst().fields().get("expectedType"));
    assertEquals("正規表現", result.diagnostics().getFirst().fields().get("actualType"));
  }

  private static void assertDiagnostic(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix)
      throws Exception {
    AnalysisResult result = AnalyzerTestSupport.checkTextRegexResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(1, result.diagnostics().size(), sourceName);
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), sourceName);
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(line, position.line(), sourceName);
    assertEquals(column, position.column(), sourceName);
    assertEquals(fields, diagnostic.fields(), sourceName);
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(List.of(fix), diagnostic.fixes(), sourceName);
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(Diagnostic::code).toList();
  }
}
