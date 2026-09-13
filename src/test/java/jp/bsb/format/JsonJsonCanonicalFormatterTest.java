package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsonJsonCanonicalFormatterTest {
  @Test
  void preservesCanonicalJsonAndJsonArrayTypeNamesIdempotently() {
    String source =
        "JSONを保つとは （JSON -- JSON）\n"
            + "こと。\n\n"
            + "JSON列を保つとは （配列<JSON> -- 配列<JSON>）\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "こと。\n";
    var formatter = new SourceFormatter();

    FormatResult first =
        formatter.format("json-types.bsb", source.getBytes(StandardCharsets.UTF_8));
    FormatResult second =
        formatter.format(
            "json-types-again.bsb",
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertTrue(second.successful(), second.diagnostics().toString());
    assertEquals(source, first.outputForStandardOutput());
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
  }
}
