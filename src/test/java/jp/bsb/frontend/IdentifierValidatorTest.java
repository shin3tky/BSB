package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.SourcePosition;
import org.junit.jupiter.api.Test;

class IdentifierValidatorTest {
  private final IdentifierValidator validator = new IdentifierValidator();

  @Test
  void accepts128NormalizedCodePoints() {
    String identifier = "名".repeat(128);
    var source = new SourceText("CORE-R002-ok.bsb", identifier, 0);
    var diagnostics = new DiagnosticCollector();

    assertEquals(
        identifier,
        validator.validate(source, 0, source.text().length(), diagnostics).orElseThrow());
    assertTrue(diagnostics.diagnostics().isEmpty());
  }

  @Test
  void rejects129NormalizedCodePoints() {
    String identifier = "名".repeat(129);
    var source = new SourceText("CORE-R002-error.bsb", identifier, 0);
    var diagnostics = new DiagnosticCollector();

    assertTrue(validator.validate(source, 0, source.text().length(), diagnostics).isEmpty());

    var diagnostic = diagnostics.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_IDENTIFIER_TOO_LONG, diagnostic.code());
    assertEquals("128", diagnostic.limit().orElseThrow());
    assertEquals("129", diagnostic.observed().orElseThrow());
  }

  @Test
  void reportsTheExactPositionOfAnInvisibleCharacter() {
    String identifier = "名\u202E前";
    var source = new SourceText("CORE-F003.bsb", identifier, 0);
    var diagnostics = new DiagnosticCollector();

    assertTrue(validator.validate(source, 0, source.text().length(), diagnostics).isEmpty());

    var diagnostic = diagnostics.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_INVISIBLE_CHARACTER, diagnostic.code());
    assertEquals(
        new SourcePosition(3, 1, 2), diagnostic.location().displayPosition().orElseThrow());
    assertEquals("U+202E", diagnostic.actual().orElseThrow());
    assertEquals("RIGHT-TO-LEFT OVERRIDE", diagnostic.fields().get("unicodeName"));
  }

  @Test
  void returnsTheNormalizedIdentifier() {
    var source = new SourceText("CORE-N006.bsb", "請求Ａ１e\u0301", 0);
    var diagnostics = new DiagnosticCollector();

    assertEquals(
        "請求A1é", validator.validate(source, 0, source.text().length(), diagnostics).orElseThrow());
  }
}
