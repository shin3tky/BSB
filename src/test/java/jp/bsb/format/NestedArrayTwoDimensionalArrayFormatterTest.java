package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 2次元配列型とliteralの正規形が再適用で変わらないことを検証します。 */
class NestedArrayTwoDimensionalArrayFormatterTest {
  @Test
  void normalizesNestedTypesAndLiteralsIdempotently() {
    String source =
        "保つとは （ 配列 < 配列 < 整数 > > -- 配列 < 配列 < 整数 > > ）\n"
            + "こと。\n"
            + "メインとは （--）\n"
            + "【 【1 、 2】 、 【3】 】 を 一行表示する\n"
            + "こと。\n";
    var formatter = new SourceFormatter();
    FormatResult first = formatter.format("matrix.bsb", source.getBytes(StandardCharsets.UTF_8));
    FormatResult second =
        formatter.format(
            "again.bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
    assertTrue(first.outputForStandardOutput().contains("配列<配列<整数>>"));
    assertTrue(first.outputForStandardOutput().contains("【【1、2】、【3】】"));
  }
}
