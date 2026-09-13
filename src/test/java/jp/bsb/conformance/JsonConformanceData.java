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

/** JSONの全IDと独立JSON・診断・資源データを未使用キーなしで読みます。 */
public final class JsonConformanceData {
  private static final String ROOT = "/conformance/json/";
  private static final Set<String> FORMER_UNIT_ONLY_WORDS =
      Set.of(
          "真偽をJSONに変換する",
          "整数をJSONに変換する",
          "小数をJSONに変換する",
          "JSONから真偽を取り出す",
          "JSONから小数を取り出す",
          "JSONヌルである",
          "JSON真偽である",
          "JSON整数である",
          "JSON小数である",
          "JSONオブジェクトである",
          "空のJSON配列",
          "JSON配列に変換する",
          "JSON配列の長さ",
          "JSON配列の一部を取り出す",
          "JSON配列の要素を置き換える",
          "JSON配列の末尾へ追加する",
          "空のJSONオブジェクト",
          "JSONオブジェクトの要素数",
          "JSONオブジェクトのキー一覧",
          "JSONオブジェクトから削除する");
  private static final Set<String> NORMAL_OBSERVATIONS =
      Set.of(
          "scalarRoundTrip",
          "predicateInputPreserved",
          "arrayRead",
          "sliceBoundaries",
          "immutableArrayUpdate",
          "arrayRoundTrip",
          "arrayIteration",
          "objectReadOrder",
          "requiredRead",
          "immutableObjectSet",
          "immutableObjectSetDelete",
          "arrayEquality",
          "objectEquality",
          "display");

  private JsonConformanceData() {}

