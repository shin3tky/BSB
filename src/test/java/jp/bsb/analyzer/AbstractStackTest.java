package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class AbstractStackTest {
  @Test
  void createsNewStacksWithoutChangingTheSharedInputStack() {
    AbstractStack entry = AbstractStack.empty().push(ValueType.INTEGER, spanAt(0));

    // 2本の枝は同じ entry を共有して開始できます。push は元のオブジェクトを変更しません。
    AbstractStack thenStack = entry.push(ValueType.BOOLEAN, spanAt(1));
    AbstractStack elseStack = entry.push(ValueType.STRING, spanAt(2));

    assertEquals(List.of(ValueType.INTEGER), entry.types());
    assertEquals(List.of(ValueType.INTEGER, ValueType.BOOLEAN), thenStack.types());
    assertEquals(List.of(ValueType.INTEGER, ValueType.STRING), elseStack.types());
    assertEquals(entry.types(), thenStack.removeTop(1).types());
  }

  @Test
  void comparesTypesButNotTheirSourceOrigins() {
    AbstractStack left =
        AbstractStack.ofTypes(List.of(ValueType.INTEGER, ValueType.BOOLEAN), spanAt(0));
    AbstractStack sameTypes =
        AbstractStack.ofTypes(List.of(ValueType.INTEGER, ValueType.BOOLEAN), spanAt(10));
    AbstractStack differentType =
        AbstractStack.ofTypes(List.of(ValueType.INTEGER, ValueType.STRING), spanAt(20));

    assertTrue(left.hasSameShape(sameTypes));
    assertEquals(1, left.firstTypeMismatch(differentType).orElseThrow());
  }

  @Test
  void rejectsRemovingMoreValuesThanExist() {
    AbstractStack stack = AbstractStack.empty().push(ValueType.INTEGER, spanAt(0));

    assertThrows(IllegalArgumentException.class, () -> stack.removeTop(2));
  }

  private static SourceSpan spanAt(long offset) {
    var position = new SourcePosition(offset, 1, Math.toIntExact(offset) + 1);
    return new SourceSpan(position, position);
  }
}
