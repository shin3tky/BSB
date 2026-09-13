package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectionLogicalConnectionRuntimeTest {
  private static final byte[] SOURCE =
      ("顧客管理APIは 論理接続。\n" + "メインとは （--）\n" + "    論理接続を確認する<顧客管理API>\n" + "こと。\n")
          .getBytes(StandardCharsets.UTF_8);

  @Test
  void resolvesOnceWithoutChangingProgramState() {
    var calls = new AtomicInteger();
    Run run =
        run(
            (name, operation) -> {
              assertEquals("顧客管理API", name);
              assertEquals("resolve", operation);
              calls.incrementAndGet();
              return ConnectionResolution.resolved(name, validPolicy());
            },
            true,
            false);

    assertEquals(0, run.result().exitCode());
    assertEquals(1, calls.get());
    assertEquals(List.of(), run.result().finalDataStack());
    assertEquals(List.of(), run.result().finalGlobalValues());
    assertEquals("", run.output().utf8Text());
    assertEquals(1, capabilityEvents(run).size());
    assertEquals("connection.resolve|顧客管理API|resolved", capabilityEvents(run).getFirst().effect());
  }

  @Test
  void dictionaryAppendsTheParameterizedCapabilityWord() {
    var word = BuiltinDictionary.find("論理接続を確認する").orElseThrow();

    assertEquals("論理接続を確認する", word.canonicalName());
    assertEquals("CONN", word.featureGroup());
    assertEquals(java.util.Set.of("connection.resolve"), word.capabilities());
    assertEquals(word.capabilities(), word.sideEffects());
    assertEquals(
        RuntimeCapability.CONNECTION_RESOLVE,
        List.copyOf(
                ExecutionEnvironment.builder(() -> 0L)
                    .connectionResolver((name, operation) -> ConnectionResolution.denied(name))
                    .build()
                    .capabilities())
            .getLast());
  }

  @Test
  void distinguishesClosedFailureStatesAndKeepsTheirEvents() {
    Run missing = run((name, operation) -> ConnectionResolution.notConfigured(name), true, false);
    Run denied = run((name, operation) -> ConnectionResolution.denied(name), true, false);
    Run invalid =
        run(
            (name, operation) -> ConnectionResolution.invalid(name, "BASE_URI_INVALID"),
            true,
            false);

    assertFailure(missing, DiagnosticCode.E_LOGICAL_CONNECTION_NOT_CONFIGURED, "notConfigured");
    assertFailure(denied, DiagnosticCode.E_LOGICAL_CONNECTION_ACCESS_DENIED, "denied");
    assertFailure(invalid, DiagnosticCode.E_LOGICAL_CONNECTION_CONFIGURATION_INVALID, "invalid");
    assertEquals(
        "BASE_URI_INVALID", invalid.result().diagnostics().getFirst().fields().get("reason"));
  }

  @Test
  void absenceAndCapabilityFailureUseExistingDiagnosticsAndNeverLeakHostText() {
    Run absent = runWithoutResolver(true);
    String marker = "HOST_FAILURE_SECRET_15";
    Run failure =
        run(
            (name, operation) -> {
              throw CapabilityException.failure(RuntimeCapability.CONNECTION_RESOLVE, operation);
            },
            true,
            false);

    assertEquals(DiagnosticCode.E_CAPABILITY_UNAVAILABLE, diagnostic(absent).code());
    assertTrue(capabilityEvents(absent).isEmpty());
    assertFailure(failure, DiagnosticCode.E_CAPABILITY_FAILURE, "failure");
    assertFalse(failure.result().toString().contains(marker));
  }

  @Test
  void contractViolationsRemainInternalAndResolverIsNeverRetried() {
    var calls = new AtomicInteger();
    ConnectionResolver nullResolver =
        (name, operation) -> {
          calls.incrementAndGet();
          return null;
        };

    assertThrows(IllegalStateException.class, () -> run(nullResolver, true, false));
    assertEquals(1, calls.get());
    assertThrows(
        IllegalStateException.class,
        () -> run((name, operation) -> ConnectionResolution.notConfigured("別名"), false, false));
    assertThrows(
        IllegalStateException.class,
        () ->
            run(
                (name, operation) -> ConnectionResolution.invalid(name, "HOST_TEXT"),
                false,
                false));
  }

  @Test
  void strongerTracePolicyRedactsOnlyTheSourceVisibleName() {
    Run run =
        run((name, operation) -> ConnectionResolution.resolved(name, validPolicy()), true, true);

    assertEquals(
        "connection.resolve|<redacted>|resolved", capabilityEvents(run).getFirst().effect());
    assertEquals(0, run.result().exitCode());
  }

  @ParameterizedTest
  @MethodSource("invalidPolicies")
  void validatesResolvedPoliciesInNormativeOrder(String expectedReason, ConnectionPolicy policy) {
    Run run = run((name, operation) -> ConnectionResolution.resolved(name, policy), true, false);

    assertFailure(run, DiagnosticCode.E_LOGICAL_CONNECTION_CONFIGURATION_INVALID, "invalid");
    assertEquals(expectedReason, diagnostic(run).fields().get("reason"));
  }

  @Test
  void policyAndCredentialReferenceAreImmutableAndSafelyPrintable() {
    var origins = new ArrayList<>(List.of("https://api.example.test"));
    var methods = new ArrayList<>(List.of("GET"));
    CredentialReference credential = CredentialReference.opaque();
    ConnectionPolicy policy =
        policy(
            "https://api.example.test/",
            origins,
            methods,
            "basic",
            Optional.of(credential),
            1,
            1,
            1,
            1,
            "deny",
            "none");
    origins.clear();
    methods.clear();

    assertEquals(List.of("https://api.example.test"), policy.allowedOrigins());
    assertEquals(List.of("GET"), policy.allowedMethods());
    assertThrows(UnsupportedOperationException.class, () -> policy.allowedMethods().add("POST"));
    assertEquals("<credential-reference>", credential.toString());
    assertEquals("<connection-policy>", policy.toString());
    assertFalse(
        ConnectionResolution.resolved("顧客管理API", policy).toString().contains("api.example"));
  }

  @Test
  void standardCliCompatibleEnvironmentDoesNotInventAResolver() {
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "logical-connection-standard.bsb",
                SOURCE,
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertEquals(10, result.exitCode());
    assertEquals(DiagnosticCode.E_CAPABILITY_UNAVAILABLE, result.diagnostics().getFirst().code());
  }

  private static Stream<Arguments> invalidPolicies() {
    return Stream.of(
        Arguments.of(
            "BASE_URI_INVALID",
            policy(
                "http://base.invalid/",
                List.of("https://api.example.test"),
                List.of("GET"),
                "none",
                Optional.empty(),
                1,
                1,
                1,
                1,
                "deny",
                "none")),
        Arguments.of(
            "ORIGIN_POLICY_INVALID",
            policy(
                "https://api.example.test/",
                List.of("https://other.example.test"),
                List.of("GET"),
                "none",
                Optional.empty(),
                1,
                1,
                1,
                1,
                "deny",
                "none")),
        Arguments.of(
            "METHOD_POLICY_INVALID",
            policy(
                "https://api.example.test/",
                List.of("https://api.example.test"),
                List.of("CONNECT"),
                "none",
                Optional.empty(),
                1,
                1,
                1,
                1,
                "deny",
                "none")),
        Arguments.of(
            "AUTHENTICATION_POLICY_INVALID",
            policy(
                "https://api.example.test/",
                List.of("https://api.example.test"),
                List.of("GET"),
                "basic",
                Optional.empty(),
                1,
                1,
                1,
                1,
                "deny",
                "none")),
        Arguments.of("CONNECT_TIMEOUT_INVALID", withNumbers(0, 1, 1, 1)),
        Arguments.of("RESPONSE_TIMEOUT_INVALID", withNumbers(1, 30_001, 1, 1)),
        Arguments.of("REQUEST_LIMIT_INVALID", withNumbers(1, 1, 67_108_865, 1)),
        Arguments.of("RESPONSE_LIMIT_INVALID", withNumbers(1, 1, 1, 0)),
        Arguments.of(
            "REDIRECT_POLICY_INVALID",
            policy(
                "https://api.example.test/",
                List.of("https://api.example.test"),
                List.of("GET"),
                "none",
                Optional.empty(),
                1,
                1,
                1,
                1,
                "follow",
                "none")),
        Arguments.of(
            "RETRY_POLICY_INVALID",
            policy(
                "https://api.example.test/",
                List.of("https://api.example.test"),
                List.of("GET"),
                "none",
                Optional.empty(),
                1,
                1,
                1,
                1,
                "deny",
                "automatic")));
  }

  private static ConnectionPolicy withNumbers(
      long connect, long response, long request, long body) {
    return policy(
        "https://api.example.test/",
        List.of("https://api.example.test"),
        List.of("GET"),
        "none",
        Optional.empty(),
        connect,
        response,
        request,
        body,
        "deny",
        "none");
  }

  private static ConnectionPolicy validPolicy() {
    return withNumbers(1_000, 2_000, 1_048_576, 2_097_152);
  }

  private static ConnectionPolicy policy(
      String base,
      List<String> origins,
      List<String> methods,
      String authentication,
      Optional<CredentialReference> credential,
      long connect,
      long response,
      long request,
      long body,
      String redirect,
      String retry) {
    return new ConnectionPolicy(
        base,
        origins,
        methods,
        authentication,
        credential,
        connect,
        response,
        request,
        body,
        redirect,
        retry);
  }

  private static Run run(ConnectionResolver resolver, boolean tracing, boolean redact) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var builder =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleOutput(output::write)
            .connectionResolver(resolver);
    if (redact) {
      builder.redactLogicalConnectionNamesInTrace();
    }
    ExecutionEnvironment environment = builder.build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "logical-connection.bsb",
                SOURCE,
                new ExecutionContext(
                    output, () -> 0L, tracing ? events::add : TraceSink.none(), environment));
    return new Run(result, output, List.copyOf(events));
  }

  private static Run runWithoutResolver(boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L).consoleOutput(output::write).build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "logical-connection.bsb",
                SOURCE,
                new ExecutionContext(
                    output, () -> 0L, tracing ? events::add : TraceSink.none(), environment));
    return new Run(result, output, List.copyOf(events));
  }

  private static Diagnostic diagnostic(Run run) {
    return run.result().diagnostics().getLast();
  }

  private static List<TraceEvent> capabilityEvents(Run run) {
    return run.events().stream().filter(event -> !event.effect().isEmpty()).toList();
  }

  private static void assertFailure(Run run, DiagnosticCode code, String outcome) {
    assertEquals(10, run.result().exitCode());
    assertEquals(code, diagnostic(run).code());
    assertEquals(List.of(), run.result().finalDataStack());
    assertEquals("", run.output().utf8Text());
    assertEquals(1, capabilityEvents(run).size());
    assertTrue(capabilityEvents(run).getFirst().effect().endsWith("|" + outcome));
  }

  private record Run(ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
