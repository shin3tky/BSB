package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.ir.IrGenerator;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class RecoverableJsonRecoverableJsonAnalysisTest {
  @Test
  void acceptsTheParserAndEveryPublicFailureAccessorWithFixedEffects() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "解析とは （文字列 -- 結果<JSON,JSON解析失敗>）\n"
                + "    JSONを解析して結果を返す\n"
                + "こと。\n\n"
                + "種類とは （JSON解析失敗 -- 文字列）\n"
                + "    JSON解析失敗の種類を取り出す\n"
                + "こと。\n\n"
                + "バイトとは （JSON解析失敗 -- 整数）\n"
                + "    JSON解析失敗のバイト位置を取り出す\n"
                + "こと。\n\n"
                + "行とは （JSON解析失敗 -- 整数）\n"
                + "    JSON解析失敗の行を取り出す\n"
                + "こと。\n\n"
                + "列とは （JSON解析失敗 -- 整数）\n"
                + "    JSON解析失敗の列を取り出す\n"
                + "こと。\n\n"
                + "保存とは （-- JSON解析失敗）\n"
                + "    値は 定数 「x」を JSONを解析して結果を返す 結果から失敗値を取り出す。\n"
                + "    値\n"
                + "こと。\n\n"
                + "入れ子を保つとは （任意<結果<JSON,JSON解析失敗>> -- 任意<結果<JSON,JSON解析失敗>>）\n"
                + "こと。\n\n"
                + "合流とは （JSON解析失敗 真偽 -- JSON解析失敗）\n"
                + "    ならば\n"
                + "    さもなければ\n"
                + "    つぎに\n"
                + "こと。\n\n"
                + "メインとは （--）\nこと。\n");

    assertTrue(
        result.successful(),
        result.diagnostics().stream()
            .map(diagnostic -> diagnostic.code() + ":" + diagnostic.fields())
            .toList()
            .toString());
    assertEquals(
        new WordSignature(
            List.of(ValueType.STRING),
            List.of(ValueType.resultOf(ValueType.JSON, ValueType.JSON_PARSE_FAILURE))),
        result.programForIrGeneration().findUserWord("解析").orElseThrow());
    assertTrue(new IrGenerator().generate(result.programForIrGeneration()).successful());
  }

  @Test
  void rejectsDirectAndTransitivelyWrappedDisplayOrEquality() {
    assertSingle(
        "表示とは （JSON解析失敗 --）\n    一行表示する\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_TYPE_MISMATCH);
    assertSingle(
        "比較とは （結果<整数,JSON解析失敗> 結果<整数,JSON解析失敗> -- 真偽）\n"
            + "    等しい\n"
            + "こと。\n\n"
            + "メインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH);
  }

  @Test
  void reservesThePublicTypeAndWordNames() {
    assertSingle("JSON解析失敗とは （--）\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_RESERVED_NAME);
    assertSingle(
        "JSON解析失敗の行を取り出すとは （--）\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_RESERVED_NAME);
  }

  private static void assertSingle(String source, DiagnosticCode code) {
    AnalysisResult result = AnalyzerTestSupport.checkText(source);
    assertEquals(
        List.of(code), result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }
}
