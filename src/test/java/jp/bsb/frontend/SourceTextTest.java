package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jp.bsb.diagnostics.SourcePosition;
import org.junit.jupiter.api.Test;

class SourceTextTest {
  @Test
  void mapsUtf8BytesGraphemeColumnsAndNewlineForms() {
    var source = new SourceText("位置.bsb", "Aあ𠮷か\u3099B\r\nC\rD\nE", 3);

    assertEquals(new SourcePosition(3, 1, 1), source.positionAt(0));
    assertEquals(new SourcePosition(4, 1, 2), source.positionAt(1));
    assertEquals(new SourcePosition(7, 1, 3), source.positionAt(2));
    assertEquals(new SourcePosition(11, 1, 4), source.positionAt(4));
    assertEquals(new SourcePosition(14, 1, 4), source.positionAt(5));
    assertEquals(new SourcePosition(17, 1, 5), source.positionAt(6));
    assertEquals(new SourcePosition(19, 1, 6), source.positionAt(8));
    assertEquals(new SourcePosition(20, 2, 1), source.positionAt(9));
    assertEquals(new SourcePosition(22, 3, 1), source.positionAt(11));
    assertEquals(new SourcePosition(24, 4, 1), source.positionAt(13));
    assertEquals(new SourcePosition(25, 4, 2), source.positionAt(14));
  }

  @Test
  void rejectsAnIndexInsideASurrogatePair() {
    var source = new SourceText("位置.bsb", "𠮷", 0);

    assertThrows(IllegalArgumentException.class, () -> source.positionAt(1));
  }

  @Test
  void rejectsUnpairedSurrogates() {
    assertThrows(IllegalArgumentException.class, () -> new SourceText("位置.bsb", "\uD800", 0));
    assertThrows(IllegalArgumentException.class, () -> new SourceText("位置.bsb", "\uDC00", 0));
  }

  @Test
  void advancesTabsToTheNextFourColumnStop() {
    var source = new SourceText("位置.bsb", "\tA\tB\t", 0);

    assertEquals(new SourcePosition(0, 1, 1), source.positionAt(0));
    assertEquals(new SourcePosition(1, 1, 5), source.positionAt(1));
    assertEquals(new SourcePosition(2, 1, 6), source.positionAt(2));
    assertEquals(new SourcePosition(3, 1, 9), source.positionAt(3));
    assertEquals(new SourcePosition(4, 1, 10), source.positionAt(4));
    assertEquals(new SourcePosition(5, 1, 13), source.positionAt(5));
  }

  @Test
  void locatesTheClusterImmediatelyBeforeAnExclusiveUtf8End() {
    var source = new SourceText("位置.bsb", "A\r\nか\u3099𠮷\n", 3);

    assertEquals(new SourcePosition(3, 1, 1), source.clusterPositionBefore(4));
    assertEquals(new SourcePosition(4, 1, 2), source.clusterPositionBefore(6));
    assertEquals(new SourcePosition(6, 2, 1), source.clusterPositionBefore(7));
    assertEquals(new SourcePosition(6, 2, 1), source.clusterPositionBefore(12));
    assertEquals(new SourcePosition(12, 2, 2), source.clusterPositionBefore(16));
    assertEquals(new SourcePosition(16, 2, 3), source.clusterPositionBefore(17));
  }
}
