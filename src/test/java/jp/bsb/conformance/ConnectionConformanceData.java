package jp.bsb.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** 論理接続の全32 ID、偽接続解決、独立期待、manifestを厳密に読みます。 */
public final class ConnectionConformanceData {
  private static final String ROOT = "/conformance/logical-connections/";

  private ConnectionConformanceData() {}

  public static List<CatalogSpec> loadCatalog() throws IOException {
    var rows = table("catalog.tsv", "case_id", "kind", "evidence", "scope");
    var result = new ArrayList<CatalogSpec>();
    var ids = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      var item =
          new CatalogSpec(
              row.get("case_id"), row.get("kind"), row.get("evidence"), row.get("scope"));
      require(ids.add(item.id()), "duplicate catalog id: " + item.id());
      require(Set.of("normal", "failure", "warning", "resource").contains(item.kind()), "kind");
      require(Set.of("public", "internal").contains(item.scope()), "scope");
      resourceBytes(item.evidence());
      result.add(item);
    }
    var expected = new ArrayList<String>();
    expected.addAll(ids("CONN-N", 10));
    expected.addAll(ids("CONN-F", 12));
    expected.addAll(ids("CONN-R", 10));
    requireEquals(expected, result.stream().map(CatalogSpec::id).toList(), "catalog ids");
    return List.copyOf(result);
  }

  public static List<CaseSpec> loadCases() throws IOException {
    var rows =
        table(
            "cases.tsv",
            "case_id",
            "variant",
            "resolver_present",
            "resolver_outcome",
            "policy_id",
            "invalid_reason",
            "expected_outcome",
            "diagnostic",
            "resolver_calls",
            "connection",
            "operation",
            "state_preserved",
            "event");
    var result = new ArrayList<CaseSpec>();
    var keys = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      var item =
          new CaseSpec(
              row.get("case_id"),
              row.get("variant"),
              bool(row.get("resolver_present")),
              row.get("resolver_outcome"),
              row.get("policy_id"),
              row.get("invalid_reason"),
              row.get("expected_outcome"),
              row.get("diagnostic"),
              Integer.parseInt(row.get("resolver_calls")),
              row.get("connection"),
              row.get("operation"),
              bool(row.get("state_preserved")),
              row.get("event"));
      require(keys.add(item.id() + '/' + item.variant()), "duplicate case variant");
      require(
          Set.of("success", "diagnostic", "internal").contains(item.expectedOutcome()),
          "case outcome");
      require(item.operation().equals("resolve"), "case operation");
      require(item.statePreserved(), "case state must be preserved");
      result.add(item);
    }
    return List.copyOf(result);
  }

  public static List<PolicySpec> loadPolicies() throws IOException {
    var rows =
        table(
            "policies.tsv",
            "policy_id",
            "base_uri",
            "origins",
            "methods",
            "auth_kind",
            "credential_reference",
            "connect_timeout_ms",
            "response_timeout_ms",
            "max_request_bytes",
            "max_response_bytes",
            "redirect_policy",
            "retry_policy",
            "expected_reason",
            "secret_marker");
    var result = new ArrayList<PolicySpec>();
    var ids = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      var item = new PolicySpec(row);
      require(ids.add(item.id()), "duplicate policy id");
      require(!item.secretMarker().equals("-"), "policy secret marker");
      result.add(item);
    }
    requireEquals(
        Set.of(
            "BASE_URI_INVALID",
            "ORIGIN_POLICY_INVALID",
            "METHOD_POLICY_INVALID",
            "AUTHENTICATION_POLICY_INVALID",
            "CONNECT_TIMEOUT_INVALID",
            "RESPONSE_TIMEOUT_INVALID",
            "REQUEST_LIMIT_INVALID",
            "RESPONSE_LIMIT_INVALID",
            "REDIRECT_POLICY_INVALID",
            "RETRY_POLICY_INVALID"),
        result.stream()
            .map(PolicySpec::expectedReason)
            .filter(value -> !value.equals("-"))
            .collect(java.util.stream.Collectors.toSet()),
        "policy reasons");
    return List.copyOf(result);
  }

  public static List<ResolverSpec> loadResolvers() throws IOException {
    var rows =
        table(
            "resolver.tsv",
            "fixture",
            "connection",
            "operation",
            "outcome",
            "policy_id",
            "invalid_reason",
            "host_failure_marker",
            "expected_calls");
    var result = new ArrayList<ResolverSpec>();
    var keys = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      var item =
          new ResolverSpec(
              row.get("fixture"),
              row.get("connection"),
              row.get("operation"),
              row.get("outcome"),
              row.get("policy_id"),
              row.get("invalid_reason"),
              row.get("host_failure_marker"),
              Integer.parseInt(row.get("expected_calls")));
      require(keys.add(item.fixture() + '/' + item.connection()), "duplicate resolver route");
      require(item.operation().equals("resolve"), "resolver operation");
      require(
          Set.of("RESOLVED", "NOT_CONFIGURED", "DENIED", "INVALID", "FAILURE", "CONTRACT_NULL")
              .contains(item.outcome()),
          "resolver outcome");
      result.add(item);
    }
    return List.copyOf(result);
  }

  public static List<ResourceSpec> loadResources() throws IOException {
    var rows =
        table(
            "resources.tsv",
            "case_id",
            "variant",
            "metric",
            "input_shape",
            "limit",
            "observed",
            "outcome",
            "diagnostic",
            "reason",
            "resolver_calls",
            "state_preserved");
    var result = new ArrayList<ResourceSpec>();
    var keys = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      var item = new ResourceSpec(row);
      require(keys.add(item.id() + '/' + item.variant()), "duplicate resource variant");
      require(item.statePreserved(), "resource state must be preserved");
      result.add(item);
    }
    requireEquals(
        IntStream.rangeClosed(1, 10)
            .mapToObj(number -> "CONN-R%03d".formatted(number))
            .collect(java.util.stream.Collectors.toSet()),
        result.stream()
            .map(ResourceSpec::id)
            .filter(value -> value.startsWith("CONN-R"))
            .collect(java.util.stream.Collectors.toSet()),
        "resource ids");
    return List.copyOf(result);
  }

  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    var rows =
        table(
            "expected/diagnostics.tsv",
            "case_id",
            "variant",
            "occurrence",
            "code",
            "stage",
            "target_lexeme",
            "fields_json",
            "expected",
            "actual");
    var result = new ArrayList<DiagnosticSpec>();
    var keys = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      var item =
          new DiagnosticSpec(
              row.get("case_id"),
              row.get("variant"),
              Integer.parseInt(row.get("occurrence")),
              row.get("code"),
              row.get("stage"),
              row.get("target_lexeme"),
              row.get("fields_json"),
              row.get("expected"),
              row.get("actual"));
      require(
          keys.add(item.id() + '/' + item.variant() + '/' + item.occurrence()), "diagnostic key");
      require(item.code().startsWith("E_"), "diagnostic code");
      result.add(item);
    }
    requireEquals(12L, result.stream().map(DiagnosticSpec::code).distinct().count(), "new codes");
    return List.copyOf(result);
  }

  public static List<StateSpec> loadStates() throws IOException {
    var rows =
        table(
            "expected/states.tsv",
            "source",
            "resolver_fixture",
            "exit",
            "data_stack",
            "globals",
            "stdout_utf8_hex",
            "stderr_utf8_hex",
            "capabilities",
            "diagnostic",
            "resolver_calls",
            "event");
    var result = new ArrayList<StateSpec>();
    for (Map<String, String> row : rows) {
      resourceBytes(row.get("source"));
      result.add(new StateSpec(row));
    }
    return List.copyOf(result);
  }

  public static List<TraceSpec> loadTraces() throws IOException {
    var rows =
        table(
            "expected/trace.tsv",
            "variant",
            "outcome",
            "effect",
            "forbidden_markers",
            "data_stack",
            "globals",
            "diagnostic",
            "resolver_calls");
    return rows.stream().map(TraceSpec::new).toList();
  }

  public static String expectedParameterizedCapabilities() throws IOException {
    return resourceText("expected/parameterized-capabilities.json");
  }

  public static List<FileHash> loadManifest() throws IOException {
    var rows = table("manifest.tsv", "path", "sha256", "bytes");
    var result = new ArrayList<FileHash>();
    var paths = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      require(paths.add(row.get("path")), "duplicate manifest path");
      byte[] bytes = resourceBytes(row.get("path"));
      int length = Integer.parseInt(row.get("bytes"));
      requireEquals(length, bytes.length, row.get("path") + ": bytes");
      requireEquals(row.get("sha256"), sha256(bytes), row.get("path") + ": sha256");
      result.add(new FileHash(row.get("path"), row.get("sha256"), length));
    }
    return List.copyOf(result);
  }

  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = ConnectionConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) throw new IOException("missing Connection resource: " + relativePath);
      byte[] bytes = input.readAllBytes();
      require(bytes.length > 0, "empty resource: " + relativePath);
      require(
          !(bytes.length >= 3
              && bytes[0] == (byte) 0xef
              && bytes[1] == (byte) 0xbb
              && bytes[2] == (byte) 0xbf),
          "BOM is forbidden: " + relativePath);
      require(bytes[bytes.length - 1] == '\n', "final LF is required: " + relativePath);
      require(new String(bytes, StandardCharsets.UTF_8).indexOf('\r') < 0, "CR is forbidden");
      return bytes;
    }
  }

  public static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static List<Map<String, String>> table(String path, String... columns)
      throws IOException {
    String text = resourceText(path);
    String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
    require(lines.length > 0, path + ": header");
    requireEquals(List.of(columns), List.of(lines[0].split("\t", -1)), path + ": header");
    var result = new ArrayList<Map<String, String>>();
    for (int line = 1; line < lines.length; line++) {
      require(!lines[line].isEmpty(), path + ": blank row");
      String[] values = lines[line].split("\t", -1);
      requireEquals(columns.length, values.length, path + ": columns");
      var row = new LinkedHashMap<String, String>();
      for (int column = 0; column < columns.length; column++)
        row.put(columns[column], values[column]);
      result.add(Map.copyOf(row));
    }
    return List.copyOf(result);
  }

  private static boolean bool(String value) {
    require(value.equals("true") || value.equals("false"), "invalid boolean");
    return Boolean.parseBoolean(value);
  }

  private static List<String> ids(String prefix, int count) {
    return IntStream.rangeClosed(1, count).mapToObj(n -> prefix + "%03d".formatted(n)).toList();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private static void requireEquals(Object expected, Object actual, String label) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(label + ": expected=" + expected + ", actual=" + actual);
    }
  }

  public record CatalogSpec(String id, String kind, String evidence, String scope) {}

  public record CaseSpec(
      String id,
      String variant,
      boolean resolverPresent,
      String resolverOutcome,
      String policyId,
      String invalidReason,
      String expectedOutcome,
      String diagnostic,
      int resolverCalls,
      String connection,
      String operation,
      boolean statePreserved,
      String event) {}

  public record PolicySpec(Map<String, String> values) {
    public PolicySpec {
      values = Map.copyOf(values);
    }

    public String id() {
      return values.get("policy_id");
    }

    public String expectedReason() {
      return values.get("expected_reason");
    }

    public String secretMarker() {
      return values.get("secret_marker");
    }
  }

  public record ResolverSpec(
      String fixture,
      String connection,
      String operation,
      String outcome,
      String policyId,
      String invalidReason,
      String hostFailureMarker,
      int expectedCalls) {}

  public record ResourceSpec(Map<String, String> values) {
    public ResourceSpec {
      values = Map.copyOf(values);
    }

    public String id() {
      return values.get("case_id");
    }

    public String variant() {
      return values.get("variant");
    }

    public boolean statePreserved() {
      return bool(values.get("state_preserved"));
    }
  }

  public record DiagnosticSpec(
      String id,
      String variant,
      int occurrence,
      String code,
      String stage,
      String targetLexeme,
      String fieldsJson,
      String expected,
      String actual) {}

  public record StateSpec(Map<String, String> values) {
    public StateSpec {
      values = Map.copyOf(values);
      HexFormat.of().parseHex(values.get("stdout_utf8_hex"));
      HexFormat.of().parseHex(values.get("stderr_utf8_hex"));
    }
  }

  public record TraceSpec(Map<String, String> values) {
    public TraceSpec {
      values = Map.copyOf(values);
      require(!values.get("forbidden_markers").isBlank(), "trace forbidden markers");
    }
  }

  public record FileHash(String path, String sha256, int bytes) {}
}
