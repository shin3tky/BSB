package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpsHttpFormatterTest {
  @Test
  void normalizesStaticArgumentsAndIsIdempotent() {
    String source = "APIは 論理接続。\nメインとは （--）\n" + "空のHTTP要求 HTTP要求を送信する< API , POST > 結果を捨てる\nこと。\n";
    String expected =
        "APIは 論理接続。\n\nメインとは （--）\n"
            + "    空のHTTP要求\n"
            + "    HTTP要求を送信する<API,POST>\n"
            + "    結果を捨てる\nこと。\n";
    var formatter = new SourceFormatter();
    FormatResult first = formatter.format("http.bsb", source.getBytes(StandardCharsets.UTF_8));
    FormatResult second =
        formatter.format(
            "again.bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertEquals(expected, first.outputForStandardOutput());
    assertEquals(expected, second.outputForStandardOutput());
  }
}
