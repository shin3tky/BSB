package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.binding.BindingTsvFormatter;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

/** 束縛の章末stdout、束縛説明、保存領域トレースを規範資源へ固定します。 */
class BindingChapterConformanceTest {
  @Test
  void allChapterArtifactsMatchByteForByteAndTracingDoesNotChangeExecution() throws IOException {
    var catalog = BindingConformanceData.loadCases();
    var chapterCase =
        catalog.cases().stream()
            .filter(spec -> spec.id().equals("BIND-N020"))
            .findFirst()
            .orElseThrow();
    byte[] source = BindingConformanceData.resourceBytes(chapterCase.source());
    byte[] expectedOutput = BindingConformanceData.resourceBytes("chapter/bindings-chapter.stdout");
    byte[] expectedBindings =
        BindingConformanceData.resourceBytes(chapterCase.bindingReportSource());
    byte[] expectedTrace =
        BindingConformanceData.resourceBytes("chapter/bindings-chapter.trace.tsv");

    var analysis = new SourceChecker().check("bindings-chapter.bsb", source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    assertArrayEquals(
        expectedBindings,
        BindingTsvFormatter.format(analysis.programForIrGeneration())
            .getBytes(StandardCharsets.UTF_8),
        "chapter binding report bytes");

    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();
    var normal =
        runner.run(
            "bindings-chapter.bsb",
            source,
            new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    var traced =
        runner.run(
            "bindings-chapter.bsb",
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
    assertArrayEquals(expectedOutput, normalOutput.bytes(), "chapter stdout bytes");
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes(), "trace must not alter stdout");
    assertArrayEquals(
        expectedTrace,
        TraceTsvFormatter.formatBinding(events).getBytes(StandardCharsets.UTF_8),
        "chapter trace bytes");
  }
}
