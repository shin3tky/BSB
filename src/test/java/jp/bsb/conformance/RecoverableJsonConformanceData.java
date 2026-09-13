package jp.bsb.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** 回復可能JSONの全32 ID、独立期待、資源レシピ、manifestを厳密に読みます。 */
public final class RecoverableJsonConformanceData {
  private static final String ROOT = "/conformance/recoverable-json/";

  private RecoverableJsonConformanceData() {}

  public static List<CatalogSpec> loadCatalog() throws IOException {
    List<String[]> rows = tsv("catalog.tsv", 4);
    requireEquals(
        List.of("case_id", "kind", "evidence", "scope"),
        List.of(rows.removeFirst()),
        "catalog header");
    var result = new ArrayList<CatalogSpec>();
    var ids = new LinkedHashSet<String>();
    for (String[] row : rows) {
      var spec = new CatalogSpec(row[0], row[1], row[2], row[3]);
      require(ids.add(spec.id()), "duplicate catalog id: " + spec.id());
      require(
          Set.of("normal", "failure", "warning", "resource").contains(spec.kind()),
          spec.id() + ": invalid kind");
      require(Set.of("public", "internal").contains(spec.scope()), spec.id() + ": invalid scope");
      resourceBytes(spec.evidence());
      result.add(spec);
    }
    var expected = new ArrayList<String>();
    expected.addAll(ids("RJSON-N", 10));
    expected.addAll(ids("RJSON-F", 10));
    expected.addAll(ids("RJSON-R", 12));
    requireEquals(expected, result.stream().map(CatalogSpec::id).toList(), "catalog ids");
    return List.copyOf(result);
  }

  public static List<CaseSpec> loadCases() throws IOException {
    List<String[]> rows = tsv("cases.tsv", 15);
    requireEquals(
        List.of(
            "case_id",
            "variant",
            "input_utf8_hex",
            "outcome",
            "payload_utf8_hex",
            "failure_kind",
            "utf8_offset",
            "line",
            "column",
            "json_work_delta",
            "json_construction_delta",
            "diagnostic",
            "input_preserved",
            "target_words",
            "evidence"),
        List.of(rows.removeFirst()),
        "case header");
    var result = new ArrayList<CaseSpec>();
    var keys = new LinkedHashSet<String>();
    for (String[] row : rows) {
      var spec =
          new CaseSpec(
              row[0],
              row[1],
              HexFormat.of().parseHex(row[2]),
              row[3],
              row[4].equals("-") ? null : HexFormat.of().parseHex(row[4]),
              row[5],
              integer(row[6]),
              integer(row[7]),
              integer(row[8]),
              Long.parseLong(row[9]),
              Long.parseLong(row[10]),
              row[11],
              Boolean.parseBoolean(row[12]),
              List.of(row[13].split("\\|", -1)),
              row[14]);
      require(keys.add(spec.id() + '/' + spec.variant()), "duplicate case variant");
      resourceBytes(spec.evidence());
      result.add(spec);
    }
    return List.copyOf(result);
  }

  public static List<ResourceSpec> loadResources() throws IOException {
    List<String[]> rows = tsv("resources.tsv", 11);
    requireEquals(
        List.of(
            "case_id",
            "variant",
            "metric",
            "input_shape",
            "limit",
            "observed",
            "outcome",
            "diagnostic",
            "json_work_delta",
            "json_construction_delta",
            "input_preserved"),
        List.of(rows.removeFirst()),
        "resource header");
    var result = new ArrayList<ResourceSpec>();
    var keys = new LinkedHashSet<String>();
    for (String[] row : rows) {
      var spec =
          new ResourceSpec(
              row[0],
              row[1],
              row[2],
              row[3],
              row[4],
              row[5],
              row[6],
              row[7],
              row[8],
              row[9],
              Boolean.parseBoolean(row[10]));
      require(keys.add(spec.id() + '/' + spec.variant()), "duplicate resource variant");
      result.add(spec);
    }
    return List.copyOf(result);
  }

