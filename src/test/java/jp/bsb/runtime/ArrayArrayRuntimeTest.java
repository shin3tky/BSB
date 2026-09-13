package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 配列値と標準操作を、規範ARRAY-N/ARRAY-Fソースから実行します。 */
class ArrayArrayRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @ParameterizedTest
  @MethodSource("normalOperationCases")
  void runsEveryTextFeatureArrayValueAndOperationCase(String sourceName, String expected)
      throws IOException {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(sourceName, output);

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertEquals(java.util.List.of(), result.finalDataStack(), sourceName);
    assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), output.bytes(), sourceName);
  }

  @ParameterizedTest
  @MethodSource("runtimeFailureCases")
  void reportsNormativeArrayBoundsFailuresWithoutConsumingInputs(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      int stackSize)
      throws IOException {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(sourceName, output);

    assertEquals(10, result.exitCode(), sourceName);
    assertArrayEquals(new byte[0], output.bytes(), sourceName);
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line(), sourceName);
    assertEquals(
        column, diagnostic.location().displayPosition().orElseThrow().column(), sourceName);
    fields.forEach((name, value) -> assertEquals(value, diagnostic.fields().get(name), sourceName));
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(java.util.List.of(fix), diagnostic.fixes(), sourceName);
    assertEquals(stackSize, result.finalDataStack().size(), sourceName);
    assertInstanceOf(ArrayValue.class, result.finalDataStack().getFirst(), sourceName);
  }

  @Test
  void arrayDisplayChecksTheWholeOutputBeforeConsumingTheValue() {
    var sink = new MemoryOutputSink();
    var output = new BoundedOutput("synthetic.bsb", sink, RuntimeLimits.OUTPUT_UTF8_BYTES - 1);
    var budget = new ExecutionBudget("synthetic.bsb", () -> 0L);
    ArrayValue array =
        new ArrayValue(ScalarType.INTEGER, List.of(new IntegerValue(BigInteger.ONE)));
    var stack = new ArrayList<RuntimeValue>(List.of(array));

    RuntimeFailure failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class,
            () ->
                new BuiltinExecutor("synthetic.bsb", output, budget)
                    .execute(BuiltinDictionary.find("一行表示する").orElseThrow(), stack, SPAN));

    assertEquals(DiagnosticCode.E_OUTPUT_LIMIT, failure.diagnostic().code());
    assertEquals(List.of(array), stack);
    assertArrayEquals(new byte[0], sink.bytes());
  }

  private static ProgramRunResult run(String sourceName, MemoryOutputSink output)
      throws IOException {
    return new ProgramRunner()
        .run(
            sourceName,
            resourceBytes(sourceName),
            new ExecutionContext(output, () -> 0L, TraceSink.none()));
  }

  private static Stream<Arguments> normalOperationCases() {
    return Stream.of(
        Arguments.of("ARRAY-N001.bsb", "【1、2、3】\n"),
        Arguments.of("ARRAY-N002.bsb", "【1、2】\n【はい、いいえ】\n【'A'、'字'】\n【「赤」、「青」】\n"),
        Arguments.of("ARRAY-N003.bsb", "0\n0\n0\n0\n"),
        Arguments.of("ARRAY-N004.bsb", "【10、22】\n"),
        Arguments.of("ARRAY-N005.bsb", "3\n0\n"),
        Arguments.of("ARRAY-N006.bsb", "10\n30\n"),
        Arguments.of("ARRAY-N007.bsb", "【】\n【20、30】\n【10、20、30、40】\n"),
        Arguments.of("ARRAY-N008.bsb", "【1、2、3】\n【1、9、3】\n"),
        Arguments.of("ARRAY-N009.bsb", "【1、2】\n【1、2、3】\n"),
        Arguments.of("ARRAY-N010.bsb", "はい\nいいえ\nいいえ\n"),
        Arguments.of("ARRAY-N011.bsb", "【70、85、90】\n"),
        Arguments.of("ARRAY-N012.bsb", "3\n"),
        Arguments.of("ARRAY-N018.bsb", "【70、80、90】\n"),
        Arguments.of("ARRAY-N019.bsb", "【1、2、3】\n"),
        Arguments.of("ARRAY-N020.bsb", "【'A'、'字'】\n【「赤」、「青」】\n"),
        Arguments.of("ARRAY-N021.bsb", "9\n"));
  }

  private static Stream<Arguments> runtimeFailureCases() {
    return Stream.of(
        Arguments.of(
            "ARRAY-F028.bsb",
            DiagnosticCode.E_ARRAY_INDEX_OUT_OF_BOUNDS,
            2,
            23,
            Map.of("operation", "get", "index", "-1", "length", "3", "validRange", "[0,3)"),
            "0以上3未満",
            "-1",
            "添字を0から2の範囲にしてください",
            2),
        Arguments.of(
            "ARRAY-F029.bsb",
            DiagnosticCode.E_ARRAY_INDEX_OUT_OF_BOUNDS,
            2,
            22,
            Map.of("operation", "get", "index", "3", "length", "3", "validRange", "[0,3)"),
            "0以上3未満",
            "3",
            "添字を0から2の範囲にしてください",
            2),
        Arguments.of(
            "ARRAY-F030.bsb",
            DiagnosticCode.E_ARRAY_INDEX_OUT_OF_BOUNDS,
            2,
            27,
            Map.of("operation", "replace", "index", "3", "length", "3", "validRange", "[0,3)"),
            "0以上3未満",
            "3",
            "添字を0から2の範囲にしてください",
            3),
        Arguments.of(
            "ARRAY-F031.bsb",
            DiagnosticCode.E_ARRAY_RANGE_OUT_OF_BOUNDS,
            2,
            27,
            Map.of("start", "-1", "end", "2", "length", "3", "validCondition", "0<=start<=end<=3"),
            "0 <= 開始 <= 終了 <= 3",
            "[-1,2)",
            "開始と終了を有効範囲にしてください",
            3),
        Arguments.of(
            "ARRAY-F032.bsb",
            DiagnosticCode.E_ARRAY_RANGE_OUT_OF_BOUNDS,
            2,
            26,
            Map.of("start", "2", "end", "1", "length", "3", "validCondition", "0<=start<=end<=3"),
            "0 <= 開始 <= 終了 <= 3",
            "[2,1)",
            "開始を終了以下にしてください",
            3));
  }

  private static byte[] resourceBytes(String sourceName) throws IOException {
    String resource = "/conformance/arrays/sources/" + sourceName;
    try (var input = ArrayArrayRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
