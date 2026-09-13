package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.runtime.RoundingModeValue;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class NumericNumericBuiltinIrTest {
  @Test
  void storesOnlyConcreteNumericEffectsOnCalls() {
    IrProgram program =
        ir(
            "メインとは （--）\n"
                + "    1.0 と 2.0 を 足す を 一行表示する\n"
                + "    1 と 2.0 を 小数で割る を 一行表示する\n"
                + "    1.0 と 2 と 3 と 最近接偶数丸め で 精度指定で割る を 一行表示する\n"
                + "こと。\n");
    Map<String, IrStackEffect> effects =
        program.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .collect(
                java.util.stream.Collectors.toMap(
                    Call::targetName,
                    call -> call.stackEffect().orElseThrow(),
                    (first, ignored) -> first,
                    java.util.LinkedHashMap::new));

    assertEquals(
        new IrStackEffect(
            List.of(ValueType.DECIMAL, ValueType.DECIMAL), List.of(ValueType.DECIMAL)),
        effects.get("足す"));
    assertEquals(
        new IrStackEffect(
            List.of(ValueType.INTEGER, ValueType.DECIMAL), List.of(ValueType.DECIMAL)),
        effects.get("小数で割る"));
    assertEquals(
        new IrStackEffect(
            List.of(
                ValueType.DECIMAL, ValueType.INTEGER, ValueType.INTEGER, ValueType.ROUNDING_MODE),
            List.of(ValueType.DECIMAL)),
        effects.get("精度指定で割る"));
  }

  @Test
  void lowersAllRoundingModeValuesToTypedPushConstants() throws Exception {
    IrProgram program = ir(numericsSource("NUM-N009"));
    List<RoundingModeValue> values =
        program.mainWord().instructions().stream()
            .filter(PushConst.class::isInstance)
            .map(PushConst.class::cast)
            .map(PushConst::value)
            .filter(RoundingModeValue.class::isInstance)
            .map(RoundingModeValue.class::cast)
            .toList();

    assertEquals(
        List.of(
            RoundingModeValue.NEAREST_EVEN,
            RoundingModeValue.NEAREST_EVEN,
            RoundingModeValue.NEAREST_AWAY_FROM_ZERO,
            RoundingModeValue.TOWARD_ZERO,
            RoundingModeValue.TOWARD_POSITIVE_INFINITY,
            RoundingModeValue.TOWARD_NEGATIVE_INFINITY),
        values);
    assertFalse(
        program.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .anyMatch(call -> RoundingModeValue.fromSourceName(call.targetName()).isPresent()));
  }

  @Test
  void verifierRejectsMixedConcreteTypesForAnNCall() {
    IrProgram valid = ir("メインとは （--）\n    1.0 と 2.0 を 足す を 一行表示する\nこと。\n");
    Call add =
        valid.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().equals("足す"))
            .findFirst()
            .orElseThrow();
    var invalidInstructions = new ArrayList<IrInstruction>(valid.mainWord().instructions());
    invalidInstructions.set(
        invalidInstructions.indexOf(add),
        new Call(
            add.symbolId(),
            add.targetName(),
            add.particles(),
            new IrStackEffect(
                List.of(ValueType.INTEGER, ValueType.DECIMAL), List.of(ValueType.DECIMAL)),
            add.span()));
    IrWord invalidMain =
        new IrWord(
            valid.mainSymbol(),
            valid.mainWord().name(),
            invalidInstructions,
            valid.mainWord().localSlots(),
            valid.mainWord().stackEffect().orElseThrow());

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrProgram(
                    valid.sourcePath(),
                    valid.mainSymbol(),
                    Map.of(valid.mainSymbol(), invalidMain),
                    valid.builtinWords(),
                    valid.instructionCount()));
    assertEquals("Call effect differs from its builtin target", failure.getMessage());
  }

  @Test
  void verifierRejectsANonNumericConcreteTypeForAnIndependentNumericInput() {
    IrProgram valid = ir("メインとは （--）\n    1 と 2.0 を 小数で割る を 一行表示する\nこと。\n");
    Call divide =
        valid.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().equals("小数で割る"))
            .findFirst()
            .orElseThrow();
    var invalidInstructions = new ArrayList<IrInstruction>(valid.mainWord().instructions());
    invalidInstructions.set(
        invalidInstructions.indexOf(divide),
        new Call(
            divide.symbolId(),
            divide.targetName(),
            divide.particles(),
            new IrStackEffect(
                List.of(ValueType.STRING, ValueType.DECIMAL), List.of(ValueType.DECIMAL)),
            divide.span()));
    IrWord invalidMain =
        new IrWord(
            valid.mainSymbol(),
            valid.mainWord().name(),
            invalidInstructions,
            valid.mainWord().localSlots(),
            valid.mainWord().stackEffect().orElseThrow());

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrProgram(
                    valid.sourcePath(),
                    valid.mainSymbol(),
                    Map.of(valid.mainSymbol(), invalidMain),
                    valid.builtinWords(),
                    valid.instructionCount()));
    assertEquals("Call effect differs from its builtin target", failure.getMessage());
  }

  @Test
  void roundingModeValuesAlreadyRunThroughExistingDisplayAndEqualityOperations() throws Exception {
    IrProgram program = ir(numericsSource("NUM-N013"));

    assertTrue(program.mainWord().instructions().stream().anyMatch(PushConst.class::isInstance));
    PushConst first =
        assertInstanceOf(
            PushConst.class,
            program.mainWord().instructions().stream()
                .filter(PushConst.class::isInstance)
                .findFirst()
                .orElseThrow());
    assertEquals(ValueType.ROUNDING_MODE, first.value().type());
  }

  private static IrProgram ir(String source) {
    return ir(source.getBytes(StandardCharsets.UTF_8));
  }

  private static IrProgram ir(byte[] source) {
    var analysis = new SourceChecker().check("numeric-builtins.bsb", source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
  }

  private static byte[] numericsSource(String caseId) throws Exception {
    String resource = "/conformance/numerics/sources/" + caseId + ".bsb";
    try (var input = NumericNumericBuiltinIrTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
