package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

/** 高階関数を使わない第3段の配列操作を公開ソースから縦断して検証します。 */
class ArrayConvenienceOperationsRuntimeTest {
  @Test
  void runsAllSixOperationsForScalarAndNestedArrays() {
    String source =
        "メインとは （--）\n"
            + "    【1、2、1、1】 と 1 を 配列内の個数を数える 一行表示する\n"
            + "    【1、3】 と 1 と 2 を 配列の位置へ挿入する 一行表示する\n"
            + "    【1、2、3】 と 1 を 配列の位置を削除する 一行表示する\n"
            + "    【1、2、1】 と 1 と 1 を 開始位置から配列を検索する 一行表示する\n"
            + "    【1、2、1】 と 1 と 3 を 開始位置から配列を検索する 一行表示する\n"
            + "    【1、2、1、3、2】を 配列の重複を除く 一行表示する\n"
            + "    「値」 と 3 を 同じ値で配列を作る 一行表示する\n"
            + "    【【1】、【2】、【1】】を 配列の重複を除く 一行表示する\n"
            + "    【1、2】 と 2 を 同じ値で配列を作る 一行表示する\n"
            + "    空の整数配列 と 1 を 配列内の個数を数える 一行表示する\n"
            + "    空の整数配列 と 0 と 5 を 配列の位置へ挿入する 一行表示する\n"
            + "    空の整数配列 と 9 と 0 を 開始位置から配列を検索する 一行表示する\n"
            + "    空の整数配列 を 配列の重複を除く 一行表示する\n"
            + "    7 と 0 を 同じ値で配列を作る 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "array-convenience.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        "3\n【1、2、3】\n【1、3】\n2\n-1\n【1、2、3】\n【「値」、「値」、「値」】\n【【1】、【2】】\n【【1、2】、【1、2】】\n0\n【5】\n-1\n【】\n【】\n",
        output.utf8Text());
  }

  @Test
  void rejectsInvalidInsertionPositionWithoutChangingThePublishedStack() {
    String source = "メインとは （--）\n" + "    【1、2】 と 3 と 9 を 配列の位置へ挿入する 一行表示する\n" + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "array-insert-range.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertFalse(result.successful());
    assertEquals(
        DiagnosticCode.E_ARRAY_INDEX_OUT_OF_BOUNDS, result.diagnostics().getFirst().code());
    assertEquals(3, result.finalDataStack().size());
    assertEquals("", output.utf8Text());
  }

  @Test
  void rejectsNegativeRepeatCount() {
    String source = "メインとは （--）\n" + "    7 と -1 を 同じ値で配列を作る 一行表示する\n" + "こと。\n";

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "array-repeat-negative.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));

    assertFalse(result.successful());
    assertEquals(DiagnosticCode.E_ARRAY_LENGTH_LIMIT, result.diagnostics().getFirst().code());
    assertEquals(2, result.finalDataStack().size());
  }

  @Test
  void safelyGetsElementsAndColumns() {
    String source =
        "メインとは （--）\n"
            + "    【10、20】 と 1 を 配列から任意で取り出す 一行表示する\n"
            + "    【10、20】 と -1 を 配列から任意で取り出す 一行表示する\n"
            + "    【10、20】 と 2 を 配列から任意で取り出す 一行表示する\n"
            + "    【【「a」、「b」】、【「c」、「d」】】 と 1 を 二次元配列から列を任意で取り出す 一行表示する\n"
            + "    【【1、2】、【3】】 と 1 を 二次元配列から列を任意で取り出す 一行表示する\n"
            + "    空の整数二次元配列 と 0 を 二次元配列から列を任意で取り出す 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "array-safe-access.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("ある（20）\nない\nない\nある（【「b」、「d」】）\nない\nない\n", output.utf8Text());
  }
}
