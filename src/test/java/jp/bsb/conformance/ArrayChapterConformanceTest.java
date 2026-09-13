package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

/** 配列の章末stdoutと21列トレースを規範資源へ固定します。 */
class ArrayChapterConformanceTest {
  @Test
  void chapterArtifactsMatchByteForByteAndTracingDoesNotChangeExecution() throws IOException {
    var chapterCase =
        ArrayConformanceData.loadCases().cases().stream()
            .filter(spec -> spec.id().equals("ARRAY-N022"))
            .findFirst()
            .orElseThrow();
    byte[] source = ArrayConformanceData.resourceBytes(chapterCase.source());
    byte[] expectedOutput = ArrayConformanceData.resourceBytes("chapter/arrays-chapter.stdout");
    byte[] expectedTrace = ArrayConformanceData.resourceBytes(chapterCase.traceSource());

    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();
    var normal =
        runner.run(
            "arrays-chapter.bsb",
            source,
            new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    var traced =
        runner.run(
            "arrays-chapter.bsb",
            source,
            new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(normal.exitCode(), traced.exitCode(), "trace must not alter exit code");
    assertEquals(normal.finalDataStack(), traced.finalDataStack(), "trace must not alter stack");
    assertEquals(
        normal.finalGlobalValues(), traced.finalGlobalValues(), "trace must not alter globals");
    assertEquals(
        normal.executedInstructions(),
        traced.executedInstructions(),
        "trace must not alter instruction count");
    assertEquals(
        normal.arrayConstructionUnits(),
        traced.arrayConstructionUnits(),
        "trace must not alter construction units");
    assertEquals(
        normal.arrayElementOperationUnits(),
        traced.arrayElementOperationUnits(),
        "trace must not alter element-operation units");
    assertArrayEquals(expectedOutput, normalOutput.bytes(), "chapter stdout bytes");
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes(), "trace must not alter stdout");
    assertArrayEquals(
        expectedTrace,
        TraceTsvFormatter.formatArray(events).getBytes(StandardCharsets.UTF_8),
        "chapter trace bytes");
  }
}
