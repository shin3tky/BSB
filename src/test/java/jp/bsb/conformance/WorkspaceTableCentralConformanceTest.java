package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteResult;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProcessArguments;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import jp.bsb.runtime.WorkspaceHandle;
import jp.bsb.runtime.WorkspacePolicy;
import jp.bsb.runtime.WorkspaceResolution;
import org.junit.jupiter.api.Test;

/** 作業領域・区切り表の全70 IDを公開ソース、CSV/TSV、偽ファイル能力へ接続します。 */
class WorkspaceTableCentralConformanceTest {
  private static final Path FILE_SAMPLE = Path.of("samples/23-named-workspaces-files.bsb");
  private static final Path DELIMITED_SAMPLE = Path.of("samples/24-csv-tsv-files.bsb");
  private static final byte[] FIRST = {1, 2, 3};
  private static final byte[] SECOND = {4, 5, 6};
  private static final byte[] CSV_INPUT =
      "\uFEFF商品,個数\r\nりんご,3\r\nみかん,\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  private static final byte[] TSV_INPUT =
      "\"注\n記\"\t値\r\n空\t\"\"\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  private static final byte[] CSV_OUTPUT =
      "商品,個数\r\nりんご,3\r\nみかん,\r\n\"注\n記\",値\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

  @Test
  void centralCatalogConsumesAllSeventyIdsAndEveryVariantWithoutOrphans() throws Exception {
    Set<String> consumed = new LinkedHashSet<>();
    Set<String> variants = new LinkedHashSet<>();
    consume(WorkspaceTableConformanceData.loadLogicalNames(), consumed, variants, "logical");
    consume(WorkspaceTableConformanceData.loadOperations(), consumed, variants, "operation");
    consume(WorkspaceTableConformanceData.loadDiagnostics(), consumed, variants, "diagnostic");
    consume(WorkspaceTableConformanceData.loadCli(), consumed, variants, "cli");
    consume(WorkspaceTableConformanceData.loadExplain(), consumed, variants, "explain");
    consume(WorkspaceTableConformanceData.loadTraces(), consumed, variants, "trace");
    consume(WorkspaceTableConformanceData.loadResources(), consumed, variants, "resource");
    consume(
        WorkspaceTableConformanceData.loadDelimitedParses(), consumed, variants, "delimited-parse");
    consume(
        WorkspaceTableConformanceData.loadDelimitedWrites(), consumed, variants, "delimited-write");
    consume(
        WorkspaceTableConformanceData.loadDelimitedDiagnostics(),
        consumed,
        variants,
        "delimited-diagnostic");
    consume(
        WorkspaceTableConformanceData.loadDelimitedTraces(), consumed, variants, "delimited-trace");
    consume(
        WorkspaceTableConformanceData.loadDelimitedResources(),
        consumed,
        variants,
        "delimited-resource");
    consumed.add("WST-F013");
    consumed.add("WST-F014");
    WorkspaceTableConformanceData.loadCatalog().stream()
        .filter(item -> item.evidence().endsWith(".bsb"))
        .map(WorkspaceTableConformanceData.CatalogSpec::id)
        .forEach(consumed::add);

    Set<String> catalog =
        WorkspaceTableConformanceData.loadCatalog().stream()
            .map(WorkspaceTableConformanceData.CatalogSpec::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(catalog, consumed);
    assertEquals(70, consumed.size());
  }

  @Test
  void everyPublishedSourceMatchesItsCataloguedStaticOutcome() throws Exception {
    var outcomes = new java.util.LinkedHashMap<String, Boolean>();
    for (var item : WorkspaceTableConformanceData.loadCatalog()) {
      if (!item.scope().equals("public") || !item.evidence().endsWith(".bsb")) continue;
      boolean successful =
          outcomes.computeIfAbsent(
              item.evidence(),
              path -> {
                try {
                  return new SourceChecker()
                      .check(path, WorkspaceTableConformanceData.resourceBytes(path))
                      .successful();
                } catch (java.io.IOException failure) {
                  throw new java.io.UncheckedIOException(failure);
                }
              });
      assertEquals(item.kind().equals("normal"), successful, item.id());
    }
    assertEquals(
        Set.of(
            "sources/WST-N-declarations.bsb",
            "sources/WST-N-types.bsb",
            "chapter/workspace-files-chapter.bsb",
            "sources/WST-F-static.bsb",
            "sources/WST-N-delimited-types.bsb",
            "chapter/delimited-tables-chapter.bsb",
            "sources/WST-F-delimited-static.bsb"),
        outcomes.keySet());
  }

  @Test
  void referenceProgramIsEquivalentWithAndWithoutTraceAndDoesNotLeakValues() throws Exception {
    Observation normal = runReference(false);
    Observation traced = runReference(true);

    assertTrue(normal.result().successful(), normal.result().diagnostics().toString());
    assertArrayEquals(
        "3\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), normal.output().bytes());
    assertArrayEquals(FIRST, normal.published());
    assertTrue(normal.result().finalDataStack().isEmpty());
    assertEquals(3, normal.resolverCalls());
    assertEquals(2, normal.readCalls());
    assertEquals(1, normal.writeCalls());
    assertEquals(3, normal.result().fileOperations());
    assertEquals(6, normal.result().fileReadBytes());
    assertEquals(3, normal.result().fileWriteAttemptBytes());
    assertEquivalent(normal.result(), traced.result());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes());
    assertArrayEquals(normal.published(), traced.published());
    String publishedTrace = TraceTsvFormatter.formatNestedArray(traced.events());
    assertTrue(publishedTrace.contains("file.read:<redacted>:read:success"));
    assertTrue(publishedTrace.contains("file.write:<redacted>:write:success"));
    for (String secret : List.of("入力A.dat", "入力B.dat", "出力.dat", "[1, 2, 3]")) {
      assertFalse(publishedTrace.contains(secret));
    }
  }

  @Test
  void canonicalChapterAndUserSampleRemainTheSameReferenceProgram() throws Exception {
    byte[] chapter =
        WorkspaceTableConformanceData.resourceBytes("chapter/workspace-files-chapter.bsb");
    byte[] canonical =
        WorkspaceTableConformanceData.resourceBytes("canonical/workspace-files-chapter.bsb");
    assertArrayEquals(chapter, canonical);
    assertArrayEquals(chapter, Files.readAllBytes(FILE_SAMPLE));
    byte[] delimitedChapter =
        WorkspaceTableConformanceData.resourceBytes("chapter/delimited-tables-chapter.bsb");
    assertArrayEquals(delimitedChapter, Files.readAllBytes(DELIMITED_SAMPLE));
  }

  @Test
  void delimitedReferenceRunsEndToEndWithAndWithoutTrace() throws Exception {
    Observation normal = runDelimitedReference(false);
    Observation traced = runDelimitedReference(true);

    assertTrue(normal.result().successful(), normal.result().diagnostics().toString());
    assertArrayEquals(
        "55\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), normal.output().bytes());
    assertArrayEquals(CSV_OUTPUT, normal.published());
    assertTrue(normal.result().finalDataStack().isEmpty());
    assertEquals(3, normal.resolverCalls());
    assertEquals(2, normal.readCalls());
    assertEquals(1, normal.writeCalls());
    assertEquals(3, normal.result().fileOperations());
    assertEquals(CSV_INPUT.length + TSV_INPUT.length, normal.result().fileReadBytes());
    assertEquals(CSV_OUTPUT.length, normal.result().fileWriteAttemptBytes());
    assertEquals(25, normal.result().arrayConstructionUnits());
    assertEquals(5, normal.result().arrayElementOperationUnits());
    assertEquals(121, normal.result().byteSequenceConstructionBytes());
    assertEquals(121, normal.result().byteSequenceWorkBytes());
    assertEquals(198, normal.result().delimitedTextWorkUnits());
    assertEquals(0, normal.result().regexWorkUnits());
    assertEquals(0, normal.result().jsonConstructionUnits());
    assertEquals(0, normal.result().jsonWorkUnits());
    assertEquals(0, normal.result().httpMetadataConstructionBytes());
    assertEquals(0, normal.result().httpSendCalls());
    assertEquals(0, normal.result().httpRequestAttemptBytes());
    assertEquals(0, normal.result().httpResponseReceivedBytes());
    assertEquals(3, normal.result().outputBytes());
    assertEquals(0, normal.result().errorOutputBytes());
    assertEquivalent(normal.result(), traced.result());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes());
    assertArrayEquals(normal.published(), traced.published());
    String trace = TraceTsvFormatter.formatNestedArray(traced.events());
    assertTrue(trace.contains("file.read:<redacted>:read:success"));
    assertTrue(trace.contains("file.write:<redacted>:write:success"));
    for (String secret : List.of("商品.csv", "注記.tsv", "集計.csv", "りんご", "みかん", "注", "記")) {
      assertFalse(trace.contains(secret));
    }
  }

  private static void consume(
      List<WorkspaceTableConformanceData.RowSpec> rows,
      Set<String> consumed,
      Set<String> variants,
      String table) {
    for (var row : rows) {
      String id = row.value("case_id");
      consumed.add(id);
      assertTrue(variants.add(table + '/' + id + '/' + row.value("variant")));
    }
  }

  private static Observation runReference(boolean tracing) throws Exception {
    var published = new AtomicReference<byte[]>();
    var resolverCalls = new AtomicInteger();
    var readCalls = new AtomicInteger();
    var writeCalls = new AtomicInteger();
    var handle = WorkspaceHandle.opaque();
    var policy = new WorkspacePolicy(16_777_216, 16_777_216);
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var clock = (jp.bsb.runtime.MonotonicClock) () -> 0L;
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .processArguments(ProcessArguments.fixed(List.of("入力A.dat", "入力B.dat", "出力.dat")))
            .workspaceResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  assertEquals("帳票", name);
                  return WorkspaceResolution.resolved(name, operation, handle, policy);
                })
            .fileReadCapability(
                request -> {
                  readCalls.incrementAndGet();
                  byte[] bytes =
                      switch (request.logicalName()) {
                        case "入力A.dat" -> FIRST;
                        case "入力B.dat" -> SECOND;
                        default -> throw new AssertionError("unexpected logical file");
                      };
                  return FileReadResult.success(request, bytes);
                })
            .fileWriteCapability(
                request -> {
                  writeCalls.incrementAndGet();
                  assertEquals("出力.dat", request.logicalName());
                  published.set(request.bodyBytes());
                  return FileWriteResult.success(request);
                })
            .redactWorkspaceNamesInTrace()
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                FILE_SAMPLE,
                new ExecutionContext(
                    output, clock, tracing ? events::add : TraceSink.none(), environment));
    return new Observation(
        result,
        output,
        published.get(),
        resolverCalls.get(),
        readCalls.get(),
        writeCalls.get(),
        List.copyOf(events));
  }

  private static Observation runDelimitedReference(boolean tracing) throws Exception {
    var published = new AtomicReference<byte[]>();
    var resolverCalls = new AtomicInteger();
    var readCalls = new AtomicInteger();
    var writeCalls = new AtomicInteger();
    var inputHandle = WorkspaceHandle.opaque();
    var outputHandle = WorkspaceHandle.opaque();
    var policy = new WorkspacePolicy(16_777_216, 16_777_216);
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var clock = (jp.bsb.runtime.MonotonicClock) () -> 0L;
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .processArguments(ProcessArguments.fixed(List.of("商品.csv", "注記.tsv", "集計.csv")))
            .workspaceResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  return switch (name) {
                    case "入力" -> WorkspaceResolution.resolved(name, operation, inputHandle, policy);
                    case "出力" ->
                        WorkspaceResolution.resolved(name, operation, outputHandle, policy);
                    default -> throw new AssertionError("unexpected workspace");
                  };
                })
            .fileReadCapability(
                request -> {
                  readCalls.incrementAndGet();
                  byte[] bytes =
                      switch (request.logicalName()) {
                        case "商品.csv" -> CSV_INPUT;
                        case "注記.tsv" -> TSV_INPUT;
                        default -> throw new AssertionError("unexpected logical input");
                      };
                  return FileReadResult.success(request, bytes);
                })
            .fileWriteCapability(
                request -> {
                  writeCalls.incrementAndGet();
                  assertEquals(outputHandle, request.handle());
                  assertEquals("集計.csv", request.logicalName());
                  published.set(request.bodyBytes());
                  return FileWriteResult.success(request);
                })
            .redactWorkspaceNamesInTrace()
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                DELIMITED_SAMPLE,
                new ExecutionContext(
                    output, clock, tracing ? events::add : TraceSink.none(), environment));
    return new Observation(
        result,
        output,
        published.get(),
        resolverCalls.get(),
        readCalls.get(),
        writeCalls.get(),
        List.copyOf(events));
  }

  private static void assertEquivalent(ProgramRunResult first, ProgramRunResult second) {
    assertEquals(first.exitCode(), second.exitCode());
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.finalDataStack(), second.finalDataStack());
    assertEquals(
        first.finalGlobalValues().stream().map(value -> value.map(Object::toString)).toList(),
        second.finalGlobalValues().stream().map(value -> value.map(Object::toString)).toList());
    assertEquals(first.executedInstructions(), second.executedInstructions());
    assertEquals(first.outputBytes(), second.outputBytes());
    assertEquals(first.fileOperations(), second.fileOperations());
    assertEquals(first.fileReadBytes(), second.fileReadBytes());
    assertEquals(first.fileWriteAttemptBytes(), second.fileWriteAttemptBytes());
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
    assertEquals(first.delimitedTextWorkUnits(), second.delimitedTextWorkUnits());
    assertEquals(first.errorOutputBytes(), second.errorOutputBytes());
    assertEquals(first.termination(), second.termination());
  }

  private record Observation(
      ProgramRunResult result,
      MemoryOutputSink output,
      byte[] published,
      int resolverCalls,
      int readCalls,
      int writeCalls,
      List<TraceEvent> events) {
    private Observation {
      published = Arrays.copyOf(published, published.length);
      events = List.copyOf(events);
    }

    @Override
    public byte[] published() {
      return Arrays.copyOf(published, published.length);
    }
  }
}
