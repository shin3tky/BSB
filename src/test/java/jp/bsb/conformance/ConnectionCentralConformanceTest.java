package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.cli.BsbCli;
import jp.bsb.format.SourceFormatter;
import jp.bsb.runtime.CapabilityException;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.ConnectionResolver;
import jp.bsb.runtime.CredentialReference;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.RuntimeCapability;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.TraceTsvFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 論理接続の中央表を公開コマンドと偽resolverへ接続します。 */
class ConnectionCentralConformanceTest {
  private static final String BASIC = "sources/CONN-N-basic.bsb";
  private static final String CHAPTER = "chapter/logical-connections-chapter.bsb";

  @TempDir Path temporaryDirectory;

  @Test
  void everyPublicSourceHasTheCataloguedStaticOutcome() throws Exception {
    for (var item : ConnectionConformanceData.loadCatalog()) {
      if (!item.scope().equals("public") || !item.evidence().endsWith(".bsb")) {
        continue;
      }
      var analysis =
          new SourceChecker()
              .check(item.evidence(), ConnectionConformanceData.resourceBytes(item.evidence()));
      boolean runtimeOnlyFailure =
          item.id().equals("CONN-F010")
              || item.id().equals("CONN-F011")
              || item.id().equals("CONN-F012");
      assertEquals(
          item.kind().equals("normal") || runtimeOnlyFailure,
          analysis.successful(),
          item.id() + analysis.diagnostics());
    }
  }

  @Test
  void everyPublishedCanonicalIsExactAndIdempotent() throws Exception {
    for (String name : List.of("CONN-N-basic.bsb", "CONN-N-comments.bsb", "CONN-N-multiple.bsb")) {
      assertCanonical("sources/" + name, "canonical/" + name);
    }
    assertCanonical(CHAPTER, "canonical/logical-connections-chapter.bsb");
  }

  @Test
  void chapterPublicCommandsAreByteStableAcrossTwoInvocations() throws Exception {
    Path source = temporaryDirectory.resolve("logical-connections-chapter.bsb");
    Files.write(source, ConnectionConformanceData.resourceBytes(CHAPTER));
    for (List<String> command :
        List.of(
            List.of("check", source.toString()),
            List.of("check", "--json", source.toString()),
            List.of("run", source.toString()),
            List.of("format", source.toString()),
            List.of("explain", "--json", source.toString()))) {
      Invocation first = invoke(command);
      Invocation second = invoke(command);
      assertEquals(first.exitCode(), second.exitCode(), command.toString());
      assertArrayEquals(first.stdout(), second.stdout(), command + "/stdout");
      assertArrayEquals(first.stderr(), second.stderr(), command + "/stderr");
    }

    assertEquals(0, invoke(List.of("check", source.toString())).exitCode());
    assertEquals(0, invoke(List.of("check", "--json", source.toString())).exitCode());
    assertEquals(10, invoke(List.of("run", source.toString())).exitCode());
    assertArrayEquals(
        ConnectionConformanceData.resourceBytes("canonical/logical-connections-chapter.bsb"),
        invoke(List.of("format", source.toString())).stdout());
    Invocation explain = invoke(List.of("explain", "--json", source.toString()));
    assertEquals(0, explain.exitCode());
    String explanation = new String(explain.stdout(), StandardCharsets.UTF_8);
    assertTrue(explanation.contains("\"parameterizedCapabilities\""));
    assertTrue(explanation.contains("\"connection.resolve\""));
  }

  @Test
  void everyCentralResolverCaseMatchesOutcomeCallsStateAndEvent() throws Exception {
    byte[] source = ConnectionConformanceData.resourceBytes(BASIC);
    Map<String, ConnectionPolicy> policies = policies();
    for (var spec : ConnectionConformanceData.loadCases()) {
      var calls = new AtomicInteger();
      ConnectionResolver resolver = resolver(spec, policies, calls);
      if (spec.expectedOutcome().equals("internal")) {
        assertThrows(
            IllegalStateException.class,
            () -> run(BASIC, source, Optional.of(resolver), true, false));
        assertEquals(spec.resolverCalls(), calls.get(), spec.variant());
        continue;
      }
      Observation observation =
          run(
              BASIC,
              source,
              spec.resolverPresent() ? Optional.of(resolver) : Optional.empty(),
              true,
              false);
      ProgramRunResult result = observation.result();
      assertEquals(
          spec.expectedOutcome().equals("success") ? 0 : 10, result.exitCode(), spec.variant());
      assertEquals(spec.diagnostic(), diagnostic(result), spec.variant());
      assertEquals(spec.resolverCalls(), calls.get(), spec.variant());
      assertEquals(List.of(), result.finalDataStack(), spec.variant());
      assertEquals(List.of(), result.finalGlobalValues(), spec.variant());
      assertEquals(0, result.outputBytes(), spec.variant());
      assertEquals(0, result.errorOutputBytes(), spec.variant());
      assertEquals(spec.event(), effects(observation.events()), spec.variant());
    }
  }

