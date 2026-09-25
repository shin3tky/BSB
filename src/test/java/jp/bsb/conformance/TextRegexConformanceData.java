package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 文字列・正規表現の適合データを、欠落、重複、未使用項目を許さず読み込む支援クラスです。 */
public final class TextRegexConformanceData {
  private static final String ROOT = "/conformance/text-regex/";
  private static final Set<String> COMMANDS = Set.of("check", "run", "format");

  private TextRegexConformanceData() {}

  /** TEXTの全59ケースとコマンド期待値を厳密に読み込みます。 */
  public static CaseCatalog loadCases() throws IOException {
    CheckedProperties properties = loadProperties("cases.properties");
    assertEquals("1", properties.require("schema.version"));
    List<String> ids = csv(properties.require("case.ids"));
    assertEquals(ids.size(), new LinkedHashSet<>(ids).size(), "case.ids contains duplicates");

    FailureGroup syntax = failureGroup(properties, "syntax", true);
    FailureGroup staticallyRejected = failureGroup(properties, "static", false);
    FailureGroup runtime = failureGroup(properties, "runtime", false);
    assertTrue(Collections.disjoint(syntax.ids(), staticallyRejected.ids()));
    assertTrue(Collections.disjoint(syntax.ids(), runtime.ids()));
    assertTrue(Collections.disjoint(staticallyRejected.ids(), runtime.ids()));

    var cases = new ArrayList<CaseSpec>();
    for (String id : ids) {
      String source = properties.require(id + ".source");
      String explicitCommands = properties.optional(id + ".commands");
      List<String> commands =
          explicitCommands != null
              ? csv(explicitCommands)
              : syntax.ids().contains(id)
                  ? syntax.commands()
                  : staticallyRejected.ids().contains(id)
                      ? staticallyRejected.commands()
                      : runtime.ids().contains(id) ? runtime.commands() : List.of();
      requireCommands(id + ".commands", commands);

      var expectations = new LinkedHashMap<String, CommandExpectation>();
      for (String command : commands) {
        Integer explicitExit = optionalInteger(properties.optional(id + '.' + command + ".exit"));
        boolean stdoutEmpty =
            optionalBoolean(properties.optional(id + '.' + command + ".stdout.empty"), false);
        String stdoutText = properties.optional(id + '.' + command + ".stdout.text");
        String stdoutHex = properties.optional(id + '.' + command + ".stdout.hex");
        String stdoutSource = properties.optional(id + '.' + command + ".stdout.source");
        boolean trailingLf =
            optionalBoolean(properties.optional(id + '.' + command + ".stdout.trailingLf"), false);
        boolean stderrEmpty =
            optionalBoolean(properties.optional(id + '.' + command + ".stderr.empty"), false);
        int outputForms =
            (stdoutEmpty ? 1 : 0)
                + (stdoutText == null ? 0 : 1)
                + (stdoutHex == null ? 0 : 1)
                + (stdoutSource == null ? 0 : 1);
        assertTrue(outputForms <= 1, id + '/' + command + ": stdout expectation is ambiguous");

        FailureGroup group = groupFor(id, syntax, staticallyRejected, runtime);
        int exit = explicitExit != null ? explicitExit : group == null ? 0 : group.exit(command);
        if (group != null) {
          stdoutEmpty |= group.stdoutEmpty(command);
          stderrEmpty |= group.stderrEmpty(command);
        }
        expectations.put(
            command,
            new CommandExpectation(
                exit, stdoutEmpty, stdoutText, stdoutHex, stdoutSource, trailingLf, stderrEmpty));
      }
      cases.add(
          new CaseSpec(
              id,
              source,
              List.copyOf(commands),
              Collections.unmodifiableMap(expectations),
              properties.optional(id + ".trace.source")));
    }

    assertTrue(ids.containsAll(syntax.ids()), "failure.syntax.ids contains an unknown case");
    assertTrue(
        ids.containsAll(staticallyRejected.ids()), "failure.static.ids contains an unknown case");
    assertTrue(ids.containsAll(runtime.ids()), "failure.runtime.ids contains an unknown case");
    assertEquals(27, ids.stream().filter(id -> id.startsWith("TEXT-N")).count(), "TEXT-N count");
    assertEquals(32, ids.stream().filter(id -> id.startsWith("TEXT-F")).count(), "TEXT-F count");
    properties.assertFullyConsumed();
    return new CaseCatalog(
        List.copyOf(cases), syntax.ids(), staticallyRejected.ids(), runtime.ids());
  }

