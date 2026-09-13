package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.BuildArray;
import jp.bsb.ir.IrStorageSlot;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class NumericValueTypeModelTest {
  @Test
  void carriesEveryNumericFeatureTypeThroughSharedStaticHolders() {
    var decimalArray = ValueType.arrayOf(ValueType.DECIMAL);
    List<ValueType> types = List.of(ValueType.DECIMAL, ValueType.ROUNDING_MODE, decimalArray);
    var stack = AbstractStack.ofTypes(types, spanAt(0));
    var signature = new WordSignature(types, types);
    var bindingType = BindingTypeState.inferred(ValueType.DECIMAL);
    var storageSlot =
        new IrStorageSlot(
            new BindingId(1),
            "金額",
            BindingKind.CONSTANT,
            BindingStorage.GLOBAL,
            ValueType.DECIMAL,
            0,
            Optional.empty());

    assertEquals(types, stack.types());
    assertEquals(types, signature.inputTypes());
    assertEquals(types, signature.outputTypes());
    assertEquals(Optional.of(ValueType.DECIMAL), bindingType.type());
    assertEquals(ValueType.DECIMAL, storageSlot.valueType());
  }

  @Test
  void resolvesNumericFeatureDeclarationTypesAndDecimalLiterals() {
    AnalysisResult declared =
        AnalyzerTestSupport.checkText(
            "保持とは （小数 丸め方法 配列<小数> -- 小数 丸め方法 配列<小数>）\n" + "こと。\n\n" + "メインとは （--）\n" + "こと。\n");
    AnalysisResult literal = AnalyzerTestSupport.checkText("メインとは （--）\n    1.0 を 一行表示する\nこと。\n");

    assertTrue(declared.successful(), declared.diagnostics().toString());
    WordSignature signature = declared.programForIrGeneration().findUserWord("保持").orElseThrow();
    assertEquals(
        List.of(ValueType.DECIMAL, ValueType.ROUNDING_MODE, ValueType.arrayOf(ValueType.DECIMAL)),
        signature.inputTypes());
    assertEquals(signature.inputTypes(), signature.outputTypes());
    assertTrue(literal.successful(), literal.diagnostics().toString());
  }

  @Test
  void rejectsRoundingModesAtEveryArrayConstructionBoundary() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BuildArray(ValueType.ROUNDING_MODE, 0, spanAt(0)));
  }

  private static SourceSpan spanAt(long offset) {
    var position = new SourcePosition(offset, 1, Math.toIntExact(offset) + 1);
    return new SourceSpan(position, position);
  }
}
