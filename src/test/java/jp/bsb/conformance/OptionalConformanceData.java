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

/** 任意値の全52 ID、公開コマンド、診断、生成資源を厳密に読みます。 */
public final class OptionalConformanceData {
  private static final String ROOT = "/conformance/optional-values/";

  private OptionalConformanceData() {}

  /** N12/F12全42件のコマンドと最終状態期待を読みます。 */
  public static List<CaseSpec> loadCases() throws IOException {
    List<String[]> rows = tsv("cases.tsv", 18);
    requireEquals(
        List.of(
            "case_id",
            "kind",
            "source",
            "canonical",
            "stdout",
            "commands",
            "check_exit",
            "run_exit",
            "format_exit",
            "explain_exit",
            "diagnostic_rows",
            "final_stack",
            "final_globals",
            "instructions",
            "output_bytes",
            "json_construction",
            "json_work",
            "target_words"),
        List.of(rows.removeFirst()),
        "case header");
    var result = new ArrayList<CaseSpec>();
    for (String[] row : rows) {
      result.add(new CaseSpec(row));
    }
    requireEquals(expectedCaseIds(), result.stream().map(CaseSpec::id).toList(), "case ids");
    requireUnique(result.stream().map(CaseSpec::id).toList(), "case id");
    requireEquals(
        16L, result.stream().filter(spec -> spec.kind().equals("normal")).count(), "N12 count");
    requireEquals(
        26L, result.stream().filter(spec -> spec.kind().equals("failure")).count(), "F12 count");
    List<DiagnosticSpec> diagnostics = loadDiagnostics();
    for (CaseSpec spec : result) {
      require(Set.of("normal", "failure").contains(spec.kind()), spec.id() + ": invalid kind");
      for (String path : List.of(spec.source(), spec.canonical(), spec.stdout())) {
        if (!path.equals("-")) {
          resourceBytes(path);
        }
      }
      requireEquals(
          (long) spec.diagnosticRows(),
          diagnostics.stream().filter(item -> item.id().equals(spec.id())).count(),
          spec.id() + ": diagnostic rows");
    }
    return List.copyOf(result);
  }

  /** F12全26件の構造化診断29行を読みます。 */
  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    List<String[]> rows = tsv("diagnostics.tsv", 16);
    requireEquals(
        List.of(
            "case_id",
            "occurrence",
            "command",
            "code",
            "severity",
            "stage",
            "line",
            "column",
            "fields",
            "expected",
            "actual",
            "fixes",
            "related",
            "limit_name",
            "limit",
            "observed"),
        List.of(rows.removeFirst()),
        "diagnostic header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<DiagnosticSpec>();
    for (String[] row : rows) {
      DiagnosticSpec spec = new DiagnosticSpec(row);
      require(keys.add(spec.id() + '/' + spec.occurrence()), "duplicate diagnostic row");
      result.add(spec);
    }
    requireEquals(29, result.size(), "diagnostic row count");
    requireEquals(
        Set.copyOf(expectedIds("OPT-F", 26)),
        result.stream().map(DiagnosticSpec::id).collect(java.util.stream.Collectors.toSet()),
        "diagnostic ids");
    return List.copyOf(result);
  }

