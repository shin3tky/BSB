package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.WorkspaceReference;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class WorkspaceTableFileRuntimeTest {
  private static final String SOURCE = "WorkspaceTable-file-runtime.bsb";
  private static final SourceSpan CALL_SPAN = span(0, 1);
  private static final WorkspaceReference READ_REFERENCE =
      new WorkspaceReference("帳票", "read", span(20, 21), span(10, 11));
  private static final WorkspaceReference WRITE_REFERENCE =
      new WorkspaceReference("帳票", "write", span(20, 21), span(10, 11));
  private static final WorkspaceHandle HANDLE = WorkspaceHandle.opaque();
  private static final WorkspacePolicy POLICY = new WorkspacePolicy(64, 64);

  @Test
  void readsAndWritesThroughExactlyOneResolverAndFileCall() throws Exception {
    var resolverCalls = new AtomicInteger();
    var readCalls = new AtomicInteger();
    var writeCalls = new AtomicInteger();
    byte[] returned = {1, 2, 3};
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .workspaceResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  return WorkspaceResolution.resolved(name, operation, HANDLE, POLICY);
                })
            .fileReadCapability(
                request -> {
                  readCalls.incrementAndGet();
                  assertEquals("入力.dat", request.logicalName());
                  assertEquals(64, request.maximumBytes());
                  return FileReadResult.success(request, returned);
                })
            .fileWriteCapability(
                request -> {
                  writeCalls.incrementAndGet();
                  assertEquals("出力.dat", request.logicalName());
                  assertArrayEquals(new byte[] {4, 5}, request.body().copyBytes());
                  return FileWriteResult.success(request);
                })
            .build();

    Execution read = executeRead("入力.dat", environment, new ExecutionBudget(SOURCE, () -> 0L));
    returned[0] = 9;
    ResultValue readResult = assertInstanceOf(ResultValue.class, read.stack().getFirst());
    assertTrue(readResult.isSuccess());
    assertArrayEquals(new byte[] {1, 2, 3}, ((ByteSequenceValue) readResult.value()).copyBytes());
    assertEquals("file.read:帳票:read:success", read.executor().effect());

    Execution write =
        executeWrite(
            "出力.dat", new byte[] {4, 5}, environment, new ExecutionBudget(SOURCE, () -> 0L));
    ResultValue writeResult = assertInstanceOf(ResultValue.class, write.stack().getFirst());
    assertTrue(writeResult.isSuccess());
    assertEquals(BigInteger.TWO, ((IntegerValue) writeResult.value()).value());
    assertEquals("file.write:帳票:write:success", write.executor().effect());
    assertEquals(2, resolverCalls.get());
    assertEquals(1, readCalls.get());
    assertEquals(1, writeCalls.get());
  }

  @Test
  void mapsEveryPhysicalFailureToAClosedRecoverableValue() throws Exception {
    for (String kind : FileReadFailureValue.KINDS) {
      Execution execution =
          executeRead(
              "入力.dat",
              readEnvironment(request -> FileReadResult.failure(request, kind)),
              new ExecutionBudget(SOURCE, () -> 0L));
      ResultValue result = assertInstanceOf(ResultValue.class, execution.stack().getFirst());
      assertTrue(result.isFailure());
      assertEquals(kind, ((FileReadFailureValue) result.value()).kind());
    }
    for (String kind : FileWriteFailureValue.KINDS) {
      Execution execution =
          executeWrite(
              "出力.dat",
              new byte[] {1},
              writeEnvironment(request -> FileWriteResult.failure(request, kind)),
              new ExecutionBudget(SOURCE, () -> 0L));
      ResultValue result = assertInstanceOf(ResultValue.class, execution.stack().getFirst());
      assertTrue(result.isFailure());
      assertEquals(kind, ((FileWriteFailureValue) result.value()).kind());
    }
  }

  @Test
  void extractsFailureKindsWithoutRevealingHostData() throws Exception {
    var readStack =
        new ArrayList<RuntimeValue>(List.of(new FileReadFailureValue("notRegularFile")));
    executor(emptyEnvironment(), new ExecutionBudget(SOURCE, () -> 0L))
        .execute(BuiltinDictionary.find("ファイル読取失敗の種類を取り出す").orElseThrow(), readStack, CALL_SPAN);
    assertEquals(new StringValue("notRegularFile"), readStack.getFirst());

    var writeStack =
        new ArrayList<RuntimeValue>(
            List.of(new FileWriteFailureValue("atomicReplacementUnavailable")));
    executor(emptyEnvironment(), new ExecutionBudget(SOURCE, () -> 0L))
        .execute(BuiltinDictionary.find("ファイル書込失敗の種類を取り出す").orElseThrow(), writeStack, CALL_SPAN);
    assertEquals(new StringValue("atomicReplacementUnavailable"), writeStack.getFirst());
    assertEquals(
        "ファイル読取失敗:<redacted>",
        TraceValueFormatter.format(
            new FileReadFailureValue("ioFailure"), TraceValuePolicy.byteSequence()));
  }

  @Test
  void validatesLogicalNamesBeforeCapabilitiesAndNeverDisclosesThem() {
    List<String> names =
        List.of("e\u0301", "", "あ".repeat(86), "a/b", "a\\b", "a\u0000b", ".", "..");
    List<String> reasons =
        List.of(
            "notNfc",
            "empty",
            "tooLong",
            "separator",
            "separator",
            "controlCharacter",
            "dotName",
            "dotName");
    for (int index = 0; index < names.size(); index++) {
      String name = names.get(index);
      RuntimeFailure failure =
          assertThrows(
              RuntimeFailure.class,
              () -> executeRead(name, emptyEnvironment(), new ExecutionBudget(SOURCE, () -> 0L)));
      assertEquals(DiagnosticCode.E_LOGICAL_FILE_NAME_INVALID, failure.diagnostic().code());
      assertEquals(reasons.get(index), failure.diagnostic().fields().get("reason"));
      assertFalse(failure.diagnostic().fields().containsValue(name));
    }
  }

  @Test
  void requiresBothCapabilitiesBeforeReservingOrResolving() {
    var resolverCalls = new AtomicInteger();
    WorkspaceResolver resolver =
        (name, operation) -> {
          resolverCalls.incrementAndGet();
          return WorkspaceResolution.resolved(name, operation, HANDLE, POLICY);
        };
    List<ExecutionEnvironment> environments =
        List.of(
            ExecutionEnvironment.builder(() -> 0L).fileReadCapability(request -> null).build(),
            ExecutionEnvironment.builder(() -> 0L).workspaceResolver(resolver).build());
    List<String> capabilities = List.of("workspace.resolve", "file.read");
    for (int index = 0; index < environments.size(); index++) {
      var budget = new ExecutionBudget(SOURCE, () -> 0L);
      ExecutionEnvironment environment = environments.get(index);
      RuntimeFailure failure =
          assertThrows(RuntimeFailure.class, () -> executeRead("a", environment, budget));
      assertEquals(DiagnosticCode.E_CAPABILITY_UNAVAILABLE, failure.diagnostic().code());
      assertEquals(capabilities.get(index), failure.diagnostic().fields().get("capability"));
      assertEquals(0, budget.fileOperations());
    }
    assertEquals(0, resolverCalls.get());
  }

  @Test
  void classifiesWorkspaceAndMappingBoundariesWithoutCallingTheFileAgain() {
    List<WorkspaceResolution> resolutions =
        List.of(
            WorkspaceResolution.notConfigured("帳票", "read"),
            WorkspaceResolution.denied("帳票", "read"),
            WorkspaceResolution.invalid("帳票", "read", "invalidPolicy"));
    List<DiagnosticCode> codes =
        List.of(
            DiagnosticCode.E_WORKSPACE_NOT_CONFIGURED,
            DiagnosticCode.E_WORKSPACE_ACCESS_DENIED,
            DiagnosticCode.E_WORKSPACE_CONFIGURATION_INVALID);
    for (int index = 0; index < resolutions.size(); index++) {
      var fileCalls = new AtomicInteger();
      WorkspaceResolution resolution = resolutions.get(index);
      ExecutionEnvironment environment =
          ExecutionEnvironment.builder(() -> 0L)
              .workspaceResolver((name, operation) -> resolution)
              .fileReadCapability(
                  request -> {
                    fileCalls.incrementAndGet();
                    return null;
                  })
              .build();
      var budget = new ExecutionBudget(SOURCE, () -> 0L);
      RuntimeFailure failure =
          assertThrows(RuntimeFailure.class, () -> executeRead("SECRET", environment, budget));
      assertEquals(codes.get(index), failure.diagnostic().code());
      assertEquals(1, budget.fileOperations());
      assertEquals(0, fileCalls.get());
      assertFalse(failure.diagnostic().toString().contains("SECRET"));
    }

    for (FileReadCapability reader :
        List.<FileReadCapability>of(FileReadResult::notMapped, FileReadResult::accessDenied)) {
      var budget = new ExecutionBudget(SOURCE, () -> 0L);
      RuntimeFailure failure =
          assertThrows(
              RuntimeFailure.class, () -> executeRead("SECRET", readEnvironment(reader), budget));
      assertTrue(
          failure.diagnostic().code() == DiagnosticCode.E_WORKSPACE_FILE_NOT_MAPPED
              || failure.diagnostic().code() == DiagnosticCode.E_WORKSPACE_FILE_ACCESS_DENIED);
      assertEquals(1, budget.fileOperations());
      assertFalse(failure.diagnostic().toString().contains("SECRET"));
    }
  }

  @Test
  void turnsCancellationCapabilityExceptionsAndMalformedResponsesIntoDiagnostics() {
    RuntimeFailure cancelled =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executeRead(
                    "a",
                    readEnvironment(FileReadResult::cancelled),
                    new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_FILE_CANCELLED, cancelled.diagnostic().code());

    RuntimeFailure exception =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executeRead(
                    "a",
                    readEnvironment(
                        request -> {
                          throw CapabilityException.failure(RuntimeCapability.FILE_READ, "read");
                        }),
                    new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_CAPABILITY_FAILURE, exception.diagnostic().code());

    List<FileReadCapability> malformed =
        List.of(
            request -> null,
            request ->
                new FileReadResult(
                    request.workspaceName(),
                    request.handle(),
                    request.logicalName(),
                    FileReadResult.State.UNKNOWN,
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    false),
            request ->
                new FileReadResult(
                    request.workspaceName(),
                    WorkspaceHandle.opaque(),
                    request.logicalName(),
                    FileReadResult.State.NOT_MAPPED,
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    false),
            request ->
                new FileReadResult(
                    request.workspaceName(),
                    request.handle(),
                    request.logicalName(),
                    FileReadResult.State.SUCCESS,
                    Optional.of(ByteSequenceValue.copyOf(new byte[] {1})),
                    Optional.empty(),
                    1,
                    false));
    for (FileReadCapability reader : malformed) {
      RuntimeFailure failure =
          assertThrows(
              RuntimeFailure.class,
              () ->
                  executeRead("a", readEnvironment(reader), new ExecutionBudget(SOURCE, () -> 0L)));
      assertEquals(DiagnosticCode.E_CAPABILITY_FAILURE, failure.diagnostic().code());
    }

    RuntimeFailure wrongLength =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executeWrite(
                    "a",
                    new byte[] {1},
                    writeEnvironment(request -> FileWriteResult.success(request, 2)),
                    new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_CAPABILITY_FAILURE, wrongLength.diagnostic().code());
  }

  @Test
  void rejectsMalformedResolverResponsesAsCapabilityFailures() {
    List<WorkspaceResolver> malformed =
        List.of(
            (name, operation) -> null,
            (name, operation) -> WorkspaceResolution.resolved("別領域", operation, HANDLE, POLICY),
            (name, operation) ->
                new WorkspaceResolution(
                    name,
                    operation,
                    WorkspaceResolution.State.RESOLVED,
                    Optional.empty(),
                    Optional.of(POLICY),
                    Optional.empty()),
            (name, operation) -> WorkspaceResolution.invalid(name, operation, "FREE_TEXT"));
    for (WorkspaceResolver resolver : malformed) {
      ExecutionEnvironment environment =
          ExecutionEnvironment.builder(() -> 0L)
              .workspaceResolver(resolver)
              .fileReadCapability(request -> FileReadResult.success(request, new byte[0]))
              .build();
      RuntimeFailure failure =
          assertThrows(
              RuntimeFailure.class,
              () -> executeRead("a", environment, new ExecutionBudget(SOURCE, () -> 0L)));
      assertEquals(DiagnosticCode.E_CAPABILITY_FAILURE, failure.diagnostic().code());
      assertEquals("workspace.resolve", failure.diagnostic().fields().get("capability"));
    }
  }

  @Test
  void enforcesOperationAndCumulativeBudgetsAtomically() throws Exception {
    var operationBudget = new ExecutionBudget(SOURCE, () -> 0L, FileLimits.MAX_OPERATIONS, 0, 0);
    RuntimeFailure operation =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executeRead(
                    "a",
                    readEnvironment(request -> FileReadResult.success(request, new byte[0])),
                    operationBudget));
    assertEquals(DiagnosticCode.E_FILE_OPERATION_LIMIT, operation.diagnostic().code());
    assertEquals(
        Map.of(
            "word", "ファイルを読む", "limitName", "fileOperations", "limit", "4096", "observed", "4097"),
        operation.diagnostic().fields());
    assertEquals(FileLimits.MAX_OPERATIONS, operationBudget.fileOperations());

    var writeBudget =
        new ExecutionBudget(SOURCE, () -> 0L, 0, 0, FileLimits.MAX_WRITE_ATTEMPT_BYTES);
    RuntimeFailure write =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executeWrite(
                    "a", new byte[] {1}, writeEnvironment(FileWriteResult::success), writeBudget));
    assertEquals(DiagnosticCode.E_FILE_WRITE_TOTAL_LIMIT, write.diagnostic().code());
    assertEquals(
        Map.of(
            "word",
            "ファイルへ書く",
            "limitName",
            "fileWriteAttemptBytes",
            "limit",
            "134217728",
            "used",
            "134217728",
            "requested",
            "1"),
        write.diagnostic().fields());
    assertEquals(0, writeBudget.fileOperations());
    assertEquals(FileLimits.MAX_WRITE_ATTEMPT_BYTES, writeBudget.fileWriteAttemptBytes());

    var readBudget = new ExecutionBudget(SOURCE, () -> 0L, 0, FileLimits.MAX_READ_BYTES, 0);
    RuntimeFailure read =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executeRead(
                    "a",
                    readEnvironment(request -> FileReadResult.failure(request, "tooLarge")),
                    readBudget));
    assertEquals(DiagnosticCode.E_FILE_READ_TOTAL_LIMIT, read.diagnostic().code());
    assertEquals(
        Map.of(
            "word",
            "ファイルを読む",
            "limitName",
            "fileReadBytes",
            "limit",
            "134217728",
            "used",
            "134217728",
            "requested",
            "1"),
        read.diagnostic().fields());
    assertEquals(FileLimits.MAX_READ_BYTES, readBudget.fileReadBytes());

    var constructionBudget = new ExecutionBudget(SOURCE, () -> 0L);
    constructionBudget.beforeByteSequenceWork(
        ByteSequenceLimits.MAX_CONSTRUCTION_BYTES, 0, CALL_SPAN, "prepare");
    RuntimeFailure construction =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executeRead(
                    "a",
                    readEnvironment(request -> FileReadResult.success(request, new byte[] {1})),
                    constructionBudget));
    assertEquals(
        DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT, construction.diagnostic().code());
    assertEquals(0, constructionBudget.fileReadBytes());
  }

  @Test
  void appliesWritePolicyBeforeTheFileCapabilityButAfterReservation() throws Exception {
    var fileCalls = new AtomicInteger();
    WorkspacePolicy oneByte = new WorkspacePolicy(1, 1);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .workspaceResolver(
                (name, operation) -> WorkspaceResolution.resolved(name, operation, HANDLE, oneByte))
            .fileWriteCapability(
                request -> {
                  fileCalls.incrementAndGet();
                  return FileWriteResult.success(request);
                })
            .build();
    var budget = new ExecutionBudget(SOURCE, () -> 0L);

    Execution execution = executeWrite("a", new byte[] {1, 2}, environment, budget);

    ResultValue result = assertInstanceOf(ResultValue.class, execution.stack().getFirst());
    assertTrue(result.isFailure());
    assertEquals("tooLarge", ((FileWriteFailureValue) result.value()).kind());
    assertEquals(0, fileCalls.get());
    assertEquals(1, budget.fileOperations());
    assertEquals(2, budget.fileWriteAttemptBytes());
  }

  @Test
  void excludesResolverAndFileWaitingAndReportsResourceMetrics() throws Exception {
    for (String outcome : List.of("success", "failure", "cancelled", "exception")) {
      var now = new AtomicLong();
      ExecutionEnvironment environment =
          ExecutionEnvironment.builder(now::get)
              .workspaceResolver(
                  (name, operation) -> {
                    now.addAndGet(40_000_000_000L);
                    return WorkspaceResolution.resolved(name, operation, HANDLE, POLICY);
                  })
              .fileReadCapability(
                  request -> {
                    now.addAndGet(40_000_000_000L);
                    return switch (outcome) {
                      case "success" -> FileReadResult.success(request, new byte[] {1});
                      case "failure" -> FileReadResult.failure(request, "ioFailure");
                      case "cancelled" -> FileReadResult.cancelled(request);
                      case "exception" ->
                          throw CapabilityException.failure(RuntimeCapability.FILE_READ, "read");
                      default -> throw new AssertionError(outcome);
                    };
                  })
              .build();
      var budget = new ExecutionBudget(SOURCE, now::get);
      if (outcome.equals("cancelled") || outcome.equals("exception")) {
        assertThrows(RuntimeFailure.class, () -> executeRead("a", environment, budget));
      } else {
        executeRead("a", environment, budget);
      }
      assertEquals(1, budget.fileOperations());
      assertEquals(outcome.equals("success") ? 1 : 0, budget.fileReadBytes());
      now.addAndGet(30_000_000_000L);
      budget.beforeInstruction(CALL_SPAN);
      now.incrementAndGet();
      assertEquals(
          DiagnosticCode.E_EXECUTION_TIMEOUT,
          assertThrows(RuntimeFailure.class, () -> budget.beforeInstruction(CALL_SPAN))
              .diagnostic()
              .code());
    }

    var resolverNow = new AtomicLong();
    ExecutionEnvironment resolverFailure =
        ExecutionEnvironment.builder(resolverNow::get)
            .workspaceResolver(
                (name, operation) -> {
                  resolverNow.addAndGet(60_000_000_000L);
                  throw CapabilityException.failure(RuntimeCapability.WORKSPACE_RESOLVE, "resolve");
                })
            .fileReadCapability(request -> null)
            .build();
    var resolverBudget = new ExecutionBudget(SOURCE, resolverNow::get);
    assertThrows(RuntimeFailure.class, () -> executeRead("a", resolverFailure, resolverBudget));
    resolverNow.addAndGet(30_000_000_000L);
    resolverBudget.beforeInstruction(CALL_SPAN);
  }

  @Test
  void preservesSourceOrderAndDoesNotRollBackAnEarlierPublishedFile() throws Exception {
    var events = new ArrayList<String>();
    var firstPublished = new AtomicInteger();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .workspaceResolver(
                (name, operation) -> {
                  events.add("resolve:" + name + ":" + operation);
                  return WorkspaceResolution.resolved(name, operation, HANDLE, POLICY);
                })
            .fileWriteCapability(
                request -> {
                  events.add("write:" + request.logicalName());
                  if (request.logicalName().equals("first")) {
                    firstPublished.set(request.body().length());
                    return FileWriteResult.success(request);
                  }
                  return FileWriteResult.failure(request, "ioFailure");
                })
            .build();

    executeWrite("first", new byte[] {1, 2, 3}, environment, new ExecutionBudget(SOURCE, () -> 0L));
    Execution second =
        executeWrite(
            "second", new byte[] {4, 5, 6}, environment, new ExecutionBudget(SOURCE, () -> 0L));

    assertTrue(((ResultValue) second.stack().getFirst()).isFailure());
    assertEquals(3, firstPublished.get());
    assertEquals(
        List.of("resolve:帳票:write", "write:first", "resolve:帳票:write", "write:second"), events);
  }

  @Test
  void keepsSourceOrderAcrossMultipleWorkspacesAndFiles() {
    byte[] source =
        ("帳票は 作業領域。\n"
                + "画像は 作業領域。\n"
                + "メインとは （--）\n"
                + "    「one」を ファイルを読む<帳票> 結果を捨てる\n"
                + "    「two」を ファイルを読む<画像> 結果を捨てる\n"
                + "    「one」を ファイルを読む<帳票> 結果を捨てる\n"
                + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);
    var calls = new ArrayList<String>();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .workspaceResolver(
                (name, operation) -> {
                  calls.add("resolve:" + name);
                  return WorkspaceResolution.resolved(name, operation, HANDLE, POLICY);
                })
            .fileReadCapability(
                request -> {
                  calls.add("read:" + request.workspaceName() + ":" + request.logicalName());
                  return FileReadResult.success(request, new byte[0]);
                })
            .build();
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE,
                source,
                new ExecutionContext(output, () -> 0L, TraceSink.none(), environment));

    assertEquals(0, result.exitCode(), result.diagnostics().toString());
    assertEquals(3, result.fileOperations());
    assertEquals(
        List.of(
            "resolve:帳票", "read:帳票:one", "resolve:画像", "read:画像:two", "resolve:帳票", "read:帳票:one"),
        calls);
  }

  @Test
  void redactsLogicalNamesContentsHandlesAndNestedFailures() {
    String logicalSecret = "LOGICAL_SECRET";
    String contentSecret = "CONTENT_SECRET";
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .workspaceResolver(
                (name, operation) -> WorkspaceResolution.notConfigured(name, operation))
            .fileReadCapability(request -> null)
            .build();
    byte[] source =
        ("帳票は 作業領域。\n"
                + "メインとは （--）\n"
                + "    「"
                + logicalSecret
                + "」を ファイルを読む<帳票> 結果を捨てる\n"
                + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE,
                source,
                new ExecutionContext(output, () -> 0L, TraceSink.none(), environment));

    assertEquals(10, result.exitCode());
    String visible = result.diagnostics() + output.utf8Text();
    assertFalse(visible.contains(logicalSecret));
    assertFalse(visible.contains(contentSecret));
    assertFalse(HANDLE.toString().contains(logicalSecret));

    ResultValue wrapped =
        ResultValue.failure(
            jp.bsb.stdlib.ValueType.resultOf(
                jp.bsb.stdlib.ValueType.BYTE_SEQUENCE, jp.bsb.stdlib.ValueType.FILE_READ_FAILURE),
            new FileReadFailureValue("ioFailure"));
    assertEquals(
        "結果<バイト列,ファイル読取失敗>:<redacted>",
        TraceValueFormatter.format(wrapped, TraceValuePolicy.byteSequence()));
  }

  @Test
  void exposesFileBudgetMetricsThroughThePublicRunResult() {
    byte[] source =
        ("帳票は 作業領域。\n"
                + "メインとは （--）\n"
                + "    「input」を ファイルを読む<帳票> 結果を捨てる\n"
                + "    「output」と 空のバイト列 を ファイルへ書く<帳票> 結果を捨てる\n"
                + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .workspaceResolver(
                (name, operation) -> WorkspaceResolution.resolved(name, operation, HANDLE, POLICY))
            .fileReadCapability(request -> FileReadResult.success(request, new byte[] {1, 2, 3}))
            .fileWriteCapability(FileWriteResult::success)
            .build();
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE,
                source,
                new ExecutionContext(output, () -> 0L, TraceSink.none(), environment));

    assertEquals(0, result.exitCode(), result.diagnostics().toString());
    assertEquals(2, result.fileOperations());
    assertEquals(3, result.fileReadBytes());
    assertEquals(0, result.fileWriteAttemptBytes());
  }

  @Test
  void emitsOneRedactedFileCapabilityEventWithoutLogicalNameOrContent() {
    String logicalSecret = "LOGICAL_SECRET_TRACE";
    String contentSecret = "CONTENT_SECRET_TRACE";
    byte[] source =
        ("帳票は 作業領域。\n"
                + "メインとは （--）\n"
                + "    「"
                + logicalSecret
                + "」を ファイルを読む<帳票> 結果を捨てる\n"
                + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);
    var events = new ArrayList<TraceEvent>();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .workspaceResolver(
                (name, operation) -> WorkspaceResolution.resolved(name, operation, HANDLE, POLICY))
            .fileReadCapability(
                request ->
                    FileReadResult.success(request, contentSecret.getBytes(StandardCharsets.UTF_8)))
            .redactWorkspaceNamesInTrace()
            .build();
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(SOURCE, source, new ExecutionContext(output, () -> 0L, events::add, environment));

    assertEquals(0, result.exitCode(), result.diagnostics().toString());
    List<TraceEvent> fileEvents =
        events.stream().filter(event -> event.effect().startsWith("file.read:")).toList();
    assertEquals(1, fileEvents.size());
    assertEquals("file.read:<redacted>:read:success", fileEvents.getFirst().effect());
    String visible = TraceTsvFormatter.formatNestedArray(events);
    assertFalse(visible.contains(logicalSecret));
    assertFalse(visible.contains(contentSecret));
  }

  private static Execution executeRead(
      String logicalName, ExecutionEnvironment environment, ExecutionBudget budget)
      throws RuntimeFailure {
    var stack = new ArrayList<RuntimeValue>(List.of(new StringValue(logicalName)));
    BuiltinExecutor executor = executor(environment, budget);
    executor.execute(
        BuiltinDictionary.find("ファイルを読む").orElseThrow(),
        stack,
        CALL_SPAN,
        Optional.empty(),
        Optional.empty(),
        Optional.of(READ_REFERENCE));
    return new Execution(stack, executor);
  }

  private static Execution executeWrite(
      String logicalName, byte[] body, ExecutionEnvironment environment, ExecutionBudget budget)
      throws RuntimeFailure {
    var stack =
        new ArrayList<RuntimeValue>(
            List.of(new StringValue(logicalName), ByteSequenceValue.copyOf(body)));
    BuiltinExecutor executor = executor(environment, budget);
    executor.execute(
        BuiltinDictionary.find("ファイルへ書く").orElseThrow(),
        stack,
        CALL_SPAN,
        Optional.empty(),
        Optional.empty(),
        Optional.of(WRITE_REFERENCE));
    return new Execution(stack, executor);
  }

  private static BuiltinExecutor executor(
      ExecutionEnvironment environment, ExecutionBudget budget) {
    return new BuiltinExecutor(
        SOURCE, new BoundedOutput(SOURCE, new MemoryOutputSink()), budget, environment);
  }

  private static ExecutionEnvironment readEnvironment(FileReadCapability reader) {
    return ExecutionEnvironment.builder(() -> 0L)
        .workspaceResolver(
            (name, operation) -> WorkspaceResolution.resolved(name, operation, HANDLE, POLICY))
        .fileReadCapability(reader)
        .build();
  }

  private static ExecutionEnvironment writeEnvironment(FileWriteCapability writer) {
    return ExecutionEnvironment.builder(() -> 0L)
        .workspaceResolver(
            (name, operation) -> WorkspaceResolution.resolved(name, operation, HANDLE, POLICY))
        .fileWriteCapability(writer)
        .build();
  }

  private static ExecutionEnvironment emptyEnvironment() {
    return ExecutionEnvironment.builder(() -> 0L).build();
  }

  private static SourceSpan span(int start, int end) {
    return new SourceSpan(
        new SourcePosition(start, 1, start + 1), new SourcePosition(end, 1, end + 1));
  }

  private record Execution(ArrayList<RuntimeValue> stack, BuiltinExecutor executor) {}
}
