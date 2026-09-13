package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import jp.bsb.regex.RegexLimits;
import jp.bsb.regex.RegexProgram;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class RegexValueTest {
  @Test
  void keepsRawPatternCanonicalFlagsAndCompiledMetadata() {
    var program = new TestProgram(12, 2, List.of("year"));
    var value = new RegexValue("(?<year>\\p{Han}+)", "ims", program);

    assertEquals(ValueType.REGEX, value.type());
    assertEquals("(?<year>\\p{Han}+)", value.rawPattern());
    assertEquals("ims", value.canonicalFlags());
    assertEquals(program, value.program());
    assertEquals("正規表現「(?<year>\\p{Han}+)」ims", value.displayText());
  }

  @Test
  void rejectsInvalidFlagsRawContentsAndCompiledMetadata() {
    var valid = new TestProgram(1, 0, List.of());

    assertThrows(IllegalArgumentException.class, () -> new RegexValue("a", "mi", valid));
    assertThrows(IllegalArgumentException.class, () -> new RegexValue("a", "ii", valid));
    assertThrows(IllegalArgumentException.class, () -> new RegexValue("a\nb", "", valid));
    assertThrows(IllegalArgumentException.class, () -> new RegexValue("a」b", "", valid));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexValue("a", "", new TestProgram(0, 0, List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegexValue(
                "a", "", new TestProgram(RegexLimits.MAX_PROGRAM_INSTRUCTIONS + 1, 0, List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexValue("a", "", new TestProgram(1, RegexLimits.MAX_CAPTURES + 1, List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexValue("a", "", new TestProgram(1, 1, List.of("x", "y"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexValue("a", "", new TestProgram(1, 2, List.of("x", "x"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexValue("a", "", new TestProgram(1, 1, List.of("年"))));
  }

  @Test
  void acceptsThePatternByteLimitAndRejectsOneByteMore() {
    String maximum = "a".repeat(RegexLimits.MAX_PATTERN_UTF8_BYTES);
    var program = new TestProgram(1, 0, List.of());

    assertEquals(maximum, new RegexValue(maximum, "", program).rawPattern());
    assertThrows(IllegalArgumentException.class, () -> new RegexValue(maximum + "a", "", program));
  }

  @Test
  void fixesTheStringValueLimitAtSixteenMebibytes() {
    assertEquals(16_777_216, StringLimits.MAX_UTF8_BYTES);
  }

  private record TestProgram(int instructionCount, int captureCount, List<String> namedCaptures)
      implements RegexProgram {
    private TestProgram {
      namedCaptures = List.copyOf(namedCaptures);
    }

    @Override
    public boolean matchesEntire(String input) {
      return false;
    }

    @Override
    public boolean containsMatch(String input) {
      return false;
    }

    @Override
    public java.util.Optional<jp.bsb.regex.RegexMatch> firstMatch(String input) {
      return java.util.Optional.empty();
    }

    @Override
    public jp.bsb.regex.RegexMatchCursor matchCursor(String input) {
      throw new UnsupportedOperationException();
    }
  }
}
