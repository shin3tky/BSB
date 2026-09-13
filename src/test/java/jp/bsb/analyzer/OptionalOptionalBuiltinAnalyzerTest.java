package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.ir.IrGenerator;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class OptionalOptionalBuiltinAnalyzerTest {
  @Test
  void concretizesAllOptionalRulesAndJsonLookupThroughControlAndStorage() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "包むとは （整数 -- 任意<整数>）\n"
                + "    任意にする\n"
                + "こと。\n\n"
                + "選ぶとは （任意<整数> -- 整数）\n"
                + "    任意に値がある\n"
                + "    ならば\n"
                + "        任意から値を取り出す\n"
                + "    さもなければ\n"
                + "        任意を捨てる\n"
                + "        0\n"
                + "    つぎに\n"
                + "こと。\n\n"
                + "検索とは （JSON 文字列 -- 任意<JSON>）\n"
                + "    JSONオブジェクトから任意値を取り出す\n"
                + "こと。\n\n"
                + "保存とは （-- 整数）\n"
                + "    値は 変数 1 任意にする。\n"
                + "    値 を 任意から値を取り出す\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    var analyzed = result.programForIrGeneration();
    assertEquals(
        new WordSignature(
            List.of(ValueType.INTEGER), List.of(ValueType.optionalOf(ValueType.INTEGER))),
        analyzed.findUserWord("包む").orElseThrow());
    assertEquals(
        ValueType.optionalOf(ValueType.INTEGER),
        analyzed.nameResolution().bindings().getFirst().typeState().type().orElseThrow());
    assertTrue(new IrGenerator().generate(analyzed).successful());
  }

  @Test
  void rejectsMissingNonOptionalAndTransitivelyNonDisplayableOrComparableInputs() {
    assertSingle("メインとは （--）\n    任意にする\nこと。\n", DiagnosticCode.E_STACK_UNDERFLOW);
    assertSingle("メインとは （--）\n    1 任意に値がある\nこと。\n", DiagnosticCode.E_TYPE_MISMATCH);
    assertSingle("メインとは （--）\n    1 任意から値を取り出す\nこと。\n", DiagnosticCode.E_TYPE_MISMATCH);
    assertSingle("メインとは （--）\n    1 任意を捨てる\nこと。\n", DiagnosticCode.E_TYPE_MISMATCH);
    assertSingle("メインとは （--）\n    正規表現「x」 任意にする 一行表示する\nこと。\n", DiagnosticCode.E_TYPE_MISMATCH);
    assertSingle(
        "比較とは （任意<日時> 任意<日時> -- 真偽）\n    等しい\nこと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH);
  }

  private static void assertSingle(String source, DiagnosticCode code) {
    AnalysisResult result = AnalyzerTestSupport.checkText(source);
    assertEquals(
        List.of(code), result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }
}
