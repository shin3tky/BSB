package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
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

  @Test
  void convertsBetweenUtf8OffsetsAndZeroBasedUtf16Positions() {
    var source = new SourceText("位置.bsb", "Aあ𠮷か\u3099B\r\nC\rD\nE", 3);

    assertEquals(0, source.utf16IndexAtUtf8Offset(3));
    assertEquals(2, source.utf16IndexAtUtf8Offset(7));
    assertEquals(4, source.utf16IndexAtUtf8Offset(11));
    assertEquals(5, source.utf16IndexAtUtf8Offset(14));
    assertEquals(6, source.utf16IndexAtUtf8Offset(17));
    assertEquals(14, source.utf16IndexAtUtf8Offset(25));

    assertEquals(new Utf16Position(0, 4), source.utf16PositionAt(source.positionAt(4)));
    assertEquals(new Utf16Position(0, 5), source.utf16PositionAt(source.positionAt(5)));
    assertEquals(new Utf16Position(1, 0), source.utf16PositionAt(source.positionAt(9)));
    assertEquals(new Utf16Position(3, 1), source.utf16PositionAt(source.positionAt(14)));

    assertEquals(4, source.utf16IndexAt(new Utf16Position(0, 4)));
    assertEquals(source.positionAt(11), source.positionAt(new Utf16Position(2, 0)));
    assertEquals(source.positionAt(14), source.positionAt(new Utf16Position(3, 1)));
  }

  @Test
  void convertsHalfOpenSpansWithoutUsingGraphemeColumns() {
    var source = new SourceText("位置.bsb", "か\u3099𠮷\n次", 3);
    SourceSpan span = source.span(1, 4);

    assertEquals(
        new Utf16Range(new Utf16Position(0, 1), new Utf16Position(0, 4)),
        source.utf16RangeOf(span));
  }

  @Test
  void mapsEmptySourcesAndTrailingEmptyLines() {
    var empty = new SourceText("空.bsb", "", 0);
    var trailingLf = new SourceText("改行.bsb", "一\n", 0);

    assertEquals(0, empty.utf16IndexAt(new Utf16Position(0, 0)));
    assertEquals(new Utf16Position(0, 0), empty.utf16PositionAt(empty.positionAt(0)));
    assertEquals(1, trailingLf.utf16IndexAt(new Utf16Position(0, 1)));
    assertEquals(2, trailingLf.utf16IndexAt(new Utf16Position(1, 0)));
    assertEquals(new Utf16Position(1, 0), trailingLf.utf16PositionAt(trailingLf.positionAt(2)));
  }

  @Test
  void keepsTabsAsOneUtf16CodeUnit() {
    var source = new SourceText("位置.bsb", "\tA", 0);

    SourcePosition internal = source.positionAt(1);
    assertEquals(5, internal.column());
    assertEquals(new Utf16Position(0, 1), source.utf16PositionAt(internal));
  }

  @Test
  void convertsOffsetsBeyondTheCheckpointInterval() {
    String text = "a".repeat(4095) + "𠮷" + "終";
    var source = new SourceText("長文.bsb", text, 3);

    assertEquals(4095, source.utf16IndexAtUtf8Offset(4098));
    assertEquals(4097, source.utf16IndexAtUtf8Offset(4102));
    assertEquals(new Utf16Position(0, 4097), source.utf16PositionAt(source.positionAt(4097)));
  }

  @Test
  void rejectsInvalidUtf8AndUtf16Coordinates() {
    var source = new SourceText("位置.bsb", "あ𠮷\r\n次", 3);

    assertThrows(IllegalArgumentException.class, () -> source.utf16IndexAtUtf8Offset(2));
    assertThrows(IllegalArgumentException.class, () -> source.utf16IndexAtUtf8Offset(4));
    assertThrows(IllegalArgumentException.class, () -> source.utf16IndexAtUtf8Offset(14));
    assertThrows(
        IllegalArgumentException.class, () -> source.utf16IndexAt(new Utf16Position(0, 2)));
    assertThrows(
        IllegalArgumentException.class, () -> source.utf16IndexAt(new Utf16Position(0, 4)));
    assertThrows(
        IllegalArgumentException.class, () -> source.utf16IndexAt(new Utf16Position(2, 0)));

    SourcePosition insideCrLf = source.positionAt(4);
    assertThrows(IllegalArgumentException.class, () -> source.utf16PositionAt(insideCrLf));
  }

  @Test
  void utf16ValueTypesRejectInvalidCoordinatesAndRanges() {
    assertThrows(IllegalArgumentException.class, () -> new Utf16Position(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> new Utf16Position(0, -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Utf16Range(new Utf16Position(1, 0), new Utf16Position(0, 1)));
  }
}
