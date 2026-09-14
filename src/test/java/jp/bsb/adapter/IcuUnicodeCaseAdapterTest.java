package jp.bsb.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class IcuUnicodeCaseAdapterTest {
  @Test
  void appliesUnicodeSixteenFullMappings() {
    assertEquals("STRASSE", IcuUnicodeAdapter.toUpperCase("Straße"));
    assertEquals("ος", IcuUnicodeAdapter.toLowerCase("ΟΣ"));
    assertEquals("i\u0307", IcuUnicodeAdapter.toLowerCase("İ"));
    assertEquals("strasse", IcuUnicodeAdapter.foldCase("Straße"));
    assertEquals("σ", IcuUnicodeAdapter.foldCase("ς"));
  }

  @Test
  void mappingsDoNotDependOnTheHostDefaultLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr"));
      assertEquals("i", IcuUnicodeAdapter.toLowerCase("I"));
      assertEquals("I", IcuUnicodeAdapter.toUpperCase("i"));
      assertEquals("i", IcuUnicodeAdapter.foldCase("I"));
    } finally {
      Locale.setDefault(previous);
    }
  }
}
