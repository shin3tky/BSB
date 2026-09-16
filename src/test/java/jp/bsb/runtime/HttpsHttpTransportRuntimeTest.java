package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.LogicalConnectionReference;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class HttpsHttpTransportRuntimeTest {
  private static final String SOURCE = "https-http-transport.bsb";
  private static final SourceSpan CALL_SPAN = span(0, 1);
  private static final SourceSpan ARGUMENT_SPAN = span(30, 31);
  private static final LogicalConnectionReference POST_REFERENCE =
      new LogicalConnectionReference(
          "顧客管理API", "resolve", span(100, 101), ARGUMENT_SPAN, Optional.of("POST"));

  @Test
  void resolvesBuildsAndSendsOneImmutableRequestAsASuccessResult() throws Exception {
    var resolverCalls = new AtomicInteger();
    var transportCalls = new AtomicInteger();
    ConnectionPolicy policy = policy("none", Optional.empty(), List.of("POST"), 32, 64);
    ExecutionEnvironment environment =
        environment(
            (name, operation) -> {
              resolverCalls.incrementAndGet();
              assertEquals("resolve", operation);
              return ConnectionResolution.resolved(name, policy);
            },
            request -> {
              transportCalls.incrementAndGet();
              assertEquals("顧客管理API", request.connectionName());
              assertEquals("POST", request.method());
              assertEquals(
                  URI.create("https://api.example.test/root/%E9%A1%A7%E5%AE%A2?q=a%20b"),
                  request.targetUri());
              assertEquals(
                  List.of(new HttpTransportHeader("accept", "application/json")),
                  request.headers());
              assertArrayEquals(
                  "本文".getBytes(StandardCharsets.UTF_8), request.bodyBytes().orElseThrow());
              assertSame(policy, request.policy());
              assertEquals(64, request.responseBodyLimit());
              return HttpTransportResult.response(
                  request.connectionName(),
                  request.method(),
                  404,
                  List.of(
                      new HttpTransportHeader("Set-Cookie", "a=1"),
                      new HttpTransportHeader("set-cookie", "b=2")),
                  new byte[] {0, (byte) 0xff});
            });
    HttpRequestValue request =
        HttpRequestValue.empty()
            .withPath("顧客")
            .withQueryItem("q", "a b")
            .withHeader("accept", "application/json")
            .withBody(
                HttpRequestValue.BodyKind.STRING,
                ByteSequenceValue.copyOf("本文".getBytes(StandardCharsets.UTF_8)));
    var execution =
        execute(request, environment, POST_REFERENCE, new ExecutionBudget(SOURCE, () -> 0L));

    assertEquals(1, resolverCalls.get());
    assertEquals(1, transportCalls.get());
    ResultValue result = assertInstanceOf(ResultValue.class, execution.stack().getFirst());
    assertTrue(result.isSuccess());
    HttpResponseValue response = assertInstanceOf(HttpResponseValue.class, result.value());
    assertEquals(404, response.status());
    assertEquals(List.of("a=1", "b=2"), response.headerValues("SET-COOKIE"));
    assertArrayEquals(new byte[] {0, (byte) 0xff}, response.body().copyBytes());
    assertEquals("http.send:顧客管理API:POST:response", execution.executor().effect());
  }

  @ParameterizedTest
  @MethodSource("failureKinds")
  void mapsAllTenTransportFailuresToRecoverableResults(String kind) throws Exception {
    ConnectionPolicy policy = policy("none", Optional.empty(), List.of("POST"), 1, 1);
    ExecutionEnvironment environment =
        environment(
            (name, operation) -> ConnectionResolution.resolved(name, policy),
            request ->
                HttpTransportResult.failure(request.connectionName(), request.method(), kind));

    Execution execution =
        execute(
            HttpRequestValue.empty(),
            environment,
            POST_REFERENCE,
            new ExecutionBudget(SOURCE, () -> 0L));

    ResultValue result = assertInstanceOf(ResultValue.class, execution.stack().getFirst());
    assertTrue(result.isFailure());
    assertEquals(kind, ((HttpSendFailureValue) result.value()).kind());
    assertEquals("http.send:顧客管理API:POST:failure:" + kind, execution.executor().effect());
  }

  @Test
  void mapsEveryFinalStatusClassToSuccessfulResponses() throws Exception {
    ConnectionPolicy policy = policy("none", Optional.empty(), List.of("POST"), 1, 1);
    for (int status : List.of(200, 204, 302, 404, 500, 599)) {
      Execution execution =
          execute(
              HttpRequestValue.empty(),
              environment(
                  (name, operation) -> ConnectionResolution.resolved(name, policy),
                  request ->
                      HttpTransportResult.response(
                          request.connectionName(),
                          request.method(),
                          status,
                          List.of(),
                          new byte[0])),
              POST_REFERENCE,
              new ExecutionBudget(SOURCE, () -> 0L));

      ResultValue result = assertInstanceOf(ResultValue.class, execution.stack().getFirst());
      assertTrue(result.isSuccess());
      assertEquals(status, ((HttpResponseValue) result.value()).status());
    }
  }

  @Test
  void requiresBothCapabilitiesBeforeResolvingAndKeepsTheRequestOnFailure() {
    var calls = new AtomicInteger();
    ConnectionResolver resolver =
        (name, operation) -> {
          calls.incrementAndGet();
          return ConnectionResolution.resolved(
              name, policy("none", Optional.empty(), List.of("POST"), 1, 1));
        };
    var noResolver = ExecutionEnvironment.builder(() -> 0L).httpTransport(request -> null).build();
    var request = HttpRequestValue.empty();
    RuntimeFailure missingResolver =
        assertThrows(
            RuntimeFailure.class,
            () ->
                execute(
                    request, noResolver, POST_REFERENCE, new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_CAPABILITY_UNAVAILABLE, missingResolver.diagnostic().code());
    assertEquals("connection.resolve", missingResolver.diagnostic().fields().get("capability"));

    var noTransport = ExecutionEnvironment.builder(() -> 0L).connectionResolver(resolver).build();
    RuntimeFailure missingTransport =
        assertThrows(
            RuntimeFailure.class,
            () ->
                execute(
                    request, noTransport, POST_REFERENCE, new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_CAPABILITY_UNAVAILABLE, missingTransport.diagnostic().code());
    assertEquals("http.send", missingTransport.diagnostic().fields().get("capability"));
    assertEquals(0, calls.get());
  }

  @Test
  void preservesResolverFailureStatesAndNeverCallsTransport() {
    var transportCalls = new AtomicInteger();
    List<ConnectionResolution> resolutions =
        List.of(
            ConnectionResolution.notConfigured("顧客管理API"),
            ConnectionResolution.denied("顧客管理API"),
            ConnectionResolution.invalid("顧客管理API", "BASE_URI_INVALID"));
    List<DiagnosticCode> codes =
        List.of(
            DiagnosticCode.E_LOGICAL_CONNECTION_NOT_CONFIGURED,
            DiagnosticCode.E_LOGICAL_CONNECTION_ACCESS_DENIED,
            DiagnosticCode.E_LOGICAL_CONNECTION_CONFIGURATION_INVALID);
    for (int index = 0; index < resolutions.size(); index++) {
      ConnectionResolution resolution = resolutions.get(index);
      ExecutionEnvironment environment =
          environment(
              (name, operation) -> resolution,
              request -> {
                transportCalls.incrementAndGet();
                return null;
              });
      RuntimeFailure failure =
          assertThrows(
              RuntimeFailure.class,
              () ->
                  execute(
                      HttpRequestValue.empty(),
                      environment,
                      POST_REFERENCE,
                      new ExecutionBudget(SOURCE, () -> 0L)));
      assertEquals(codes.get(index), failure.diagnostic().code());
    }
    assertEquals(0, transportCalls.get());
  }

  @Test
  void rejectsResolverMethodBodySizeAndUnsupportedAuthenticationBeforeTransport() {
    assertPreTransportFailure(
        policy("none", Optional.empty(), List.of("GET"), 1, 1),
        HttpRequestValue.empty(),
        POST_REFERENCE,
        DiagnosticCode.E_HTTP_METHOD_NOT_ALLOWED);
    var getReference =
        new LogicalConnectionReference(
            "顧客管理API", "resolve", span(100, 101), ARGUMENT_SPAN, Optional.of("GET"));
    assertPreTransportFailure(
        policy("none", Optional.empty(), List.of("GET"), 1, 1),
        HttpRequestValue.empty()
            .withBody(HttpRequestValue.BodyKind.BYTES, ByteSequenceValue.empty()),
        getReference,
        DiagnosticCode.E_HTTP_BODY_NOT_ALLOWED);
    assertPreTransportFailure(
        policy("none", Optional.empty(), List.of("POST"), 1, 1),
        HttpRequestValue.empty()
            .withBody(HttpRequestValue.BodyKind.BYTES, ByteSequenceValue.copyOf(new byte[] {1, 2})),
        POST_REFERENCE,
        DiagnosticCode.E_HTTP_REQUEST_SIZE_LIMIT);
    assertPreTransportFailure(
        policy("oauth2", Optional.of(CredentialReference.opaque()), List.of("POST"), 1, 1),
        HttpRequestValue.empty(),
        POST_REFERENCE,
        DiagnosticCode.E_HTTP_AUTHENTICATION_UNSUPPORTED);
  }

  @Test
  void separatesCancellationCredentialStatesAndCapabilityFailure() {
    assertTransportDiagnostic(
        request -> HttpTransportResult.cancelled(request.connectionName(), request.method()),
        "none",
        Optional.empty(),
        DiagnosticCode.E_HTTP_CANCELLED,
        "cancelled");
    assertTransportDiagnostic(
        request ->
            HttpTransportResult.credentialNotConfigured(request.connectionName(), request.method()),
        "apiKey",
        Optional.of(CredentialReference.opaque()),
        DiagnosticCode.E_HTTP_CREDENTIAL_NOT_CONFIGURED,
        "credentialNotConfigured");
    assertTransportDiagnostic(
        request -> HttpTransportResult.credentialDenied(request.connectionName(), request.method()),
        "apiKey",
        Optional.of(CredentialReference.opaque()),
        DiagnosticCode.E_HTTP_CREDENTIAL_ACCESS_DENIED,
        "credentialDenied");
    assertTransportDiagnostic(
        request ->
            HttpTransportResult.credentialInvalid(
                request.connectionName(), request.method(), "HEADER_NAME_INVALID"),
        "apiKey",
        Optional.of(CredentialReference.opaque()),
        DiagnosticCode.E_HTTP_CREDENTIAL_INVALID,
        "credentialInvalid");

    HttpTransport failed =
        request -> {
          throw CapabilityException.failure(RuntimeCapability.HTTP_SEND, "send");
        };
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                execute(
                    HttpRequestValue.empty(),
                    environment(
                        (name, operation) ->
                            ConnectionResolution.resolved(
                                name, policy("none", Optional.empty(), List.of("POST"), 1, 1)),
                        failed),
                    POST_REFERENCE,
                    new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(DiagnosticCode.E_CAPABILITY_FAILURE, failure.diagnostic().code());
  }

  @Test
  void resolvesAndAppliesApiKeyOnlyInsideTheTransportBoundary() throws Exception {
    String secret = "SECRET_API_KEY_17";
    CredentialReference reference = CredentialReference.opaque();
    ConnectionPolicy policy = policy("apiKey", Optional.of(reference), List.of("POST"), 1, 1);
    HttpRequestValue request = HttpRequestValue.empty().withHeader("x-api-key", "public-value");
    ExecutionEnvironment environment =
        environment(
            (name, operation) -> ConnectionResolution.resolved(name, policy),
            transportRequest -> {
              assertTrue(
                  transportRequest.policy().credentialReference().orElseThrow() == reference);
              var applied = new java.util.LinkedHashMap<String, String>();
              transportRequest
                  .headers()
                  .forEach(header -> applied.put(header.name(), header.value()));
              applied.put("x-api-key", secret);
              assertEquals(secret, applied.get("x-api-key"));
              assertFalse(transportRequest.toString().contains(secret));
              return HttpTransportResult.response(
                  transportRequest.connectionName(),
                  transportRequest.method(),
                  200,
                  List.of(),
                  new byte[0]);
            });

    Execution execution =
        execute(request, environment, POST_REFERENCE, new ExecutionBudget(SOURCE, () -> 0L));

    assertTrue(((ResultValue) execution.stack().getFirst()).isSuccess());
    assertFalse(execution.stack().toString().contains(secret));
    assertFalse(execution.executor().effect().contains(secret));
  }

  @Test
  void transportDtosDefensivelyCopyCollectionsAndResponseBytes() {
    var headers = new ArrayList<>(List.of(new HttpTransportHeader("x-test", "one")));
    byte[] body = {1, 2, 3};
    HttpTransportResult result = HttpTransportResult.response("接続", "POST", 200, headers, body);
    headers.clear();
    body[0] = 9;

    assertEquals(1, result.headers().size());
    assertArrayEquals(new byte[] {1, 2, 3}, result.body().orElseThrow().copyBytes());
    assertThrows(
        UnsupportedOperationException.class,
        () -> result.headers().add(new HttpTransportHeader("x", "two")));
    assertEquals("<http-header>", result.headers().getFirst().toString());
    assertFalse(result.toString().contains("x-test"));
  }

  @Test
  void rejectsInvalidTransportResponsesAsInternalContractViolations() {
    ConnectionPolicy policy = policy("none", Optional.empty(), List.of("POST"), 1, 1);
    List<HttpTransport> transports =
        List.of(
            request ->
                HttpTransportResult.response("別の接続", request.method(), 200, List.of(), new byte[0]),
            request ->
                HttpTransportResult.response(
                    request.connectionName(), "PUT", 200, List.of(), new byte[0]),
            request ->
                HttpTransportResult.response(
                    request.connectionName(), request.method(), 199, List.of(), new byte[0]),
            request ->
                HttpTransportResult.response(
                    request.connectionName(), request.method(), 200, List.of(), new byte[] {1, 2}));
    for (HttpTransport transport : transports) {
      var stack = new ArrayList<RuntimeValue>(List.of(HttpRequestValue.empty()));
      var executor =
          new BuiltinExecutor(
              SOURCE,
              new BoundedOutput(SOURCE, new MemoryOutputSink()),
              new ExecutionBudget(SOURCE, () -> 0L),
              environment(
                  (name, operation) -> ConnectionResolution.resolved(name, policy), transport));

      assertThrows(
          IllegalStateException.class,
          () ->
              executor.execute(
                  BuiltinDictionary.find("HTTP要求を送信する").orElseThrow(),
                  stack,
                  CALL_SPAN,
                  Optional.empty(),
                  Optional.of(POST_REFERENCE)));
      assertEquals(List.of(HttpRequestValue.empty()), stack);
    }
  }

  @Test
  void excludesHttpWaitingFromActiveTimeForResponseFailureAndException() throws Exception {
    for (String outcome : List.of("response", "failure", "exception")) {
      var now = new AtomicLong();
      ConnectionPolicy policy = policy("none", Optional.empty(), List.of("POST"), 1, 1);
      HttpTransport transport =
          request -> {
            now.addAndGet(60_000_000_000L);
            return switch (outcome) {
              case "response" ->
                  HttpTransportResult.response(
                      request.connectionName(), request.method(), 200, List.of(), new byte[0]);
              case "failure" ->
                  HttpTransportResult.failure(
                      request.connectionName(), request.method(), "responseTimeout");
              case "exception" ->
                  throw CapabilityException.failure(RuntimeCapability.HTTP_SEND, "send");
              default -> throw new AssertionError(outcome);
            };
          };
      ExecutionEnvironment environment =
          environment((name, operation) -> ConnectionResolution.resolved(name, policy), transport);
      var budget = new ExecutionBudget(SOURCE, now::get);
      if (outcome.equals("exception")) {
        assertThrows(
            RuntimeFailure.class,
            () -> execute(HttpRequestValue.empty(), environment, POST_REFERENCE, budget));
      } else {
        execute(HttpRequestValue.empty(), environment, POST_REFERENCE, budget);
      }
      now.addAndGet(30_000_000_000L);
      budget.beforeInstruction(CALL_SPAN);
      now.incrementAndGet();
      RuntimeFailure timeout =
          assertThrows(RuntimeFailure.class, () -> budget.beforeInstruction(CALL_SPAN));
      assertEquals(DiagnosticCode.E_EXECUTION_TIMEOUT, timeout.diagnostic().code());
    }
  }

  @Test
  void tracingChangesNeitherTransportCallsNorFinalProgramState() {
    Run normal = runProgram(false, false);
    Run traced = runProgram(true, true);

    assertEquals(0, normal.result().exitCode());
    assertEquals(normal.result().termination(), traced.result().termination());
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(normal.result().executedInstructions(), traced.result().executedInstructions());
    assertEquals(1, normal.resolverCalls());
    assertEquals(1, normal.transportCalls());
    assertEquals(normal.resolverCalls(), traced.resolverCalls());
    assertEquals(normal.transportCalls(), traced.transportCalls());
    assertTrue(
        traced.events().stream()
            .anyMatch(event -> event.effect().equals("http.send:<redacted>:POST:response")));
    assertFalse(traced.events().toString().contains("api.example.test"));
  }

  @Test
  void runsTheChapterAndRequestProgramsThroughTheFakeTransport() throws Exception {
    for (String sourcePath :
        List.of(
            "tests/conformance/https/chapter/https-chapter.bsb",
            "tests/conformance/https/sources/HTTPS-N-request.bsb")) {
      var output = new MemoryOutputSink();
      var resolverCalls = new AtomicInteger();
      var transportCalls = new AtomicInteger();
      ConnectionPolicy policy = policy("none", Optional.empty(), List.of("POST"), 64, 64);
      ExecutionEnvironment environment =
          ExecutionEnvironment.builder(() -> 0L)
              .consoleOutput(output::write)
              .connectionResolver(
                  (name, operation) -> {
                    resolverCalls.incrementAndGet();
                    return ConnectionResolution.resolved(name, policy);
                  })
              .httpTransport(
                  request -> {
                    transportCalls.incrementAndGet();
                    assertTrue(request.targetUri().toASCIIString().contains("v1/"));
                    assertArrayEquals(
                        "{}".getBytes(StandardCharsets.UTF_8), request.bodyBytes().orElseThrow());
                    return HttpTransportResult.response(
                        request.connectionName(), request.method(), 200, List.of(), new byte[0]);
                  })
              .build();
      ProgramRunResult result =
          new ProgramRunner()
              .run(
                  sourcePath,
                  Files.readAllBytes(Path.of(sourcePath)),
                  new ExecutionContext(output, () -> 0L, TraceSink.none(), environment));

      assertEquals(0, result.exitCode(), sourcePath);
      assertEquals("200\n", output.utf8Text(), sourcePath);
      assertEquals(1, resolverCalls.get(), sourcePath);
      assertEquals(1, transportCalls.get(), sourcePath);
    }
  }

  private static void assertPreTransportFailure(
      ConnectionPolicy policy,
      HttpRequestValue request,
      LogicalConnectionReference reference,
      DiagnosticCode code) {
    var transportCalls = new AtomicInteger();
    ExecutionEnvironment environment =
        environment(
            (name, operation) -> ConnectionResolution.resolved(name, policy),
            transportRequest -> {
              transportCalls.incrementAndGet();
              return null;
            });
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute(request, environment, reference, new ExecutionBudget(SOURCE, () -> 0L)));
    assertEquals(code, failure.diagnostic().code());
    assertEquals(0, transportCalls.get());
  }

  private static void assertTransportDiagnostic(
      Function<HttpTransportRequest, HttpTransportResult> outcome,
      String authentication,
      Optional<CredentialReference> credential,
      DiagnosticCode code,
      String effectSuffix) {
    ConnectionPolicy policy = policy(authentication, credential, List.of("POST"), 1, 1);
    ExecutionEnvironment environment =
        environment(
            (name, operation) -> ConnectionResolution.resolved(name, policy), outcome::apply);
    var stack = new ArrayList<RuntimeValue>(List.of(HttpRequestValue.empty()));
    var executor =
        new BuiltinExecutor(
            SOURCE,
            new BoundedOutput(SOURCE, new MemoryOutputSink()),
            new ExecutionBudget(SOURCE, () -> 0L),
            environment);
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                executor.execute(
                    BuiltinDictionary.find("HTTP要求を送信する").orElseThrow(),
                    stack,
                    CALL_SPAN,
                    Optional.empty(),
                    Optional.of(POST_REFERENCE)));
    assertEquals(code, failure.diagnostic().code());
    assertEquals(List.of(HttpRequestValue.empty()), stack);
    assertTrue(executor.effect().endsWith(":" + effectSuffix));
  }

  private static Run runProgram(boolean tracing, boolean redact) {
    byte[] source =
        ("顧客管理APIは 論理接続。\n"
                + "メインとは （--）\n"
                + "    空のHTTP要求\n"
                + "    HTTP要求を送信する<顧客管理API,POST>\n"
                + "    結果を捨てる\n"
                + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);
    var resolverCalls = new AtomicInteger();
    var transportCalls = new AtomicInteger();
    ConnectionPolicy policy = policy("none", Optional.empty(), List.of("POST"), 1, 1);
    var builder =
        ExecutionEnvironment.builder(() -> 0L)
            .connectionResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  return ConnectionResolution.resolved(name, policy);
                })
            .httpTransport(
                request -> {
                  transportCalls.incrementAndGet();
                  return HttpTransportResult.response(
                      request.connectionName(), request.method(), 200, List.of(), new byte[0]);
                });
    if (redact) builder.redactLogicalConnectionNamesInTrace();
    var events = new ArrayList<TraceEvent>();
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "https-http-program.bsb",
                source,
                new ExecutionContext(
                    output, () -> 0L, tracing ? events::add : TraceSink.none(), builder.build()));
    return new Run(result, resolverCalls.get(), transportCalls.get(), List.copyOf(events));
  }

  private static Execution execute(
      HttpRequestValue request,
      ExecutionEnvironment environment,
      LogicalConnectionReference reference,
      ExecutionBudget budget)
      throws RuntimeFailure {
    var stack = new ArrayList<RuntimeValue>(List.of(request));
    var executor =
        new BuiltinExecutor(
            SOURCE, new BoundedOutput(SOURCE, new MemoryOutputSink()), budget, environment);
    executor.execute(
        BuiltinDictionary.find("HTTP要求を送信する").orElseThrow(),
        stack,
        CALL_SPAN,
        Optional.empty(),
        Optional.of(reference));
    return new Execution(stack, executor);
  }

  private static ExecutionEnvironment environment(
      ConnectionResolver resolver, HttpTransport transport) {
    return ExecutionEnvironment.builder(() -> 0L)
        .connectionResolver(resolver)
        .httpTransport(transport)
        .build();
  }

  private static ConnectionPolicy policy(
      String authentication,
      Optional<CredentialReference> credential,
      List<String> methods,
      long requestBytes,
      long responseBytes) {
    return new ConnectionPolicy(
        "https://api.example.test/root/",
        List.of("https://api.example.test"),
        methods,
        authentication,
        credential,
        1_000,
        2_000,
        requestBytes,
        responseBytes,
        "deny",
        "none");
  }

  private static Stream<String> failureKinds() {
    return Stream.of(
        "nameResolutionFailure",
        "connectTimeout",
        "connectionFailure",
        "tlsFailure",
        "responseTimeout",
        "responseTooLarge",
        "responseHeadersTooLarge",
        "contentDecodingFailure",
        "protocolFailure",
        "transportFailure");
  }

  private static SourceSpan span(int start, int end) {
    return new SourceSpan(
        new SourcePosition(start, 1, start + 1), new SourcePosition(end, 1, end + 1));
  }

  private record Execution(ArrayList<RuntimeValue> stack, BuiltinExecutor executor) {}

  private record Run(
      ProgramRunResult result, int resolverCalls, int transportCalls, List<TraceEvent> events) {}
}