  @Test
  void everyCentralStateMatchesNormalAndTracedExecution() throws Exception {
    Map<String, ConnectionConformanceData.ResolverSpec> resolverFixtures =
        ConnectionConformanceData.loadResolvers().stream()
            .collect(
                Collectors.toMap(
                    item -> item.fixture() + '/' + item.connection(), Function.identity()));
    Map<String, ConnectionPolicy> policies = policies();
    for (var spec : ConnectionConformanceData.loadStates()) {
      Map<String, String> expected = spec.values();
      byte[] source = ConnectionConformanceData.resourceBytes(expected.get("source"));
      var calls = new AtomicInteger();
      Optional<ConnectionResolver> resolver =
          expected.get("resolver_fixture").equals("absent")
              ? Optional.empty()
              : Optional.of(
                  fixtureResolver(
                      expected.get("resolver_fixture"), resolverFixtures, policies, calls));
      Observation normal = run(expected.get("source"), source, resolver, false, false);
      Observation traced = run(expected.get("source"), source, resolver, true, false);

      assertEquals(
          Integer.parseInt(expected.get("exit")),
          normal.result().exitCode(),
          expected.get("source"));
      assertArrayEquals(
          HexFormat.of().parseHex(expected.get("stdout_utf8_hex")), normal.output().bytes());
      assertEquals(expected.get("diagnostic"), diagnostic(normal.result()));
      assertEquals("[]", normal.result().finalDataStack().toString());
      assertEquals("{}", globals(normal.result()));
      assertEquals(
          expected.get("capabilities"), resolver.isPresent() ? "[\"connection.resolve\"]" : "[]");
      assertEquivalent(normal.result(), traced.result());
      assertArrayEquals(normal.output().bytes(), traced.output().bytes());
      assertEquals(expected.get("event"), effects(traced.events()));
      assertEquals(Integer.parseInt(expected.get("resolver_calls")) * 2, calls.get());
    }
  }

  @Test
  void everyTraceVariantMatchesItsSafeEffectAndForbiddenMarkers() throws Exception {
    byte[] source = ConnectionConformanceData.resourceBytes(BASIC);
    Map<String, ConnectionPolicy> policies = policies();
    for (var spec : ConnectionConformanceData.loadTraces()) {
      Map<String, String> expected = spec.values();
      var calls = new AtomicInteger();
      String variant = expected.get("variant");
      ConnectionConformanceData.CaseSpec caseSpec = traceCase(variant);
      Observation observation =
          run(
              BASIC,
              source,
              Optional.of(resolver(caseSpec, policies, calls)),
              true,
              variant.equals("strong-redaction"));
      String published =
          observation.result().toString()
              + observation.result().diagnostics()
              + observation.events()
              + TraceTsvFormatter.formatRecoverableJson(observation.events());
      assertEquals(expected.get("effect"), effects(observation.events()), variant);
      assertEquals(expected.get("diagnostic"), diagnostic(observation.result()), variant);
      assertEquals(Integer.parseInt(expected.get("resolver_calls")), calls.get(), variant);
      for (String marker : expected.get("forbidden_markers").split("\\|")) {
        assertFalse(published.contains(marker), variant + '/' + marker);
      }
    }
  }

  private static ConnectionConformanceData.CaseSpec traceCase(String variant) throws Exception {
    if (variant.equals("strong-redaction")) {
      variant = "resolved";
    } else if (variant.equals("invalid")) {
      variant = "explicit-invalid";
    } else if (variant.equals("failure")) {
      variant = "capability-failure";
    }
    String selected = variant;
    return ConnectionConformanceData.loadCases().stream()
        .filter(item -> item.variant().equals(selected))
        .findFirst()
        .orElseThrow();
  }

  private static ConnectionResolver resolver(
      ConnectionConformanceData.CaseSpec spec,
      Map<String, ConnectionPolicy> policies,
      AtomicInteger calls) {
    return (name, operation) -> {
      calls.incrementAndGet();
      assertEquals(spec.connection(), name, spec.variant());
      assertEquals(spec.operation(), operation, spec.variant());
      return switch (spec.resolverOutcome()) {
        case "RESOLVED" -> ConnectionResolution.resolved(name, policies.get(spec.policyId()));
        case "NOT_CONFIGURED" -> ConnectionResolution.notConfigured(name);
        case "DENIED" -> ConnectionResolution.denied(name);
        case "INVALID" -> ConnectionResolution.invalid(name, spec.invalidReason());
        case "FAILURE" ->
            throw CapabilityException.failure(RuntimeCapability.CONNECTION_RESOLVE, operation);
        case "CONTRACT_NULL" -> null;
        case "-" -> throw new AssertionError("absent resolver must not be called");
        default -> throw new AssertionError(spec.resolverOutcome());
      };
    };
  }

