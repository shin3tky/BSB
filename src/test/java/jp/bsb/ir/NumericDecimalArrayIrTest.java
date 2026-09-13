package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

class NumericDecimalArrayIrTest {
  @Test
  void lowersDecimalLiteralsAndTheTypedEmptyValueToConcreteBuildArrayInstructions()
      throws Exception {
    IrProgram program = ir("NUM-N014");
    List<BuildArray> arrays =
        program.mainWord().instructions().stream()
            .filter(BuildArray.class::isInstance)
            .map(BuildArray.class::cast)
            .toList();

    assertEquals(List.of(3, 0), arrays.stream().map(BuildArray::elementCount).toList());
    assertTrue(arrays.stream().allMatch(array -> array.elementType() == ScalarType.DECIMAL));
  }

  @Test
  void lowersPureNumericElementExpressionsBeforeTheDecimalArrayBuild() throws Exception {
    IrProgram program = ir("NUM-N017");
    List<BuildArray> arrays =
        program.mainWord().instructions().stream()
            .filter(BuildArray.class::isInstance)
            .map(BuildArray.class::cast)
            .toList();

    assertEquals(1, arrays.size());
    assertEquals(ScalarType.DECIMAL, arrays.getFirst().elementType());
    assertEquals(2, arrays.getFirst().elementCount());
    assertTrue(
        program.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .map(Call::targetName)
            .toList()
            .containsAll(List.of("小数に変換する", "足す")));
  }

  private static IrProgram ir(String caseId) throws Exception {
    byte[] source = numericsSource(caseId);
    var analysis = new SourceChecker().check(caseId + ".bsb", source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
  }

  private static byte[] numericsSource(String caseId) throws Exception {
    String resource = "/conformance/numerics/sources/" + caseId + ".bsb";
    try (var input = NumericDecimalArrayIrTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
