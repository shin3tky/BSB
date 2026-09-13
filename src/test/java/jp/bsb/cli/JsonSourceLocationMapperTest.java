package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jp.bsb.frontend.SourceText;
import org.junit.jupiter.api.Test;

class JsonSourceLocationMapperTest {
  @Test
  void mapsAnExplanationSpanFromTheSameImmutableSnapshot() {
    var source = new SourceText("位置.bsb", "A\tか\u3099𠮷\r\n", 0);

    JsonSpan span = JsonSourceLocationMapper.span(source.span(1, 6), source);

    assertEquals(new JsonLineColumn(1, 2), span.start());
    assertEquals(new JsonLineColumn(1, 6), span.endInclusive());
    assertEquals(1, span.utf8Start());
    assertEquals(12, span.utf8EndExclusive());
  }

  @Test
  void rejectsAZeroWidthExplanationLocation() {
    var source = new SourceText("位置.bsb", "A", 0);

    assertThrows(
        IllegalArgumentException.class,
        () -> JsonSourceLocationMapper.span(source.span(1, 1), source));
  }
}
