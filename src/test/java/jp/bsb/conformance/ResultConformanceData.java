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

/** 結果値の全70 IDと中央カタログの公開コマンド・固定成果物を厳密に読みます。 */
public final class ResultConformanceData {
  private static final String ROOT = "/conformance/result-values/";

  private ResultConformanceData() {}

  /** N13/F13/R13全70件を、欠落・重複・順序違いを許さず読みます。 */
  public static List<CatalogSpec> loadCatalog() throws IOException {
    List<String[]> rows = tsv("central/catalog.tsv", 5);
    requireEquals(
        List.of("case_id", "kind", "evidence", "primary_code", "commands"),
        List.of(rows.removeFirst()),
        "catalog header");
    var result = new ArrayList<CatalogSpec>();
    var keys = new LinkedHashSet<String>();
    for (String[] row : rows) {
      CatalogSpec spec = new CatalogSpec(row[0], row[1], row[2], row[3], row[4]);
      require(keys.add(spec.id()), "duplicate catalog id: " + spec.id());
      require(
          Set.of("normal", "failure", "warning", "resource").contains(spec.kind()),
          spec.id() + ": invalid kind");
      require(
          Set.of("existing", "public", "internal").contains(spec.commands()),
          spec.id() + ": invalid command boundary");
      resourceBytes(spec.evidence());
      result.add(spec);
    }
    requireEquals(expectedIds(), result.stream().map(CatalogSpec::id).toList(), "catalog ids");
    requireEquals(
        20L, result.stream().filter(item -> item.id().startsWith("RESULT-N")).count(), "N count");
    requireEquals(
        36L, result.stream().filter(item -> item.id().startsWith("RESULT-F")).count(), "F count");
    requireEquals(
        14L, result.stream().filter(item -> item.id().startsWith("RESULT-R")).count(), "R count");
    return List.copyOf(result);
  }

  /** 中央カタログの公開24ソース×5コマンドの固定観測値を読みます。 */
  public static List<CommandSpec> loadCommandHashes() throws IOException {
    List<String[]> rows = tsv("central/command-hashes.tsv", 7);
    requireEquals(
        List.of(
            "source",
            "command",
            "exit",
            "stdout_sha256",
            "stderr_sha256",
            "stdout_bytes",
            "stderr_bytes"),
        List.of(rows.removeFirst()),
        "command hash header");
    var result = new ArrayList<CommandSpec>();
    var keys = new LinkedHashSet<String>();
    for (String[] row : rows) {
      CommandSpec spec =
          new CommandSpec(
              row[0],
              row[1],
              Integer.parseInt(row[2]),
              row[3],
              row[4],
              Integer.parseInt(row[5]),
              Integer.parseInt(row[6]));
      require(keys.add(spec.source() + '/' + spec.command()), "duplicate command hash");
      require(
          Set.of("check", "checkJson", "run", "format", "explain").contains(spec.command()),
          "unknown command: " + spec.command());
      requireSha(spec.stdoutSha256(), spec.source() + "/" + spec.command() + "/stdout");
      requireSha(spec.stderrSha256(), spec.source() + "/" + spec.command() + "/stderr");
      require(spec.stdoutBytes() >= 0 && spec.stderrBytes() >= 0, "negative command byte count");
      resourceBytes(spec.source());
      result.add(spec);
    }
    requireEquals(120, result.size(), "command hash count");
    Set<String> publicSources = new LinkedHashSet<>();
    for (CatalogSpec spec : loadCatalog()) {
      if (spec.commands().equals("public")) {
        publicSources.add(spec.evidence());
      }
    }
    requireEquals(
        publicSources,
        result.stream()
            .map(CommandSpec::source)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
        "public sources");
    for (String source : publicSources) {
      requireEquals(
          5L,
          result.stream().filter(item -> item.source().equals(source)).count(),
          source + ": command count");
    }
    return List.copyOf(result);
  }