  /** TEXT-Fの全32構造化診断を規定順で読み込みます。 */
  public static Map<String, DiagnosticSpec> loadDiagnostics() throws IOException {
    List<List<String>> rows =
        loadTsv(
            "diagnostics.tsv",
            List.of(
                "case_id",
                "command",
                "severity",
                "code",
                "line",
                "column",
                "fields",
                "expected",
                "actual",
                "fix",
                "related"));
    var result = new LinkedHashMap<String, DiagnosticSpec>();
    for (List<String> row : rows) {
      String id = row.getFirst();
      DiagnosticSpec previous =
          result.put(
              id,
              new DiagnosticSpec(
                  id,
                  row.get(1),
                  row.get(2),
                  row.get(3),
                  integer(row.get(4)),
                  integer(row.get(5)),
                  parseFields(row.get(6)),
                  dashToNull(unescape(row.get(7))),
                  dashToNull(unescape(row.get(8))),
                  dashToNull(unescape(row.get(9))),
                  dashToNull(unescape(row.get(10)))));
      assertEquals(null, previous, id + ": duplicate diagnostic row");
    }
    return Collections.unmodifiableMap(result);
  }

  /** R7の全27境界・内部ケースを文字列variantも保って読み込みます。 */
  public static List<ResourceSpec> loadResources() throws IOException {
    List<List<String>> rows =
        loadTsv(
            "resources.tsv",
            List.of(
                "case_id", "target", "variant", "outcome", "exit", "code", "limit", "observed"));
    var result = new ArrayList<ResourceSpec>();
    var keys = new HashSet<String>();
    for (List<String> row : rows) {
      var spec =
          new ResourceSpec(
              row.get(0),
              row.get(1),
              row.get(2),
              row.get(3),
              integer(row.get(4)),
              dashToNull(row.get(5)),
              dashToNull(row.get(6)),
              dashToNull(row.get(7)));
      assertTrue(
          keys.add(spec.id() + '/' + spec.target() + '/' + spec.variant()),
          "duplicate resource row: " + spec);
      result.add(spec);
    }
    return List.copyOf(result);
  }

  /** ケースが参照するソースを加工せず決定的に返します。 */
  public static GeneratedSource generateCaseSource(CaseSpec spec) throws IOException {
    return new GeneratedSource("source", resourceBytes(spec.source()));
  }

  /** R7生成定義を未使用キー検査つきで読み込みます。 */
  public static CheckedProperties loadGeneratedResource(String id) throws IOException {
    return loadProperties("generated/" + id + ".properties");
  }

