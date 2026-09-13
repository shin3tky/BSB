package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.FileOffset;
import jp.bsb.diagnostics.SourcePosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Utf8SourceReaderTest {
  private final Utf8SourceReader reader = new Utf8SourceReader();

  @Test
  void acceptsOneLeadingBomAndKeepsOriginalByteOffsets() {
    byte[] content = "メイン".getBytes(StandardCharsets.UTF_8);
    byte[] bytes = new byte[3 + content.length];
    bytes[0] = (byte) 0xEF;
    bytes[1] = (byte) 0xBB;
    bytes[2] = (byte) 0xBF;
    System.arraycopy(content, 0, bytes, 3, content.length);
    var diagnostics = new DiagnosticCollector();

    SourceText source = reader.read("BOM.bsb", bytes, diagnostics).orElseThrow();

    assertEquals("メイン", source.text());
    assertEquals(new SourcePosition(3, 1, 1), source.positionAt(0));
    assertTrue(diagnostics.diagnostics().isEmpty());
  }

  @Test
  void removesOnlyTheFirstLeadingBom() {
    byte[] bytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    var diagnostics = new DiagnosticCollector();

    SourceText source = reader.read("BOM.bsb", bytes, diagnostics).orElseThrow();

    assertEquals("\uFEFF", source.text());
    assertTrue(UnicodeRules.isForbiddenIdentifierCodePoint(source.text().codePointAt(0)));
    assertEquals(new SourcePosition(3, 1, 1), source.positionAt(0));
  }

  @Test
  void preservesTextThatMustNotReceiveIdentifierNormalization() {
    String original = "「Ａe\u0301」 # Ｂe\u0301\n";
    var diagnostics = new DiagnosticCollector();

    SourceText source =
        reader.read("内容.bsb", original.getBytes(StandardCharsets.UTF_8), diagnostics).orElseThrow();

    assertEquals(original, source.text());
  }

  @Test
  void rejectsMalformedUtf8WithoutReplacement() {
    byte[] prefix = "メインとは （--）\n".getBytes(StandardCharsets.UTF_8);
    byte[] suffix = "\nこと。\n".getBytes(StandardCharsets.UTF_8);
    byte[] bytes = new byte[prefix.length + 2 + suffix.length];
    System.arraycopy(prefix, 0, bytes, 0, prefix.length);
    bytes[prefix.length] = (byte) 0xC3;
    bytes[prefix.length + 1] = 0x28;
    System.arraycopy(suffix, 0, bytes, prefix.length + 2, suffix.length);
    var diagnostics = new DiagnosticCollector();

    assertTrue(reader.read("CORE-F001.bsb", bytes, diagnostics).isEmpty());

    var diagnostic = diagnostics.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_INVALID_UTF8, diagnostic.code());
    assertEquals(25, diagnostic.location().utf8Offset());
    assertEquals(
        new SourcePosition(25, 2, 1), diagnostic.location().displayPosition().orElseThrow());
    assertEquals("C3 28", diagnostic.actual().orElseThrow());
    assertFalse(diagnostics.limitReached());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r", "\r\n"})
  void recognizesAllSpecifiedPhysicalLineEndings(String lineEnding) {
    var diagnostics = new DiagnosticCollector();
    byte[] bytes = ("前" + lineEnding + "後").getBytes(StandardCharsets.UTF_8);

    SourceText source = reader.read("改行.bsb", bytes, diagnostics).orElseThrow();

    assertEquals(
        new SourcePosition(bytes.length - 3L, 2, 1), source.positionAt(source.text().length() - 1));
  }

  @Test
  void acceptsTheExactSourceByteLimit() {
    byte[] bytes = new byte[Utf8SourceReader.MAX_SOURCE_BYTES];
    Arrays.fill(bytes, (byte) '#');
    var diagnostics = new DiagnosticCollector();

    SourceText source = reader.read("CORE-R001-ok.bsb", bytes, diagnostics).orElseThrow();

    assertEquals(Utf8SourceReader.MAX_SOURCE_BYTES, source.text().length());
    assertEquals(
        new SourcePosition(
            Utf8SourceReader.MAX_SOURCE_BYTES, 1, Utf8SourceReader.MAX_SOURCE_BYTES + 1),
        source.positionAt(source.text().length()));
    assertTrue(diagnostics.diagnostics().isEmpty());
  }

  @Test
  void rejectsOneByteOverTheSourceLimitBeforeDecoding() {
    byte[] bytes = new byte[Utf8SourceReader.MAX_SOURCE_BYTES + 1];
    Arrays.fill(bytes, (byte) 0xFF);
    var diagnostics = new DiagnosticCollector();

    assertTrue(reader.read("CORE-R001-error.bsb", bytes, diagnostics).isEmpty());

    var diagnostic = diagnostics.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_SOURCE_SIZE_LIMIT, diagnostic.code());
    assertEquals(new FileOffset(Utf8SourceReader.MAX_SOURCE_BYTES), diagnostic.location());
    assertEquals("33554432", diagnostic.limit().orElseThrow());
    assertEquals("33554433", diagnostic.observed().orElseThrow());
  }
}
