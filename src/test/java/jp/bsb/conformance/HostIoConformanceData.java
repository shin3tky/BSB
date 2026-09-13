package jp.bsb.conformance;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** ホスト入出力のケース、診断、資源、能力イベントを未使用キーなしで読む支援クラスです。 */
public final class HostIoConformanceData {
  private static final String ROOT = "/conformance/host-io/";
  private static final Set<String> COMMANDS = Set.of("check", "run", "format");
  private static final Set<String> CASE_SUFFIXES =
      Set.of(
          "source",
          "io",
          "commands",
          "check.exit",
          "check.stdout.empty",
          "check.stderr.empty",
          "run.exit",
          "run.termination",
          "run.stdout.empty",
          "run.stdout.text",
          "run.stdout.trailingLf",
          "run.stdout.source",
          "run.program.stderr.empty",
          "run.program.stderr.text",
          "run.program.stderr.trailingLf",
          "run.program.stderr.source",
          "run.diagnostic.stderr.empty",
          "format.exit",
          "format.stderr.empty",
          "format.stdout.source",
          "effects.source",
          "explain.source",
          "trace.source");
  private static final Set<String> IO_KEYS =
      Set.of(
          "capabilities",
          "input.events",
          "input.raw.hex",
          "input.generated",
          "console.output.result",
          "console.error.result",
          "arguments.values",
          "arguments.generated",
          "program.name",
          "program.location",
          "sleep.results",
          "monotonic.millis",
          "wall.values");
  private static final Set<String> CAPABILITIES =
      Set.of(
          "console.input",
          "console.output",
          "console.error",
          "process.exit",
          "process.arguments",
          "program.identity",
          "time.sleep",
          "time.monotonic",
          "time.wall");

  private HostIoConformanceData() {}

  /** N8/F8全50ケースを読み、未知・未使用キーと参照欠落を拒否します。 */
  public static CaseCatalog loadCases() throws IOException {
    Checked properties = loadProperties("cases.properties");
    requireEquals("1", properties.take("schema.version"), "schema.version");
    List<String> ids = csv(properties.take("case.ids"));
    require(ids.size() == new LinkedHashSet<>(ids).size(), "duplicate case id");

    Set<String> normal = Set.copyOf(csv(properties.take("normal.ids")));
    consumeGroup(properties, "normal", List.of("check", "run"));
    Set<String> staticallyRejected = Set.copyOf(csv(properties.take("failure.static.ids")));
    consumeGroup(properties, "failure.static", List.of("check", "run", "format"));
    Set<String> runtime = Set.copyOf(csv(properties.take("failure.runtime.ids")));
    consumeGroup(properties, "failure.runtime", List.of("check", "run", "format"));

    var cases = new ArrayList<CaseSpec>();
    for (String id : ids) {
      var attributes = new LinkedHashMap<String, String>();
      for (String suffix : CASE_SUFFIXES) {
        String value = properties.optional(id + '.' + suffix);
        if (value != null) {
          attributes.put(suffix, value);
        }
      }
      require(attributes.containsKey("source"), id + ": missing source");
      resourceBytes(attributes.get("source"));
      if (attributes.containsKey("io")) {
        resourceBytes(attributes.get("io"));
      }
      List<String> commands =
          attributes.containsKey("commands")
              ? csv(attributes.get("commands"))
              : normal.contains(id)
                  ? List.of("check", "run")
                  : staticallyRejected.contains(id) || runtime.contains(id)
                      ? List.of("check", "run", "format")
                      : List.of();
      require(!commands.isEmpty() && COMMANDS.containsAll(commands), id + ": invalid commands");
      for (Map.Entry<String, String> attribute : attributes.entrySet()) {
        if (attribute.getKey().endsWith(".source") || attribute.getKey().equals("trace.source")) {
          resourceBytes(attribute.getValue());
        }
      }
      cases.add(new CaseSpec(id, Map.copyOf(attributes), List.copyOf(commands)));
    }
    require(ids.containsAll(normal), "unknown normal id");
    require(ids.containsAll(staticallyRejected), "unknown static id");
    require(ids.containsAll(runtime), "unknown runtime id");
    require(Collections.disjoint(staticallyRejected, runtime), "overlapping failure groups");
    require(ids.stream().filter(id -> id.startsWith("IO-N")).count() == 24, "N8 count");
    require(ids.stream().filter(id -> id.startsWith("IO-F")).count() == 26, "F8 count");
    properties.assertEmpty();
    return new CaseCatalog(List.copyOf(cases), normal, staticallyRejected, runtime);
  }

