package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import jp.bsb.conformance.JsonConformanceData;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonParseException;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** JSON-F001〜016のcodec reasonを、JSON-F017〜028のCLI診断証跡と分離して検証します。 */
class JsonRuntimeDiagnosticConformanceTest {
  private static final String SOURCE_PATH = "JSON-F-codec.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(10, 2, 5), new SourcePosition(20, 2, 15));

  @ParameterizedTest(name = "{0}")
  @MethodSource("codecFailures")
  void mapsEveryCodecFailureToAnEnumeratedReasonAndPreservesTheInput(
      JsonConformanceData.CodecFailureSpec spec) throws Exception {
    String input = JsonConformanceData.materializeFailureInput(spec.input());
    JsonParseException codecFailure =
        assertThrows(JsonParseException.class, () -> JsonCodec.parse(input), spec.id());
    var stack = new ArrayList<RuntimeValue>(List.of(new StringValue(input)));
    List<RuntimeValue> before = List.copyOf(stack);
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var output = new MemoryOutputSink();
    JsonConformanceData.DiagnosticSpec expected =
        JsonConformanceData.loadDiagnostics().stream()
            .filter(row -> row.id().equals(spec.id()))
            .findFirst()
            .orElseThrow();

    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, output), budget)
                    .execute(BuiltinDictionary.find("JSONを解析する").orElseThrow(), stack, SPAN));
    var diagnostic = failure.diagnostic();

    assertEquals("codec", expected.kind(), spec.id());
    assertEquals(DiagnosticCode.valueOf(expected.code()), diagnostic.code(), spec.id());
    assertEquals(SPAN, diagnostic.location(), spec.id());
    assertTrue(List.of(expected.reason().split(",", -1)).contains(spec.reason()), spec.id());
    assertEquals(spec.reason(), diagnostic.fields().get("reason"), spec.id());
    assertEquals(
        Long.toString(codecFailure.utf8Offset()),
        diagnostic.fields().get("jsonUtf8Offset"),
        spec.id());
    assertEquals(
        Integer.toString(codecFailure.line()), diagnostic.fields().get("jsonLine"), spec.id());
    assertEquals(
        Integer.toString(codecFailure.column()), diagnostic.fields().get("jsonColumn"), spec.id());
    assertTrue(diagnostic.expected().isPresent(), spec.id());
    assertTrue(diagnostic.actual().isPresent(), spec.id());
    assertFalse(diagnostic.fixes().isEmpty(), spec.id());
    assertEquals(before, stack, spec.id());
    assertEquals("", output.utf8Text(), spec.id());
    assertEquals(0, budget.jsonConstructionUnits(), spec.id());
    assertTrue(input.isEmpty() || budget.jsonWorkUnits() > 0, spec.id());
  }

  static Stream<JsonConformanceData.CodecFailureSpec> codecFailures() throws Exception {
    return JsonConformanceData.loadFailureCorpus().stream();
  }
}
