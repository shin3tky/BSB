package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConnectionLogicalConnectionFormatterTest {
  @Test
  void normalizesDeclarationsArgumentsAndFullWidthAsciiNamesIdempotently() {
    String source =
        "顧客管理ＡＰＩは\n  論理接続。\n"
            + "通知APIは 論理接続。\n\n"
            + "メインとは （--）\n"
            + "論理接続を確認する< 顧客管理API >\n"
            + "論理接続を確認する<通知API>\n"
            + "こと。\n";
    String expected =
        "顧客管理APIは 論理接続。\n"
            + "通知APIは 論理接続。\n\n"
            + "メインとは （--）\n"
            + "    論理接続を確認する<顧客管理API>\n"
            + "    論理接続を確認する<通知API>\n"
            + "こと。\n";
    var formatter = new SourceFormatter();
    FormatResult first = formatter.format("接続.bsb", source.getBytes(StandardCharsets.UTF_8));
    FormatResult second =
        formatter.format(
            "再整形.bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertEquals(expected, first.outputForStandardOutput());
    assertEquals(expected, second.outputForStandardOutput());
  }

  @Test
  void keepsACommentInsideAConnectionDeclarationBeforeItsTerminator() {
    String source = "接続Aは 論理接続 # 接続注釈\n。\n\nメインとは （--）\nこと。\n";
    FormatResult result =
        new SourceFormatter().format("注釈.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(source, result.outputForStandardOutput());
  }
}
