package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 数値演算の小数表示、21列トレース、章末成果物を規範資源へ固定します。 */
class NumericTraceTest {
  @ParameterizedTest
  @MethodSource("traceCases")
  void traceMatchesByteForByteAndDoesNotChangeExecution(
      String sourceName, String stdoutName, String traceName) throws IOException {
    byte[] source = resourceBytes(sourceName);
    byte[] expectedOutput = resourceBytes(stdoutName);
    byte[] expectedTrace = resourceBytes(traceName);
    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();

    ProgramRunResult normal =
        runner.run(
            sourceName, source, new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    ProgramRunResult traced =
        runner.run(sourceName, source, new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(normal.exitCode(), traced.exitCode());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertEquals(normal.finalGlobalValues(), traced.finalGlobalValues());
    assertEquals(normal.executedInstructions(), traced.executedInstructions());
    assertEquals(normal.arrayConstructionUnits(), traced.arrayConstructionUnits());
    assertEquals(normal.arrayElementOperationUnits(), traced.arrayElementOperationUnits());
    assertArrayEquals(expectedOutput, normalOutput.bytes());
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes());
    assertArrayEquals(
        expectedTrace, TraceTsvFormatter.formatNumeric(events).getBytes(StandardCharsets.UTF_8));
  }

  private static Stream<Arguments> traceCases() {
    return Stream.of(
        Arguments.of(
            "sources/NUM-N019.bsb",
            "chapter/numerics-decimal.stdout",
            "chapter/numerics-decimal.trace.tsv"),
        Arguments.of(
            "chapter/numerics-chapter.bsb",
            "chapter/numerics-chapter.stdout",
            "chapter/numerics-chapter.trace.tsv"));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String path = "/conformance/numerics/" + relativePath;
    try (var input = NumericTraceTest.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }
}
