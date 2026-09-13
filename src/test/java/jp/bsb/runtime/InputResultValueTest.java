package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class InputResultValueTest {
  @Test
  void lineEndAndCancelRemainDistinctWithoutTraceDisclosure() {
    InputResultValue line = InputResultValue.line("秘密");

    assertEquals(InputResultValue.State.LINE, line.state());
    assertEquals("秘密", line.line().orElseThrow());
    assertFalse(line.traceText().contains("秘密"));
    assertEquals(InputResultValue.State.END, InputResultValue.end().state());
    assertEquals(InputResultValue.State.CANCEL, InputResultValue.cancel().state());
  }

  @Test
  void inputResultIsConcreteButNotDisplayableOrArrayEligible() {
    assertEquals(ValueType.INPUT_RESULT, InputResultValue.end().type());
    assertTrue(ValueType.fromSourceName("入力結果").isPresent());
    assertFalse(ValueType.INPUT_RESULT.isArrayElementType());
    assertFalse(ValueType.arrayElementTypes().contains(ValueType.INPUT_RESULT));
    assertThrows(IllegalArgumentException.class, () -> ValueType.arrayOf(ValueType.INPUT_RESULT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArrayValue(ValueType.STRING, List.of(InputResultValue.end())));
    assertFalse(TraceValuePolicy.textRegex().mayReveal(InputResultValue.line("秘密")));
  }
}
