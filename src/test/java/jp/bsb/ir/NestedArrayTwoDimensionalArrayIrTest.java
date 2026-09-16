package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

/** 検査済みの行型がBuildArrayと配列loop IRで保たれることを検証します。 */
class NestedArrayTwoDimensionalArrayIrTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void lowersNestedLiteralsWithTheirConcreteRowType() throws IOException {
    IrProgram program = generate("NARRAY-N-ragged.bsb");
    List<BuildArray> arrays =
        program.mainWord().instructions().stream()
            .filter(BuildArray.class::isInstance)
            .map(BuildArray.class::cast)
            .toList();

    assertTrue(arrays.stream().anyMatch(array -> array.elementType().equals(ValueType.INTEGER)));
    assertTrue(
        arrays.stream()
            .anyMatch(
                array ->
                    array.elementType().equals(ValueType.arrayOf(ValueType.INTEGER))
                        && array.elementCount() == 3));
  }

  @Test
  void lowersBothLevelsOfNestedArrayLoops() throws IOException {
    IrProgram program = generate("NARRAY-N-loops.bsb");

    assertEquals(
        2,
        program.mainWord().instructions().stream()
            .filter(ArrayLoopStart.class::isInstance)
            .count());
    assertEquals(
        2,
        program.mainWord().instructions().stream().filter(ArrayLoopNext.class::isInstance).count());
  }

  @Test
  void acceptsRowAndWrapperBuildsButRejectsTwoDimensionalElementTypes() {
    ValueType rowType = ValueType.arrayOf(ValueType.INTEGER);
    ValueType matrixType = ValueType.arrayOf(rowType);

    assertEquals(rowType, new BuildArray(rowType, 0, SPAN).elementType());
    assertEquals(
        ValueType.optionalOf(ValueType.INTEGER),
        new BuildArray(ValueType.optionalOf(ValueType.INTEGER), 0, SPAN).elementType());
    assertEquals(
        ValueType.resultOf(ValueType.INTEGER, ValueType.STRING),
        new BuildArray(ValueType.resultOf(ValueType.INTEGER, ValueType.STRING), 0, SPAN)
            .elementType());
    assertThrows(IllegalArgumentException.class, () -> new BuildArray(matrixType, 0, SPAN));
  }

  private static IrProgram generate(String sourceName) throws IOException {
    String resource = "/conformance/nested-arrays/sources/" + sourceName;
    try (var input = NestedArrayTwoDimensionalArrayIrTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      var checked = new SourceChecker().check(sourceName, input.readAllBytes());
      assertTrue(checked.successful(), checked.diagnostics().toString());
      return new IrGenerator().generate(checked.programForIrGeneration()).programForExecution();
    }
  }
}