  /** N9/F9全58ケースを規範順で読み、グループの重複・不足と参照欠落を拒否します。 */
  public static CaseCatalog loadCases() throws IOException {
    Checked properties = loadProperties("cases.properties");
    requireEquals("2", properties.take("schema.version"), "schema.version");
    List<String> ids = csv(properties.take("case.ids"));
    require(ids.size() == new LinkedHashSet<>(ids).size(), "duplicate case id");
    requireEquals(expectedIds("JSON-N", 30, "JSON-F", 28), ids, "case ids");

    var cases = new ArrayList<CaseSpec>();
    var grouped = new LinkedHashSet<String>();
    consumeGroup(properties, cases, grouped, "normal.codec", "codec", true);
    var artifacts =
        new LinkedHashSet<>(
            List.of(
                "normal-corpus.tsv",
                "failure-corpus.tsv",
                "diagnostics.tsv",
                "resources.tsv",
                "generated/resources.properties",
                "canonical/JSON-N028.bsb",
                "canonical/json-chapter.bsb",
                "chapter/json-chapter.bsb",
                "chapter/json-chapter.stdin",
                "chapter/json-chapter.stdout"));
    var sources = new LinkedHashSet<String>();
    var stdoutResources = new LinkedHashSet<String>();
    consumeCliGroup(
        properties, cases, grouped, artifacts, sources, stdoutResources, "normal.cli", true);
    consumeGroup(properties, cases, grouped, "normal.format", "format", true);
    consumeGroup(properties, cases, grouped, "normal.explain", "explain", true);
    consumeGroup(properties, cases, grouped, "normal.chapter", "chapter", true);
    consumeGroup(properties, cases, grouped, "failure.codec", "codec", false);
    consumeCliGroup(
        properties, cases, grouped, artifacts, sources, stdoutResources, "failure.cli", false);
    requireEquals(new LinkedHashSet<>(ids), grouped, "case group coverage");

    artifacts.forEach(
        path -> {
          try {
            resourceBytes(path);
          } catch (IOException failure) {
            throw new IllegalArgumentException("missing json artifact: " + path, failure);
          }
        });
    properties.assertEmpty();

    Map<String, CaseSpec> byId = new LinkedHashMap<>();
    cases.forEach(spec -> byId.put(spec.id(), spec));
    Set<String> normalCliWords =
        cases.stream()
            .filter(spec -> spec.normal() && spec.fixture().equals("cli"))
            .flatMap(spec -> spec.words().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    require(
        normalCliWords.containsAll(FORMER_UNIT_ONLY_WORDS),
        "normal CLI cases omit former UNIT_ONLY words: "
            + difference(FORMER_UNIT_ONLY_WORDS, normalCliWords));
    return new CaseCatalog(ids.stream().map(byId::get).toList(), List.copyOf(artifacts));
  }

  /** JSON-N001〜009の独立入力と規範直列化結果を読みます。 */
  public static List<CodecNormalSpec> loadNormalCorpus() throws IOException {
    List<String[]> rows = tsv("normal-corpus.tsv", 3);
    requireEquals(
        List.of("case_id", "input", "expected"), List.of(rows.removeFirst()), "normal header");
    var result = new ArrayList<CodecNormalSpec>();
    for (String[] row : rows) {
      result.add(new CodecNormalSpec(row[0], row[1], row[2]));
    }
    requireEquals(
        expectedIds("JSON-N", 9),
        result.stream().map(CodecNormalSpec::id).toList(),
        "normal corpus ids");
    return List.copyOf(result);
  }

  /** JSON-F001〜016の独立不正入力と安定reasonを読みます。 */
  public static List<CodecFailureSpec> loadFailureCorpus() throws IOException {
    List<String[]> rows = tsv("failure-corpus.tsv", 3);
    requireEquals(
        List.of("case_id", "input", "reason"), List.of(rows.removeFirst()), "failure header");
    var result = new ArrayList<CodecFailureSpec>();
    for (String[] row : rows) {
      result.add(new CodecFailureSpec(row[0], row[1], row[2]));
    }
    requireEquals(
        Set.copyOf(expectedIds("JSON-F", 16)),
        result.stream()
            .map(CodecFailureSpec::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
        "failure corpus ids");
    return List.copyOf(result);
  }

  /** F9全28件のコマンド・位置・構造化フィールドを重複なしで読みます。 */
  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    List<String[]> rows = tsv("diagnostics.tsv", 14);
    requireEquals(
        List.of(
            "case_id",
            "kind",
            "command",
            "severity",
            "code",
            "stage",
            "line",
            "column",
            "fields",
            "expected",
            "actual",
            "fix",
            "related",
            "reason"),
        List.of(rows.removeFirst()),
        "diagnostic header");
    var ids = new LinkedHashSet<String>();
    var result = new ArrayList<DiagnosticSpec>();
    for (String[] row : rows) {
      require(ids.add(row[0]), "duplicate diagnostic id: " + row[0]);
      require(Set.of("codec", "cli").contains(row[1]), "unknown diagnostic kind: " + row[1]);
      requireEquals(row[1].equals("codec") ? "codec" : "run", row[2], row[0] + " command");
      result.add(
          new DiagnosticSpec(
              row[0],
              row[1],
              row[2],
              row[3],
              row[4],
              nullable(row[5]),
              Integer.parseInt(row[6]),
              Integer.parseInt(row[7]),
              fields(row[8]),
              nullable(row[9]),
              nullable(row[10]),
              nullable(row[11]),
              nullable(row[12]),
              nullable(row[13])));
    }
    requireEquals(
        expectedIds("JSON-F", 28),
        result.stream().map(DiagnosticSpec::id).toList(),
        "diagnostic ids");
    return List.copyOf(result);
  }

  /** R9全18件の2境界を読み、ID・variantの重複を拒否します。 */
  public static List<ResourceSpec> loadResources() throws IOException {
    List<String[]> rows = tsv("resources.tsv", 6);
    requireEquals(
        List.of("case_id", "variant", "limit", "observed", "outcome", "code"),
        List.of(rows.removeFirst()),
        "resource header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<ResourceSpec>();
    for (String[] row : rows) {
      require(keys.add(row[0] + '/' + row[1]), "duplicate resource variant");
      require(Set.of("success", "failure").contains(row[4]), "unknown resource outcome");
      result.add(
          new ResourceSpec(
              row[0], row[1], Long.parseLong(row[2]), Long.parseLong(row[3]), row[4], row[5]));
    }
    requireEquals(36, result.size(), "resource row count");
    requireEquals(
        Set.copyOf(expectedIds("JSON-R", 18)),
        result.stream()
            .map(ResourceSpec::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
        "resource ids");
    return List.copyOf(result);
  }

  /** 決定的生成器の版、順序、上限、seed、ヒープを厳密に読みます。 */
  public static GeneratorSpec loadGenerator() throws IOException {
    Checked properties = loadProperties("generated/resources.properties");
    requireEquals("1", properties.take("schema.version"), "generator schema");
    List<String> ids = csv(properties.take("generator.ids"));
    requireEquals(18, ids.size(), "generator id count");
    List<Long> limits = csv(properties.take("limits")).stream().map(Long::parseLong).toList();
    requireEquals(12, limits.size(), "generator limit count");
    long seed = Long.parseLong(properties.take("seed"));
    int heapMiB = Integer.parseInt(properties.take("heapMiB"));
    properties.assertEmpty();
    return new GeneratorSpec(ids, limits, seed, heapMiB);
  }

  /** json配下のUTF-8資源を読みます。 */
  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = JsonConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) {
        throw new IOException("missing json resource: " + relativePath);
      }
      return input.readAllBytes();
    }
  }

