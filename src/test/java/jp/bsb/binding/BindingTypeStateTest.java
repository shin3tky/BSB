package jp.bsb.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class BindingTypeStateTest {
  @Test
  void representsEveryTypeAnalysisStateWithoutNull() {
    var uninferred = BindingTypeState.uninferred();
    var inferred = BindingTypeState.inferred(ValueType.INTEGER);
    var diagnosed = BindingTypeState.diagnosed();

    assertEquals(BindingTypeState.Status.UNINFERRED, uninferred.status());
    assertTrue(uninferred.type().isEmpty());
    assertEquals(BindingTypeState.Status.INFERRED, inferred.status());
    assertEquals(Optional.of(ValueType.INTEGER), inferred.type());
    assertEquals(BindingTypeState.Status.DIAGNOSED, diagnosed.status());
    assertTrue(diagnosed.type().isEmpty());
  }

  @Test
  void rejectsInconsistentStateAndTypePairs() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BindingTypeState(
                BindingTypeState.Status.UNINFERRED, Optional.of(ValueType.INTEGER)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BindingTypeState(BindingTypeState.Status.INFERRED, Optional.empty()));
  }
}
