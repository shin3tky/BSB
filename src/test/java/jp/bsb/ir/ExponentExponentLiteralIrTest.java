package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.runtime.DecimalValue;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ExponentExponentLiteralIrTest {
  @Test
  void lowersExponentLexemesFromBoundedComponentsToDecimalConstants() {
    String source =
        "メインとは （--）\n"
            + "    1e2 を 一行表示する\n"
            + "    1E-2 を 一行表示する\n"
            + "    1.500e3 を 一行表示する\n"
            + "    -0e10 を 一行表示する\n"
            + "    1e002 を 一行表示する\n"
            + "    100e-2 を 一行表示する\n"
            + "こと。\n";
    var analysis =
        new SourceChecker().check("exponent-ir.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());

    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    List<DecimalValue> values =
        program.mainWord().instructions().stream()
            .filter(PushConst.class::isInstance)
            .map(PushConst.class::cast)
            .map(PushConst::value)
            .map(value -> assertInstanceOf(DecimalValue.class, value))
            .toList();

    assertEquals(
        List.of("100.0", "0.01", "1500.0", "0.0", "100.0", "1.0"),
        values.stream().map(DecimalValue::displayText).toList());
    assertTrue(values.stream().allMatch(value -> value.type() == ValueType.DECIMAL));
  }
}
