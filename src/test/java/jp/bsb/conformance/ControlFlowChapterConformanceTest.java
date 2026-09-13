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

/** 制御フローの章末プログラムについて、通常出力と制御トレースを規範資源へ固定します。 */
class ControlFlowChapterConformanceTest {
  @Test
  void chapterStdoutAndTraceMatchByteForByteAndTracingDoesNotChangeExecution() throws IOException {
    byte[] source = ControlFlowConformanceData.resourceBytes("chapter/control-flow-chapter.bsb");
    byte[] expectedOutput =
        ControlFlowConformanceData.resourceBytes("chapter/control-flow-chapter.stdout");
    byte[] expectedTrace =
        ControlFlowConformanceData.resourceBytes("chapter/control-flow-chapter.trace.tsv");
    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();

    // 時計を固定し、コンピュータの速さや実行日時によって結果が変わらないようにする。
    var normal =
        runner.run(
            "control-flow-chapter.bsb",
            source,
            new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    var traced =
        runner.run(
            "control-flow-chapter.bsb",
            source,
            new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(normal.exitCode(), traced.exitCode(), "trace must not alter exit code");
    assertEquals(normal.finalDataStack(), traced.finalDataStack(), "trace must not alter stack");
    assertEquals(
        normal.executedInstructions(),
        traced.executedInstructions(),
        "trace must not alter instruction count");
    assertArrayEquals(expectedOutput, normalOutput.bytes(), "chapter stdout bytes");
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes(), "trace must not alter stdout");
    assertArrayEquals(
        expectedTrace,
        TraceTsvFormatter.formatControlFlow(events).getBytes(StandardCharsets.UTF_8),
        "chapter trace bytes");
  }
}
