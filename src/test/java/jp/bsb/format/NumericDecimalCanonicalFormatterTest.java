package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NumericDecimalCanonicalFormatterTest {
  @Test
  void formatsNumericN018CanonicallyWhilePreservingEveryDecimalLexeme() throws Exception {
    byte[] source = FormatTestSupport.numericsResourceBytes("sources/NUM-N018.bsb");
    byte[] expected = FormatTestSupport.numericsResourceBytes("canonical/NUM-N018.bsb");
    var formatter = new SourceFormatter();

    FormatResult first = formatter.format("NUM-N018.bsb", source);
    FormatResult second =
        formatter.format(
            "formatted-NUM-N018.bsb",
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertArrayEquals(expected, first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
    assertTrue(first.outputForStandardOutput().contains("-0.0"));
    assertTrue(first.outputForStandardOutput().contains("1.00"));
    assertTrue(first.outputForStandardOutput().contains("0.00100"));
  }
}
