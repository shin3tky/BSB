package jp.bsb.conformance;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.IntStream;

/** 小数文字列の全28 IDと独立した文法・診断・資源期待を読みます。 */
public final class DecimalTextConformanceData {
  private static final String ROOT = "/conformance/decimal-text/";
  private static final List<String> GENERATED_KEYS =
      List.of(
          "DTXT-R001.exact.literal",
          "DTXT-R001.over.literal",
          "DTXT-R002.exact.literal",
          "DTXT-R002.over.literal",
          "DTXT-R003.exact.literal",
          "DTXT-R003.over.literal",
          "DTXT-R004.exact.literal",
          "DTXT-R004.over.literal",
          "DTXT-R005.exact.literal",
          "DTXT-R005.over.literal",
          "DTXT-R006.exact.literal",
          "DTXT-R006.over.literal",
          "DTXT-R007.literal",
          "DTXT-R008.run.literal",
          "DTXT-R008.format.literal");

  private DecimalTextConformanceData() {}

  /** 全ケース、資源ID、通常ケース資源、全成果物を未使用キーなしで読みます。 */
  public static Catalog loadCatalog() throws IOException {
    Checked properties = loadProperties("cases.properties");
    requireEquals("1", properties.take("schema.version"), "schema.version");
    List<String> caseIds = csv(properties.take("case.ids"));
    List<String> resourceIds = csv(properties.take("resource.ids"));
    List<String> normalIds = csv(properties.take("normal.ids"));
    List<String> failureIds = csv(properties.take("failure.ids"));
    requireEquals(concat(expectedIds("DTXT-N", 8), expectedIds("DTXT-F", 12)), caseIds, "case ids");
    requireEquals(expectedIds("DTXT-R", 8), resourceIds, "resource ids");
    requireEquals(expectedIds("DTXT-N", 8), normalIds, "normal ids");
    requireEquals(expectedIds("DTXT-F", 12), failureIds, "failure ids");
    requireUnique(caseIds, "case id");
    requireUnique(resourceIds, "resource id");

    List<String> artifacts = csv(properties.take("artifact.paths"));
    requireUnique(artifacts, "artifact path");
    for (String artifact : artifacts) {
      resourceBytes(artifact);
    }

    var normals = new LinkedHashMap<String, NormalCase>();
    for (String id : normalIds) {
      Map<String, String> attributes = properties.takePrefix(id + ".");
      if (id.equals("DTXT-N001")) {
        requireEquals(Set.of("oracle"), attributes.keySet(), id + " attributes");
        require(artifacts.contains(attributes.get("oracle")), id + " unlisted oracle");
      } else {
        Set<String> expected =
            id.equals("DTXT-N007")
                ? Set.of("source", "stdout", "trace")
                : Set.of("source", "stdout");
        requireEquals(expected, attributes.keySet(), id + " attributes");
        attributes
            .values()
            .forEach(path -> require(artifacts.contains(path), id + " unlisted artifact"));
      }
      normals.put(id, new NormalCase(id, attributes));
    }
    properties.assertEmpty();
    return new Catalog(caseIds, resourceIds, normalIds, failureIds, Map.copyOf(normals), artifacts);
  }

  /** 正常8形と不正文法13形を独立TSVから読みます。 */
  public static List<DecimalTextSpec> loadDecimalTexts() throws IOException {
    List<String[]> rows = tsv("decimal-text.tsv", 6);
    requireEquals(
        List.of("case_id", "variant", "input", "outcome", "value_display", "code"),
        List.of(rows.removeFirst()),
        "decimal text header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<DecimalTextSpec>();
    for (String[] row : rows) {
      require(keys.add(row[0] + '/' + row[1]), "duplicate decimal text variant");
      require(Set.of("accepted", "failure").contains(row[3]), "unknown decimal outcome");
      result.add(new DecimalTextSpec(row[0], row[1], row[2], row[3], row[4], row[5]));
    }
    requireEquals(21, result.size(), "decimal text rows");
    requireEquals(
        Set.copyOf(concat(List.of("DTXT-N001"), expectedIds("DTXT-F", 9))),
        result.stream().map(DecimalTextSpec::id).collect(java.util.stream.Collectors.toSet()),
        "decimal text ids");
    return List.copyOf(result);
  }

  /** F11全12件の実行時診断期待を読みます。 */
  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    List<String[]> rows = tsv("diagnostics.tsv", 10);
    requireEquals(
        List.of(
            "case_id",
            "input_spec",
            "word",
            "code",
            "line",
            "column",
            "fields",
            "expected",
            "actual",
            "fix"),
        List.of(rows.removeFirst()),
        "diagnostic header");
    var result = new ArrayList<DiagnosticSpec>();
    for (String[] row : rows) {
      result.add(
          new DiagnosticSpec(
              row[0],
              row[1],
              row[2],
              row[3],
              Integer.parseInt(row[4]),
              Integer.parseInt(row[5]),
              fields(row[6]),
              row[7],
              row[8],
              row[9]));
    }
    requireEquals(
        expectedIds("DTXT-F", 12),
        result.stream().map(DiagnosticSpec::id).toList(),
        "diagnostic ids");
    return List.copyOf(result);
  }

