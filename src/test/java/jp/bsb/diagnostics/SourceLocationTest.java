package jp.bsb.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SourceLocationTest {
  @Test
  void spanUsesItsStartForOrderingAndDisplay() {
    var start = new SourcePosition(10, 2, 3);
    var end = new SourcePosition(14, 2, 5);
    var span = new SourceSpan(start, end);

    assertEquals(10, span.utf8Offset());
    assertEquals(start, span.displayPosition().orElseThrow());
  }

  @Test
  void positionsAndSpansRejectInvalidRanges() {
    assertThrows(IllegalArgumentException.class, () -> new SourcePosition(-1, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new SourcePosition(0, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new SourcePosition(0, 1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SourceSpan(new SourcePosition(2, 1, 2), new SourcePosition(1, 1, 1)));
  }
}
