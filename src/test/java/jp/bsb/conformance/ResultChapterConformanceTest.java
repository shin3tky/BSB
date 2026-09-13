package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.cli.BsbCli;
import jp.bsb.format.SourceFormatter;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

class ResultChapterConformanceTest {
  private static final Path PUBLIC_SOURCE =
      Path.of("tests/conformance/result-values/chapter/result-values-chapter.bsb");

  @Test
  void chapterCheckFormatRunExplainStateAndBothTracePoliciesMatchFixedArtifacts() throws Exception {
    byte[] source = ResultConformanceData.resourceBytes("chapter/result-values-chapter.bsb");
    byte[] expectedOutput =
        ResultConformanceData.resourceBytes("chapter/result-values-chapter.stdout");
    assertEquals(35, expectedOutput.length);

    var checked = new SourceChecker().check(PUBLIC_SOURCE.toString(), source);
    assertTrue(checked.successful(), checked.diagnostics().toString());
    var formatted = new SourceFormatter().format(PUBLIC_SOURCE.toString(), source);
    assertTrue(formatted.successful(), formatted.diagnostics().toString());
    assertArrayEquals(
        ResultConformanceData.resourceBytes("canonical/result-values-chapter.bsb"),
        formatted.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    Observation normal = run(source, false);
    Observation traced = run(source, true);
    assertEquals(0, normal.result().exitCode(), normal.result().diagnostics().toString());
    assertEquivalent(normal.result(), traced.result());
    assertArrayEquals(expectedOutput, normal.output().bytes());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes());

    String reveal = TraceTsvFormatter.formatResult(traced.events(), ignored -> true);
    String redacted = TraceTsvFormatter.formatResult(traced.events());
    assertEquals(
        ResultConformanceData.resourceText("chapter/result-values-chapter.trace.tsv")
            .replace("\\t\n", "\t\n"),
        reveal);
    assertEquals(
        ResultConformanceData.resourceText("chapter/result-values-chapter.redacted.trace.tsv")
            .replace("\\t\n", "\t\n"),
        redacted);
    assertTrue(redacted.contains("結果<任意<JSON>,文字列>:<redacted>"));
    assertFalse(redacted.contains("結果<任意<JSON>,文字列>:成功("));
    assertFalse(redacted.contains("結果<任意<JSON>,文字列>:失敗("));

    Map<String, String> state = state();
    assertEquals(state.get("exit"), Integer.toString(normal.result().exitCode()));
    assertEquals(
        state.get("final_stack"), Integer.toString(normal.result().finalDataStack().size()));
    assertEquals(
        state.get("final_globals"), Integer.toString(normal.result().finalGlobalValues().size()));
    assertEquals(state.get("instructions"), Long.toString(normal.result().executedInstructions()));
    assertEquals(state.get("output_bytes"), Long.toString(normal.result().outputBytes()));
    assertEquals(
        state.get("error_output_bytes"), Long.toString(normal.result().errorOutputBytes()));
    assertEquals(
        state.get("array_construction"), Long.toString(normal.result().arrayConstructionUnits()));
    assertEquals(
        state.get("array_work"), Long.toString(normal.result().arrayElementOperationUnits()));
    assertEquals(
        state.get("json_construction"), Long.toString(normal.result().jsonConstructionUnits()));
    assertEquals(state.get("json_work"), Long.toString(normal.result().jsonWorkUnits()));
    assertEquals("console.output", state.get("capabilities"));

    Invocation explain = invoke("explain", "--json", PUBLIC_SOURCE.toString());
    assertEquals(0, explain.exitCode());
    assertArrayEquals(
        ResultConformanceData.resourceBytes("chapter/result-values-chapter.explain.json"),
        ExplainBuiltinCompatibility.withoutByteSequenceWords(explain.stdout()));
    assertEquals(0, explain.stderr().length);

    Invocation centralExplain =
        invoke("explain", "--json", "tests/conformance/result-values/central/RESULT-N-runtime.bsb");
    assertEquals(0, centralExplain.exitCode());
    assertArrayEquals(
        ResultConformanceData.resourceBytes("explain/RESULT-N017.json"),
        ExplainBuiltinCompatibility.withoutByteSequenceWords(centralExplain.stdout()));
    assertEquals(0, centralExplain.stderr().length);
  }

  private static void assertEquivalent(ProgramRunResult normal, ProgramRunResult traced) {
    assertEquals(normal.exitCode(), traced.exitCode());
    assertEquals(normal.diagnostics(), traced.diagnostics());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertEquals(normal.finalGlobalValues(), traced.finalGlobalValues());
    assertEquals(normal.executedInstructions(), traced.executedInstructions());
    assertEquals(normal.outputBytes(), traced.outputBytes());
    assertEquals(normal.errorOutputBytes(), traced.errorOutputBytes());
    assertEquals(normal.arrayConstructionUnits(), traced.arrayConstructionUnits());
    assertEquals(normal.arrayElementOperationUnits(), traced.arrayElementOperationUnits());
    assertEquals(normal.jsonConstructionUnits(), traced.jsonConstructionUnits());
    assertEquals(normal.jsonWorkUnits(), traced.jsonWorkUnits());
    assertEquals(normal.termination(), traced.termination());
  }

  private static Map<String, String> state() throws Exception {
    String[] lines =
        ResultConformanceData.resourceText("chapter/result-values-chapter.state.tsv").split("\n");
    assertEquals("metric\tvalue", lines[0]);
    var result = new LinkedHashMap<String, String>();
    for (int index = 1; index < lines.length; index++) {
      if (lines[index].isEmpty()) {
        continue;
      }
      String[] row = lines[index].split("\t", -1);
      assertEquals(2, row.length, lines[index]);
      assertEquals(null, result.put(row[0], row[1]), "duplicate state metric " + row[0]);
    }
    assertEquals(
        java.util.Set.of(
            "exit",
            "final_stack",
            "final_globals",
            "instructions",
            "output_bytes",
            "error_output_bytes",
            "array_construction",
            "array_work",
            "json_construction",
            "json_work",
            "capabilities"),
        result.keySet());
    return Map.copyOf(result);
  }

  private static Observation run(byte[] source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                PUBLIC_SOURCE.toString(),
                source,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, output, List.copyOf(events));
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
