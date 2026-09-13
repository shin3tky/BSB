package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.IrStorageSlot;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ArrayValueTypeModelTest {
  @Test
  void carriesOneStructuralArrayTypeThroughEverySharedTypeHolder() {
    var arrayType = ValueType.arrayOf(ValueType.INTEGER);
    var stack = AbstractStack.ofTypes(List.of(arrayType), spanAt(0));
    var signature = new WordSignature(List.of(arrayType), List.of(arrayType));
    var bindingType = BindingTypeState.inferred(arrayType);
    var storageSlot =
        new IrStorageSlot(
            new BindingId(1),
            "数列",
            BindingKind.CONSTANT,
            BindingStorage.GLOBAL,
            arrayType,
            0,
            Optional.empty());

    assertEquals(List.of(ValueType.arrayOf(ValueType.INTEGER)), stack.types());
    assertEquals(List.of(ValueType.arrayOf(ValueType.INTEGER)), signature.inputTypes());
    assertEquals(List.of(ValueType.arrayOf(ValueType.INTEGER)), signature.outputTypes());
    assertEquals(Optional.of(ValueType.arrayOf(ValueType.INTEGER)), bindingType.type());
    assertEquals(ValueType.arrayOf(ValueType.INTEGER), storageSlot.valueType());
  }

  @Test
  void comparesArrayStackShapesByValueInsteadOfJavaIdentity() {
    AbstractStack left =
        AbstractStack.ofTypes(
            List.of(ValueType.arrayOf(ValueType.INTEGER), ValueType.STRING), spanAt(0));
    AbstractStack sameShape =
        AbstractStack.ofTypes(
            List.of(ValueType.arrayOf(ValueType.INTEGER), ValueType.STRING), spanAt(10));
    AbstractStack differentElementType =
        AbstractStack.ofTypes(
            List.of(ValueType.arrayOf(ValueType.STRING), ValueType.STRING), spanAt(20));

    assertTrue(left.hasSameShape(sameShape));
    assertTrue(left.firstTypeMismatch(sameShape).isEmpty());
    assertEquals(0, left.firstTypeMismatch(differentElementType).orElseThrow());
  }

  private static SourceSpan spanAt(long offset) {
    var position = new SourcePosition(offset, 1, Math.toIntExact(offset) + 1);
    return new SourceSpan(position, position);
  }
}
