package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.LogicalConnectionReference;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class HttpReliabilityRuntimeTest {
  private static final SourceSpan SPAN = span(0, 1);
  private static final LogicalConnectionReference POST =
      new LogicalConnectionReference(
          "API", "resolve", span(5, 6), span(30, 31), Optional.of("POST"));
  private static final LogicalConnectionReference OTHER_POST =
      new LogicalConnectionReference(
          "OTHER", "resolve", span(7, 8), span(32, 33), Optional.of("POST"));

  @Test
  void retriesWithBackoffAndChargesEveryPhysicalAttempt() throws Exception {
    var calls = new AtomicInteger();
    var nanos = new AtomicLong();
    var sleeps = new ArrayList<Long>();
    ConnectionPolicy policy =
        policy(retry("apiGuaranteed", 3, List.of("connectionFailure"), List.of(), 100, 400));
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(nanos::get)
            .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, policy))
            .httpTransport(
                request ->
                    calls.incrementAndGet() < 3
                        ? HttpTransportResult.failure("API", "POST", "connectionFailure")
                        : HttpTransportResult.response(
                            "API", "POST", 201, List.of(), new byte[] {1}))
            .sleepCapability(
                milliseconds -> {
                  sleeps.add(milliseconds);
                  nanos.addAndGet(milliseconds * 1_000_000L);
                  return SleepCapability.Result.COMPLETED;
                })
            .build();
    Execution execution = execute(environment);
    assertEquals(3, calls.get());
    assertEquals(List.of(100L, 200L), sleeps);
    assertEquals(3, execution.budget().httpSendCalls());
    assertEquals(300, execution.budget().httpReliabilityWaitMilliseconds());
    assertEquals(2, execution.budget().httpRetryAttempts());
    ResultValue value = assertInstanceOf(ResultValue.class, execution.stack().getFirst());
    assertTrue(value.isSuccess());
  }

  @Test
  void honorsRetryAfterAndRecordsExhaustedStatusWithoutPayload() throws Exception {
    var nanos = new AtomicLong();
    var sleeps = new ArrayList<Long>();
    var records = new ArrayList<HttpFinalFailureRecord>();
    HttpRetryPolicy base = retry("apiGuaranteed", 2, List.of(), List.of(503), 10, 10);
    HttpRetryPolicy retry =
        new HttpRetryPolicy(
            base.mode(),
            base.maximumAttempts(),
            base.retryableFailureKinds(),
            base.retryableStatusCodes(),
            base.initialDelayMilliseconds(),
            base.maximumDelayMilliseconds(),
            base.backoffMultiplier(),
            true,
            1_500,
            base.methodSafety(),
            base.idempotencyKeyHeader(),
            0,
            "required");
    ConnectionPolicy policy = policy(retry);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(nanos::get)
            .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, policy))
            .httpTransport(
                request ->
                    HttpTransportResult.response(
                        "API",
                        "POST",
                        503,
                        List.of(new HttpTransportHeader("Retry-After", "2")),
                        "SECRET_BODY".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .sleepCapability(
                milliseconds -> {
                  sleeps.add(milliseconds);
                  nanos.addAndGet(milliseconds * 1_000_000L);
                  return SleepCapability.Result.COMPLETED;
                })
            .httpFinalFailureSink(
                record -> {
                  records.add(record);
                  return HttpFinalFailureSink.Result.RECORDED;
                })
            .build();
    Execution execution = execute(environment);
    assertEquals(List.of(1_500L), sleeps);
    assertEquals(1, records.size());
    assertEquals("httpStatus", records.getFirst().classification());
    assertEquals(503, records.getFirst().status().orElseThrow());
    assertEquals(2, execution.budget().httpSendCalls());
    assertEquals(1, execution.budget().httpFinalFailureRecords());
  }

  @Test
  void honorsStrictImfFixdateUsingWallClockOnlyWhenNeeded() throws Exception {
    var calls = new AtomicInteger();
    var sleeps = new ArrayList<Long>();
    HttpRetryPolicy base = retry("apiGuaranteed", 2, List.of(), List.of(503), 10, 10);
    HttpRetryPolicy retry =
        new HttpRetryPolicy(
            base.mode(),
            base.maximumAttempts(),
            base.retryableFailureKinds(),
            base.retryableStatusCodes(),
            base.initialDelayMilliseconds(),
            base.maximumDelayMilliseconds(),
            base.backoffMultiplier(),
            true,
            5_000,
            base.methodSafety(),
            base.idempotencyKeyHeader(),
            0,
            "disabled");
    ConnectionPolicy policy = policy(retry);
    Execution execution =
        execute(
            ExecutionEnvironment.builder(() -> 0L)
                .connectionResolver(
                    (name, operation) -> ConnectionResolution.resolved(name, policy))
                .httpTransport(
                    request ->
                        calls.getAndIncrement() == 0
                            ? HttpTransportResult.response(
                                "API",
                                "POST",
                                503,
                                List.of(
                                    new HttpTransportHeader(
                                        "retry-after", "Sun, 06 Nov 1994 08:49:37 GMT")),
                                new byte[0])
                            : HttpTransportResult.response(
                                "API", "POST", 204, List.of(), new byte[0]))
                .wallTime(() -> new WallTimeReading(784_111_775_000L, 0))
                .sleepCapability(
                    milliseconds -> {
                      sleeps.add(milliseconds);
                      return SleepCapability.Result.COMPLETED;
                    })
                .build());
    assertEquals(List.of(2_000L), sleeps);
    assertEquals(2_000, execution.budget().httpReliabilityWaitMilliseconds());
  }

  @Test
  void idempotencyKeySafetyRequiresConfiguredNonEmptyHeader() throws Exception {
    HttpRetryPolicy base = retry("apiGuaranteed", 2, List.of("connectionFailure"), List.of(), 0, 0);
    HttpRetryPolicy retry =
        new HttpRetryPolicy(
            base.mode(),
            base.maximumAttempts(),
            base.retryableFailureKinds(),
            base.retryableStatusCodes(),
            0,
            0,
            1,
            false,
            0,
            "idempotencyKey",
            Optional.of("idempotency-key"),
            0,
            "disabled");
    var calls = new AtomicInteger();
    ConnectionPolicy policy = policy(retry);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, policy))
            .httpTransport(
                request -> {
                  calls.incrementAndGet();
                  return HttpTransportResult.failure("API", "POST", "connectionFailure");
                })
            .build();
    execute(environment, HttpRequestValue.empty());
    assertEquals(1, calls.get());
    execute(environment, HttpRequestValue.empty().withHeader("idempotency-key", "request-1"));
    assertEquals(3, calls.get());
  }

  @Test
  void minimumStartIntervalIsKeptPerConnection() throws Exception {
    var nanos = new AtomicLong();
    var sleeps = new ArrayList<Long>();
    HttpRetryPolicy base = retry("apiGuaranteed", 2, List.of(), List.of(), 0, 0);
    HttpRetryPolicy limited =
        new HttpRetryPolicy(
            base.mode(),
            base.maximumAttempts(),
            base.retryableFailureKinds(),
            base.retryableStatusCodes(),
            0,
            0,
            1,
            false,
            0,
            base.methodSafety(),
            Optional.empty(),
            100,
            "disabled");
    ConnectionPolicy policy = policy(limited);
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(nanos::get)
            .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, policy))
            .httpTransport(
                request ->
                    HttpTransportResult.response(
                        request.connectionName(), request.method(), 200, List.of(), new byte[0]))
            .sleepCapability(
                milliseconds -> {
                  sleeps.add(milliseconds);
                  nanos.addAndGet(milliseconds * 1_000_000L);
                  return SleepCapability.Result.COMPLETED;
                })
            .build();
    var budget = new ExecutionBudget("http-rate-limit.bsb", environment.resourceClock());
    var executor =
        new BuiltinExecutor(
            "http-rate-limit.bsb",
            new BoundedOutput("http-rate-limit.bsb", new MemoryOutputSink()),
            budget,
            environment);
    execute(executor, POST);
    execute(executor, OTHER_POST);
    execute(executor, POST);
    assertEquals(List.of(100L), sleeps);
    assertEquals(3, budget.httpSendCalls());
  }

  @Test
  void safeOnlyDoesNotRetryPost() throws Exception {
    var calls = new AtomicInteger();
    ConnectionPolicy policy =
        policy(retry("safeOnly", 3, List.of("connectionFailure"), List.of(), 0, 0));
    Execution execution =
        execute(
            ExecutionEnvironment.builder(() -> 0L)
                .connectionResolver(
                    (name, operation) -> ConnectionResolution.resolved(name, policy))
                .httpTransport(
                    request -> {
                      calls.incrementAndGet();
                      return HttpTransportResult.failure("API", "POST", "connectionFailure");
                    })
                .build());
    assertEquals(1, calls.get());
    assertTrue(((ResultValue) execution.stack().getFirst()).isFailure());
  }

  @Test
  void reportsMissingWaitAndRequiredFinalSink() {
    ConnectionPolicy delayed =
        policy(retry("apiGuaranteed", 2, List.of("connectionFailure"), List.of(), 1, 1));
    RuntimeFailure wait = assertThrows(RuntimeFailure.class, () -> execute(environment(delayed)));
    assertEquals(DiagnosticCode.E_HTTP_RETRY_WAIT_UNAVAILABLE, wait.diagnostic().code());
    HttpRetryPolicy base = retry("apiGuaranteed", 2, List.of("connectionFailure"), List.of(), 0, 0);
    HttpRetryPolicy required =
        new HttpRetryPolicy(
            base.mode(),
            2,
            base.retryableFailureKinds(),
            base.retryableStatusCodes(),
            0,
            0,
            1,
            false,
            0,
            base.methodSafety(),
            Optional.empty(),
            0,
            "required");
    RuntimeFailure sink =
        assertThrows(RuntimeFailure.class, () -> execute(environment(policy(required))));
    assertEquals(DiagnosticCode.E_HTTP_FINAL_FAILURE_UNAVAILABLE, sink.diagnostic().code());
  }

  @Test
  void reportsWaitAndFinalSinkFailureOrCancellation() {
    ConnectionPolicy delayed =
        policy(retry("apiGuaranteed", 2, List.of("connectionFailure"), List.of(), 1, 1));
    ExecutionEnvironment waitFailure =
        ExecutionEnvironment.builder(() -> 0L)
            .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, delayed))
            .httpTransport(
                request -> HttpTransportResult.failure("API", "POST", "connectionFailure"))
            .sleepCapability(
                milliseconds -> {
                  throw CapabilityException.failure(RuntimeCapability.TIME_SLEEP, "sleep");
                })
            .build();
    RuntimeFailure failedWait = assertThrows(RuntimeFailure.class, () -> execute(waitFailure));
    assertEquals(DiagnosticCode.E_HTTP_RETRY_WAIT_FAILURE, failedWait.diagnostic().code());

    HttpRetryPolicy base = retry("apiGuaranteed", 2, List.of("connectionFailure"), List.of(), 0, 0);
    HttpRetryPolicy required =
        new HttpRetryPolicy(
            "bounded",
            2,
            base.retryableFailureKinds(),
            base.retryableStatusCodes(),
            0,
            0,
            1,
            false,
            0,
            base.methodSafety(),
            Optional.empty(),
            0,
            "required");
    ExecutionEnvironment cancelledSink =
        ExecutionEnvironment.builder(() -> 0L)
            .connectionResolver(
                (name, operation) -> ConnectionResolution.resolved(name, policy(required)))
            .httpTransport(
                request -> HttpTransportResult.failure("API", "POST", "connectionFailure"))
            .httpFinalFailureSink(record -> HttpFinalFailureSink.Result.CANCELLED)
            .build();
    RuntimeFailure cancelled = assertThrows(RuntimeFailure.class, () -> execute(cancelledSink));
    assertEquals(DiagnosticCode.E_HTTP_FINAL_FAILURE_CANCELLED, cancelled.diagnostic().code());
  }

  @Test
  void validatesPolicyBoundaryAndRedactsItsRepresentation() {
    assertEquals(Optional.empty(), HttpRetryPolicies.validationProblem(HttpRetryPolicy.none()));
    assertEquals(
        Optional.empty(),
        HttpRetryPolicies.validationProblem(retry("apiGuaranteed", 1, List.of(), List.of(), 0, 0)));
    HttpRetryPolicy invalid = retry("apiGuaranteed", 9, List.of(), List.of(), 0, 0);
    assertEquals(
        Optional.of("ATTEMPT_LIMIT_INVALID"), HttpRetryPolicies.validationProblem(invalid));
    assertEquals("<http-retry-policy>", invalid.toString());
  }

  @Test
  void waitAndFinalRecordBudgetsRejectOneBeyondTheirExactLimits() throws Exception {
    var budget = new ExecutionBudget("http-limits.bsb", () -> 0L);
    budget.beforeHttpReliabilityWait(
        HttpLimits.MAX_RELIABILITY_WAIT_MILLISECONDS, SPAN, "HTTP要求を送信する");
    RuntimeFailure wait =
        assertThrows(
            RuntimeFailure.class, () -> budget.beforeHttpReliabilityWait(1, SPAN, "HTTP要求を送信する"));
    assertEquals(DiagnosticCode.E_HTTP_RETRY_WAIT_LIMIT, wait.diagnostic().code());
    for (int index = 0; index < HttpLimits.MAX_FINAL_FAILURE_RECORDS; index++) {
      budget.beforeHttpFinalFailureRecord(SPAN, "HTTP要求を送信する");
    }
    RuntimeFailure records =
        assertThrows(
            RuntimeFailure.class, () -> budget.beforeHttpFinalFailureRecord(SPAN, "HTTP要求を送信する"));
    assertEquals(DiagnosticCode.E_HTTP_FINAL_FAILURE_LIMIT, records.diagnostic().code());
  }

  @Test
  void publicProgramResultCarriesRetryMetricsAndKeepsOneFinalTraceEvent() {
    Run normal = runProgram(false);
    Run traced = runProgram(true);
    for (Run run : List.of(normal, traced)) {
      assertEquals(0, run.result().exitCode());
      assertEquals(2, run.result().httpSendCalls());
      assertEquals(1, run.result().httpRetryAttempts());
      assertEquals(0, run.result().httpReliabilityWaitMilliseconds());
      assertEquals(0, run.result().httpFinalFailureRecords());
    }
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(
        1,
        traced.events().stream()
            .filter(event -> event.effect().equals("http.send:API:POST:response"))
            .count());
  }

  private static ExecutionEnvironment environment(ConnectionPolicy policy) {
    return ExecutionEnvironment.builder(() -> 0L)
        .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, policy))
        .httpTransport(request -> HttpTransportResult.failure("API", "POST", "connectionFailure"))
        .build();
  }

  private static Execution execute(ExecutionEnvironment environment) throws RuntimeFailure {
    return execute(environment, HttpRequestValue.empty());
  }

  private static Execution execute(ExecutionEnvironment environment, HttpRequestValue request)
      throws RuntimeFailure {
    var stack = new ArrayList<RuntimeValue>(List.of(request));
    var budget = new ExecutionBudget("http-reliability.bsb", environment.resourceClock());
    var executor =
        new BuiltinExecutor(
            "http-reliability.bsb",
            new BoundedOutput("http-reliability.bsb", new MemoryOutputSink()),
            budget,
            environment);
    executor.execute(
        BuiltinDictionary.find("HTTP要求を送信する").orElseThrow(),
        stack,
        SPAN,
        Optional.empty(),
        Optional.of(POST));
    return new Execution(stack, budget);
  }

  private static void execute(BuiltinExecutor executor, LogicalConnectionReference reference)
      throws RuntimeFailure {
    var stack = new ArrayList<RuntimeValue>(List.of(HttpRequestValue.empty()));
    executor.execute(
        BuiltinDictionary.find("HTTP要求を送信する").orElseThrow(),
        stack,
        SPAN,
        Optional.empty(),
        Optional.of(reference));
  }

  private static Run runProgram(boolean tracing) {
    byte[] source =
        ("APIは 論理接続。\n"
                + "メインとは （--）\n"
                + "    空のHTTP要求\n"
                + "    HTTP要求を送信する<API,POST>\n"
                + "    結果を捨てる\n"
                + "こと。\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var calls = new AtomicInteger();
    ConnectionPolicy policy =
        policy(retry("apiGuaranteed", 2, List.of("connectionFailure"), List.of(), 0, 0));
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, policy))
            .httpTransport(
                request ->
                    calls.getAndIncrement() == 0
                        ? HttpTransportResult.failure("API", "POST", "connectionFailure")
                        : HttpTransportResult.response("API", "POST", 200, List.of(), new byte[0]))
            .build();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "http-reliability-program.bsb",
                source,
                new ExecutionContext(
                    new MemoryOutputSink(),
                    () -> 0L,
                    tracing ? events::add : TraceSink.none(),
                    environment));
    return new Run(result, List.copyOf(events));
  }

  private static ConnectionPolicy policy(HttpRetryPolicy retry) {
    return new ConnectionPolicy(
        "https://api.example.test/",
        List.of("https://api.example.test"),
        List.of("POST"),
        "none",
        Optional.empty(),
        1_000,
        2_000,
        1_024,
        1_024,
        "deny",
        retry.mode(),
        retry);
  }

  private static HttpRetryPolicy retry(
      String safety,
      int attempts,
      List<String> failures,
      List<Integer> statuses,
      long initial,
      long maximum) {
    return new HttpRetryPolicy(
        "bounded",
        attempts,
        failures,
        statuses,
        initial,
        maximum,
        2,
        false,
        0,
        safety,
        Optional.empty(),
        0,
        "disabled");
  }

  private static SourceSpan span(int start, int end) {
    return new SourceSpan(
        new SourcePosition(start, 1, start + 1), new SourcePosition(end, 1, end + 1));
  }

  private record Execution(ArrayList<RuntimeValue> stack, ExecutionBudget budget) {}

  private record Run(ProgramRunResult result, List<TraceEvent> events) {}
}
