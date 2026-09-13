package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.runtime.DateTimeValue;
import jp.bsb.runtime.InputResultValue;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class HostIoInputResultIrTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void rejectsSyntheticDisplayAndArrayTypeMismatchesBeforeExecution() {
    var mainSymbol = new SymbolId(0);
    var builtinSymbol = new SymbolId(1);
    var display = BuiltinDictionary.find("一行表示する").orElseThrow();
    var invalidEffect = new IrStackEffect(List.of(ValueType.INPUT_RESULT), List.of());
    var main =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(
                new PushConst(InputResultValue.end(), SPAN),
                new Call(builtinSymbol, display.canonicalName(), List.of(), invalidEffect, SPAN),
                new Return(SPAN)),
            List.of(),
            new IrStackEffect(List.of(), List.of()));

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrProgram(
                    "r8-011.bsb",
                    mainSymbol,
                    Map.of(mainSymbol, main),
                    Map.of(builtinSymbol, display),
                    3));

    assertEquals("Call effect differs from its builtin target", failure.getMessage());
    assertThrows(
        IllegalArgumentException.class, () -> new BuildArray(ValueType.INPUT_RESULT, 0, SPAN));
  }

  @Test
  void rejectsSyntheticDateTimeDisplayEqualityAndArrayEffectsBeforeExecution() {
    var mainSymbol = new SymbolId(0);
    var builtinSymbol = new SymbolId(1);
    var display = BuiltinDictionary.find("一行表示する").orElseThrow();
    var main =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(
                new PushConst(new DateTimeValue(0, 0), SPAN),
                new Call(
                    builtinSymbol,
                    display.canonicalName(),
                    List.of(),
                    new IrStackEffect(List.of(ValueType.DATE_TIME), List.of()),
                    SPAN),
                new Return(SPAN)),
            List.of(),
            new IrStackEffect(List.of(), List.of()));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrProgram(
                "r8-011-datetime.bsb",
                mainSymbol,
                Map.of(mainSymbol, main),
                Map.of(builtinSymbol, display),
                3));
    assertThrows(
        IllegalArgumentException.class, () -> new BuildArray(ValueType.DATE_TIME, 0, SPAN));
  }
}