  /** R12全10件の20境界行を読みます。 */
  public static List<ResourceSpec> loadResources() throws IOException {
    List<String[]> rows = tsv("resources.tsv", 8);
    requireEquals(
        List.of(
            "case_id",
            "target",
            "variant",
            "outcome",
            "code",
            "limit",
            "observed",
            "generator_key"),
        List.of(rows.removeFirst()),
        "resource header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<ResourceSpec>();
    for (String[] row : rows) {
      ResourceSpec spec = new ResourceSpec(row);
      require(keys.add(spec.id() + '/' + spec.variant()), "duplicate resource variant");
      result.add(spec);
    }
    requireEquals(20, result.size(), "resource row count");
    requireEquals(
        Set.copyOf(expectedIds("OPT-R", 10)),
        result.stream().map(ResourceSpec::id).collect(java.util.stream.Collectors.toSet()),
        "resource ids");
    return List.copyOf(result);
  }

  /** 決定的な境界ソースを生成します。 */
  public static byte[] generatedInput(String key) {
    return switch (key) {
      case "typeDepth256" -> typeDepthSource(256);
      case "typeDepth257" -> typeDepthSource(257);
      case "stack65536" -> stackRecipe(65_536, false, false);
      case "stack65537" -> stackRecipe(65_537, false, false);
      case "globals10000" -> bindingSource(10_000, false);
      case "globals10001" -> bindingSource(10_001, false);
      case "locals1024" -> bindingSource(1_024, true);
      case "locals1025" -> bindingSource(1_025, true);
      case "integrated" -> stackRecipe(65_535, true, true);
      default -> throw new IllegalArgumentException("unknown generator key: " + key);
    };
  }

  /** 合成IR用レシピを未使用フィールドなしで読みます。 */
  public static StackRecipe loadStackRecipe(String key) {
    String text = new String(generatedInput(key), StandardCharsets.UTF_8);
    var values = new LinkedHashMap<String, String>();
    for (String line : text.split("\n")) {
      if (line.isEmpty()) {
        continue;
      }
      int equals = line.indexOf('=');
      require(equals > 0, "invalid stack recipe: " + key);
      require(
          values.putIfAbsent(line.substring(0, equals), line.substring(equals + 1)) == null,
          "duplicate stack recipe field: " + key);
    }
    requireEquals("syntheticOptionalStack", values.remove("kind"), key + ": recipe kind");
    int count = Integer.parseInt(values.remove("count"));
    boolean lookupFirst = Boolean.parseBoolean(values.remove("lookupFirst"));
    int typeDepth = Integer.parseInt(values.remove("typeDepth"));
    require(values.isEmpty(), key + ": unused recipe fields " + values.keySet());
    return new StackRecipe(count, lookupFirst, typeDepth);
  }

  /** 生成資源の独立SHA-256期待を読み、未使用キーを拒否します。 */
  public static Map<String, String> loadGeneratedHashes() throws IOException {
    List<String[]> rows = tsv("generated/resources.tsv", 2);
    requireEquals(List.of("generator_key", "sha256"), List.of(rows.removeFirst()), "hash header");
    var result = new LinkedHashMap<String, String>();
    for (String[] row : rows) {
      require(result.putIfAbsent(row[0], row[1]) == null, "duplicate generator hash");
      generatedInput(row[0]);
      require(row[1].matches("[0-9a-f]{64}"), "invalid SHA-256: " + row[0]);
    }
    requireEquals(
        Set.of(
            "typeDepth256",
            "typeDepth257",
            "stack65536",
            "stack65537",
            "globals10000",
            "globals10001",
            "locals1024",
            "locals1025",
            "integrated"),
        result.keySet(),
        "generator keys");
    return Map.copyOf(result);
  }

  /** SHA-256を小文字16進表記で返します。 */
  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /** Optional配下の資源を読みます。 */
  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = OptionalConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) {
        throw new IOException("missing Optional resource: " + relativePath);
      }
      byte[] bytes = input.readAllBytes();
      require(
          bytes.length < 3
              || bytes[0] != (byte) 0xef
              || bytes[1] != (byte) 0xbb
              || bytes[2] != (byte) 0xbf,
          relativePath + ": BOM");
      return bytes;
    }
  }

  /** Optional配下のUTF-8資源を読みます。 */
  public static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  public record CaseSpec(
      String id,
      String kind,
      String source,
      String canonical,
      String stdout,
      List<String> commands,
      String checkExit,
      String runExit,
      String formatExit,
      String explainExit,
      int diagnosticRows,
      String finalStack,
      String finalGlobals,
      String instructions,
      String outputBytes,
      String jsonConstruction,
      String jsonWork,
      List<String> targetWords) {
    CaseSpec(String[] row) {
      this(
          row[0],
          row[1],
          row[2],
          row[3],
          row[4],
          split(row[5]),
          row[6],
          row[7],
          row[8],
          row[9],
          Integer.parseInt(row[10]),
          row[11],
          row[12],
          row[13],
          row[14],
          row[15],
          row[16],
          split(row[17]));
    }

    public int exitFor(String command) {
      String value =
          switch (command) {
            case "check" -> checkExit;
            case "run" -> runExit;
            case "format" -> formatExit;
            case "explain" -> explainExit;
            default -> throw new IllegalArgumentException(command);
          };
      return Integer.parseInt(value);
    }
  }

  public record DiagnosticSpec(
      String id,
      int occurrence,
      String command,
      String code,
      String severity,
      String stage,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      List<String> fixes,
      List<String> related,
      String limitName,
      String limit,
      String observed) {
    DiagnosticSpec(String[] row) {
      this(
          row[0],
          Integer.parseInt(row[1]),
          row[2],
          row[3],
          row[4],
          row[5],
          Integer.parseInt(row[6]),
          Integer.parseInt(row[7]),
          OptionalConformanceData.fields(row[8]),
          row[9],
          row[10],
          split(row[11]),
          split(row[12]),
          row[13],
          row[14],
          row[15]);
    }
  }

  public record ResourceSpec(
      String id,
      String target,
      String variant,
      String outcome,
      String code,
      long limit,
      long observed,
      String generatorKey) {
    ResourceSpec(String[] row) {
      this(
          row[0],
          row[1],
          row[2],
          row[3],
          row[4],
          Long.parseLong(row[5]),
          Long.parseLong(row[6]),
          row[7]);
    }
  }

  public record StackRecipe(int count, boolean lookupFirst, int typeDepth) {}

  private static List<String[]> tsv(String relativePath, int columns) throws IOException {
    String text = resourceText(relativePath);
    require(text.endsWith("\n"), relativePath + ": missing final LF");
    require(!text.contains("\r"), relativePath + ": CR is not allowed");
    var rows = new ArrayList<String[]>();
    for (String line : text.split("\n")) {
      if (!line.isEmpty()) {
        String[] row = line.split("\t", -1);
        requireEquals(columns, row.length, relativePath + ": columns");
        rows.add(row);
      }
    }
    return rows;
  }

  private static byte[] typeDepthSource(int depth) {
    String type = "任意<".repeat(depth) + "整数" + ">".repeat(depth);
    return ("深いとは （" + type + " -- " + type + "）\nこと。\n\nメインとは （--）\nこと。\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] stackRecipe(int count, boolean lookupFirst, boolean typeDepth) {
    return ("kind=syntheticOptionalStack\n"
            + "count="
            + count
            + "\nlookupFirst="
            + lookupFirst
            + "\ntypeDepth="
            + (typeDepth ? 256 : 0)
            + "\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] bindingSource(int count, boolean local) {
    var source = new StringBuilder(count * 30 + 64);
    if (local) {
      source.append("メインとは （--）\n");
    }
    for (int index = 0; index < count; index++) {
      if (local) {
        source.append("    ");
      }
      source.append(local ? "局所" : "大域").append(index).append("は 定数 0 任意にする。\n");
    }
    if (!local) {
      source.append("\nメインとは （--）\n");
    }
    return source.append("こと。\n").toString().getBytes(StandardCharsets.UTF_8);
  }

  private static Map<String, String> fields(String text) {
    if (text.equals("-")) {
      return Map.of();
    }
    var result = new LinkedHashMap<String, String>();
    for (String entry : text.split(";", -1)) {
      int equals = entry.indexOf('=');
      require(equals > 0, "invalid field: " + entry);
      require(
          result.putIfAbsent(entry.substring(0, equals), entry.substring(equals + 1)) == null,
          "duplicate field: " + entry);
    }
    return Map.copyOf(result);
  }

  private static List<String> split(String value) {
    return value.equals("-") ? List.of() : List.of(value.split("\\|", -1));
  }

  private static List<String> expectedCaseIds() {
    var result = new ArrayList<>(expectedIds("OPT-N", 16));
    result.addAll(expectedIds("OPT-F", 26));
    return List.copyOf(result);
  }

  private static List<String> expectedIds(String prefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(index -> prefix + "%03d".formatted(index))
        .toList();
  }

  private static void requireUnique(List<String> values, String label) {
    require(values.size() == new LinkedHashSet<>(values).size(), "duplicate " + label);
  }

  private static void requireEquals(Object expected, Object actual, String label) {
    require(expected.equals(actual), label + ": expected " + expected + " but got " + actual);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
