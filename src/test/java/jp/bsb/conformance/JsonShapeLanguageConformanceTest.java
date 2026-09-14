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

class JsonShapeLanguageConformanceTest {
  @Test
  void referenceProgramChecksFormatsRunsAndKeepsTraceSemantics() throws Exception {
    byte[] source = resourceBytes("chapter/json-shapes-chapter.bsb");
    var checked = new SourceChecker().check("json-shapes-chapter.bsb", source);
    assertTrue(checked.successful(), checked.diagnostics().toString());

    var formatted = new SourceFormatter().format("json-shapes-chapter.bsb", source);
    assertTrue(formatted.successful(), formatted.diagnostics().toString());
    assertArrayEquals(source, formatted.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    Observation ordinary = run(source, false);
    Observation traced = run(source, true);
    assertTrue(ordinary.result().successful(), ordinary.result().diagnostics().toString());
    assertTrue(traced.result().successful(), traced.result().diagnostics().toString());
    assertArrayEquals(resourceBytes("chapter/json-shapes-chapter.stdout"), ordinary.output());
    assertArrayEquals(ordinary.output(), traced.output());
    assertEquals(ordinary.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(ordinary.result().jsonShapeWorkUnits(), traced.result().jsonShapeWorkUnits());
    assertTrue(ordinary.result().jsonShapeWorkUnits() > 0);

    String trace = TraceTsvFormatter.formatTextRegex(traced.events());
    assertTrue(trace.contains("JSON形状:<redacted>"));
    assertTrue(trace.contains("JSON形状失敗:<redacted>"));
    assertFalse(trace.contains("JSON形状:object"));
    assertFalse(trace.contains("JSON形状失敗:kindMismatch"));
  }

  @Test
  void staticInputMismatchRemainsAnAnalyzerDiagnostic() {
    byte[] source =
        "メインとは （--）\n    1 JSON文字列の形状 JSONの形状を検証する\nこと。\n".getBytes(StandardCharsets.UTF_8);
    var analysis = new SourceChecker().check("json-shape-type-error.bsb", source);
    assertFalse(analysis.successful());
    assertTrue(
        analysis.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().name().equals("E_TYPE_MISMATCH")));
  }

  private static Observation run(byte[] source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "json-shapes-chapter.bsb",
                source,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, output.bytes(), List.copyOf(events));
  }

  private static byte[] resourceBytes(String name) throws Exception {
    String path = "/conformance/json-shapes/" + name;
    try (var input = JsonShapeLanguageConformanceTest.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IllegalStateException("missing " + path);
      }
      return input.readAllBytes();
    }
  }

  private record Observation(ProgramRunResult result, byte[] output, List<TraceEvent> events) {}
}
