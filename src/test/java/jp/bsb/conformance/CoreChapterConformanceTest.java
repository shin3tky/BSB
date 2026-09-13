package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

/** 仕様書の章末プログラムについて、通常実行とトレース実行の観測結果を固定します。 */
class CoreChapterConformanceTest {
  @Test
  void chapterOutputAndTraceMatchAndTracingDoesNotChangeExecution() throws IOException {
    byte[] source = ConformanceData.resourceBytes("chapter/language-core-chapter.bsb");
    byte[] expectedOutput = ConformanceData.resourceBytes("chapter/language-core-chapter.stdout");
    String expectedTrace = ConformanceData.resourceText("chapter/language-core-chapter.trace.tsv");

    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();

    // 単調時計を0に固定すると、実行速度やOS時刻に依存せず同じ試験結果になります。
    var normal =
        runner.run(
            "language-core-chapter.bsb",
            source,
            new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    var traced =
        runner.run(
            "language-core-chapter.bsb",
            source,
            new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(0, normal.exitCode());
    assertEquals(normal.exitCode(), traced.exitCode());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertArrayEquals(expectedOutput, normalOutput.bytes(), "chapter stdout");
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes(), "trace must not alter stdout");
    assertEquals(expectedTrace, TraceTsvFormatter.format(events), "chapter trace TSV");
  }
}
