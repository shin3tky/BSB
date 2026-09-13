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

/** 指数リテラルの全32 IDと独立オラクルを未使用キーなしで読みます。 */
public final class ExponentConformanceData {
  private static final String ROOT = "/conformance/exponent-literals/";
  private static final Set<String> CASE_ATTRIBUTES =
      Set.of(
          "oracle",
          "source",
          "commands",
          "check.exit",
          "run.exit",
          "format.exit",
          "check.stdout.empty",
          "run.stdout.empty",
          "format.stdout.empty",
          "check.stderr.empty",
          "run.stderr.empty",
          "format.stderr.empty",
          "check.stdout.source",
          "run.stdout.source",
          "format.stdout.source");

  private ExponentConformanceData() {}

  /** N10/F10/R10とコマンド期待、全18資源を読みます。 */
  public static Catalog loadCatalog() throws IOException {
    Checked properties = loadProperties("cases.properties");
    requireEquals("1", properties.take("schema.version"), "schema.version");
    List<String> caseIds = csv(properties.take("case.ids"));
    List<String> resourceIds = csv(properties.take("resource.ids"));
    requireEquals(expectedIds("EXP-N", 12, "EXP-F", 12), caseIds, "case ids");
    requireEquals(expectedIds("EXP-R", 8), resourceIds, "resource ids");
    requireUnique(caseIds, "case id");
    requireUnique(resourceIds, "resource id");

    var groupById = new LinkedHashMap<String, String>();
    var defaultsById = new LinkedHashMap<String, Map<String, String>>();
    consumeGroup(properties, groupById, defaultsById, "normal.literal", Map.of());
    consumeGroup(properties, groupById, defaultsById, "normal.runtime", Map.of());
    consumeGroup(properties, groupById, defaultsById, "normal.format", Map.of());
    consumeGroup(properties, groupById, defaultsById, "normal.chapter", Map.of());

    List<String> lexicalCommands = csv(properties.take("failure.lexical.commands"));
    String lexicalExit = properties.take("failure.lexical.exit");
    String lexicalStdoutEmpty = properties.take("failure.lexical.stdout.empty");
    consumeGroup(
        properties,
        groupById,
        defaultsById,
        "failure.lexical",
        commandDefaults(lexicalCommands, lexicalExit, lexicalStdoutEmpty));

    List<String> staticCommands = csv(properties.take("failure.static.commands"));
    var staticDefaults = new LinkedHashMap<String, String>();
    staticDefaults.put("commands", String.join(",", staticCommands));
    for (String command : staticCommands) {
      staticDefaults.put(command + ".exit", properties.take("failure.static." + command + ".exit"));
    }
    staticDefaults.put("check.stdout.empty", properties.take("failure.static.check.stdout.empty"));
    staticDefaults.put("run.stdout.empty", properties.take("failure.static.run.stdout.empty"));
    staticDefaults.put(
        "format.stderr.empty", properties.take("failure.static.format.stderr.empty"));
    consumeGroup(properties, groupById, defaultsById, "failure.static", Map.copyOf(staticDefaults));
    consumeGroup(properties, groupById, defaultsById, "failure.runtime", Map.of());
    requireEquals(new LinkedHashSet<>(caseIds), groupById.keySet(), "case group coverage");

    var cases = new ArrayList<CaseSpec>();
    for (String id : caseIds) {
      Map<String, String> own = properties.takePrefix(id + ".");
      require(!own.isEmpty(), "case has no attributes: " + id);
      require(CASE_ATTRIBUTES.containsAll(own.keySet()), "unknown case attributes: " + id);
      var merged = new LinkedHashMap<>(defaultsById.get(id));
      merged.putAll(own);
      cases.add(new CaseSpec(id, groupById.get(id), Map.copyOf(merged)));
    }

    List<String> artifacts = csv(properties.take("artifact.paths"));
    requireUnique(artifacts, "artifact path");
    for (String path : artifacts) {
      resourceBytes(path);
    }
    Set<String> artifactSet = Set.copyOf(artifacts);
    for (CaseSpec spec : cases) {
      for (Map.Entry<String, String> attribute : spec.attributes().entrySet()) {
        if (attribute.getKey().equals("source")
            || attribute.getKey().equals("oracle")
            || attribute.getKey().endsWith(".stdout.source")) {
          require(
              artifactSet.contains(attribute.getValue()),
              spec.id() + ": unlisted artifact " + attribute.getValue());
        }
      }
    }
    properties.assertEmpty();
    return new Catalog(List.copyOf(cases), List.copyOf(resourceIds), List.copyOf(artifacts));
  }

