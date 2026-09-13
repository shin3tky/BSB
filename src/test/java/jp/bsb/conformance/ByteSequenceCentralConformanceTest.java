package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

/** バイト列の中央カタログを公開ソース、独立表、通常・trace実行へ接続します。 */
class ByteSequenceCentralConformanceTest {
  private static final String BASIC = "sources/BYTES-N-basic.bsb";
  private static final String CONVERSIONS = "sources/BYTES-N-conversions.bsb";
  private static final String CHAPTER = "chapter/byte-sequences-chapter.bsb";
  private static final String STATIC_FAILURE = "sources/BYTES-F-static.bsb";
  private static final String RANGE_FAILURE = "sources/BYTES-F-range.bsb";

  @Test
  void centralCatalogConsumesAllThirtyFourIdsWithoutOrphans() throws Exception {
    Set<String> consumed = new LinkedHashSet<>();
    consumed.add("BYTES-N001");
    consumed.add("BYTES-N011");
    consumed.add("BYTES-N012");
    consumed.addAll(ids("BYTES-F", 1, 10));
    consumed.add("BYTES-F011");
    consumed.addAll(
        ByteSequenceConformanceData.loadUtf8Vectors().stream()
            .map(ByteSequenceConformanceData.Utf8Vector::id)
            .collect(Collectors.toSet()));
    consumed.addAll(
        ByteSequenceConformanceData.loadUtf8Failures().stream()
            .map(ByteSequenceConformanceData.Utf8Failure::id)
            .collect(Collectors.toSet()));
    consumed.addAll(
        ByteSequenceConformanceData.loadBase64Vectors().stream()
            .map(ByteSequenceConformanceData.Base64Vector::id)
            .collect(Collectors.toSet()));
    consumed.addAll(
        ByteSequenceConformanceData.loadBase64Failures().stream()
            .map(ByteSequenceConformanceData.Base64Failure::id)
            .collect(Collectors.toSet()));
    consumed.addAll(tableIds(ByteSequenceConformanceData.loadSlices()));
    consumed.addAll(tableIds(ByteSequenceConformanceData.loadEquality()));
    consumed.addAll(
        ByteSequenceConformanceData.loadResources().stream()
            .map(ByteSequenceConformanceData.ResourceSpec::id)
            .collect(Collectors.toSet()));
    consumed.addAll(
        ByteSequenceConformanceData.loadTraces().stream()
            .map(item -> item.fields().get("case_id"))
            .collect(Collectors.toSet()));

    Set<String> catalog =
        ByteSequenceConformanceData.loadCatalog().stream()
            .map(ByteSequenceConformanceData.CatalogSpec::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(catalog, consumed);
    assertEquals(34, consumed.size());
  }

  @Test
  void publicStaticSourceMatchesTheTenCataloguedFailuresInSourceOrder() throws Exception {
    byte[] source = ByteSequenceConformanceData.resourceBytes(STATIC_FAILURE);
    var analysis = new SourceChecker().check(STATIC_FAILURE, source);
    assertFalse(analysis.successful());
    List<Diagnostic> actual = analysis.diagnostics();
    assertEquals(10, actual.size());
    assertEquals(
        List.of(
            DiagnosticCode.E_TYPE_MISMATCH,
            DiagnosticCode.E_TYPE_MISMATCH,
            DiagnosticCode.E_TYPE_MISMATCH,
            DiagnosticCode.E_TYPE_MISMATCH,
            DiagnosticCode.E_TYPE_MISMATCH,
            DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
            DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
            DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
            DiagnosticCode.E_STACK_UNDERFLOW,
            DiagnosticCode.E_TYPE_MISMATCH),
        actual.stream().map(Diagnostic::code).toList());
    assertEquals(
        List.of(
            "バイト列",
            "UTF8復号失敗",
            "Base64復号失敗",
            "UTF8復号失敗",
            "Base64復号失敗",
            "バイト列",
            "UTF8復号失敗",
            "Base64復号失敗",
            "[]",
            "[整数]"),
        actual.stream().map(item -> item.actual().orElse("")).toList());
  }

  @Test
  void publishedProgramsMatchFixedOutputBudgetsAndTracing() throws Exception {
    assertPublished(BASIC, "0\n", 0, 1);
    assertPublished(CONVERSIONS, "5pel5pys6Kqe\n日本語\n", 18, 60);
    assertPublished(
        CHAPTER,
        "19\n44GT44KT44Gr44Gh44Gv8J+MjQ==\nこんにちは🌍\nはい\ninvalidLeadingByte\n0\ninvalidLength\n2\n",
        36,
        133);
  }

  @Test
  void rangeFailureReachesRuntimeAndPreservesAcceptedStateAndBudgets() throws Exception {
    byte[] source = ByteSequenceConformanceData.resourceBytes(RANGE_FAILURE);
    Observation normal = run(RANGE_FAILURE, source, false);
    Observation traced = run(RANGE_FAILURE, source, true);
    ProgramRunResult result = normal.result();

    assertEquals(10, result.exitCode());
    assertEquals(
        List.of(DiagnosticCode.E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS),
        result.diagnostics().stream().map(Diagnostic::code).toList());
    Diagnostic diagnostic = result.diagnostics().getFirst();
    Map<String, String> independent =
        ByteSequenceConformanceData.loadDiagnostics().getFirst().fields();
    assertEquals(independent.get("expected"), diagnostic.expected().orElseThrow());
    assertEquals(independent.get("actual"), diagnostic.actual().orElseThrow());
    assertEquals(
        "バイト列:<redacted>", diagnostic.fields().get("dataStack").split(", ")[0].substring(1));
    assertEquals(3, result.finalDataStack().size());
    assertEquals(12, result.byteSequenceConstructionBytes());
    assertEquals(41, result.byteSequenceWorkBytes());
    assertArrayEquals(new byte[0], normal.output().bytes());
    assertEquivalent(result, traced.result());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes());
  }