  /** json配下のUTF-8資源を文字列で読みます。 */
  public static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  /** 失敗コーパスの指示子を決定的なJSON入力へ展開します。 */
  public static String materializeFailureInput(String directive) {
    return switch (directive) {
      case "{EMPTY}" -> "";
      case "{BOM}null" -> "\uFEFFnull";
      case "{FULLWIDTH}null" -> "\u3000null";
      case "\"{CTRL}\"" -> "\"\u0001\"";
      case "{DIGITS4097}" -> "1".repeat(4097);
      case "{DEPTH257}" -> "[".repeat(257) + "0" + "]".repeat(257);
      case "{ARRAY65537}" -> repeatedArray(65_537);
      case "{OBJECT65537}" -> repeatedObject(65_537);
      case "{NODES250001}" -> nodeLimitDocument();
      default -> directive;
    };
  }

  public record CaseSpec(
      String id,
      String fixture,
      boolean normal,
      String source,
      List<String> commands,
      Map<String, CommandExpectation> expectations,
      List<String> words,
      RunExpectation runExpectation,
      FailureExpectation failureExpectation) {}

  public record CaseCatalog(List<CaseSpec> cases, List<String> artifactPaths) {}

  public record CommandExpectation(int exit, String stdoutPath, String diagnosticId) {}

  public record RunExpectation(
      boolean finalStackEmpty, long jsonConstruction, long jsonWork, String observes) {}

  public record FailureExpectation(
      long jsonConstruction, long jsonWork, List<String> finalGlobals) {}

  public record CodecNormalSpec(String id, String input, String expected) {}

  public record CodecFailureSpec(String id, String input, String reason) {}

  public record DiagnosticSpec(
      String id,
      String kind,
      String command,
      String severity,
      String code,
      String stage,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      String related,
      String reason) {}

  public record ResourceSpec(
      String id, String variant, long limit, long observed, String outcome, String code) {}

  public record GeneratorSpec(List<String> ids, List<Long> limits, long seed, int heapMiB) {}

  private static void consumeGroup(
      Checked properties,
      List<CaseSpec> result,
      Set<String> grouped,
      String key,
      String fixture,
      boolean normal) {
    for (String id : csv(properties.take(key + ".ids"))) {
      require(grouped.add(id), "case appears in multiple groups: " + id);
      require(id.startsWith(normal ? "JSON-N" : "JSON-F"), "case in wrong group: " + id);
      result.add(
          new CaseSpec(id, fixture, normal, null, List.of(), Map.of(), List.of(), null, null));
    }
  }

