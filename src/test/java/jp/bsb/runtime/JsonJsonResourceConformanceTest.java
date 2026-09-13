package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.bsb.conformance.JsonConformanceData;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonArray;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonDecimal;
import jp.bsb.json.JsonInteger;
import jp.bsb.json.JsonLimits;
import jp.bsb.json.JsonMember;
import jp.bsb.json.JsonNull;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonParseErrorKind;
import jp.bsb.json.JsonParseException;
import jp.bsb.json.JsonString;
import jp.bsb.json.JsonValue;
import jp.bsb.json.JsonWriteException;
import org.junit.jupiter.api.Test;

class JsonJsonResourceConformanceTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void independentResourceTableUsesEveryNormativeLimitAndStableCode() throws Exception {
    Map<String, Long> expectedLimits =
        Map.ofEntries(
            Map.entry("JSON-R001", JsonLimits.INPUT_UTF8_BYTES),
            Map.entry("JSON-R002", JsonLimits.OUTPUT_UTF8_BYTES),
            Map.entry("JSON-R003", (long) JsonLimits.DEPTH),
            Map.entry("JSON-R004", (long) JsonLimits.ARRAY_LENGTH),
            Map.entry("JSON-R005", (long) JsonLimits.OBJECT_MEMBERS),
            Map.entry("JSON-R006", JsonLimits.VALUE_NODES),
            Map.entry("JSON-R007", (long) JsonLimits.INPUT_NUMBER_DIGITS),
            Map.entry("JSON-R008", (long) JsonLimits.INTEGER_DIGITS),
            Map.entry("JSON-R009", (long) JsonLimits.DECIMAL_PRECISION),
            Map.entry("JSON-R010", (long) JsonLimits.DECIMAL_ABSOLUTE_SCALE),
            Map.entry("JSON-R011", JsonLimits.CONSTRUCTION_UNITS),
            Map.entry("JSON-R012", JsonLimits.WORK_UNITS));
    var resources = JsonConformanceData.loadResources();

