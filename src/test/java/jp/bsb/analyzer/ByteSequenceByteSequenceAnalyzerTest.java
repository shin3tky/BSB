package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.ir.IrGenerator;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ByteSequenceByteSequenceAnalyzerTest {
  @Test
  void acceptsFixedEffectsStorageControlWrappersAndByteEquality() {
    AnalysisResult result =
        AnalyzerTestSupport.checkText(
            "比較とは （バイト列 バイト列 -- 真偽）\n"
                + "    等しい\n"
                + "こと。\n\n"
                + "保持とは （真偽 -- 結果<バイト列,整数>）\n"
                + "    値は 変数 空のバイト列。\n"
                + "    ならば\n"
                + "        値 を 成功にする<バイト列,整数>\n"
                + "    さもなければ\n"
                + "        値 を 成功にする<バイト列,整数>\n"
                + "    つぎに\n"
                + "こと。\n\n"
                + "変換とは （文字列 -- 結果<文字列,UTF8復号失敗>）\n"
                + "    文字列をUTF8バイト列に変換する\n"
                + "    バイト列をUTF8文字列に変換して結果を返す\n"
                + "こと。\n\n"
                + "メインとは （--）\n"
                + "こと。\n");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        new WordSignature(
            List.of(ValueType.BOOLEAN),
            List.of(ValueType.resultOf(ValueType.BYTE_SEQUENCE, ValueType.INTEGER))),
        result.programForIrGeneration().findUserWord("保持").orElseThrow());
    assertEquals(
        ValueType.BYTE_SEQUENCE,
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
  void rejectsDisplayFailureEqualityAndAllThreeArrayElementTypes() {
    assertSingle(
        "表示とは （バイト列 --）\n    表示する\nこと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH,
        "バイト列");
    assertSingle(
        "比較とは （UTF8復号失敗 UTF8復号失敗 -- 真偽）\n" + "    等しい\nこと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH,
        "UTF8復号失敗");
    assertSingle(
        "表示とは （結果<整数,Base64復号失敗> --）\n" + "    表示する\nこと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH,
        "結果<整数,Base64復号失敗>");
    for (String type : List.of("バイト列", "UTF8復号失敗", "Base64復号失敗")) {
      assertSingle(
          "不正とは （配列<" + type + "> --）\nこと。\n\nメインとは （--）\nこと。\n",
          DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
          type);
    }
  }

  @Test
  void reservesNewTypeAndWordNames() {
    assertSingle("バイト列とは （--）\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_RESERVED_NAME, "バイト列");
    assertSingle(
        "空のバイト列とは （--）\nこと。\n\nメインとは （--）\nこと。\n", DiagnosticCode.E_RESERVED_NAME, "空のバイト列");
  }

  private static void assertSingle(String source, DiagnosticCode code, String actual) {
    AnalysisResult result = AnalyzerTestSupport.checkText(source);
    assertEquals(List.of(code), result.diagnostics().stream().map(d -> d.code()).toList());
    assertEquals(actual, result.diagnostics().getFirst().actual().orElseThrow());
  }
}
