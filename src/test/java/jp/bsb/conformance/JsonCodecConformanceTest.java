package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonParseErrorKind;
import jp.bsb.json.JsonParseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JsonCodecConformanceTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("normalCases")
  void parsesAndSerializesEveryIndependentNormalCorpusCase(JsonConformanceData.CodecNormalSpec spec)
      throws Exception {
    String[] inputs = expand(spec.input()).split("\\|", -1);
    String[] expected = expand(spec.expected()).split("\\|", -1);
    assertEquals(expected.length, inputs.length, spec.id());

    for (int index = 0; index < inputs.length; index++) {
      assertEquals(expected[index], JsonCodec.serialize(JsonCodec.parse(inputs[index])), spec.id());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("failureCases")
  void rejectsEveryIndependentFailureCorpusCaseWithItsStableKindAndReason(
      JsonConformanceData.CodecFailureSpec spec) throws Exception {
    JsonParseException failure =
        assertThrows(
            JsonParseException.class,
            () -> JsonCodec.parse(JsonConformanceData.materializeFailureInput(spec.input())),
            spec.id());
    JsonConformanceData.DiagnosticSpec diagnostic = diagnostics().get(spec.id());

    assertEquals(spec.reason(), failure.reason(), spec.id());
    assertEquals(expectedKind(diagnostic.code()), failure.kind(), spec.id());
    org.junit.jupiter.api.Assertions.assertTrue(failure.utf8Offset() >= 0, spec.id());
    org.junit.jupiter.api.Assertions.assertTrue(failure.line() >= 1, spec.id());
    org.junit.jupiter.api.Assertions.assertTrue(failure.column() >= 1, spec.id());
  }

  static Stream<JsonConformanceData.CodecNormalSpec> normalCases() throws Exception {
    return JsonConformanceData.loadNormalCorpus().stream();
  }

  static Stream<JsonConformanceData.CodecFailureSpec> failureCases() throws Exception {
    return JsonConformanceData.loadFailureCorpus().stream();
  }

  private static Map<String, JsonConformanceData.DiagnosticSpec> diagnostics() throws Exception {
    var result = new LinkedHashMap<String, JsonConformanceData.DiagnosticSpec>();
    JsonConformanceData.loadDiagnostics().forEach(spec -> result.put(spec.id(), spec));
    return Map.copyOf(result);
  }

  private static JsonParseErrorKind expectedKind(String code) {
    return switch (code) {
      case "E_JSON_SYNTAX" -> JsonParseErrorKind.SYNTAX;
      case "E_JSON_DUPLICATE_KEY" -> JsonParseErrorKind.DUPLICATE_KEY;
      case "E_JSON_NUMBER_LIMIT" -> JsonParseErrorKind.NUMBER_LIMIT;
      case "E_JSON_DEPTH_LIMIT" -> JsonParseErrorKind.DEPTH_LIMIT;
      case "E_JSON_NODE_LIMIT" -> JsonParseErrorKind.NODE_LIMIT;
      case "E_JSON_ARRAY_LENGTH_LIMIT" -> JsonParseErrorKind.ARRAY_LENGTH_LIMIT;
      case "E_JSON_OBJECT_MEMBER_LIMIT" -> JsonParseErrorKind.OBJECT_MEMBER_LIMIT;
      default -> throw new IllegalArgumentException("not a codec diagnostic: " + code);
    };
  }

  private static String expand(String value) {
    return value
        .replace("{SP}", " ")
        .replace("{HTAB}", "\t")
        .replace("{LF}", "\n")
        .replace("{CR}", "\r");
  }
}
