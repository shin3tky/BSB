package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.format.SourceFormatter;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

class OptionalChapterConformanceTest {
  @Test
  void chapterCheckFormatRunAndRedactedTraceMatchIndependentArtifacts() throws Exception {
    byte[] source = OptionalConformanceData.resourceBytes("chapter/optional-values-chapter.bsb");
    byte[] expectedOutput =
        OptionalConformanceData.resourceBytes("chapter/optional-values-chapter.stdout");
    String expectedTrace =
        OptionalConformanceData.resourceText("chapter/optional-values-chapter.trace.tsv")
            .replace("\\t\n", "\t\n");

    var checked = new SourceChecker().check("optional-values-chapter.bsb", source);
    assertTrue(checked.successful(), checked.diagnostics().toString());
    var formatted = new SourceFormatter().format("optional-values-chapter.bsb", source);
    assertTrue(formatted.successful(), formatted.diagnostics().toString());
    assertArrayEquals(
        OptionalConformanceData.resourceBytes("canonical/optional-values-chapter.bsb"),
        formatted.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    Observation normal = run(source, false);
    Observation traced = run(source, true);
    assertEquals(0, normal.result().exitCode(), normal.result().diagnostics().toString());
    assertArrayEquals(expectedOutput, normal.output().bytes());
    assertEquals(normal.result().termination(), traced.result().termination());
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(normal.result().finalGlobalValues(), traced.result().finalGlobalValues());
    assertEquals(normal.result().executedInstructions(), traced.result().executedInstructions());
    assertEquals(normal.result().outputBytes(), traced.result().outputBytes());
    assertEquals(normal.result().jsonConstructionUnits(), traced.result().jsonConstructionUnits());
    assertEquals(normal.result().jsonWorkUnits(), traced.result().jsonWorkUnits());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes());
    assertEquals(40, normal.result().executedInstructions());
    assertEquals(7, normal.result().jsonConstructionUnits());
    assertEquals(55, normal.result().jsonWorkUnits());

    String trace = TraceTsvFormatter.formatOptional(traced.events());
    assertEquals(expectedTrace, trace);
    assertTrue(trace.contains("任意<JSON>:<redacted>"));
    assertFalse(trace.contains("configured"));
    assertFalse(trace.contains(":ない"));
  }

  private static Observation run(byte[] source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "optional-values-chapter.bsb",
                source,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, output, List.copyOf(events));
  }

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
