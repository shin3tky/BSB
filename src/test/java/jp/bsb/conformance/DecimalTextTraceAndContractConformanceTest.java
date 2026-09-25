package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class DecimalTextTraceAndContractConformanceTest {
  @Test
  void traceDistinguishesTheInputStringFromTheCanonicalDecimalValue() throws Exception {
    byte[] source = DecimalTextConformanceData.resourceBytes("sources/DTXT-N-trace.bsb");
    var events = new ArrayList<TraceEvent>();
    var output = new MemoryOutputSink();
    var traced =
        new ProgramRunner()
            .run("DTXT-N007.bsb", source, new ExecutionContext(output, () -> 0L, events::add));
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertArrayEquals(
        DecimalTextConformanceData.resourceBytes("sources/DTXT-N-trace.stdout"), output.bytes());
    String trace = TraceTsvFormatter.formatTextRegex(events);
    assertEquals(DecimalTextConformanceData.resourceText("sources/DTXT-N-trace.trace.tsv"), trace);
    assertTrue(trace.contains("文字列:1e2"));
    assertTrue(trace.contains("小数:100.0"));
    assertFalse(trace.contains("小数:1e2"));
  }

  @Test
  void sourceJsonAndIntegerConversionBoundariesRemainIndependent() throws Exception {
    byte[] source = DecimalTextConformanceData.resourceBytes("sources/DTXT-N-independent.bsb");
    var output = new MemoryOutputSink();
    var result =
        new ProgramRunner()
            .run("DTXT-N005.bsb", source, new ExecutionContext(output, () -> 0L, TraceSink.none()));
    assertTrue(result.successful(), result.diagnostics().toString());
    assertArrayEquals(
        DecimalTextConformanceData.resourceBytes("sources/DTXT-N-independent.stdout"),
        output.bytes());
    assertEquals(200, BuiltinDictionary.words().size());
  }

  @Test
  void chapterOutputIsStableAndTracingDoesNotChangeExecution() throws Exception {
    byte[] source = DecimalTextConformanceData.resourceBytes("chapter/decimal-text-chapter.bsb");
    Run ordinary = run(source, false);
    Run traced = run(source, true);
    assertTrue(ordinary.result().successful(), ordinary.result().diagnostics().toString());
    assertTrue(traced.result().successful(), traced.result().diagnostics().toString());
    assertArrayEquals(
        DecimalTextConformanceData.resourceBytes("chapter/decimal-text-chapter.stdout"),
        ordinary.output());
    assertArrayEquals(ordinary.output(), traced.output());
    assertEquals(ordinary.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(ordinary.result().executedInstructions(), traced.result().executedInstructions());
  }

  private static Run run(byte[] source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var result =
        new ProgramRunner()
            .run(
                "decimal-text-chapter.bsb",
                source,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Run(result, output.bytes(), List.copyOf(events));
  }

  private record Run(
      jp.bsb.runtime.ProgramRunResult result, byte[] output, java.util.List<TraceEvent> events) {}
}
