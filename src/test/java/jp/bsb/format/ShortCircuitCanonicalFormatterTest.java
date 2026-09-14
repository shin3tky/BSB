package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ShortCircuitCanonicalFormatterTest {
  @Test
  void formatsEvaluationBlocksLikeOtherPostfixControlOpeners() {
    String source = "メインとは （--）\n" + "はい または いいえ かつ はい つぎに つぎに 真偽を捨てる\n" + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("short-circuit.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        "メインとは （--）\n"
            + "    はい または\n"
            + "        いいえ かつ\n"
            + "            はい\n"
            + "        つぎに\n"
            + "    つぎに\n"
            + "    真偽を捨てる\n"
            + "こと。\n",
        result.outputForStandardOutput());
  }
}