  private static void assertPublished(
      String path, String expectedOutput, long construction, long work) throws Exception {
    byte[] source = ByteSequenceConformanceData.resourceBytes(path);
    Observation normal = run(path, source, false);
    Observation traced = run(path, source, true);
    assertTrue(normal.result().successful(), path + normal.result().diagnostics());
    assertArrayEquals(
        expectedOutput.getBytes(StandardCharsets.UTF_8), normal.output().bytes(), path);
    assertTrue(normal.result().finalDataStack().isEmpty(), path);
    assertTrue(
        normal.result().finalGlobalValues().stream().allMatch(java.util.Optional::isEmpty), path);
    assertEquals(construction, normal.result().byteSequenceConstructionBytes(), path);
    assertEquals(work, normal.result().byteSequenceWorkBytes(), path);
    assertEquivalent(normal.result(), traced.result());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes(), path);
    String trace = TraceTsvFormatter.formatByteSequence(traced.events());
    assertTrue(trace.endsWith("\n"), path);
    if (path.equals(CHAPTER)) {
      assertTrue(trace.contains("バイト列:<redacted>"));
      assertTrue(trace.contains("UTF8復号失敗:<redacted>"));
      assertTrue(trace.contains("Base64復号失敗:<redacted>"));
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
    assertEquals(
        first.diagnostics().stream()
            .map(ByteSequenceCentralConformanceTest::diagnosticState)
            .toList(),
        second.diagnostics().stream()
            .map(ByteSequenceCentralConformanceTest::diagnosticState)
            .toList());
    assertEquals(first.finalDataStack(), second.finalDataStack());
    assertEquals(first.finalGlobalValues(), second.finalGlobalValues());
    assertEquals(first.executedInstructions(), second.executedInstructions());
    assertEquals(first.outputBytes(), second.outputBytes());
    assertEquals(first.errorOutputBytes(), second.errorOutputBytes());
    assertEquals(first.byteSequenceConstructionBytes(), second.byteSequenceConstructionBytes());
    assertEquals(first.byteSequenceWorkBytes(), second.byteSequenceWorkBytes());
    assertEquals(first.termination(), second.termination());
  }

  private static List<Object> diagnosticState(Diagnostic diagnostic) {
    return List.of(
        diagnostic.code(),
        diagnostic.stage(),
        diagnostic.location(),
        diagnostic.fields(),
        diagnostic.expected(),
        diagnostic.actual(),
        diagnostic.fixes(),
        diagnostic.limitName(),
        diagnostic.limit(),
        diagnostic.observed());
  }

  private static Set<String> tableIds(List<? extends Record> rows) {
    return rows.stream()
        .map(
            row ->
                row instanceof ByteSequenceConformanceData.SliceSpec slice
                    ? slice.fields().get("case_id")
                    : ((ByteSequenceConformanceData.EqualitySpec) row).fields().get("case_id"))
        .collect(Collectors.toSet());
  }

  private static Set<String> ids(String prefix, int first, int last) {
    return IntStream.rangeClosed(first, last)
        .mapToObj(number -> prefix + "%03d".formatted(number))
        .collect(Collectors.toSet());
  }

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
