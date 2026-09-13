package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.ir.IrGenerator;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class OptionalOptionalTypeAnalyzerTest {
  @Test
  void resolvesOptionalSignaturesThroughCallsRecursionBranchesAndLoops() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "保つとは （任意<配列<JSON>> -- 任意<配列<JSON>>）\n"
                + "こと。\n\n"
                + "前方とは （任意<整数> -- 任意<整数>）\n"
                + "    再帰\n"
                + "こと。\n\n"
                + "再帰とは （任意<整数> -- 任意<整数>）\n"
                + "    再帰\n"
                + "こと。\n\n"
                + "分岐とは （任意<文字列> 真偽 -- 任意<文字列>）\n"
                + "    ならば\n"
                + "    さもなければ\n"
                + "    つぎに\n"
                + "こと。\n\n"
                + "反復とは （任意<整数> 整数 -- 任意<整数>）\n"
                + "    回だけ\n"
                + "    繰り返す\n"
                + "こと。\n\n"
                + "メインとは （--）\nこと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    ValueType optionalJsonArray = ValueType.optionalOf(ValueType.arrayOf(ValueType.JSON));
    assertEquals(
        new WordSignature(List.of(optionalJsonArray), List.of(optionalJsonArray)),
        result.programForIrGeneration().findUserWord("保つ").orElseThrow());
    assertTrue(new IrGenerator().generate(result.programForIrGeneration()).successful());
  }

  @Test
  void distinguishesBareOptionalConstraintsUnknownTypesAndArrayElementRestriction() {
    assertSingle(
        "裸とは （任意 --）\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_OPTIONAL_ELEMENT_TYPE_REQUIRED);
    assertSingle(
        "制約とは （任意<T> --）\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_TYPE_CONSTRAINT_NOT_ALLOWED);
    assertSingle("未知とは （任意<未知型> --）\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_UNKNOWN_TYPE);
    assertSingle(
        "配列検査とは （配列<任意<整数>> --）\nこと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED);
  }

  @Test
  void reservesOptionalAsATypeName() {
    AnalysisResult result = AnalyzerTestSupport.checkText("任意とは （--）\nこと。\n\nメインとは （--）\nこと。\n");

    assertEquals(
        List.of(DiagnosticCode.E_RESERVED_NAME),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  private static void assertSingle(String source, DiagnosticCode code) {
    AnalysisResult result = AnalyzerTestSupport.checkText(source);
    assertEquals(
        List.of(code), result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }
}