  private static ConnectionResolver fixtureResolver(
      String fixture,
      Map<String, ConnectionConformanceData.ResolverSpec> fixtures,
      Map<String, ConnectionPolicy> policies,
      AtomicInteger calls) {
    return (name, operation) -> {
      calls.incrementAndGet();
      var spec = fixtures.get(fixture + '/' + name);
      if (spec == null) throw new AssertionError(fixture + '/' + name);
      assertEquals(spec.operation(), operation);
      return switch (spec.outcome()) {
        case "RESOLVED" -> ConnectionResolution.resolved(name, policies.get(spec.policyId()));
        case "NOT_CONFIGURED" -> ConnectionResolution.notConfigured(name);
        case "DENIED" -> ConnectionResolution.denied(name);
        case "INVALID" -> ConnectionResolution.invalid(name, spec.invalidReason());
        case "FAILURE" ->
            throw CapabilityException.failure(RuntimeCapability.CONNECTION_RESOLVE, operation);
        case "CONTRACT_NULL" -> null;
        default -> throw new AssertionError(spec.outcome());
      };
    };
  }

  private static Map<String, ConnectionPolicy> policies() throws Exception {
    return ConnectionConformanceData.loadPolicies().stream()
        .collect(
            Collectors.toMap(
                ConnectionConformanceData.PolicySpec::id,
                ConnectionCentralConformanceTest::policy));
  }

  private static ConnectionPolicy policy(ConnectionConformanceData.PolicySpec spec) {
    Map<String, String> row = spec.values();
    String reference = row.get("credential_reference");
    return new ConnectionPolicy(
        row.get("base_uri"),
        split(row.get("origins")),
        split(row.get("methods")),
        row.get("auth_kind"),
        reference.equals("-") ? Optional.empty() : Optional.of(CredentialReference.opaque()),
        Long.parseLong(row.get("connect_timeout_ms")),
        Long.parseLong(row.get("response_timeout_ms")),
        Long.parseLong(row.get("max_request_bytes")),
        Long.parseLong(row.get("max_response_bytes")),
        row.get("redirect_policy"),
        row.get("retry_policy"));
  }

  private static List<String> split(String value) {
    return value.isEmpty() ? List.of() : Arrays.asList(value.split("\\|", -1));
  }

  private static void assertCanonical(String sourcePath, String canonicalPath) throws Exception {
    byte[] source = ConnectionConformanceData.resourceBytes(sourcePath);
    var first = new SourceFormatter().format(sourcePath, source);
    assertTrue(first.successful(), first.diagnostics().toString());
    byte[] formatted = first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(
        ConnectionConformanceData.resourceBytes(canonicalPath), formatted, sourcePath);
    var second = new SourceFormatter().format(canonicalPath, formatted);
    assertTrue(second.successful(), second.diagnostics().toString());
    assertArrayEquals(formatted, second.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
  }

  private static Observation run(
      String path,
      byte[] source,
      Optional<ConnectionResolver> resolver,
      boolean tracing,
      boolean redact) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var builder = ExecutionEnvironment.builder(() -> 0L).consoleOutput(output::write);
    resolver.ifPresent(builder::connectionResolver);
    if (redact) builder.redactLogicalConnectionNamesInTrace();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                path,
                source,
                new ExecutionContext(
                    output, () -> 0L, tracing ? events::add : TraceSink.none(), builder.build()));
    return new Observation(result, output, List.copyOf(events));
  }

  private static String diagnostic(ProgramRunResult result) {
    return result.diagnostics().stream()
        .filter(item -> item.severity() == jp.bsb.diagnostics.Severity.ERROR)
        .map(item -> item.code().name())
        .findFirst()
        .orElse("-");
  }

  private static String effects(List<TraceEvent> events) {
    String joined =
        events.stream()
            .map(TraceEvent::effect)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.joining(";"));
    return joined.isEmpty() ? "-" : joined;
  }

  private static String globals(ProgramRunResult result) {
    return result.finalGlobalValues().stream().allMatch(Optional::isEmpty) ? "{}" : "non-empty";
  }

  private static void assertEquivalent(ProgramRunResult first, ProgramRunResult second) {
    assertEquals(first.exitCode(), second.exitCode());
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
            .toList());
    assertEquals(first.finalDataStack(), second.finalDataStack());
    assertEquals(first.finalGlobalValues(), second.finalGlobalValues());
    assertEquals(first.executedInstructions(), second.executedInstructions());
    assertEquals(first.outputBytes(), second.outputBytes());
    assertEquals(first.errorOutputBytes(), second.errorOutputBytes());
    assertEquals(first.arrayConstructionUnits(), second.arrayConstructionUnits());
    assertEquals(first.arrayElementOperationUnits(), second.arrayElementOperationUnits());
    assertEquals(first.jsonConstructionUnits(), second.jsonConstructionUnits());
    assertEquals(first.jsonWorkUnits(), second.jsonWorkUnits());
    assertEquals(first.termination(), second.termination());
  }

  private static Invocation invoke(List<String> arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(arguments.toArray(String[]::new), stdout, stderr);
    return new Invocation(exit, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