  /** 中央カタログに含まれる成果物のSHA-256とバイト数を読みます。 */
  public static Map<String, FileHash> loadFileHashes() throws IOException {
    List<String[]> rows = tsv("central/file-hashes.tsv", 3);
    requireEquals(
        List.of("path", "sha256", "bytes"), List.of(rows.removeFirst()), "file hash header");
    var result = new LinkedHashMap<String, FileHash>();
    for (String[] row : rows) {
      requireSha(row[1], row[0]);
      FileHash hash = new FileHash(row[1], Integer.parseInt(row[2]));
      require(result.putIfAbsent(row[0], hash) == null, "duplicate file hash: " + row[0]);
      byte[] bytes = resourceBytes(row[0]);
      requireEquals(hash.bytes(), bytes.length, row[0] + ": bytes");
      requireEquals(hash.sha256(), sha256(bytes), row[0] + ": SHA-256");
    }
    requireEquals(34, result.size(), "file hash count");
    return Map.copyOf(result);
  }

  /** RESULT-R014の合成試験レシピを未使用キーなしで読みます。 */
  public static IntegratedRecipe loadIntegratedRecipe() throws IOException {
    var values = new LinkedHashMap<String, String>();
    for (String line : resourceText("central/RESULT-R014.properties").split("\n")) {
      if (line.isEmpty()) {
        continue;
      }
      int equals = line.indexOf('=');
      require(equals > 0, "invalid RESULT-R014 recipe");
      require(
          values.putIfAbsent(line.substring(0, equals), line.substring(equals + 1)) == null,
          "duplicate RESULT-R014 field");
    }
    requireEquals("integratedResult", values.remove("kind"), "RESULT-R014 kind");
    IntegratedRecipe recipe =
        new IntegratedRecipe(
            Integer.parseInt(values.remove("depth")),
            Integer.parseInt(values.remove("stackWidth")),
            Boolean.parseBoolean(values.remove("sharedPayload")),
            Integer.parseInt(values.remove("jsonComparisons")),
            Boolean.parseBoolean(values.remove("trace")),
            Integer.parseInt(values.remove("heapMiB")));
    require(values.isEmpty(), "unused RESULT-R014 fields: " + values.keySet());
    return recipe;
  }

  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = ResultConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) {
        throw new IOException("missing Result resource: " + relativePath);
      }
      byte[] bytes = input.readAllBytes();
      require(bytes.length > 0, "empty Result resource: " + relativePath);
      require(
          !(bytes.length >= 3
              && bytes[0] == (byte) 0xef
              && bytes[1] == (byte) 0xbb
              && bytes[2] == (byte) 0xbf),
          "BOM is forbidden: " + relativePath);
      for (byte value : bytes) {
        require(value != '\r', "CR is forbidden: " + relativePath);
      }
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
      requireEquals(columns, row.length, path + ": column count");
      result.add(row);
    }
    require(result.size() >= 2, path + ": no data rows");
    return result;
  }

  private static List<String> expectedIds() {
    var ids = new ArrayList<String>();
    ids.addAll(expectedIds("RESULT-N", 20));
    ids.addAll(expectedIds("RESULT-F", 36));
    ids.addAll(expectedIds("RESULT-R", 14));
    return List.copyOf(ids);
  }

  private static List<String> expectedIds(String prefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(number -> prefix + "%03d".formatted(number))
        .toList();
  }

  private static void requireSha(String value, String label) {
    require(value.matches("[0-9a-f]{64}"), label + ": invalid SHA-256");
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

  public record CatalogSpec(
      String id, String kind, String evidence, String primaryCode, String commands) {}

  public record CommandSpec(
      String source,
      String command,
      int exitCode,
      String stdoutSha256,
      String stderrSha256,
      int stdoutBytes,
      int stderrBytes) {}

  public record FileHash(String sha256, int bytes) {}

  public record IntegratedRecipe(
      int depth,
      int stackWidth,
      boolean sharedPayload,
      int jsonComparisons,
      boolean trace,
      int heapMiB) {}
}
