package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class RoundingModeValueTest {
  @Test
  void exposesExactlyTheFiveNormativeBsbValuesInSpecificationOrder() {
    assertEquals(
        List.of("最近接偶数丸め", "四捨五入", "0方向へ丸め", "正方向へ丸め", "負方向へ丸め"),
        List.of(RoundingModeValue.values()).stream().map(RoundingModeValue::sourceName).toList());
    for (RoundingModeValue value : RoundingModeValue.values()) {
      assertEquals(ValueType.ROUNDING_MODE, value.type());
      assertEquals(value.sourceName(), value.displayText());
      assertEquals(value.sourceName(), value.toString());
    }
  }

  @Test
  void resolvesOnlyCanonicalNamesAndKeepsValuesDistinct() {
    assertEquals(
        Optional.of(RoundingModeValue.NEAREST_EVEN), RoundingModeValue.fromSourceName("最近接偶数丸め"));
    assertEquals(
        Optional.of(RoundingModeValue.NEAREST_AWAY_FROM_ZERO),
        RoundingModeValue.fromSourceName("四捨五入"));
    assertTrue(RoundingModeValue.fromSourceName(null).isEmpty());
    assertTrue(RoundingModeValue.fromSourceName("HALF_EVEN").isEmpty());
    assertNotEquals(RoundingModeValue.NEAREST_EVEN, RoundingModeValue.NEAREST_AWAY_FROM_ZERO);
  }
}
