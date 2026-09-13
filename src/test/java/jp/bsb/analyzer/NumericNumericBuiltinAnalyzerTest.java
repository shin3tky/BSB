package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class NumericNumericBuiltinAnalyzerTest {
  @Test
  void acceptsAllNumericProgramsWhoseRemainingWorkIsRuntimeExecution() throws Exception {
    for (String caseId :
        List.of(
            "NUM-N002",
            "NUM-N003",
            "NUM-N004",
            "NUM-N005",
            "NUM-N006",
            "NUM-N007",
            "NUM-N008",
            "NUM-N009",
            "NUM-N010",
            "NUM-N011",
            "NUM-N012",
            "NUM-N013",
            "NUM-N014",
            "NUM-N015",
            "NUM-N016",
            "NUM-N017")) {
      AnalysisResult result = AnalyzerTestSupport.checkText(numericsSource(caseId));

      assertTrue(result.successful(), caseId + ": " + result.diagnostics());
      assertTrue(result.diagnostics().isEmpty(), caseId);
    }
  }

  @Test
  void resolvesNAndIndependentNumericInputsToConcreteCallSignatures() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "メインとは （--）\n"
                + "    1.0 と 2.0 を 足す を 一行表示する\n"
                + "    1 と 2 を 小数で割る を 一行表示する\n"
                + "    1 と 2.0 を 小数で割る を 一行表示する\n"
                + "    1.0 と 2 を 小数で割る を 一行表示する\n"
                + "    1.0 と 2.0 を 小数で割る を 一行表示する\n"
                + "    1.0 と 2 と 3 と 最近接偶数丸め で 精度指定で割る を 一行表示する\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    AnalyzedProgram analyzed = result.programForIrGeneration();
    Map<String, List<WordSignature>> signaturesByName =
        analyzed.callSignatures().entrySet().stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    entry -> entry.getKey().name(),
                    java.util.LinkedHashMap::new,
                    java.util.stream.Collectors.mapping(
                        Map.Entry::getValue, java.util.stream.Collectors.toList())));

    assertEquals(
        List.of(
            new WordSignature(
                List.of(ValueType.DECIMAL, ValueType.DECIMAL), List.of(ValueType.DECIMAL))),
        signaturesByName.get("足す"));
    assertEquals(
        Set.of(
            new WordSignature(
                List.of(ValueType.INTEGER, ValueType.INTEGER), List.of(ValueType.DECIMAL)),
            new WordSignature(
                List.of(ValueType.INTEGER, ValueType.DECIMAL), List.of(ValueType.DECIMAL)),
            new WordSignature(
                List.of(ValueType.DECIMAL, ValueType.INTEGER), List.of(ValueType.DECIMAL)),
            new WordSignature(
                List.of(ValueType.DECIMAL, ValueType.DECIMAL), List.of(ValueType.DECIMAL))),
        Set.copyOf(signaturesByName.get("小数で割る")));
    assertEquals(
        List.of(
            new WordSignature(
                List.of(
                    ValueType.DECIMAL,
                    ValueType.INTEGER,
                    ValueType.INTEGER,
                    ValueType.ROUNDING_MODE),
                List.of(ValueType.DECIMAL))),
        signaturesByName.get("精度指定で割る"));
    assertEquals(
        List.of(new WordSignature(List.of(), List.of(ValueType.ROUNDING_MODE))),
        signaturesByName.get("最近接偶数丸め"));
  }

  @Test
  void reportsTheNormativeNumericFeatureStaticDiagnostics() throws Exception {
    assertDiagnostic(
        "NUM-F001",
        DiagnosticCode.E_TYPE_MISMATCH,
        Map.of(
            "word", "足す",
            "inputIndex", "2",
            "expectedType", "整数",
            "actualType", "小数"),
        "[整数, 整数]",
        "[整数, 小数]",
        "両入力を同じ数値型にしてください");
    assertDiagnostic(
        "NUM-F002",
        DiagnosticCode.E_TYPE_MISMATCH,
        Map.of(
            "word", "割った商",
            "inputIndex", "1",
            "expectedType", "整数",
            "actualType", "小数"),
        "[整数, 整数]",
        "[小数, 整数]",
        "整数の被除数を渡してください");
    assertDiagnostic(
        "NUM-F003",
        DiagnosticCode.E_TYPE_MISMATCH,
        Map.of(
            "word", "比べて小さい",
            "inputIndex", "2",
            "expectedType", "整数",
            "actualType", "小数"),
        "[整数, 整数]",
        "[整数, 小数]",
        "両入力を同じ数値型にしてください");
    assertDiagnostic(
        "NUM-F004",
        DiagnosticCode.E_TYPE_MISMATCH,
        Map.of(
            "word", "整数に丸める",
            "inputIndex", "2",
            "expectedType", "丸め方法",
            "actualType", "整数"),
        "[小数, 丸め方法]",
        "[小数, 整数]",
        "5種類の丸め方法から1つ指定してください");
    assertDiagnostic(
        "NUM-F005",
        DiagnosticCode.E_ARRAY_ELEMENT_TYPE_MISMATCH,
        Map.of("elementIndex", "2", "expectedType", "整数", "actualType", "小数"),
        "整数",
        "小数",
        "各整数を明示的に小数へ変換するか要素型を揃えてください");
    assertDiagnostic(
        "NUM-F006",
        DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
        Map.of(
            "actualType", "丸め方法",
            "allowedTypes", "整数,真偽,文字,文字列,小数,JSON",
            "plannedFeature", "配列要素の対象外"),
        "配列要素にできる型",
        "丸め方法",
        "丸め方法を個別の値として使用してください");
    assertDiagnostic(
        "NUM-F007",
        DiagnosticCode.E_ASSIGNMENT_TYPE_MISMATCH,
        Map.of(
            "target", "値",
            "expectedType", "整数",
            "actualType", "小数",
            "declarationLine", "1",
            "declarationColumn", "1"),
        "整数",
        "小数",
        "整数へ変換または丸めてから代入してください");
    assertDiagnostic(
        "NUM-F008",
        DiagnosticCode.E_TYPE_MISMATCH,
        Map.of(
            "word", "小数に変換する",
            "inputIndex", "1",
            "expectedType", "整数",
            "actualType", "小数"),
        "整数",
        "小数",
        "整数値を渡してください");
    assertDiagnostic(
        "NUM-F009",
        DiagnosticCode.E_TYPE_CONSTRAINT_NOT_ALLOWED,
        Map.of("type", "数値", "context", "利用者定義スタック効果"),
        "具体型",
        "数値",
        "整数または小数の具体型を書いてください");
  }

  @Test
  void rejectsNInAUserEffectByTheSameDictionaryConstraintRule() {
    AnalysisResult result = AnalyzerTestSupport.checkText("処理とは （N --）\nこと。\n\nメインとは （--）\nこと。\n");

    assertFalse(result.successful());
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_TYPE_CONSTRAINT_NOT_ALLOWED, diagnostic.code());
    assertEquals("N", diagnostic.fields().get("type"));
    assertEquals("整数または小数の具体型を書いてください", diagnostic.fixes().getFirst());
  }

  @Test
  void reservesNumericWordsAndValuesAndOffersThemAsCorrectionCandidates() {
    for (String name : List.of("割った商", "最近接偶数丸め", "空の小数配列")) {
      AnalysisResult collision =
          AnalyzerTestSupport.checkText(name + "とは （--）\nこと。\n\nメインとは （--）\nこと。\n");

      assertEquals(List.of(DiagnosticCode.E_RESERVED_NAME), codes(collision), name);
    }

    AnalysisResult misspelled = AnalyzerTestSupport.checkText("メインとは （--）\n    割った商商\nこと。\n");
    assertEquals(List.of(DiagnosticCode.E_UNDEFINED_WORD), codes(misspelled));
    assertEquals("割った商へ変更してください", misspelled.diagnostics().getFirst().fixes().getFirst());
  }

  private static void assertDiagnostic(
      String caseId,
      DiagnosticCode code,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix)
      throws Exception {
    AnalysisResult result = AnalyzerTestSupport.checkText(numericsSource(caseId));

    assertFalse(result.successful(), caseId);
    assertEquals(1, result.diagnostics().size(), caseId);
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), caseId);
    assertEquals(fields, diagnostic.fields(), caseId);
    assertEquals(expected, diagnostic.expected().orElseThrow(), caseId);
    assertEquals(actual, diagnostic.actual().orElseThrow(), caseId);
    assertEquals(List.of(fix), diagnostic.fixes(), caseId);
  }

  private static String numericsSource(String caseId) throws Exception {
    String resource = "/conformance/numerics/sources/" + caseId + ".bsb";
    try (var input = NumericNumericBuiltinAnalyzerTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(Diagnostic::code).toList();
  }
}
