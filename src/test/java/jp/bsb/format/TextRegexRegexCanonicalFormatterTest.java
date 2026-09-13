package jp.bsb.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextRegexRegexCanonicalFormatterTest {
  @Test
  void canonicalizesOnlyTheFlagOrderForBothNormativeCases() throws Exception {
    assertCanonical("TEXT-N012.bsb");
    assertCanonical("TEXT-N020.bsb");
  }

  @Test
  void formatPreservesRawPatternAndDoesNotCompileEngineSyntax() {
    String source = "メインとは （--）\n" + "    正規表現「(?=a) \\p{Han} # 、」smi\n" + "こと。\n";

    FormatResult result =
        new SourceFormatter().format("raw.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(source.replace("」smi", "」ims"), result.outputForStandardOutput());
  }

  private static void assertCanonical(String name) throws Exception {
    byte[] source = FormatTestSupport.TextRegexResourceBytes("sources/" + name);
    byte[] expected = FormatTestSupport.TextRegexResourceBytes("canonical/" + name);
    var formatter = new SourceFormatter();

    FormatResult first = formatter.format(name, source);
    assertTrue(first.successful(), first.diagnostics().toString());
    byte[] actual = first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(expected, actual);

    FormatResult second = formatter.format(name, actual);
    assertTrue(second.successful(), second.diagnostics().toString());
    assertArrayEquals(actual, second.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
  }
}
