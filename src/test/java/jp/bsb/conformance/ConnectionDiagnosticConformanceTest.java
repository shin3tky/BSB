package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonString;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.CredentialReference;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.Test;

/** 独立表の新12診断を、実際の静的・実行時診断へ完全一致させます。 */
class ConnectionDiagnosticConformanceTest {
  @Test
  void everyStaticDiagnosticMatchesCodeStageSpanFieldsExpectedAndActual() throws Exception {
    Map<String, ConnectionConformanceData.CatalogSpec> catalog =
        ConnectionConformanceData.loadCatalog().stream()
            .collect(
                Collectors.toMap(ConnectionConformanceData.CatalogSpec::id, Function.identity()));
    Map<String, List<ConnectionConformanceData.DiagnosticSpec>> groups =
        ConnectionConformanceData.loadDiagnostics().stream()
            .filter(item -> !item.stage().equals("runtime"))
            .collect(Collectors.groupingBy(ConnectionConformanceData.DiagnosticSpec::id));
    for (var entry : groups.entrySet()) {
      String id = entry.getKey();
      String path;
      byte[] source;
      if (id.equals("CONN-F003")) {
        path = "generated/CONN-F003.bsb";
        source = declarations(10_001);
      } else {
        path = catalog.get(id).evidence();
        source = ConnectionConformanceData.resourceBytes(path);
      }
      List<Diagnostic> actual = new SourceChecker().check(path, source).diagnostics();
      assertEquals(entry.getValue().size(), actual.size(), id + actual);
      for (int index = 0; index < actual.size(); index++) {
        assertDiagnostic(entry.getValue().get(index), actual.get(index), source);
      }
    }
  }

  @Test
  void everyRuntimeDiagnosticRowMatchesTheCentralResolverOrPolicy() throws Exception {
    Map<String, ConnectionPolicy> policies =
        ConnectionConformanceData.loadPolicies().stream()
            .collect(
                Collectors.toMap(
                    ConnectionConformanceData.PolicySpec::expectedReason,
                    ConnectionDiagnosticConformanceTest::policy,
                    (first, ignored) -> first));
    for (var expected :
        ConnectionConformanceData.loadDiagnostics().stream()
            .filter(item -> item.stage().equals("runtime"))
            .toList()) {
      String path =
          switch (expected.id()) {
            case "CONN-F010" -> "sources/CONN-F-not-configured.bsb";
            case "CONN-F011" -> "sources/CONN-F-denied.bsb";
            case "CONN-F012" -> "sources/CONN-F-invalid.bsb";
            default -> throw new AssertionError(expected.id());
          };
      byte[] source = ConnectionConformanceData.resourceBytes(path);
      var output = new MemoryOutputSink();
      var environment =
          ExecutionEnvironment.builder(() -> 0L)
              .consoleOutput(output::write)
              .connectionResolver(
                  (name, operation) ->
                      switch (expected.id()) {
                        case "CONN-F010" -> ConnectionResolution.notConfigured(name);
                        case "CONN-F011" -> ConnectionResolution.denied(name);
                        case "CONN-F012" ->
                            ConnectionResolution.resolved(name, policies.get(expected.variant()));
                        default -> throw new AssertionError(expected.id());
                      })
              .build();
      var result =
          new ProgramRunner()
              .run(
                  path,
                  source,
                  new ExecutionContext(output, () -> 0L, TraceSink.none(), environment));
      assertEquals(10, result.exitCode(), expected.id() + '/' + expected.variant());
      assertEquals(1, result.diagnostics().size(), expected.id() + '/' + expected.variant());
      assertDiagnostic(expected, result.diagnostics().getFirst(), source);
    }
  }

  private static void assertDiagnostic(
      ConnectionConformanceData.DiagnosticSpec expected, Diagnostic actual, byte[] source)
      throws Exception {
    String label = expected.id() + '/' + expected.variant();
    assertEquals(expected.code(), actual.code().name(), label + "/code");
    assertEquals(expected.stage(), publicStage(actual), label + "/stage");
    assertEquals(
        expected.targetLexeme(),
        lexeme(source, actual, expected.targetLexeme()),
        label + "/target");
    Map<String, String> requiredFields = fields(expected.fieldsJson());
    assertEquals(
        requiredFields,
        actual.fields().entrySet().stream()
            .filter(item -> requiredFields.containsKey(item.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
        label + "/fields");
    assertEquals(expected.expected(), actual.expected().orElse("-"), label + "/expected");
    assertEquals(expected.actual(), actual.actual().orElse("-"), label + "/actual");
  }

  private static Map<String, String> fields(String json) throws Exception {
    JsonObject object = (JsonObject) JsonCodec.parse(json);
    return object.members().stream()
        .collect(
            Collectors.toMap(
                item -> item.key(),
                item -> ((JsonString) item.value()).value(),
                (first, ignored) -> first,
                java.util.LinkedHashMap::new));
  }

  private static String lexeme(byte[] source, Diagnostic diagnostic, String expected) {
    if (!(diagnostic.location() instanceof SourceSpan span)) {
      int start = Math.toIntExact(diagnostic.location().utf8Offset());
      int length = expected.getBytes(StandardCharsets.UTF_8).length;
      return new String(
          source, start, Math.min(length, source.length - start), StandardCharsets.UTF_8);
    }
    return new String(
        source,
        Math.toIntExact(span.start().utf8Offset()),
        Math.toIntExact(span.end().utf8Offset() - span.start().utf8Offset()),
        StandardCharsets.UTF_8);
  }

  private static String publicStage(Diagnostic diagnostic) {
    return switch (diagnostic.stage()) {
      case UTF8 -> "utf8";
      case LEXICAL -> "lexical";
      case SYNTAX -> "syntax";
      case NAME -> "name";
      case TYPE_AND_STACK -> "typeAndStack";
      case IR -> "ir";
      case RUNTIME -> "runtime";
    };
  }

  private static byte[] declarations(int count) {
    var source = new StringBuilder(count * 28);
    for (int index = 1; index <= count; index++) {
      source.append("接続").append(index).append("は 論理接続。\n");
    }
    source.append("メインとは （--）\nこと。\n");
    return source.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static ConnectionPolicy policy(ConnectionConformanceData.PolicySpec spec) {
    Map<String, String> row = spec.values();
    return new ConnectionPolicy(
        row.get("base_uri"),
        List.of(row.get("origins").split("\\|", -1)),
        List.of(row.get("methods").split("\\|", -1)),
        row.get("auth_kind"),
        row.get("credential_reference").equals("-")
            ? Optional.empty()
            : Optional.of(CredentialReference.opaque()),
        Long.parseLong(row.get("connect_timeout_ms")),
        Long.parseLong(row.get("response_timeout_ms")),
        Long.parseLong(row.get("max_request_bytes")),
        Long.parseLong(row.get("max_response_bytes")),
        row.get("redirect_policy"),
        row.get("retry_policy"));
  }
}