  private static void consumeCliGroup(
      Checked properties,
      List<CaseSpec> result,
      Set<String> grouped,
      Set<String> artifacts,
      Set<String> sources,
      Set<String> stdoutResources,
      String key,
      boolean normal)
      throws IOException {
    for (String id : csv(properties.take(key + ".ids"))) {
      require(grouped.add(id), "case appears in multiple groups: " + id);
      require(id.startsWith(normal ? "JSON-N" : "JSON-F"), "case in wrong group: " + id);
      String source = properties.take(id + ".source");
      require(sources.add(source), "duplicate CLI source: " + source);
      artifacts.add(source);
      String sourceText = resourceText(source);
      List<String> commands = csv(properties.take(id + ".commands"));
      requireEquals(
          normal ? List.of("check", "run", "format") : List.of("check", "run"),
          commands,
          id + " commands");
      List<String> words = csv(properties.take(id + ".words"));
      require(!words.isEmpty(), id + ": missing words");
      require(words.size() == new LinkedHashSet<>(words).size(), id + ": duplicate words");
      words.forEach(word -> require(sourceText.contains(word), id + ": source omits word " + word));

      var expectations = new LinkedHashMap<String, CommandExpectation>();
      for (String command : commands) {
        int exit = Integer.parseInt(properties.take(id + '.' + command + ".exit"));
        String stdout = properties.optional(id + '.' + command + ".stdout");
        String diagnostic = properties.optional(id + '.' + command + ".diagnostic");
        require(
            (stdout == null ? 0 : 1) + (diagnostic == null ? 0 : 1) <= 1,
            id + '/' + command + ": ambiguous expectation");
        if (stdout != null) {
          require(stdoutResources.add(stdout), "duplicate stdout resource: " + stdout);
          artifacts.add(stdout);
        }
        if (command.equals("check")) {
          require(
              exit == 0 && stdout == null && diagnostic == null,
              id + ": invalid check expectation");
        } else if (command.equals("format") || normal) {
          require(exit == 0 && stdout != null && diagnostic == null, id + ": invalid normal run");
        } else {
          require(
              exit == 10 && stdout == null && id.equals(diagnostic), id + ": invalid failure run");
        }
        expectations.put(command, new CommandExpectation(exit, stdout, diagnostic));
      }
      RunExpectation runExpectation = null;
      FailureExpectation failureExpectation = null;
      if (normal) {
        requireEquals("true", properties.take(id + ".run.finalStack.empty"), id + " final stack");
        long construction = Long.parseLong(properties.take(id + ".run.jsonConstruction"));
        long work = Long.parseLong(properties.take(id + ".run.jsonWork"));
        String observes = properties.take(id + ".run.observes");
        require(construction > 0 && work > 0, id + ": JSON budgets must be positive");
        require(NORMAL_OBSERVATIONS.contains(observes), id + ": unknown observation " + observes);
        runExpectation = new RunExpectation(true, construction, work, observes);
      } else {
        long construction = Long.parseLong(properties.take(id + ".run.jsonConstruction"));
        long work = Long.parseLong(properties.take(id + ".run.jsonWork"));
        String globals = properties.take(id + ".run.finalGlobals");
        failureExpectation =
            new FailureExpectation(
                construction,
                work,
                globals.equals("-") ? List.of() : List.of(globals.split("\\|", -1)));
      }
      result.add(
          new CaseSpec(
              id,
              "cli",
              normal,
              source,
              List.copyOf(commands),
              Map.copyOf(expectations),
              List.copyOf(words),
              runExpectation,
              failureExpectation));
    }
  }

  private static Map<String, String> fields(String text) {
    if (text.equals("-")) {
      return Map.of();
    }
    var result = new LinkedHashMap<String, String>();
    for (String field : text.split(";", -1)) {
      String[] pair = field.split("=", 2);
      require(pair.length == 2 && !pair[0].isEmpty(), "invalid diagnostic field: " + field);
      require(
          result.putIfAbsent(pair[0], pair[1]) == null, "duplicate diagnostic field: " + pair[0]);
    }
    return Map.copyOf(result);
  }

  private static String nullable(String value) {
    return value.equals("-") ? null : value;
  }

  private static Set<String> difference(Set<String> expected, Set<String> actual) {
    var result = new LinkedHashSet<>(expected);
    result.removeAll(actual);
    return Set.copyOf(result);
  }

  private static List<String> expectedIds(String prefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(value -> prefix + "%03d".formatted(value))
        .toList();
  }

  private static List<String> expectedIds(
      String firstPrefix, int firstCount, String secondPrefix, int secondCount) {
    var result = new ArrayList<>(expectedIds(firstPrefix, firstCount));
    result.addAll(expectedIds(secondPrefix, secondCount));
    return List.copyOf(result);
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

  private static String repeatedArray(int size) {
    var result = new StringBuilder(size * 5 + 2).append('[');
    for (int index = 0; index < size; index++) {
      if (index > 0) {
        result.append(',');
      }
      result.append("null");
    }
    return result.append(']').toString();
  }

  private static String repeatedObject(int size) {
    var result = new StringBuilder(size * 11 + 2).append('{');
    for (int index = 0; index < size; index++) {
      if (index > 0) {
        result.append(',');
      }
      result.append('"').append(index).append("\":null");
    }
    return result.append('}').toString();
  }

  private static String nodeLimitDocument() {
    var result = new StringBuilder(1_300_000).append('[');
    for (int group = 0; group < 4; group++) {
      if (group > 0) {
        result.append(',');
      }
      result.append(repeatedArray(62_500));
    }
    return result.append(']').toString();
  }

  private static Checked loadProperties(String relativePath) throws IOException {
    var values = new DuplicateRejectingProperties();
    try (Reader reader =
        new InputStreamReader(
            JsonConformanceData.class.getResourceAsStream(ROOT + relativePath),
            StandardCharsets.UTF_8)) {
      values.load(reader);
    } catch (NullPointerException failure) {
      throw new IOException("missing json properties: " + relativePath, failure);
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

    private String optional(String key) {
      return remaining.remove(key);
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
