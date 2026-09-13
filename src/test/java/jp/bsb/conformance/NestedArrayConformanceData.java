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

/** 多次元配列の全34 IDと、独立した値・診断・資源・trace期待を厳密に読みます。 */
public final class NestedArrayConformanceData {
  private static final String ROOT = "/conformance/nested-arrays/";

  private NestedArrayConformanceData() {}

  public static List<CatalogSpec> loadCatalog() throws IOException {
    var rows = table("catalog.tsv", "case_id", "kind", "evidence", "scope");
    var result = new ArrayList<CatalogSpec>();
    var seen = new LinkedHashSet<String>();
    for (var row : rows) {
      var item =
          new CatalogSpec(
              row.get("case_id"), row.get("kind"), row.get("evidence"), row.get("scope"));
      require(seen.add(item.id()), "duplicate catalog id: " + item.id());
      require(Set.of("normal", "failure", "resource").contains(item.kind()), "catalog kind");
      require(Set.of("public", "internal").contains(item.scope()), "catalog scope");
      resourceBytes(item.evidence());
      result.add(item);
    }
    var expected = new ArrayList<String>();
    expected.addAll(ids("NARRAY-N", 12));
    expected.addAll(ids("NARRAY-F", 12));
    expected.addAll(ids("NARRAY-R", 10));
    requireEquals(expected, result.stream().map(CatalogSpec::id).toList(), "catalog ids");
    return List.copyOf(result);
  }

  public static List<RowSpec> loadStates() throws IOException {
    return rows(
        "expected/states.tsv",
        "case_id",
        "variant",
        "value_type",
        "display",
        "stdout",
        "construction_units",
        "operation_units",
        "original_preserved");
  }

  public static List<RowSpec> loadDiagnostics() throws IOException {
    return rows(
        "expected/diagnostics.tsv",
        "case_id",
        "variant",
        "code",
        "stage",
        "line",
        "column",
        "fields");
  }

  public static List<RowSpec> loadTraces() throws IOException {
    return rows(
        "expected/trace.tsv",
        "case_id",
        "variant",
        "value_type",
        "expected_preview",
        "forbidden_markers",
        "budget_delta");
  }

  public static List<RowSpec> loadResources() throws IOException {
    var result =
        rows(
            "resources.tsv",
            "case_id",
            "variant",
            "metric",
            "recipe",
            "limit",
            "used",
            "requested",
            "observed",
            "outcome",
            "diagnostic",
            "construction_after",
            "operation_after",
            "state_preserved");
    require(
        result.stream().allMatch(row -> row.value("state_preserved").equals("true")),
        "resource atomicity");
    return result;
  }

  public static List<FileHash> loadManifest() throws IOException {
    var rows = table("manifest.tsv", "path", "sha256", "bytes");
    var result = new ArrayList<FileHash>();
    var paths = new LinkedHashSet<String>();
    for (var row : rows) {
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
    try (var input = NestedArrayConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) throw new IOException("missing NestedArray resource: " + relativePath);
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

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static List<RowSpec> rows(String path, String... columns) throws IOException {
    return table(path, columns).stream().map(RowSpec::new).toList();
  }

  private static List<Map<String, String>> table(String path, String... columns)
      throws IOException {
    byte[] bytes = resourceBytes(path);
    String text = new String(bytes, StandardCharsets.UTF_8);
    String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
    requireEquals(List.of(columns), List.of(lines[0].split("\t", -1)), path + ": header");
    var result = new ArrayList<Map<String, String>>();
    var keys = new LinkedHashSet<String>();
    for (int line = 1; line < lines.length; line++) {
      require(!lines[line].isEmpty(), path + ": blank row");
      String[] values = lines[line].split("\t", -1);
      requireEquals(columns.length, values.length, path + ": columns");
      var row = new LinkedHashMap<String, String>();
      for (int column = 0; column < columns.length; column++) {
        row.put(columns[column], values[column]);
      }
      String key = values[0] + "\u0000" + (values.length > 1 ? values[1] : "");
      require(keys.add(key), path + ": duplicate row");
      result.add(Map.copyOf(row));
    }
    return List.copyOf(result);
  }

  private static List<String> ids(String prefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(number -> prefix + "%03d".formatted(number))
        .toList();
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

  public record RowSpec(Map<String, String> fields) {
    public RowSpec {
      fields = Map.copyOf(fields);
    }

    public String value(String name) {
      return fields.get(name);
    }
  }

  public record FileHash(String path, String sha256, int bytes) {}
}
