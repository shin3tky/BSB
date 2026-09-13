package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.LogicalConnectionReference;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class HttpsHttpResourceRuntimeTest {
  private static final String SOURCE = "https-http-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final SourceSpan ARGUMENT_SPAN =
      new SourceSpan(new SourcePosition(30, 1, 31), new SourcePosition(31, 1, 32));
  private static final LogicalConnectionReference POST =
      new LogicalConnectionReference(
          "顧客管理API", "resolve", ARGUMENT_SPAN, ARGUMENT_SPAN, Optional.of("POST"));

  @Test
  void allFourHttpBudgetsAcceptTheirExactBoundaryAndRejectTheNextAtomically() throws Exception {
    ExecutionBudget budget = budget(0, 0, 0, 0, 0, 0);
    budget.beforeHttpMetadata(HttpLimits.MAX_METADATA_CONSTRUCTION_BYTES, SPAN, "word");
    assertLimit(
        () -> budget.beforeHttpMetadata(1, SPAN, "word"),
        DiagnosticCode.E_HTTP_METADATA_CONSTRUCTION_LIMIT);
    assertEquals(
        HttpLimits.MAX_METADATA_CONSTRUCTION_BYTES, budget.httpMetadataConstructionBytes());

    budget.beforeHttpSend(0, SPAN, "word");
    ExecutionBudget sendLimit = budget(0, HttpLimits.MAX_SEND_CALLS, 0, 0, 0, 0);
    assertLimit(() -> sendLimit.beforeHttpSend(0, SPAN, "word"), DiagnosticCode.E_HTTP_SEND_LIMIT);
    assertEquals(HttpLimits.MAX_SEND_CALLS, sendLimit.httpSendCalls());

    ExecutionBudget request = budget(0, 0, HttpLimits.MAX_REQUEST_ATTEMPT_BYTES - 1, 0, 0, 0);
    request.beforeHttpSend(1, SPAN, "word");
    assertEquals(HttpLimits.MAX_REQUEST_ATTEMPT_BYTES, request.httpRequestAttemptBytes());
    assertLimit(
        () -> request.beforeHttpSend(1, SPAN, "word"), DiagnosticCode.E_HTTP_REQUEST_TOTAL_LIMIT);
    assertEquals(1, request.httpSendCalls());

    ExecutionBudget response = budget(0, 0, 0, HttpLimits.MAX_RESPONSE_RECEIVED_BYTES - 1, 0, 0);
    response.afterHttpResponseBytes(1, SPAN, "word");
    assertEquals(HttpLimits.MAX_RESPONSE_RECEIVED_BYTES, response.httpResponseReceivedBytes());
    assertLimit(
        () -> response.afterHttpResponseBytes(1, SPAN, "word"),
        DiagnosticCode.E_HTTP_RESPONSE_TOTAL_LIMIT);
    assertEquals(HttpLimits.MAX_RESPONSE_RECEIVED_BYTES, response.httpResponseReceivedBytes());
  }

  @Test
  void requestBuildersChargeOnlyNewMetadataAndUtf8BodyBytes() throws Exception {
    ExecutionBudget budget = budget(0, 0, 0, 0, 0, 0);
    var executor = executor(budget, ExecutionEnvironment.builder(() -> 0L).build());
    var stack =
        new ArrayList<RuntimeValue>(List.of(HttpRequestValue.empty(), new StringValue("a b")));
    execute(executor, "HTTP要求に経路を設定する", stack);
    stack.add(new StringValue("x"));
    stack.add(new StringValue("y"));
    execute(executor, "HTTP要求に問い合わせ項目を追加する", stack);
    stack.add(new StringValue("X"));
    stack.add(new StringValue("y"));
    execute(executor, "HTTP要求にヘッダーを設定する", stack);
    assertEquals(13, budget.httpMetadataConstructionBytes());

    stack.add(new StringValue("本"));
    execute(executor, "HTTP要求に文字列本文を設定する", stack);
    assertEquals(3, budget.byteSequenceConstructionBytes());
    assertEquals(3, budget.byteSequenceWorkBytes());
  }

  @Test
  void sendBudgetsRunBeforeResolverAndCommitTogetherOnlyWhenBothFit() {
    var resolverCalls = new AtomicInteger();
    ExecutionEnvironment environment =
        environment(
            resolverCalls,
            request ->
                HttpTransportResult.response(
                    request.connectionName(), request.method(), 200, List.of(), new byte[0]));
    ExecutionBudget sendLimit = budget(0, HttpLimits.MAX_SEND_CALLS, 0, 0, 0, 0);
    RuntimeFailure sendFailure =
        assertThrows(
            RuntimeFailure.class, () -> send(HttpRequestValue.empty(), sendLimit, environment));
    assertEquals(DiagnosticCode.E_HTTP_SEND_LIMIT, sendFailure.diagnostic().code());
    assertEquals(0, resolverCalls.get());

    ExecutionBudget requestLimit = budget(0, 10, HttpLimits.MAX_REQUEST_ATTEMPT_BYTES, 0, 0, 0);
    HttpRequestValue oneByte =
        HttpRequestValue.empty()
            .withBody(HttpRequestValue.BodyKind.BYTES, ByteSequenceValue.copyOf(new byte[] {1}));
    RuntimeFailure requestFailure =
        assertThrows(RuntimeFailure.class, () -> send(oneByte, requestLimit, environment));
    assertEquals(DiagnosticCode.E_HTTP_REQUEST_TOTAL_LIMIT, requestFailure.diagnostic().code());
    assertEquals(10, requestLimit.httpSendCalls());
    assertEquals(0, resolverCalls.get());
  }

  @Test
  void responseTotalAndByteConstructionFailuresKeepTheInputButRetainAcceptedReceiveWork() {
    var resolverCalls = new AtomicInteger();
    ExecutionBudget responseLimit = budget(0, 0, 0, HttpLimits.MAX_RESPONSE_RECEIVED_BYTES, 0, 0);
    var original = HttpRequestValue.empty();
    RuntimeFailure total =
        assertThrows(
            RuntimeFailure.class,
            () ->
                send(
                    original,
                    responseLimit,
                    environment(
                        resolverCalls,
                        request ->
                            HttpTransportResult.failure(
                                request.connectionName(),
                                request.method(),
                                "responseTooLarge",
                                1))));
    assertEquals(DiagnosticCode.E_HTTP_RESPONSE_TOTAL_LIMIT, total.diagnostic().code());
    assertEquals(HttpLimits.MAX_RESPONSE_RECEIVED_BYTES, responseLimit.httpResponseReceivedBytes());

    ExecutionBudget byteLimit = budget(0, 0, 0, 0, ByteSequenceLimits.MAX_CONSTRUCTION_BYTES, 0);
    RuntimeFailure bytes =
        assertThrows(
            RuntimeFailure.class,
            () ->
                send(
                    original,
                    byteLimit,
                    environment(
                        resolverCalls,
                        request ->
                            HttpTransportResult.response(
                                request.connectionName(),
                                request.method(),
                                200,
                                List.of(),
                                new byte[] {1}))));
    assertEquals(DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT, bytes.diagnostic().code());
    assertEquals(1, byteLimit.httpResponseReceivedBytes());
  }

  private static void send(
      HttpRequestValue request, ExecutionBudget budget, ExecutionEnvironment environment)
      throws RuntimeFailure {
    var stack = new ArrayList<RuntimeValue>(List.of(request));
    execute(executor(budget, environment), "HTTP要求を送信する", stack, Optional.of(POST));
  }

  private static ExecutionEnvironment environment(
      AtomicInteger resolverCalls, HttpTransport transport) {
    ConnectionPolicy policy =
        new ConnectionPolicy(
            "https://api.example.test/",
            List.of("https://api.example.test"),
            List.of("POST"),
            "none",
            Optional.empty(),
            1_000,
            2_000,
            ByteSequenceLimits.MAX_VALUE_BYTES,
            ByteSequenceLimits.MAX_VALUE_BYTES,
            "deny",
            "none");
    return ExecutionEnvironment.builder(() -> 0L)
        .connectionResolver(
            (name, operation) -> {
              resolverCalls.incrementAndGet();
              return ConnectionResolution.resolved(name, policy);
            })
        .httpTransport(transport)
        .build();
  }

  private static BuiltinExecutor executor(
      ExecutionBudget budget, ExecutionEnvironment environment) {
    return new BuiltinExecutor(
        SOURCE, new BoundedOutput(SOURCE, new MemoryOutputSink()), budget, environment);
  }

  private static void execute(BuiltinExecutor executor, String word, ArrayList<RuntimeValue> stack)
      throws RuntimeFailure {
    execute(executor, word, stack, Optional.empty());
  }

  private static void execute(
      BuiltinExecutor executor,
      String word,
      ArrayList<RuntimeValue> stack,
      Optional<LogicalConnectionReference> reference)
      throws RuntimeFailure {
    executor.execute(
        BuiltinDictionary.find(word).orElseThrow(), stack, SPAN, Optional.empty(), reference);
  }

  private static ExecutionBudget budget(
      long metadata,
      long sends,
      long requestBytes,
      long responseBytes,
      long byteConstruction,
      long byteWork) {
    return new ExecutionBudget(
        SOURCE,
        () -> 0L,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        byteConstruction,
        byteWork,
        metadata,
        sends,
        requestBytes,
        responseBytes);
  }

  private static void assertLimit(Throwing action, DiagnosticCode code) {
    RuntimeFailure failure = assertThrows(RuntimeFailure.class, action::run);
    assertEquals(code, failure.diagnostic().code());
    assertInstanceOf(String.class, failure.diagnostic().fields().get("observed"));
  }

  @FunctionalInterface
  private interface Throwing {
    void run() throws RuntimeFailure;
  }
}