  /** ケースの能力とイベント列を読み、未知キー・未知能力・未知イベントを拒否します。 */
  public static IoSpec loadIo(String relativePath) throws IOException {
    Checked properties = loadProperties(relativePath);
    for (String remaining : List.copyOf(properties.keys())) {
      require(IO_KEYS.contains(remaining), relativePath + ": unknown key " + remaining);
    }
    Set<String> capabilities = Set.copyOf(csv(properties.take("capabilities")));
    require(CAPABILITIES.containsAll(capabilities), relativePath + ": unknown capability");
    String eventText = properties.optional("input.events");
    String rawHex = properties.optional("input.raw.hex");
    String generated = properties.optional("input.generated");
    require(
        (eventText == null ? 0 : 1) + (rawHex == null ? 0 : 1) + (generated == null ? 0 : 1) <= 1,
        relativePath + ": ambiguous input");
    List<InputDirective> events = eventText == null ? List.of() : parseInputEvents(eventText);
    var attributes = new LinkedHashMap<String, String>();
    if (rawHex != null) {
      parseHex(rawHex);
      attributes.put("input.raw.hex", rawHex);
    }
    if (generated != null) {
      attributes.put("input.generated", generated);
    }
    for (String key : IO_KEYS) {
      if (key.equals("capabilities")
          || key.equals("input.events")
          || key.equals("input.raw.hex")
          || key.equals("input.generated")) {
        continue;
      }
      String value = properties.optional(key);
      if (value != null) {
        validateEventAttribute(key, value);
        attributes.put(key, value);
      }
    }
    properties.assertEmpty();
    return new IoSpec(capabilities, events, Map.copyOf(attributes));
  }