  public static List<StateSpec> loadStates() throws IOException {
    List<String[]> rows = tsv("expected/states.tsv", 8);
    requireEquals(
        List.of(
            "source",
            "exit",
            "data_stack",
            "globals",
            "stdout_utf8_hex",
            "stderr_utf8_hex",
            "capabilities",
            "diagnostic"),
        List.of(rows.removeFirst()),
        "state header");
    return rows.stream()
        .map(
            row ->
                new StateSpec(
                    row[0],
                    Integer.parseInt(row[1]),
                    row[2],
                    row[3],
                    HexFormat.of().parseHex(row[4]),
                    HexFormat.of().parseHex(row[5]),
                    row[6],
                    row[7]))
        .toList();
  }

  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    List<String[]> rows = tsv("expected/diagnostics.tsv", 11);
    requireEquals(
        List.of(
            "case_id",
            "variant",
            "occurrence",
            "code",
            "stage",
            "target_lexeme",
            "expected_type",
            "actual_type",
            "required_count",
            "actual_count",
            "input_preserved"),
        List.of(rows.removeFirst()),
        "diagnostic header");
    return rows.stream()
        .map(
            row ->
                new DiagnosticSpec(
                    row[0],
                    row[1],
                    Integer.parseInt(row[2]),
                    row[3],
                    row[4],
                    row[5],
                    row[6],
                    row[7],
                    row[8],
                    row[9],
                    row[10]))
        .toList();
  }

  public static List<FileHash> loadManifest() throws IOException {
    List<String[]> rows = tsv("manifest.tsv", 3);
    requireEquals(
        List.of("path", "sha256", "bytes"), List.of(rows.removeFirst()), "manifest header");
    var result = new ArrayList<FileHash>();
    var paths = new LinkedHashSet<String>();
    for (String[] row : rows) {
      require(paths.add(row[0]), "duplicate manifest path: " + row[0]);
      byte[] bytes = resourceBytes(row[0]);
      requireEquals(Integer.parseInt(row[2]), bytes.length, row[0] + ": bytes");
      requireEquals(row[1], sha256(bytes), row[0] + ": sha256");
      result.add(new FileHash(row[0], row[1], Integer.parseInt(row[2])));
    }
    return List.copyOf(result);
  }

  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input =
        RecoverableJsonConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) {
        throw new IOException("missing RecoverableJson resource: " + relativePath);
      }
      byte[] bytes = input.readAllBytes();
      require(bytes.length > 0, "empty RecoverableJson resource: " + relativePath);
      require(
          !(bytes.length >= 3
              && bytes[0] == (byte) 0xef
              && bytes[1] == (byte) 0xbb
              && bytes[2] == (byte) 0xbf),
          "BOM is forbidden: " + relativePath);
      require(bytes[bytes.length - 1] == '\n', "final LF is required: " + relativePath);
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

  private static List<String[]> tsv(String path, int columns) throws IOException {
    String text = resourceText(path);
    var result = new ArrayList<String[]>();
    for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
      require(!line.isEmpty(), path + ": blank row");
      String[] row = line.split("\t", -1);
      requireEquals(columns, row.length, path + ": columns");
      result.add(row);
    }
    return result;
  }

  private static Integer integer(String value) {
    return value.equals("-") ? null : Integer.valueOf(value);
  }

  private static List<String> ids(String prefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(number -> prefix + "%03d".formatted(number))
        .toList();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
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
      byte[] input,
      String outcome,
      byte[] payload,
      String failureKind,
      Integer utf8Offset,
      Integer line,
      Integer column,
      long jsonWorkDelta,
      long jsonConstructionDelta,
      String diagnostic,
      boolean inputPreserved,
      List<String> targetWords,
      String evidence) {
    public CaseSpec {
      input = input.clone();
      payload = payload == null ? null : payload.clone();
      targetWords = List.copyOf(targetWords);
    }

    @Override
    public byte[] input() {
      return input.clone();
    }

    @Override
    public byte[] payload() {
      return payload == null ? null : payload.clone();
    }
  }

  public record ResourceSpec(
      String id,
      String variant,
      String metric,
      String inputShape,
      String limit,
      String observed,
      String outcome,
      String diagnostic,
      String jsonWorkDelta,
      String jsonConstructionDelta,
      boolean inputPreserved) {}

  public record StateSpec(
      String source,
      int exitCode,
      String dataStack,
      String globals,
      byte[] stdout,
      byte[] stderr,
      String capabilities,
      String diagnostic) {
    public StateSpec {
      stdout = stdout.clone();
      stderr = stderr.clone();
    }

    @Override
    public byte[] stdout() {
      return stdout.clone();
    }

    @Override
    public byte[] stderr() {
      return stderr.clone();
    }
  }

  public record DiagnosticSpec(
      String id,
      String variant,
      int occurrence,
      String code,
      String stage,
      String targetLexeme,
      String expectedType,
      String actualType,
      String requiredCount,
      String actualCount,
      String inputPreserved) {}

  public record FileHash(String path, String sha256, int bytes) {}
}
