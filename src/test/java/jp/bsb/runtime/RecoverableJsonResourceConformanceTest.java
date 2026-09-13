package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bsb.conformance.RecoverableJsonConformanceData;
import jp.bsb.json.JsonLimits;
import org.junit.jupiter.api.Test;

class RecoverableJsonResourceConformanceTest {
  @Test
  void centralRecipesUseEveryNormativeLimitWithoutDerivingTheirIndependentValues()
      throws Exception {
    var rows = RecoverableJsonConformanceData.loadResources();
    assertEquals(
        Set.of(
            "RJSON-R001",
            "RJSON-R002",
            "RJSON-R003",
            "RJSON-R004",
            "RJSON-R005",
            "RJSON-R006",
            "RJSON-R007",
            "RJSON-R008",
            "RJSON-R009",
            "RJSON-R010",
            "RJSON-R011",
            "RJSON-R012"),
        rows.stream()
            .map(RecoverableJsonConformanceData.ResourceSpec::id)
            .collect(Collectors.toSet()));

    Map<String, Long> normativeLimits =
        Map.ofEntries(
            Map.entry("jsonNumberDigits", 4_096L),
            Map.entry("stringUtf8Bytes", 16_777_216L),
            Map.entry("jsonDepth", 256L),
            Map.entry("jsonNodes", 250_000L),
            Map.entry("jsonArrayLength", 65_536L),
            Map.entry("jsonObjectMembers", 65_536L),
            Map.entry("jsonWorkUnits", 67_108_864L),
            Map.entry("jsonConstructionUnits", 1_000_000L),
            Map.entry("executedInstructions", 10_000_000L),
            Map.entry("elapsedNanos", 30_000_000_000L),
            Map.entry("dataStackValues", 65_536L),
            Map.entry("heapMiB", 512L));
    for (var row : rows) {
      Long limit = normativeLimits.get(row.metric());
      if (limit != null) {
        assertEquals(limit.toString(), row.limit(), row.id() + '/' + row.variant());
      }
      assertTrue(
          Set.of("success", "recoverable", "diagnostic").contains(row.outcome()),
          row.id() + '/' + row.variant());
      if (row.outcome().equals("diagnostic")) {
        assertTrue(row.diagnostic().startsWith("E_"), row.id() + '/' + row.variant());
        assertTrue(row.inputPreserved(), row.id() + '/' + row.variant());
      }
    }

    assertEquals(4_096, JsonLimits.INPUT_NUMBER_DIGITS);
    assertEquals(16_777_216L, JsonLimits.INPUT_UTF8_BYTES);
    assertEquals(256, JsonLimits.DEPTH);
    assertEquals(250_000L, JsonLimits.VALUE_NODES);
    assertEquals(65_536, JsonLimits.ARRAY_LENGTH);
    assertEquals(65_536, JsonLimits.OBJECT_MEMBERS);
    assertEquals(67_108_864L, JsonLimits.WORK_UNITS);
    assertEquals(1_000_000L, JsonLimits.CONSTRUCTION_UNITS);
    assertEquals(10_000_000L, RuntimeLimits.EXECUTED_INSTRUCTIONS);
    assertEquals(30_000_000_000L, RuntimeLimits.ELAPSED_NANOS);
    assertEquals(65_536, RuntimeLimits.DATA_STACK_VALUES);
  }
}