  /** クラスパス上の文字列・正規表現資源を加工せず返します。 */
  public static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = ROOT + relativePath;
    try (var input = TextRegexConformanceData.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing conformance resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  /** BOMなしの厳密なUTF-8として文字列・正規表現資源を返します。 */
  public static String resourceText(String relativePath) throws IOException {
    String text = strictUtf8(resourceBytes(relativePath), relativePath);
    assertFalse(text.startsWith("\uFEFF"), relativePath + " must not have a BOM");
    return text;
  }

  /** 16進文字列で指定された期待出力をバイト列へ戻します。 */
  public static byte[] hexBytes(String value) {
    assertEquals(0, value.length() % 2, "hex text must contain complete bytes");
    byte[] result = new byte[value.length() / 2];
    for (int index = 0; index < result.length; index++) {
      int high = Character.digit(value.charAt(index * 2), 16);
      int low = Character.digit(value.charAt(index * 2 + 1), 16);
      assertTrue(high >= 0 && low >= 0, "invalid hex text: " + value);
      result[index] = (byte) ((high << 4) | low);
    }
    return result;
  }

  private static FailureGroup failureGroup(
      CheckedProperties properties, String name, boolean commonExit) {
    Set<String> ids = Set.copyOf(csv(properties.require("failure." + name + ".ids")));
    List<String> commands = csv(properties.require("failure." + name + ".commands"));
    requireCommands("failure." + name + ".commands", commands);
    Integer sharedExit =
        commonExit ? integer(properties.require("failure." + name + ".exit")) : null;
    boolean sharedStdoutEmpty =
        optionalBoolean(properties.optional("failure." + name + ".stdout.empty"), false);
    var exits = new LinkedHashMap<String, Integer>();
    var stdoutEmpty = new LinkedHashMap<String, Boolean>();
    var stderrEmpty = new LinkedHashMap<String, Boolean>();
    for (String command : commands) {
      exits.put(
          command,
          sharedExit != null
              ? sharedExit
              : integer(properties.require("failure." + name + '.' + command + ".exit")));
      stdoutEmpty.put(
          command,
          sharedStdoutEmpty
              || optionalBoolean(
                  properties.optional("failure." + name + '.' + command + ".stdout.empty"), false));
      stderrEmpty.put(
          command,
          optionalBoolean(
              properties.optional("failure." + name + '.' + command + ".stderr.empty"), false));
    }
    return new FailureGroup(
        ids,
        List.copyOf(commands),
        Collections.unmodifiableMap(exits),
        Collections.unmodifiableMap(stdoutEmpty),
        Collections.unmodifiableMap(stderrEmpty));
  }

  private static FailureGroup groupFor(
      String id, FailureGroup syntax, FailureGroup staticallyRejected, FailureGroup runtime) {
    if (syntax.ids().contains(id)) {
      return syntax;
    }
    if (staticallyRejected.ids().contains(id)) {
      return staticallyRejected;
    }
    return runtime.ids().contains(id) ? runtime : null;
  }

  private static CheckedProperties loadProperties(String relativePath) throws IOException {
    byte[] bytes = resourceBytes(relativePath);
    String text = strictUtf8(bytes, relativePath);
    assertFalse(text.startsWith("\uFEFF"), relativePath + " must not have a BOM");
    assertFalse(text.contains("\r"), relativePath + " must use LF line endings");
    assertTrue(text.endsWith("\n"), relativePath + " must end with LF");
    var values = new DuplicateRejectingProperties(relativePath);
    try (var reader =
        new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
      values.load(reader);
    }
    return new CheckedProperties(relativePath, values);
  }

  private static List<List<String>> loadTsv(String relativePath, List<String> expectedHeader)
      throws IOException {
    String text = resourceText(relativePath);
    assertFalse(text.contains("\r"), relativePath + " must use LF line endings");
    assertTrue(text.endsWith("\n"), relativePath + " must end with LF");
    String[] lines = text.split("\n", -1);
    assertEquals(expectedHeader, Arrays.asList(lines[0].split("\t", -1)), relativePath + " header");
    var rows = new ArrayList<List<String>>();
    for (int index = 1; index < lines.length - 1; index++) {
      assertFalse(lines[index].isEmpty(), relativePath + ':' + (index + 1) + " blank row");
      List<String> cells = Arrays.asList(lines[index].split("\t", -1));
      assertEquals(expectedHeader.size(), cells.size(), relativePath + ':' + (index + 1));
      rows.add(List.copyOf(cells));
    }
    return List.copyOf(rows);
  }

  private static Map<String, String> parseFields(String raw) {
    if (raw.equals("-")) {
      return Map.of();
    }
    var result = new LinkedHashMap<String, String>();
    for (String assignment : splitEscaped(raw, ';')) {
      int separator = firstUnescaped(assignment, '=');
      assertTrue(separator > 0, "invalid diagnostic field: " + assignment);
      String key = unescape(assignment.substring(0, separator));
      String previous = result.put(key, unescape(assignment.substring(separator + 1)));
      assertEquals(null, previous, "duplicate diagnostic field: " + key);
    }
    return Collections.unmodifiableMap(result);
  }

  private static int firstUnescaped(String value, char target) {
    boolean escaped = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!escaped && character == target) {
        return index;
      }
      escaped = !escaped && character == '\\';
    }
    return -1;
  }

  private static List<String> splitEscaped(String value, char separator) {
    var result = new ArrayList<String>();
    var current = new StringBuilder();
    boolean escaped = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!escaped && character == separator) {
        result.add(current.toString());
        current.setLength(0);
      } else {
        current.append(character);
      }
      escaped = !escaped && character == '\\';
    }
    result.add(current.toString());
    return result;
  }

  private static String unescape(String value) {
    var result = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character != '\\') {
        result.append(character);
        continue;
      }
      assertTrue(++index < value.length(), "unfinished TSV escape");
      char escaped = value.charAt(index);
      result.append(
          switch (escaped) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case '\\', ';', '=' -> escaped;
            default -> throw new IllegalArgumentException("unknown TSV escape: \\" + escaped);
          });
    }
    return result.toString();
  }

  private static String strictUtf8(byte[] bytes, String name) throws CharacterCodingException {
    var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException exception) {
      throw new CharacterCodingException() {
        @Override
        public String getMessage() {
          return name + " is not valid UTF-8: " + exception.getMessage();
        }
      };
    }
  }

  private static void requireCommands(String label, List<String> commands) {
    assertFalse(commands.isEmpty(), label + " must not be empty");
    assertEquals(commands.size(), new LinkedHashSet<>(commands).size(), label + " has duplicates");
    assertTrue(COMMANDS.containsAll(commands), label + " contains an unknown command");
  }

  private static List<String> csv(String value) {
    return value.isEmpty() ? List.of() : List.of(value.split(",", -1));
  }

  private static int integer(String value) {
    return Integer.parseInt(value);
  }

  private static Integer optionalInteger(String value) {
    return value == null ? null : integer(value);
  }

  private static boolean optionalBoolean(String value, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (!value.equals("true") && !value.equals("false")) {
      throw new IllegalArgumentException("boolean must be true or false: " + value);
    }
    return Boolean.parseBoolean(value);
  }

  private static String dashToNull(String value) {
    return value.equals("-") ? null : value;
  }

  /** 取得済みキーを記録し、生成仕様の読み飛ばしを検出します。 */
  public static final class CheckedProperties {
    private final String name;
    private final Properties values;
    private final Set<String> consumed = new HashSet<>();

    private CheckedProperties(String name, Properties values) {
      this.name = name;
      this.values = values;
    }

    public String require(String key) {
      String value = optional(key);
      if (value == null) {
        throw new IllegalArgumentException(name + ": missing property " + key);
      }
      return value;
    }

    public String optional(String key) {
      String value = values.getProperty(key);
      if (value != null) {
        consumed.add(key);
      }
      return value;
    }

    public void assertFullyConsumed() {
      var unused = new java.util.TreeSet<>(values.stringPropertyNames());
      unused.removeAll(consumed);
      assertTrue(unused.isEmpty(), name + ": unused properties " + unused);
    }
  }

  private static final class DuplicateRejectingProperties extends Properties {
    private final String name;

    private DuplicateRejectingProperties(String name) {
      this.name = name;
    }

    @Override
    public synchronized Object put(Object key, Object value) {
      if (containsKey(key)) {
        throw new IllegalArgumentException(name + ": duplicate property " + key);
      }
      return super.put(key, value);
    }
  }

  private record FailureGroup(
      Set<String> ids,
      List<String> commands,
      Map<String, Integer> exits,
      Map<String, Boolean> stdoutEmpty,
      Map<String, Boolean> stderrEmpty) {
    private int exit(String command) {
      return exits.get(command);
    }

    private boolean stdoutEmpty(String command) {
      return stdoutEmpty.get(command);
    }

    private boolean stderrEmpty(String command) {
      return stderrEmpty.get(command);
    }
  }

  public record CaseCatalog(
      List<CaseSpec> cases, Set<String> syntaxIds, Set<String> staticIds, Set<String> runtimeIds) {}

  public record CaseSpec(
      String id,
      String source,
      List<String> commands,
      Map<String, CommandExpectation> expectations,
      String traceSource) {}

  public record CommandExpectation(
      int exit,
      boolean stdoutEmpty,
      String stdoutText,
      String stdoutHex,
      String stdoutSource,
      boolean trailingLf,
      boolean stderrEmpty) {}

  public record GeneratedSource(String variant, byte[] bytes) {
    public GeneratedSource {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  public record DiagnosticSpec(
      String id,
      String command,
      String severity,
      String code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      String related) {}

  public record ResourceSpec(
      String id,
      String target,
      String variant,
      String outcome,
      int exit,
      String code,
      String limit,
      String observed) {}
}
