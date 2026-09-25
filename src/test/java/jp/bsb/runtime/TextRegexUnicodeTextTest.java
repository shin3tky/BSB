package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.bsb.frontend.UnicodeRules;
import org.junit.jupiter.api.Test;

class TextRegexUnicodeTextTest {
  @Test
  void distinguishesUnicode16GraphemesFromScalarValues() {
    UnicodeText text = UnicodeText.of("か\u3099葛\uDB40\uDD00👨‍👩‍👧‍👦");

    assertEquals(3, text.graphemeCount());
    assertEquals(11, text.codePointCount());
    assertEquals("か\u3099", text.graphemeAt(0));
    assertEquals("葛\uDB40\uDD00", text.graphemeAt(1));
    assertEquals("👨‍👩‍👧‍👦", text.graphemeAt(2));
    assertEquals("か", text.codePointAt(0));
    assertEquals("\u3099", text.codePointAt(1));
  }

  @Test
  void treatsSupplementaryScalarsAsOneCodePointWithoutPublishingUtf16Positions() {
    UnicodeText text = UnicodeText.of("𠮷\uD87A\uDFF0");

    assertEquals(2, text.graphemeCount());
    assertEquals(2, text.codePointCount());
    assertEquals("𠮷", text.codePointSlice(0, 1));
    assertEquals("\uD87A\uDFF0", text.codePointSlice(1, 2));
    assertEquals("", text.codePointSlice(2, 2));
  }

  @Test
  void givesTheEmptyTextOnlyItsImplicitBoundaryAndRejectsInvalidRanges() {
    UnicodeText empty = UnicodeText.of("");

    assertEquals(0, empty.graphemeCount());
    assertEquals(0, empty.codePointCount());
    assertEquals("", empty.graphemeSlice(0, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> empty.graphemeAt(0));
    assertThrows(IndexOutOfBoundsException.class, () -> empty.codePointSlice(0, 1));
  }

  @Test
  void rejectsBothKindsOfIsolatedSurrogateBeforeSlicing() {
    assertThrows(IllegalArgumentException.class, () -> UnicodeText.of("\uD800"));
    assertThrows(IllegalArgumentException.class, () -> UnicodeText.of("\uDC00"));
    assertThrows(IllegalArgumentException.class, () -> new StringValue("a\uD800b"));
  }

  @Test
  void comparesScalarNumbersInsteadOfUtf16CodeUnits() {
    String privateUseBmp = "\uE000";
    String firstSupplementary = "\uD800\uDC00";

    assertTrue(privateUseBmp.compareTo(firstSupplementary) > 0);
    assertTrue(UnicodeText.compareScalars(privateUseBmp, firstSupplementary) < 0);
    assertEquals(0, UnicodeText.compareScalars("か\u3099", "か\u3099"));
    assertTrue(UnicodeText.compareScalars("a", "aa") < 0);
  }

  @Test
  void trimsOnlyUnicode16WhiteSpaceAtTheEnds() {
    assertEquals("山田 太郎", UnicodeText.trimUnicodeWhitespace("\u3000\t 山田 太郎 \u3000"));
    assertFalse(UnicodeRules.isUnicodeWhitespace(0x200B));
    assertEquals("\u200B値\u200B", UnicodeText.trimUnicodeWhitespace("\u200B値\u200B"));
  }

  @Test
  void searchesOnlyAtWholeGraphemeBoundariesFromEitherDirection() {
    UnicodeText text = UnicodeText.of("か\u3099山か\u3099");

    assertFalse(text.containsAtGraphemeBoundary("か"));
    assertTrue(text.containsAtGraphemeBoundary("か\u3099"));
    assertTrue(text.startsWithAtGraphemeBoundary("か\u3099"));
    assertFalse(text.startsWithAtGraphemeBoundary("か"));
    assertTrue(text.endsWithAtGraphemeBoundary("か\u3099"));
    assertFalse(text.endsWithAtGraphemeBoundary("\u3099"));
    assertEquals(2, text.findAtGraphemeBoundary("か\u3099", 1));
    assertEquals(2, text.findLastAtGraphemeBoundary("か\u3099"));
    assertEquals(3, text.findLastAtGraphemeBoundary(""));
  }

  @Test
  void measuresUtf8WithoutAByteArrayAndStopsImmediatelyPastTheLimit() {
    assertEquals(new Utf8Length.Measurement(7, false), Utf8Length.measureUpTo("a𠮷é", 7));
    assertEquals(new Utf8Length.Measurement(7, true), Utf8Length.measureUpTo("a𠮷éx", 6));
    assertEquals(
        new Utf8Length.Measurement(StringLimits.MAX_UTF8_BYTES, false),
        Utf8Length.measureUpTo(
            "a".repeat(StringLimits.MAX_UTF8_BYTES), StringLimits.MAX_UTF8_BYTES));
    assertEquals(
        new Utf8Length.Measurement(StringLimits.MAX_UTF8_BYTES + 1L, true),
        Utf8Length.measureUpTo(
            "a".repeat(StringLimits.MAX_UTF8_BYTES + 1), StringLimits.MAX_UTF8_BYTES));
  }
}