  /** R11全8件の16境界行を読みます。 */
  public static List<ResourceSpec> loadResources() throws IOException {
    List<String[]> rows = tsv("resources.tsv", 10);
    requireEquals(
        List.of(
            "case_id",
            "target",
            "variant",
            "command",
            "outcome",
            "exit",
            "code",
            "limit",
            "observed",
            "generator_key"),
        List.of(rows.removeFirst()),
        "resource header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<ResourceSpec>();
    for (String[] row : rows) {
      require(keys.add(row[0] + '/' + row[1] + '/' + row[2]), "duplicate resource variant");
      require(GENERATED_KEYS.contains(row[9]), "unknown generator key");
      result.add(
          new ResourceSpec(
              row[0],
              row[1],
              row[2],
              row[3],
              row[4],
              Integer.parseInt(row[5]),
              row[6],
              row[7],
              row[8],
              row[9]));
    }
    requireEquals(16, result.size(), "resource rows");
    requireEquals(
        Set.copyOf(expectedIds("DTXT-R", 8)),
        result.stream().map(ResourceSpec::id).collect(java.util.stream.Collectors.toSet()),
        "resource ids");
    return List.copyOf(result);
  }

  /** 15個の生成入力と独立SHA-256を未使用キーなしで読みます。 */
  public static Map<String, GeneratedLiteral> loadGeneratedLiterals() throws IOException {
    Checked properties = loadProperties("generated/resources.properties");
    requireEquals("1", properties.take("schema.version"), "generator schema");
    requireEquals("UTF-8", properties.take("generator.encoding"), "generator encoding");
    requireEquals("LF", properties.take("generator.lineEnding"), "generator line ending");
    requireEquals("SHA-256", properties.take("generator.hashAlgorithm"), "hash algorithm");
    var result = new LinkedHashMap<String, GeneratedLiteral>();
    for (String key : GENERATED_KEYS) {
      String template = properties.take(key);
      result.put(
          key,
          new GeneratedLiteral(template, materialize(template), properties.take(key + ".sha256")));
    }
    properties.assertEmpty();
    return Map.copyOf(result);
  }

  /** 診断入力指定を通常文字列または生成キーから解決します。 */
  public static String diagnosticInput(DiagnosticSpec spec) throws IOException {
    GeneratedLiteral generated = loadGeneratedLiterals().get(spec.inputSpec());
    return generated == null ? spec.inputSpec() : generated.literal();
  }

  /** DecimalText配下の資源を読みます。 */
  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = DecimalTextConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) {
        throw new IOException("missing DecimalText resource: " + relativePath);
      }
      return input.readAllBytes();
    }
  }

  /** DecimalText配下のUTF-8資源を読みます。 */
  public static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  public record Catalog(
      List<String> caseIds,
      List<String> resourceIds,
      List<String> normalIds,
      List<String> failureIds,
      Map<String, NormalCase> normalCases,
      List<String> artifactPaths) {}

  public record NormalCase(String id, Map<String, String> attributes) {}

  public record DecimalTextSpec(
      String id, String variant, String input, String outcome, String valueDisplay, String code) {}

  public record DiagnosticSpec(
      String id,
      String inputSpec,
      String word,
      String code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix) {}

  public record ResourceSpec(
      String id,
      String target,
      String variant,
      String command,
      String outcome,
      int exit,
      String code,
      String limit,
      String observed,
      String generatorKey) {}

  public record GeneratedLiteral(String template, String literal, String sha256) {}

  private static List<String[]> tsv(String relativePath, int columns) throws IOException {
    String text = resourceText(relativePath);
    require(text.endsWith("\n"), relativePath + ": missing final LF");
    var rows = new ArrayList<String[]>();
    for (String line : text.split("\n")) {
      if (line.isEmpty()) {
        continue;
      }
      String[] row = line.split("\t", -1);
      requireEquals(columns, row.length, relativePath + ": columns");
      rows.add(row);
    }
    return rows;
  }

  private static Map<String, String> fields(String text) {
    var result = new LinkedHashMap<String, String>();
    for (String entry : text.split(";")) {
      int equals = entry.indexOf('=');
      require(equals > 0, "invalid field: " + entry);
      require(
          result.putIfAbsent(entry.substring(0, equals), entry.substring(equals + 1)) == null,
          "duplicate field: " + entry);
    }
    return Map.copyOf(result);
  }

  private static String materialize(String template) {
    int open = template.indexOf('{');
    if (open < 0) {
      return template;
    }
    int colon = template.indexOf(':', open);
    int close = template.indexOf('}', colon);
    require(colon > open && close > colon, "invalid template: " + template);
    String symbol = template.substring(open + 1, colon);
    int count = Integer.parseInt(template.substring(colon + 1, close));
    char repeated =
        switch (symbol) {
          case "ZERO" -> '0';
          case "NINE" -> '9';
          default -> throw new IllegalArgumentException("unknown symbol: " + symbol);
        };
    return template.substring(0, open)
        + String.valueOf(repeated).repeat(count)
        + template.substring(close + 1);
  }

  private static Checked loadProperties(String relativePath) throws IOException {
    var values = new DuplicateRejectingProperties();
    try (Reader reader =
        new InputStreamReader(
            DecimalTextConformanceData.class.getResourceAsStream(ROOT + relativePath),
            StandardCharsets.UTF_8)) {
      values.load(reader);
    } catch (NullPointerException failure) {
      throw new IOException("missing DecimalText properties: " + relativePath, failure);
    }
    var map = new LinkedHashMap<String, String>();
    values.stringPropertyNames().stream()
        .sorted()
        .forEach(key -> map.put(key, values.getProperty(key)));
    return new Checked(relativePath, map);
  }

  private static List<String> csv(String value) {
    return value.isEmpty() ? List.of() : List.of(value.split(",", -1));
  }

  private static List<String> expectedIds(String prefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(value -> prefix + "%03d".formatted(value))
        .toList();
  }

  private static List<String> concat(List<String> first, List<String> second) {
    var result = new ArrayList<String>(first);
    result.addAll(second);
    return List.copyOf(result);
  }

  private static void requireUnique(List<String> values, String kind) {
    require(values.size() == new LinkedHashSet<>(values).size(), "duplicate " + kind);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireEquals(Object expected, Object actual, String message) {
    require(expected.equals(actual), message + ": expected " + expected + " but got " + actual);
  }

  private static final class Checked {
    private final String name;
    private final Map<String, String> remaining;

    private Checked(String name, Map<String, String> remaining) {
      this.name = name;
      this.remaining = remaining;
    }

    private String take(String key) {
      String value = remaining.remove(key);
      require(value != null, name + ": missing property " + key);
      return value;
    }

    private Map<String, String> takePrefix(String prefix) {
      var result = new LinkedHashMap<String, String>();
      for (String key :
          remaining.keySet().stream().filter(key -> key.startsWith(prefix)).toList()) {
        result.put(key.substring(prefix.length()), remaining.remove(key));
      }
      return Map.copyOf(result);
    }

    private void assertEmpty() {
      require(remaining.isEmpty(), name + ": unused properties " + remaining.keySet());
    }
  }

  private static final class DuplicateRejectingProperties extends Properties {
    @Override
    public synchronized Object put(Object key, Object value) {
      if (containsKey(key)) {
        throw new IllegalArgumentException("duplicate property: " + key);
      }
      return super.put(key, value);
    }
  }
}
