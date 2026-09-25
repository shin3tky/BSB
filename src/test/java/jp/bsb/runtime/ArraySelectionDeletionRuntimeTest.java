package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

/** 端の安全な取得と不変削除を、公開ソースから縦断して検証します。 */
class ArraySelectionDeletionRuntimeTest {
  @Test
  void runsAllFiveOperationsForEmptyScalarAndNestedArrays() {
    String source =
        "メインとは （--）\n"
            + "    【1、2、3】を 配列の先頭を任意で取り出す 任意から値を取り出す 一行表示する\n"
            + "    【1、2、3】を 配列の末尾を任意で取り出す 任意から値を取り出す 一行表示する\n"
            + "    空の整数配列 を 配列の先頭を任意で取り出す 任意に値がある 一行表示する 任意を捨てる\n"
            + "    空の整数配列 を 配列の末尾を任意で取り出す 任意に値がある 一行表示する 任意を捨てる\n"
            + "    【1、2、3】を 配列の先頭を削除する 一行表示する\n"
            + "    【1、2、3】を 配列の末尾を削除する 一行表示する\n"
            + "    【1、2、3、4】 と 1 と 3 を 配列の一部を削除する 一行表示する\n"
            + "    【1、2】 と 1 と 1 を 配列の一部を削除する 一行表示する\n"
            + "    空の整数配列 を 配列の先頭を削除する 一行表示する\n"
            + "    空の整数配列 を 配列の末尾を削除する 一行表示する\n"
            + "    【【1】、【2、3】】を 配列の先頭を任意で取り出す 任意から値を取り出す 一行表示する\n"
            + "    【【1】、【2、3】、【4】】 と 1 と 2 を 配列の一部を削除する 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "array-selection-deletion.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertTrue(result.finalDataStack().isEmpty());
    assertEquals(
        "1\n3\nいいえ\nいいえ\n【2、3】\n【1、2】\n【1、4】\n【1、2】\n【】\n【】\n【1】\n【【1】、【4】】\n", output.utf8Text());
  }

  @Test
  void rejectsNonIntegerDeletionRangeStatically() {
    String source = "メインとは （--）\n" + "    【1、2】 と 「0」 と 1 を 配列の一部を削除する\n" + "こと。\n";

    var result =
        new SourceChecker().check("array-delete-type.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertFalse(result.successful());
    assertEquals(1, result.diagnostics().size(), result.diagnostics().toString());
    assertEquals(DiagnosticCode.E_TYPE_MISMATCH, result.diagnostics().getFirst().code());
    assertEquals("整数", result.diagnostics().getFirst().expected().orElseThrow());
  }
}
