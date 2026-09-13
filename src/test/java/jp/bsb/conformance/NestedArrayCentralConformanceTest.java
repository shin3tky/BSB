package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;

/** 多次元配列の中央カタログを公開ソース、独立表、通常・trace実行へ接続します。 */
class NestedArrayCentralConformanceTest {
  @Test
  void centralCatalogConsumesAllThirtyFourIdsWithoutOrphans() throws Exception {
    Set<String> consumed = new LinkedHashSet<>();
    consumed.addAll(ids(NestedArrayConformanceData.loadStates()));
    consumed.addAll(ids(NestedArrayConformanceData.loadDiagnostics()));
    consumed.addAll(ids(NestedArrayConformanceData.loadTraces()));
    consumed.addAll(ids(NestedArrayConformanceData.loadResources()));
    NestedArrayConformanceData.loadCatalog().stream()
        .filter(item -> item.scope().equals("public") && item.evidence().endsWith(".bsb"))
        .map(NestedArrayConformanceData.CatalogSpec::id)
        .forEach(consumed::add);

    Set<String> catalog =
        NestedArrayConformanceData.loadCatalog().stream()
            .map(NestedArrayConformanceData.CatalogSpec::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(catalog, consumed);
    assertEquals(34, consumed.size());
  }

  @Test
  void everyPublicSourceMatchesItsIndependentStaticOutcomeAndDiagnostic() throws Exception {
    Map<String, NestedArrayConformanceData.RowSpec> diagnostics =
        NestedArrayConformanceData.loadDiagnostics().stream()
            .collect(
                Collectors.toMap(
                    row -> row.value("case_id"),
                    row -> row,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (var item : NestedArrayConformanceData.loadCatalog()) {
      if (!item.scope().equals("public") || !item.evidence().endsWith(".bsb")) {
        continue;
      }
      var result =
          new SourceChecker()
              .check(item.evidence(), NestedArrayConformanceData.resourceBytes(item.evidence()));
      assertEquals(item.kind().equals("normal"), result.successful(), item.id());
      if (item.id().startsWith("NARRAY-F")) {
        var expected = diagnostics.get(item.id());
        Diagnostic actual = result.diagnostics().getFirst();
        assertEquals(DiagnosticCode.valueOf(expected.value("code")), actual.code(), item.id());
        assertEquals(
            Integer.parseInt(expected.value("line")),
            actual.location().displayPosition().orElseThrow().line(),
            item.id());
        assertEquals(
            Integer.parseInt(expected.value("column")),
            actual.location().displayPosition().orElseThrow().column(),
            item.id());
        parseFields(expected.value("fields"))
            .forEach(
                (name, value) -> assertEquals(value, actual.fields().get(name), item.id() + name));
      }
    }
  }

  @Test
  void allPublishedNormalProgramsRunDeterministicallyWithAndWithoutTrace() throws Exception {
    Map<String, String> expected =
        Map.of(
            "sources/NARRAY-N-types.bsb", "0\n0\n0\n0\n0\n0\n",
            "sources/NARRAY-N-ragged.bsb", "【【1、2】、【3】、【】】\n",
            "sources/NARRAY-N-operations.bsb",
                "【【9、8】、【2】】\n【【1】、【2】、【3、4】】\n2\n2\n【1】\n【1、9】\n【1、2、3】\n",
            "sources/NARRAY-N-loops.bsb", "1\n2\n3\n",
            "sources/NARRAY-N-wrappers.bsb", "【【1】】\n【【1】】\n【【「a」】】\n",
            "chapter/nested-arrays-chapter.bsb", "【【1、2】、【】、【3】】\n1\n2\n3\n");
    for (var entry : expected.entrySet()) {
      byte[] source = NestedArrayConformanceData.resourceBytes(entry.getKey());
      Observation normal = run(entry.getKey(), source, false);
      Observation traced = run(entry.getKey(), source, true);
      assertTrue(normal.result().successful(), entry.getKey() + normal.result().diagnostics());
      assertArrayEquals(entry.getValue().getBytes(StandardCharsets.UTF_8), normal.output().bytes());
      assertEquivalent(normal.result(), traced.result());
      assertArrayEquals(normal.output().bytes(), traced.output().bytes());
      String trace = TraceTsvFormatter.formatNestedArray(traced.events());
      assertTrue(trace.endsWith("\n"));
      assertFalse(trace.contains("SECRET_JSON"));
    }
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

  private static void assertEquivalent(ProgramRunResult first, ProgramRunResult second) {
    assertEquals(first.exitCode(), second.exitCode());
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.finalDataStack(), second.finalDataStack());
    assertEquals(first.finalGlobalValues(), second.finalGlobalValues());
    assertEquals(first.executedInstructions(), second.executedInstructions());
    assertEquals(first.outputBytes(), second.outputBytes());
    assertEquals(first.errorOutputBytes(), second.errorOutputBytes());
    assertEquals(first.arrayConstructionUnits(), second.arrayConstructionUnits());
    assertEquals(first.arrayElementOperationUnits(), second.arrayElementOperationUnits());
    assertEquals(first.regexWorkUnits(), second.regexWorkUnits());
    assertEquals(first.jsonConstructionUnits(), second.jsonConstructionUnits());
    assertEquals(first.jsonWorkUnits(), second.jsonWorkUnits());
    assertEquals(first.byteSequenceConstructionBytes(), second.byteSequenceConstructionBytes());
    assertEquals(first.byteSequenceWorkBytes(), second.byteSequenceWorkBytes());
    assertEquals(first.httpMetadataConstructionBytes(), second.httpMetadataConstructionBytes());
    assertEquals(first.httpSendCalls(), second.httpSendCalls());
    assertEquals(first.httpRequestAttemptBytes(), second.httpRequestAttemptBytes());
    assertEquals(first.httpResponseReceivedBytes(), second.httpResponseReceivedBytes());
    assertEquals(first.termination(), second.termination());
  }

  private static Set<String> ids(List<NestedArrayConformanceData.RowSpec> rows) {
    return rows.stream().map(row -> row.value("case_id")).collect(Collectors.toSet());
  }

  private static Map<String, String> parseFields(String source) {
    var result = new LinkedHashMap<String, String>();
    for (String item : source.split(";")) {
      int equals = item.indexOf('=');
      result.put(item.substring(0, equals), item.substring(equals + 1));
    }
    return result;
  }

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
