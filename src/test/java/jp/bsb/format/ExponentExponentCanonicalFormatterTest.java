package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ExponentExponentCanonicalFormatterTest {
  @Test
  void preservesExponentSpellingAndIsIdempotent() throws Exception {
    byte[] expected = FormatTestSupport.ExponentResourceBytes("canonical/EXP-N-format.bsb");
    SourceFormatter formatter = new SourceFormatter();

    FormatResult first = formatter.format("EXP-N-format.bsb", expected);
    FormatResult second =
        formatter.format(
            "formatted-N10.bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertArrayEquals(expected, first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
    assertTrue(first.outputForStandardOutput().contains("1E+02"));
    assertTrue(first.outputForStandardOutput().contains("1.500e-003"));
    assertTrue(first.outputForStandardOutput().contains("-0E+000"));
  }

  @Test
  void formatsAValidOverScaleExponentWithoutConstructingItsValue() {
    String source = "メインとは （--）\n    1E+065537 を 一行表示する\nこと。\n";
    SourceFormatter formatter = new SourceFormatter();

    FormatResult result =
        formatter.format("over-scale.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(source, result.outputForStandardOutput());
  }
}
