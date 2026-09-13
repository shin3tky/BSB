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

class HostIoStaticTypeAnalyzerTest {
  @Test
  void matchesInputResultStaticDiagnostics() throws Exception {
    assertDiagnostic(
        "IO-F001.bsb",
        DiagnosticCode.E_TYPE_MISMATCH,
        2,
        10,
        Map.of("word", "入力行である", "inputIndex", "1", "expectedType", "入力結果", "actualType", "文字列"),
        "入力結果",
        "文字列");
    assertDiagnostic(
        "IO-F002.bsb",
        DiagnosticCode.E_TYPE_MISMATCH,
        2,
        15,
        Map.of("word", "一行表示する", "inputIndex", "1", "expectedType", "表示可能", "actualType", "入力結果"),
        "表示可能",
        "入力結果");
    assertDiagnostic(
        "IO-F004.bsb",
        DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
        2,
        5,
        Map.of("actualType", "入力結果", "allowedTypes", "整数,真偽,文字,文字列,小数,JSON"),
        "配列要素にできる型",
        "入力結果");
  }

  @Test
  void inputPredicatesPreserveTheResultAndConsumersRemoveIt() {
    AnalysisResult predicates =
        AnalyzerTestSupport.checkText(
            "判定とは （入力結果 -- 入力結果 真偽）\n"
                + "    入力行である\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "    一行を入力する を 判定\n"
                + "    表示する\n"
                + "    入力結果を捨てる\n"
                + "こと。\n");
    AnalysisResult extraction =
        AnalyzerTestSupport.checkText(
            "取出すとは （入力結果 -- 文字列）\n"
                + "    入力行を取り出す\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "    一行を入力する を 取出す を 一行表示する\n"
                + "こと。\n");

    assertTrue(predicates.successful(), predicates.diagnostics().toString());
    assertTrue(extraction.successful(), extraction.diagnostics().toString());
    assertEquals(
        new WordSignature(
            List.of(ValueType.INPUT_RESULT), List.of(ValueType.INPUT_RESULT, ValueType.BOOLEAN)),
        predicates.programForIrGeneration().findUserWord("判定").orElseThrow());
  }

  @Test
  void exitRemovesOnlyNonReturningPathsFromJoinsAndIr() throws Exception {
    for (String source : List.of("IO-N010.bsb", "IO-N013.bsb", "IO-N014.bsb")) {
      AnalysisResult result = AnalyzerTestSupport.checkHostIoResource(source);
      assertTrue(result.successful(), source + ": " + result.diagnostics());
      assertTrue(
          new jp.bsb.ir.IrGenerator().generate(result.programForIrGeneration()).successful());
    }

    AnalysisResult allPathsExit =
        AnalyzerTestSupport.checkText(
            "止めるとは （真偽 --）\n"
                + "    ならば\n"
                + "        1 を 終了する\n"
                + "    さもなければ\n"
                + "        2 を 終了する\n"
                + "    つぎに\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "    はい を 止める\n"
                + "    「到達不能」を 一行表示する\n"
                + "こと。\n");
    assertTrue(allPathsExit.successful(), allPathsExit.diagnostics().toString());
    assertEquals(List.of(DiagnosticCode.W_UNREACHABLE_CODE), codes(allPathsExit));
    assertEquals("止める", allPathsExit.diagnostics().getFirst().fields().get("cause"));
  }

  @Test
  void directExitProducesTheNormativeUnreachableWarning() throws Exception {
    AnalysisResult result = AnalyzerTestSupport.checkHostIoResource("IO-F026.bsb");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(List.of(DiagnosticCode.W_UNREACHABLE_CODE), codes(result));
    Diagnostic warning = result.diagnostics().getFirst();
    assertEquals(3, warning.location().displayPosition().orElseThrow().line());
    assertEquals(5, warning.location().displayPosition().orElseThrow().column());
    assertEquals("終了する", warning.fields().get("cause"));
  }

  private static void assertDiagnostic(
      String source,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual)
      throws Exception {
    AnalysisResult result = AnalyzerTestSupport.checkHostIoResource(source);
    assertFalse(result.successful(), source);
    assertEquals(1, result.diagnostics().size(), source);
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), source);
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line(), source);
    assertEquals(column, diagnostic.location().displayPosition().orElseThrow().column(), source);
    assertEquals(fields, diagnostic.fields(), source);
    assertEquals(expected, diagnostic.expected().orElseThrow(), source);
    assertEquals(actual, diagnostic.actual().orElseThrow(), source);
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(Diagnostic::code).toList();
  }
}
