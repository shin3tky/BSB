package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.format.SourceFormatter;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.MemoryConsoleInput;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

class JsonChapterConformanceTest {
  @Test
  void chapterCheckFormatRunAndRedactedTraceMatchIndependentArtifacts() throws Exception {
    byte[] source = JsonConformanceData.resourceBytes("chapter/json-chapter.bsb");
    byte[] input = JsonConformanceData.resourceBytes("chapter/json-chapter.stdin");
    byte[] expectedOutput = JsonConformanceData.resourceBytes("chapter/json-chapter.stdout");

    var checked = new SourceChecker().check("json-chapter.bsb", source);
    assertTrue(checked.successful(), codes(checked.diagnostics()).toString());
    var formatted = new SourceFormatter().format("json-chapter.bsb", source);
    assertTrue(formatted.successful(), formatted.diagnostics().toString());
    assertArrayEquals(
        JsonConformanceData.resourceBytes("canonical/json-chapter.bsb"),
        formatted.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    Observation normal = run(source, input, false);
    Observation traced = run(source, input, true);
    assertEquals(0, normal.result().exitCode(), codes(normal.result().diagnostics()).toString());
    assertArrayEquals(expectedOutput, normal.stdout().bytes());
    assertArrayEquals(new byte[0], normal.stderr().bytes());
    assertEquals(normal.result().termination(), traced.result().termination());
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(normal.result().finalGlobalValues(), traced.result().finalGlobalValues());
    assertEquals(normal.result().executedInstructions(), traced.result().executedInstructions());
    assertEquals(
        normal.result().arrayConstructionUnits(), traced.result().arrayConstructionUnits());
    assertEquals(
        normal.result().arrayElementOperationUnits(), traced.result().arrayElementOperationUnits());
    assertEquals(normal.result().jsonConstructionUnits(), traced.result().jsonConstructionUnits());
    assertEquals(normal.result().jsonWorkUnits(), traced.result().jsonWorkUnits());
    assertArrayEquals(normal.stdout().bytes(), traced.stdout().bytes());

    String trace = TraceTsvFormatter.formatJson(traced.events());
    assertTrue(trace.contains("JSON:<redacted>"));
    assertTrue(trace.contains("文字列:<redacted>"));
    assertFalse(trace.contains("山田"));
    assertFalse(trace.contains("name"));
    assertFalse(trace.contains("items"));
    assertFalse(trace.contains("status"));
  }

  @Test
  void chapterOutputIsIndependentOfLocaleTimeZoneAndPhysicalLineEndings() throws Exception {
    byte[] lfSource = JsonConformanceData.resourceBytes("chapter/json-chapter.bsb");
    byte[] lfInput = JsonConformanceData.resourceBytes("chapter/json-chapter.stdin");
    byte[] crlfSource =
        new String(lfSource, StandardCharsets.UTF_8)
            .replace("\n", "\r\n")
            .getBytes(StandardCharsets.UTF_8);
    byte[] crlfInput =
        new String(lfInput, StandardCharsets.UTF_8)
            .replace("\n", "\r\n")
            .getBytes(StandardCharsets.UTF_8);
    Observation baseline = run(lfSource, lfInput, false);
    Locale originalLocale = Locale.getDefault();
    TimeZone originalTimeZone = TimeZone.getDefault();
    Observation changed;
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
      changed = run(crlfSource, crlfInput, false);
    } finally {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimeZone);
    }

    assertEquals(0, changed.result().exitCode(), codes(changed.result().diagnostics()).toString());
    assertArrayEquals(baseline.stdout().bytes(), changed.stdout().bytes());
    assertArrayEquals(baseline.stderr().bytes(), changed.stderr().bytes());
  }

  private static Observation run(byte[] source, byte[] input, boolean tracing) {
    var stdout = new MemoryOutputSink();
    var stderr = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    int terminatorBytes =
        input.length >= 2 && input[input.length - 2] == '\r' && input[input.length - 1] == '\n'
            ? 2
            : 1;
    var line = MemoryConsoleInput.rawLine(input, terminatorBytes);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleInput(new MemoryConsoleInput(List.of(line)))
            .consoleOutput(stdout::write)
            .consoleError(stderr::write)
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "json-chapter.bsb",
                source,
                new ExecutionContext(
                    stdout, () -> 0L, tracing ? events::add : TraceSink.none(), environment));
    return new Observation(result, stdout, stderr, List.copyOf(events));
  }

  private static List<?> codes(List<jp.bsb.diagnostics.Diagnostic> diagnostics) {
    return diagnostics.stream()
        .map(
            diagnostic ->
                diagnostic.code()
                    + "@"
                    + diagnostic.location().displayPosition().orElseThrow().line()
                    + ":"
                    + diagnostic.location().displayPosition().orElseThrow().column())
        .toList();
  }

  private record Observation(
      ProgramRunResult result,
      MemoryOutputSink stdout,
      MemoryOutputSink stderr,
      List<TraceEvent> events) {}
}
