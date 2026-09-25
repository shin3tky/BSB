package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

/** 連結、先頭追加、逆順、検索、空判定の公開ソース縦断を検証します。 */
class ArrayExtendedOperationsRuntimeTest {
  @Test
  void runsAllSixOperationsForScalarNestedAndWrapperArrays() {
    String source =
        "メインとは （--）\n"
            + "    【1、2】 と 【3、4】を 配列をつなぐ 一行表示する\n"
            + "    【2、3】 と 1 を 配列の先頭へ追加する 一行表示する\n"
            + "    【1、2、3】を 配列を逆順にする 一行表示する\n"
            + "    【1、2、3】 と 2 を 配列に含まれる 一行表示する\n"
            + "    【1、2、3】 と 9 を 配列に含まれる 一行表示する\n"
            + "    【1、2、1】 と 1 を 配列から検索する 一行表示する\n"
            + "    【1、2、1】 と 9 を 配列から検索する 一行表示する\n"
            + "    空の整数配列 を 配列が空である 一行表示する\n"
            + "    【1】を 配列が空である 一行表示する\n"
            + "    【【1】、【2、3】】 と 【【4】】を 配列をつなぐ 一行表示する\n"
            + "    【【1】、【2、3】】を 配列を逆順にする 一行表示する\n"
            + "    【【1】、【2、3】】 と 【2、3】を 配列から検索する 一行表示する\n"
            + "    【1 を 任意にする、2 を 任意にする】 と 2 を 任意にする を 配列に含まれる 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "array-extended.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(
        result.successful(),
        result.diagnostics().stream()
            .map(diagnostic -> diagnostic.code() + ":" + diagnostic.fields())
            .toList()
            .toString());
    assertTrue(result.finalDataStack().isEmpty());
    assertEquals(
        "【1、2、3、4】\n"
            + "【1、2、3】\n"
            + "【3、2、1】\n"
            + "はい\n"
            + "いいえ\n"
            + "0\n"
            + "-1\n"
            + "はい\n"
            + "いいえ\n"
            + "【【1】、【2、3】、【4】】\n"
            + "【【2、3】、【1】】\n"
            + "1\n"
            + "はい\n",
        output.utf8Text());
  }

  @Test
  void rejectsSearchForAnElementTypeThatCannotBeCompared() {
    String source = "メインとは （--）\n" + "    【正規表現「a」を 任意にする】 と 正規表現「a」を 任意にする を 配列に含まれる\n" + "こと。\n";

    var result =
        new SourceChecker().check("array-search-type.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertFalse(result.successful());
    assertEquals(1, result.diagnostics().size(), result.diagnostics().toString());
    assertEquals(DiagnosticCode.E_TYPE_MISMATCH, result.diagnostics().getFirst().code());
    assertEquals("等値比較可能", result.diagnostics().getFirst().expected().orElseThrow());
  }
}