  /** 全構造化診断行を読みます。 */
  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    List<String[]> rows = tsv("diagnostics.tsv", 11);
    require(rows.removeFirst()[0].equals("case_id"), "diagnostic header");
    var ids = new LinkedHashSet<String>();
    var result = new ArrayList<DiagnosticSpec>();
    for (String[] row : rows) {
      require(ids.add(row[0]), "duplicate diagnostic " + row[0]);
      result.add(
          new DiagnosticSpec(row[0], row[3], Integer.parseInt(row[4]), Integer.parseInt(row[5])));
    }
    return List.copyOf(result);
  }

  /** 全資源行を読み、ID・target・variantの重複を拒否します。 */
  public static List<ResourceSpec> loadResources() throws IOException {
    List<String[]> rows = tsv("resources.tsv", 8);
    require(rows.removeFirst()[0].equals("case_id"), "resource header");
    var keys = new LinkedHashSet<String>();
    var result = new ArrayList<ResourceSpec>();
    for (String[] row : rows) {
      require(keys.add(row[0] + '/' + row[1] + '/' + row[2]), "duplicate resource row");
      result.add(
          new ResourceSpec(row[0], row[1], row[2], row[3], Integer.parseInt(row[4]), row[5]));
    }
    return List.copyOf(result);
  }

  /** R8生成定義をgenerator別の許可キーで検証します。 */
  public static Map<String, String> loadGenerated(String id) throws IOException {
    Checked properties = loadProperties("generated/" + id + ".properties");
    String generator = properties.take("generator");
    Set<String> allowed = generatedKeys(generator);
    var result = new LinkedHashMap<String, String>();
    result.put("generator", generator);
    for (String key : List.copyOf(properties.keys())) {
      require(allowed.contains(key), id + ": unknown generated key " + key);
      result.put(key, properties.take(key));
    }
    properties.assertEmpty();
    return Map.copyOf(result);
  }

  /** イベントを1回ずつ消費し、不足と余りを明示的に検出します。 */
  public static final class EventQueue<T> {
    private final java.util.ArrayDeque<T> values;

    public EventQueue(List<T> values) {
      this.values = new java.util.ArrayDeque<>(values);
    }

    public T take() {
      if (values.isEmpty()) {
        throw new IllegalStateException("capability event shortage");
      }
      return values.removeFirst();
    }

    public void assertExhausted() {
      if (!values.isEmpty()) {
        throw new IllegalStateException("capability event surplus: " + values.size());
      }
    }
  }

  public record CaseSpec(String id, Map<String, String> attributes, List<String> commands) {}

  public record CaseCatalog(
      List<CaseSpec> cases,
      Set<String> normalIds,
      Set<String> staticFailureIds,
      Set<String> runtimeFailureIds) {}

  public record IoSpec(
      Set<String> capabilities, List<InputDirective> inputEvents, Map<String, String> attributes) {}

  public record InputDirective(String kind, String value) {}

  public record DiagnosticSpec(String id, String code, int line, int column) {}

  public record ResourceSpec(
      String id, String target, String variant, String outcome, int exit, String code) {}

  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = HostIoConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) {
        throw new IOException("missing HostIo resource: " + relativePath);
      }
      return input.readAllBytes();
    }
  }

  private static void consumeGroup(Checked properties, String prefix, List<String> commands) {
    requireEquals(commands, csv(properties.take(prefix + ".commands")), prefix + ".commands");
    properties.optional(prefix + ".stdout.empty");
    properties.optional(prefix + ".stderr.empty");
    properties.optional(prefix + ".program.stderr.empty");
    properties.optional(prefix + ".diagnostic.stderr.empty");
    for (String command : commands) {
      properties.take(prefix + '.' + command + ".exit");
      properties.optional(prefix + '.' + command + ".termination");
      properties.optional(prefix + '.' + command + ".stdout.empty");
      properties.optional(prefix + '.' + command + ".stderr.empty");
      properties.optional(prefix + '.' + command + ".program.stderr.empty");
      properties.optional(prefix + '.' + command + ".diagnostic.stderr.empty");
    }
  }

  private static List<InputDirective> parseInputEvents(String value) {
    var result = new ArrayList<InputDirective>();
    for (String event : value.split("\u001F", -1)) {
      if (event.startsWith("line:utf8:")) {
        result.add(new InputDirective("line", event.substring("line:utf8:".length())));
      } else if (event.equals("end") || event.equals("cancel")) {
        result.add(new InputDirective(event, ""));
      } else if (event.equals("failure:io")) {
        result.add(new InputDirective("failure", "io"));
      } else {
        throw new IllegalArgumentException("unknown input event: " + event);
      }
    }
    return List.copyOf(result);
  }

  private static void validateEventAttribute(String key, String value) {
    if (key.equals("console.output.result") || key.equals("console.error.result")) {
      require(value.equals("failure:io"), key + ": unknown result");
    } else if (key.equals("sleep.results")) {
      for (String event : value.split("\u001F", -1)) {
        require(event.equals("cancel") || event.startsWith("completed:"), "unknown sleep event");
      }
    }
  }

  private static Set<String> generatedKeys(String generator) {
    return switch (generator) {
      case "input-line" ->
          Set.of("variants", "encoding", "lineTerminator", "validate.beforeDecodeAllocation");
      case "input-total" ->
          Set.of("variants", "encoding", "include.lineTerminators", "events.multipleLines");
      case "standard-output" -> Set.of("variants", "encoding", "precommit.required");
      case "standard-error" ->
          Set.of("variants", "encoding", "precommit.required", "budget.independentFrom");
      case "program-arguments-count" -> Set.of("variants", "element.text", "array.limit");
      case "program-arguments-total" ->
          Set.of("variants", "encoding", "eachString.max", "element.count");
      case "wait-call" -> Set.of("variants", "unit", "realSleep");
      case "wait-total" -> Set.of("variants", "accepted.calls", "rejected.calls", "realSleep");
      case "active-time" ->
          Set.of("active.variants", "blocked.input.nanos", "blocked.sleep.nanos", "clock");
      case "host-io-trace-effect" -> Set.of("columns", "effects", "input.line", "output.hex");
      case "host-io-ir" ->
          Set.of("variants", "value.types", "mismatch.outcome", "invalidTerminationTarget.outcome");
      case "capability-atomicity" -> Set.of("variants", "state", "expected");
      default -> throw new IllegalArgumentException("unknown HostIo generator: " + generator);
    };
  }

  private static Checked loadProperties(String relativePath) throws IOException {
    var values = new Properties();
    try (var reader =
        new InputStreamReader(
            HostIoConformanceData.class.getResourceAsStream(ROOT + relativePath),
            StandardCharsets.UTF_8)) {
      values.load(reader);
    } catch (NullPointerException failure) {
      throw new IOException("missing HostIo properties: " + relativePath, failure);
    }
    var map = new LinkedHashMap<String, String>();
    values.stringPropertyNames().stream()
        .sorted()
        .forEach(key -> map.put(key, values.getProperty(key)));
    return new Checked(relativePath, map);
  }

  private static List<String[]> tsv(String relativePath, int columns) throws IOException {
    String text = new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
    var rows = new ArrayList<String[]>();
    for (String line : text.split("\n")) {
      if (line.isEmpty()) {
        continue;
      }
      String[] row = line.split("\t", -1);
      require(row.length == columns, relativePath + ": wrong column count");
      rows.add(row);
    }
    return rows;
  }

  private static List<String> csv(String value) {
    return value.isEmpty() ? List.of() : List.of(value.split(",", -1));
  }

  private static void parseHex(String value) {
    require(value.length() % 2 == 0, "incomplete hex");
    for (int index = 0; index < value.length(); index++) {
      require(Character.digit(value.charAt(index), 16) >= 0, "invalid hex");
    }
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
      if (value == null) {
        throw new IllegalArgumentException(name + ": missing key " + key);
      }
      return value;
    }

    private String optional(String key) {
      return remaining.remove(key);
    }

    private Set<String> keys() {
      return remaining.keySet();
    }

    private void assertEmpty() {
      require(remaining.isEmpty(), name + ": unused keys " + remaining.keySet());
    }
  }
}