    for (var entry : expectedLimits.entrySet()) {
      List<JsonConformanceData.ResourceSpec> rows =
          resources.stream().filter(row -> row.id().equals(entry.getKey())).toList();
      assertEquals(2, rows.size(), entry.getKey());
      assertTrue(rows.stream().allMatch(row -> row.limit() == entry.getValue()), entry.getKey());
    }
    assertTrue(
        resources.stream()
            .filter(row -> row.outcome().equals("failure"))
            .allMatch(row -> !row.code().equals("-")));
  }

  @Test
  void jsonR001AndJsonR002AcceptExactUtf8LengthsAndRejectOneMoreBeforeOutput() throws Exception {
    String exactText = "a".repeat((int) JsonLimits.INPUT_UTF8_BYTES - 2);
    String exactDocument = '"' + exactText + '"';
    assertEquals(JsonLimits.INPUT_UTF8_BYTES, exactDocument.length());
    JsonValue parsed = JsonCodec.parse(exactDocument);
    assertEquals(JsonLimits.OUTPUT_UTF8_BYTES, JsonCodec.serialize(parsed).length());

    String overInput = '"' + "a".repeat((int) JsonLimits.INPUT_UTF8_BYTES - 1) + '"';
    JsonParseException inputFailure =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse(overInput));
    assertEquals(JsonParseErrorKind.INPUT_LIMIT, inputFailure.kind());

    var overOutput = new JsonString("\0".repeat((int) (JsonLimits.OUTPUT_UTF8_BYTES / 6 + 1)));
    JsonWriteException outputFailure =
        assertThrows(JsonWriteException.class, () -> JsonCodec.serialize(overOutput));
    assertEquals(JsonLimits.OUTPUT_UTF8_BYTES, outputFailure.limit());
    assertTrue(outputFailure.observed() > outputFailure.limit());
  }

  @Test
  void jsonR003ThroughJsonR006CoverDepthWidthMembersAndTotalNodesAtExactAndOver() {
    JsonValue nested = JsonNull.INSTANCE;
    for (int depth = 0; depth < JsonLimits.DEPTH; depth++) {
      nested = new JsonArray(List.of(nested));
    }
    JsonValue maximumDepth = nested;
    assertEquals(JsonLimits.DEPTH, maximumDepth.metrics().depth());
    assertThrows(IllegalArgumentException.class, () -> new JsonArray(List.of(maximumDepth)));

    var maximumArray =
        new JsonArray(Collections.nCopies(JsonLimits.ARRAY_LENGTH, JsonNull.INSTANCE));
    assertEquals(JsonLimits.ARRAY_LENGTH, maximumArray.size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new JsonArray(Collections.nCopies(JsonLimits.ARRAY_LENGTH + 1, JsonNull.INSTANCE)));

    List<JsonMember> members = members(JsonLimits.OBJECT_MEMBERS);
    assertEquals(JsonLimits.OBJECT_MEMBERS, new JsonObject(members).size());
    var overMembers = new ArrayList<>(members);
    overMembers.add(new JsonMember("over", JsonNull.INSTANCE));
    assertThrows(IllegalArgumentException.class, () -> new JsonObject(overMembers));

    JsonArray maximumNodes = nodes(249_995);
    assertEquals(JsonLimits.VALUE_NODES, maximumNodes.metrics().nodeCount());
    assertThrows(IllegalArgumentException.class, () -> nodes(249_996));
  }

  @Test
  void jsonR007ThroughJsonR010CoverEveryNumericBoundaryBeforeOversizedValuesEscape()
      throws Exception {
    String inputDigits = "9".repeat(JsonLimits.INPUT_NUMBER_DIGITS);
    assertEquals(new BigInteger(inputDigits), ((JsonInteger) JsonCodec.parse(inputDigits)).value());
    JsonParseException inputFailure =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse(inputDigits + '9'));
    assertEquals(JsonParseErrorKind.NUMBER_LIMIT, inputFailure.kind());

    new JsonInteger(new BigInteger("9".repeat(JsonLimits.INTEGER_DIGITS)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JsonInteger(new BigInteger("9".repeat(JsonLimits.INTEGER_DIGITS + 1))));

    new JsonDecimal(new BigDecimal("9".repeat(JsonLimits.DECIMAL_PRECISION) + ".0"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JsonDecimal(new BigDecimal("9".repeat(JsonLimits.DECIMAL_PRECISION + 1) + ".0")));

    new JsonDecimal(BigDecimal.ONE.scaleByPowerOfTen(-JsonLimits.DECIMAL_ABSOLUTE_SCALE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JsonDecimal(
                BigDecimal.ONE.scaleByPowerOfTen(-JsonLimits.DECIMAL_ABSOLUTE_SCALE - 1)));
  }

  @Test
  void jsonR011AndJsonR012ApplyCumulativeBudgetBoundariesAtomically() throws Exception {
    var exact = new ExecutionBudget("R9", () -> 0L);
    exact.beforeJsonWork(
        JsonLimits.CONSTRUCTION_UNITS, JsonLimits.WORK_UNITS, SPAN, "exact-boundary");
    assertEquals(JsonLimits.CONSTRUCTION_UNITS, exact.jsonConstructionUnits());
    assertEquals(JsonLimits.WORK_UNITS, exact.jsonWorkUnits());

    RuntimeFailure construction =
        assertThrows(
            RuntimeFailure.class, () -> exact.beforeJsonWork(1, 0, SPAN, "construction-over"));
    assertEquals(DiagnosticCode.E_JSON_CONSTRUCTION_LIMIT, construction.diagnostic().code());
    assertEquals(JsonLimits.CONSTRUCTION_UNITS, exact.jsonConstructionUnits());
    assertEquals(JsonLimits.WORK_UNITS, exact.jsonWorkUnits());

    var work = new ExecutionBudget("R9", () -> 0L, 0, 0, 0, 0, 0, 0, JsonLimits.WORK_UNITS);
    RuntimeFailure workFailure =
        assertThrows(RuntimeFailure.class, () -> work.beforeJsonWork(0, 1, SPAN, "work-over"));
    assertEquals(DiagnosticCode.E_JSON_WORK_LIMIT, workFailure.diagnostic().code());
    assertEquals(0, work.jsonConstructionUnits());
    assertEquals(JsonLimits.WORK_UNITS, work.jsonWorkUnits());
  }

  @Test
  void jsonR013ThroughJsonR018KeepDeepMixedValuesDeterministicAndTraceFreeOfContent()
      throws Exception {
    JsonValue value = new JsonDecimal(new BigDecimal("1.2300"));
    for (int depth = 0; depth < 200; depth++) {
      value = new JsonArray(List.of(value, new JsonString("secret" + depth)));
    }
    String first = JsonCodec.serialize(value);
    String second = JsonCodec.serialize(JsonCodec.parse(first));
    assertEquals(first, second);
    assertEquals(value, JsonCodec.parse(first));

    var wrapped = new JsonRuntimeValue(value);
    assertEquals("JSON:<redacted>", TraceValueFormatter.format(wrapped, TraceValuePolicy.json()));
    assertEquals(
        "配列<JSON>:<redacted>",
        TraceValueFormatter.format(
            new ArrayValue(jp.bsb.stdlib.ScalarType.JSON, List.of(wrapped)),
            TraceValuePolicy.json()));
  }

  @Test
  void rejectsIncompleteOrMismatchedSyntheticValuesAsInternalContractViolations() {
    assertThrows(NullPointerException.class, () -> new JsonRuntimeValue(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArrayValue(jp.bsb.stdlib.ScalarType.JSON, List.of(new StringValue("not-json"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JsonObject(
                List.of(
                    new JsonMember("same", JsonNull.INSTANCE),
                    new JsonMember("same", new JsonString("duplicate")))));
  }

  private static List<JsonMember> members(int size) {
    return java.util.stream.IntStream.range(0, size)
        .mapToObj(index -> new JsonMember(Integer.toString(index), JsonNull.INSTANCE))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static JsonArray nodes(int scalarNodes) {
    int base = scalarNodes / 4;
    int remainder = scalarNodes % 4;
    var groups = new ArrayList<JsonValue>();
    for (int group = 0; group < 4; group++) {
      int size = base + (group < remainder ? 1 : 0);
      groups.add(new JsonArray(Collections.nCopies(size, JsonNull.INSTANCE)));
    }
    return new JsonArray(groups);
  }
}