  /** 独立字句オラクルの全25行を読みます。 */
  public static List<LiteralSpec> loadLiterals() throws IOException {
    List<String[]> rows = tsv("literals.tsv", 10);
    requireEquals(
        List.of(
            "case_id",
            "variant",
            "input",
            "outcome",
            "token_kind",
            "type",
            "value_display",
            "format",
            "code",
            "reason"),
        List.of(rows.removeFirst()),
        "literal header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<LiteralSpec>();
    for (String[] row : rows) {
      require(keys.add(row[0] + '/' + row[1]), "duplicate literal variant");
      require(Set.of("accepted", "failure").contains(row[3]), "unknown literal outcome");
      result.add(new LiteralSpec(row));
    }
    requireEquals(25, result.size(), "literal row count");
    requireEquals(
        Set.copyOf(
            concat(
                expectedIds("EXP-N", 4),
                concat(List.of("EXP-N009", "EXP-N011"), expectedIds("EXP-F", 10)))),
        result.stream().map(LiteralSpec::id).collect(java.util.stream.Collectors.toSet()),
        "literal ids");
    return List.copyOf(result);
  }

  /** F10全12件の公開診断期待を読みます。 */
  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    List<String[]> rows = tsv("diagnostics.tsv", 11);
    requireEquals(
        List.of(
            "case_id",
            "command",
            "severity",
            "stage",
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
              row[4],
              Integer.parseInt(row[5]),
              Integer.parseInt(row[6]),
              fields(row[7]),
              row[8],
              row[9],
              row[10]));
    }
    requireEquals(
        expectedIds("EXP-F", 12),
        result.stream().map(DiagnosticSpec::id).toList(),
        "diagnostic ids");
    return List.copyOf(result);
  }

  /** R10全8件の16境界行を読みます。 */
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
            "lexeme_spec"),
        List.of(rows.removeFirst()),
        "resource header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<ResourceSpec>();
    for (String[] row : rows) {
      require(keys.add(row[0] + '/' + row[1] + '/' + row[2]), "duplicate resource variant");
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
    requireEquals(16, result.size(), "resource row count");
    requireEquals(
        Set.copyOf(expectedIds("EXP-R", 8)),
        result.stream().map(ResourceSpec::id).collect(java.util.stream.Collectors.toSet()),
        "resource ids");
    return List.copyOf(result);
  }

  /** 決定的生成字句と独立SHA-256を未使用キーなしで読みます。 */
  public static GeneratorSpec loadGenerator() throws IOException {
    Checked properties = loadProperties("generated/resources.properties");
    requireEquals("1", properties.take("schema.version"), "generator schema");
    requireEquals("UTF-8", properties.take("generator.encoding"), "generator encoding");
    requireEquals("LF", properties.take("generator.lineEnding"), "generator line ending");
    requireEquals("SHA-256", properties.take("generator.hashAlgorithm"), "hash algorithm");
    List<String> keys =
        List.of(
            "EXP-R001.exact.literal",
            "EXP-R001.over.literal",
            "EXP-R002.exact.literal",
            "EXP-R002.over.literal",
            "EXP-R003.exact.literal",
            "EXP-R003.over.literal",
            "EXP-R004.exact.literal",
            "EXP-R004.over.literal",
            "EXP-R005.exact.literal",
            "EXP-R005.over.literal",
            "EXP-R006.exact.literal",
            "EXP-R006.over.literal",
            "EXP-R007.literal",
            "EXP-R008.run.literal",
            "EXP-R008.format.literal");
    var literals = new LinkedHashMap<String, GeneratedLiteral>();
    for (String key : keys) {
      String template = properties.take(key);
      literals.put(
          key,
          new GeneratedLiteral(
              template, materializeTemplate(template), properties.take(key + ".sha256")));
    }
    int expectedObserved = Integer.parseInt(properties.take("EXP-R007.expectedObserved"));
    int expectedCodePoints = Integer.parseInt(properties.take("EXP-R008.run.expectedCodePoints"));
    properties.assertEmpty();
    return new GeneratorSpec(Map.copyOf(literals), expectedObserved, expectedCodePoints);
  }

  /** Exponent配下の資源を読みます。 */
  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = ExponentConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) {
        throw new IOException("missing Exponent resource: " + relativePath);
      }
      return input.readAllBytes();
    }
  }

  /** Exponent配下のUTF-8資源を読みます。 */
  public static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  public record Catalog(
      List<CaseSpec> cases, List<String> resourceIds, List<String> artifactPaths) {}

  public record CaseSpec(String id, String group, Map<String, String> attributes) {
    public List<String> commands() {
      return csv(attributes.getOrDefault("commands", ""));
    }
  }

  public record LiteralSpec(
      String id,
      String variant,
      String input,
      String outcome,
      String tokenKind,
      String type,
      String valueDisplay,
      String format,
      String code,
      String reason) {
    private LiteralSpec(String[] row) {
      this(row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9]);
    }
  }

  public record DiagnosticSpec(
      String id,
      String command,
      String severity,
      String stage,
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
      String lexemeSpec) {}

  public record GeneratedLiteral(String template, String literal, String sha256) {}

  public record GeneratorSpec(
      Map<String, GeneratedLiteral> literals, int expectedObserved, int expectedCodePoints) {}

  private static Map<String, String> commandDefaults(
      List<String> commands, String exit, String stdoutEmpty) {
    var result = new LinkedHashMap<String, String>();
    result.put("commands", String.join(",", commands));
    for (String command : commands) {
      result.put(command + ".exit", exit);
      result.put(command + ".stdout.empty", stdoutEmpty);
    }
    return Map.copyOf(result);
  }

  private static void consumeGroup(
      Checked properties,
      Map<String, String> groupById,
      Map<String, Map<String, String>> defaultsById,
      String group,
      Map<String, String> defaults) {
    for (String id : csv(properties.take(group + ".ids"))) {
      require(groupById.putIfAbsent(id, group) == null, "case appears in multiple groups: " + id);
      defaultsById.put(id, defaults);
    }
  }

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
    if (text.equals("-")) {
      return Map.of();
    }
    var result = new LinkedHashMap<String, String>();
    for (String entry : text.split(";")) {
      int equals = entry.indexOf('=');
      require(equals > 0, "invalid diagnostic field: " + entry);
      require(
          result.putIfAbsent(entry.substring(0, equals), entry.substring(equals + 1)) == null,
          "duplicate diagnostic field: " + entry);
    }
    return Map.copyOf(result);
  }

  private static String materializeTemplate(String template) {
    int open = template.indexOf('{');
    if (open < 0) {
      return template;
    }
    int colon = template.indexOf(':', open);
    int close = template.indexOf('}', colon);
    require(colon > open && close > colon, "invalid generator template: " + template);
    String name = template.substring(open + 1, colon);
    int count = Integer.parseInt(template.substring(colon + 1, close));
    char repeated =
        switch (name) {
          case "ZERO" -> '0';
          case "NINE" -> '9';
          default -> throw new IllegalArgumentException("unknown generator symbol: " + name);
        };
    return template.substring(0, open)
        + String.valueOf(repeated).repeat(count)
        + template.substring(close + 1);
  }

  private static Checked loadProperties(String relativePath) throws IOException {
    var values = new DuplicateRejectingProperties();
    try (Reader reader =
        new InputStreamReader(
            ExponentConformanceData.class.getResourceAsStream(ROOT + relativePath),
            StandardCharsets.UTF_8)) {
      values.load(reader);
    } catch (NullPointerException failure) {
      throw new IOException("missing Exponent properties: " + relativePath, failure);
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

  private static List<String> expectedIds(
      String firstPrefix, int firstCount, String secondPrefix, int secondCount) {
    return concat(expectedIds(firstPrefix, firstCount), expectedIds(secondPrefix, secondCount));
  }

  private static List<String> concat(List<String> first, List<String> second) {
    var result = new ArrayList<String>();
    result.addAll(first);
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
      var keys = remaining.keySet().stream().filter(key -> key.startsWith(prefix)).toList();
      for (String key : keys) {
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
