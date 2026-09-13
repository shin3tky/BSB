package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.explain.ProgramExplainer;
import jp.bsb.format.SourceFormatter;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.StringValue;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

class RecoverableJsonPublishedStateConformanceTest {
  private static final Map<String, BudgetState> BUDGETS =
      Map.of(
          "sources/RJSON-N-success-values.bsb", new BudgetState(43, 80, 13),
          "sources/RJSON-N-failure-kinds.bsb", new BudgetState(92, 59, 0),
          "sources/RJSON-N-position-fields.bsb", new BudgetState(21, 15, 0),
          "sources/RJSON-N-continuation.bsb", new BudgetState(10, 2, 0),
          "chapter/recoverable-json-chapter.bsb", new BudgetState(43, 43, 3),
          "sources/RJSON-F-existing-parser.bsb", new BudgetState(2, 1, 0));

  @Test
  void everyCentralStateMatchesRunOutputStackGlobalsCapabilitiesDiagnosticAndTrace()
      throws Exception {
    var actualBudgets = new java.util.LinkedHashMap<String, BudgetState>();
    for (var expected : RecoverableJsonConformanceData.loadStates()) {
      byte[] source = RecoverableJsonConformanceData.resourceBytes(expected.source());
      Observation normal = run(expected.source(), source, false);
      Observation traced = run(expected.source(), source, true);
      ProgramRunResult result = normal.result();

      assertEquals(expected.exitCode(), result.exitCode(), expected.source());
      assertArrayEquals(expected.stdout(), normal.output().bytes(), expected.source() + "/stdout");
      assertArrayEquals(expected.stderr(), new byte[0], expected.source() + "/stderr");
      assertEquals(expected.dataStack(), dataStack(result), expected.source() + "/stack");
      assertEquals(expected.globals(), globals(result), expected.source() + "/globals");
      assertEquals(expected.diagnostic(), diagnostic(result), expected.source() + "/diagnostic");
      assertEquals(expected.capabilities(), capabilities(expected.source(), source));
      assertEquivalent(result, traced.result(), expected.source());
      assertArrayEquals(normal.output().bytes(), traced.output().bytes(), expected.source());

      actualBudgets.put(
          expected.source(),
          new BudgetState(
              result.executedInstructions(),
              result.jsonWorkUnits(),
              result.jsonConstructionUnits()));

      String trace = TraceTsvFormatter.formatRecoverableJson(traced.events());
      assertTrue(trace.endsWith("\n"), expected.source());
      if (expected.source().contains("failure")
          || expected.source().contains("position")
          || expected.source().contains("chapter")) {
        assertTrue(trace.contains("JSON解析失敗:<redacted>"), expected.source());
      }
    }
    assertEquals(BUDGETS, actualBudgets, "central budgets");
  }

  @Test
  void everyPublishedCanonicalIsTheExactIdempotentFormatterOutput() throws Exception {
    for (String name :
        List.of(
            "RJSON-N-success-values.bsb",
            "RJSON-N-failure-kinds.bsb",
            "RJSON-N-position-fields.bsb")) {
      assertCanonical("sources/" + name, "canonical/" + name);
    }
    assertCanonical(
        "chapter/recoverable-json-chapter.bsb", "canonical/recoverable-json-chapter.bsb");
  }

  private static void assertCanonical(String sourcePath, String canonicalPath) throws Exception {
    var formatted =
        new SourceFormatter()
            .format(sourcePath, RecoverableJsonConformanceData.resourceBytes(sourcePath));
    assertTrue(formatted.successful(), sourcePath);
    byte[] actual = formatted.outputForStandardOutput().getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(
        RecoverableJsonConformanceData.resourceBytes(canonicalPath), actual, sourcePath);
    var second = new SourceFormatter().format(canonicalPath, actual);
    assertTrue(second.successful(), canonicalPath);
    assertArrayEquals(actual, second.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
  }

  private static String dataStack(ProgramRunResult result) {
    if (result.finalDataStack().isEmpty()) {
      return "[]";
    }
    if (result.finalDataStack().size() == 1
        && result.finalDataStack().getFirst() instanceof StringValue) {
      return "[\"文字列:<redacted>\"]";
    }
    throw new AssertionError("unexpected central data stack: " + result.finalDataStack());
  }

  private static String globals(ProgramRunResult result) {
    return result.finalGlobalValues().stream().allMatch(java.util.Optional::isEmpty)
        ? "{}"
        : "non-empty";
  }

  private static String diagnostic(ProgramRunResult result) {
    return result.diagnostics().stream()
        .filter(item -> item.severity() == jp.bsb.diagnostics.Severity.ERROR)
        .map(item -> item.code().name())
        .findFirst()
        .orElse("-");
  }

  private static String capabilities(String path, byte[] source) {
    var analysis = new SourceChecker().check(path, source);
    assertTrue(analysis.successful(), path + analysis.diagnostics());
    List<String> capabilities =
        new ProgramExplainer().explain(analysis.programForIrGeneration()).summary().capabilities();
    return capabilities.isEmpty()
        ? "[]"
        : capabilities.stream()
            .map(value -> "\"" + value + "\"")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
  }

  private static Observation run(String path, byte[] source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                path,
                source,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, output, List.copyOf(events));
  }

  private static void assertEquivalent(
      ProgramRunResult first, ProgramRunResult second, String label) {
    assertEquals(first.exitCode(), second.exitCode(), label);
    assertEquals(
        first.diagnostics().stream()
            .map(
                item ->
                    List.of(
                        item.code(),
                        item.stage(),
                        item.fields(),
                        item.expected(),
                        item.actual(),
                        item.fixes()))
            .toList(),
        second.diagnostics().stream()
            .map(
                item ->
                    List.of(
                        item.code(),
                        item.stage(),
                        item.fields(),
                        item.expected(),
                        item.actual(),
                        item.fixes()))
            .toList(),
        label);
    assertEquals(first.finalDataStack(), second.finalDataStack(), label);
    assertEquals(first.finalGlobalValues(), second.finalGlobalValues(), label);
    assertEquals(first.executedInstructions(), second.executedInstructions(), label);
    assertEquals(first.outputBytes(), second.outputBytes(), label);
    assertEquals(first.errorOutputBytes(), second.errorOutputBytes(), label);
    assertEquals(first.jsonWorkUnits(), second.jsonWorkUnits(), label);
    assertEquals(first.jsonConstructionUnits(), second.jsonConstructionUnits(), label);
    assertEquals(first.termination(), second.termination(), label);
  }

  private record BudgetState(long instructions, long jsonWork, long jsonConstruction) {}

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
