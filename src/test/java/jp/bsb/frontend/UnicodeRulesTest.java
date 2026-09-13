package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnicodeRulesTest {
  @Test
  void fixesTheUnicodeDataVersionTo1600() {
    assertEquals("16.0.0", UnicodeRules.UNICODE_VERSION);
  }

  @Test
  void matchesTheConformanceDataVersionLedger() throws IOException {
    var properties = new Properties();
    try (var reader =
        new InputStreamReader(
            UnicodeRulesTest.class.getResourceAsStream(
                "/conformance/language-core/unicode-data.properties"),
            StandardCharsets.UTF_8)) {
      properties.load(reader);
    }

    assertEquals(properties.getProperty("unicode.version"), UnicodeRules.UNICODE_VERSION);
    assertEquals("false", properties.getProperty("allow.latest.alias"));
  }

  @Test
  void convertsOnlyFullwidthAlphanumericsThenAppliesNfc() {
    assertEquals("ABCabc019é①Ⅰ", UnicodeRules.normalizeIdentifier("ＡＢＣａｂｃ０１９e\u0301①Ⅰ"));
    assertEquals("ABC", UnicodeRules.normalizeIdentifier("ABC"));
    assertFalse(
        UnicodeRules.normalizeIdentifier("ABC").equals(UnicodeRules.normalizeIdentifier("abc")));
  }

  @Test
  void followsTheSpecifiedIdentifierCategories() {
    assertTrue(UnicodeRules.isValidIdentifier("請求A1_明細・二"));
    assertTrue(UnicodeRules.isValidIdentifier("か\u3099"));
    assertFalse(UnicodeRules.isValidIdentifier("1請求"));
    assertFalse(UnicodeRules.isValidIdentifier("請求-明細"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x202E, 0xFE0F, 0x034F, 0x3164, 0x200C, 0x200D})
  void rejectsInvisibleAndDefaultIgnorableCodePoints(int codePoint) {
    assertTrue(UnicodeRules.isForbiddenIdentifierCodePoint(codePoint));
  }

  @Test
  void segmentsExtendedGraphemeClusters() {
    String familyEmoji = "👨‍👩‍👧‍👦";
    String value = "が" + "か\u3099" + "𠮷" + familyEmoji;

    assertEquals(4, UnicodeRules.graphemeClusterCount(value));
    assertEquals(List.of(0, 1, 3, 5, value.length()), UnicodeRules.graphemeBoundaries(value));
  }

  @Test
  void computesUnicode16ConfusableSkeletons() {
    assertEquals(
        UnicodeRules.confusableSkeleton("paypal"),
        UnicodeRules.confusableSkeleton("pаypal")); // 2文字目はキリル小文字а
  }
}
