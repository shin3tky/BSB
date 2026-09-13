package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.ir.IrGenerator;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ResultResultTypeAnalyzerTest {
  @Test
  void concretizesConstructorsPredicatesUnwrapsDropStorageAndControlJoin() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "成功化とは （整数 -- 結果<整数,文字列>）\n"
                + "    成功にする<整数,文字列>\n"
                + "こと。\n\n"
                + "失敗化とは （文字列 -- 結果<整数,文字列>）\n"
                + "    失敗にする<整数,文字列>\n"
                + "こと。\n\n"
                + "成功判定とは （結果<整数,文字列> -- 結果<整数,文字列> 真偽）\n"
                + "    結果が成功である\n"
                + "こと。\n\n"
                + "失敗判定とは （結果<整数,文字列> -- 結果<整数,文字列> 真偽）\n"
                + "    結果が失敗である\n"
                + "こと。\n\n"
                + "成功取出しとは （結果<整数,文字列> -- 整数）\n"
                + "    結果から成功値を取り出す\n"
                + "こと。\n\n"
                + "失敗取出しとは （結果<整数,文字列> -- 文字列）\n"
                + "    結果から失敗値を取り出す\n"
                + "こと。\n\n"
                + "保存とは （-- 整数）\n"
                + "    値は 変数 42 成功にする<整数,文字列>。\n"
                + "    値 を 結果から成功値を取り出す\n"
                + "こと。\n\n"
                + "合流とは （結果<整数,文字列> 真偽 -- 結果<整数,文字列>）\n"
                + "    ならば\n"
                + "    さもなければ\n"
                + "    つぎに\n"
                + "こと。\n\n"
                + "破棄とは （結果<整数,文字列> --）\n"
                + "    結果を捨てる\n"
                + "こと。\n\n"
                + "メインとは （--）\nこと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    ValueType resultType = ValueType.resultOf(ValueType.INTEGER, ValueType.STRING);
    assertEquals(
        new WordSignature(List.of(ValueType.INTEGER), List.of(resultType)),
        result.programForIrGeneration().findUserWord("成功化").orElseThrow());
    assertEquals(
        resultType,
        result
            .programForIrGeneration()
            .nameResolution()
            .bindings()
            .getFirst()
            .typeState()
            .type()
            .orElseThrow());
    assertTrue(new IrGenerator().generate(result.programForIrGeneration()).successful());
  }

  @Test
  void rejectsBareConstructorsWrongPayloadAndNonSelectedTraitViolations() {
    assertSingle("メインとは （--）\n    1 成功にする\nこと。\n", DiagnosticCode.E_RESULT_TYPE_ARGUMENTS_REQUIRED);
    assertSingle("メインとは （--）\n    1 失敗にする<整数,文字列>\nこと。\n", DiagnosticCode.E_TYPE_MISMATCH);
    assertSingle(
        "表示とは （結果<整数,正規表現> --）\n    一行表示する\nこと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH);
    assertSingle(
        "比較とは （結果<整数,日時> 結果<整数,日時> -- 真偽）\n    等しい\nこと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH);
  }

  private static void assertSingle(String source, DiagnosticCode code) {
    AnalysisResult result = AnalyzerTestSupport.checkText(source);
    assertEquals(
        List.of(code), result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }
}
