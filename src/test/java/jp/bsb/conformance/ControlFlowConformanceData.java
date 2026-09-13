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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 制御フローの適合データだけを、欠落、重複、未使用の項目を許さず読み込む支援クラスです。 */
public final class ControlFlowConformanceData {
  private static final String ROOT = "/conformance/control-flow/";

  private ControlFlowConformanceData() {}

  /**
   * 正常例と失敗例の一覧を読み込みます。
   *
   * <p>propertiesのキーを全部「使用済み」にすることで、仕様データへ項目を追加したのにテスト側が読み忘れた事故も検出します。
   */
  public static CaseCatalog loadCases() throws IOException {
    CheckedProperties properties = loadProperties("cases.properties");
    assertEquals("1", properties.require("schema.version"));
    List<String> ids = csv(properties.require("case.ids"));
    assertEquals(ids.size(), new LinkedHashSet<>(ids).size(), "case.ids contains duplicates");

    Set<String> syntaxIds = Set.copyOf(csv(properties.require("failure.syntax.ids")));
    int syntaxExit = integer(properties.require("failure.syntax.exit"));
    boolean syntaxStdoutEmpty = booleanValue(properties.require("failure.syntax.stdout.empty"));
    Set<String> staticIds = Set.copyOf(csv(properties.require("failure.static.ids")));
    int staticCheckExit = integer(properties.require("failure.static.check.exit"));
    int staticRunExit = integer(properties.require("failure.static.run.exit"));
    boolean staticStdoutEmpty = booleanValue(properties.require("failure.static.stdout.empty"));
    int staticFormatExit = integer(properties.require("failure.static.format.exit"));
    boolean staticFormatStderrEmpty =
        booleanValue(properties.require("failure.static.format.stderr.empty"));

    var cases = new ArrayList<CaseSpec>();
    for (String id : ids) {
      String source = properties.optional(id + ".source");
      String generated = properties.optional(id + ".source.generated");
      assertTrue((source == null) != (generated == null), id + ": exactly one source is required");
      List<String> commands = csv(properties.require(id + ".commands"));
      assertFalse(commands.isEmpty(), id + ": commands must not be empty");

      var expectations = new LinkedHashMap<String, CommandExpectation>();
      for (String command : commands) {
        assertTrue(Set.of("check", "run", "format").contains(command), id + ": unknown command");
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
        boolean idempotent =
            optionalBoolean(properties.optional(id + '.' + command + ".idempotent"), false);

        int outputForms =
            (stdoutEmpty ? 1 : 0)
                + (stdoutText == null ? 0 : 1)
                + (stdoutHex == null ? 0 : 1)
                + (stdoutSource == null ? 0 : 1);
        assertTrue(outputForms <= 1, id + '/' + command + ": stdout expectation is ambiguous");

        int exit =
            explicitExit != null
                ? explicitExit
                : syntaxIds.contains(id)
                    ? syntaxExit
                    : staticIds.contains(id) && command.equals("check")
                        ? staticCheckExit
                        : staticIds.contains(id) && command.equals("run")
                            ? staticRunExit
                            : staticIds.contains(id) && command.equals("format")
                                ? staticFormatExit
                                : 0;
        // 共通規則も各ケースの期待値へ展開し、規則と個別値が同じ比較経路を通るようにする。
        stdoutEmpty |= syntaxIds.contains(id) && syntaxStdoutEmpty;
        stdoutEmpty |= staticIds.contains(id) && !command.equals("format") && staticStdoutEmpty;
        stderrEmpty |=
            staticIds.contains(id) && command.equals("format") && staticFormatStderrEmpty;
        expectations.put(
            command,
            new CommandExpectation(
                exit,
                stdoutEmpty,
                stdoutText,
                stdoutHex,
                stdoutSource,
                trailingLf,
                stderrEmpty,
                idempotent));
      }
      cases.add(new CaseSpec(id, source, generated, commands, Map.copyOf(expectations)));
    }

    assertTrue(ids.containsAll(syntaxIds), "failure.syntax.ids contains an unknown case");
    assertTrue(ids.containsAll(staticIds), "failure.static.ids contains an unknown case");
    properties.assertFullyConsumed();
    return new CaseCatalog(List.copyOf(cases), syntaxIds, staticIds);
  }

  /** 期待する構造化診断をTSVから読み、ケースIDごとの辞書にします。 */
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
      String id = row.get(0);
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
    return Map.copyOf(result);
  }

  /** 上限ちょうどと1単位超過を表す資源境界行を読み込みます。 */
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
              Long.parseLong(row.get(2)),
              row.get(3),
              integer(row.get(4)),
              dashToNull(row.get(5)),
              Long.parseLong(row.get(6)),
              Long.parseLong(row.get(7)));
      assertTrue(keys.add(spec.id() + '/' + spec.variant()), "duplicate resource row: " + spec);
      result.add(spec);
    }
    return List.copyOf(result);
  }

  /** ケースが参照するファイルまたは決定的生成定義から、入力バイト列を再現します。 */
  public static GeneratedSource generateCaseSource(CaseSpec spec) throws IOException {
    if (spec.source() != null) {
      return new GeneratedSource("source", resourceBytes(spec.source()));
    }
    CheckedProperties properties = loadProperties(spec.generated());
    assertEquals("nested-control", properties.require("generator"));
    int depth = integer(properties.require("depth"));
    assertEquals("conditional-no-else", properties.require("control"));
    int expectedLines = integer(properties.require("expected.lines"));
    int expectedDepthLine = integer(properties.require("expected.depth.line"));
    int expectedDepthColumn = integer(properties.require("expected.depth.column"));
    assertEquals(4, integer(properties.require("line.indent.spaces")));
    properties.assertFullyConsumed();

    String text = nestedConditionalSource(depth);
    assertEquals(expectedLines, text.lines().count());
    DiagnosticSpec diagnostic = loadDiagnostics().get(spec.id());
    assertEquals(expectedDepthLine, diagnostic.line());
    assertEquals(expectedDepthColumn, diagnostic.column());
    return new GeneratedSource("depth-" + depth, text.getBytes(StandardCharsets.UTF_8));
  }

  /** 資源ケースの生成定義を、全キーの使用状況を追跡できる形で返します。 */
  public static CheckedProperties loadGeneratedResource(String id) throws IOException {
    return loadProperties("generated/" + id + ".properties");
  }

  /** 深さ上限試験で使う、同じ形の入れ子条件分岐を決定的に生成します。 */
  public static String nestedConditionalSource(int depth) {
    var source = new StringBuilder("メインとは （--）\n");
    source.append("    はい ならば\n".repeat(depth));
    source.append("    つぎに\n".repeat(depth));
    return source.append("こと。\n").toString();
  }

  /** クラスパス上の制御フロー資源を加工せずに返します。 */
  public static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = ROOT + relativePath;
    try (var input = ControlFlowConformanceData.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing conformance resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  /** BOMなしの厳密なUTF-8として、制御フローのテキスト資源を返します。 */
  public static String resourceText(String relativePath) throws IOException {
    byte[] bytes = resourceBytes(relativePath);
    String text = strictUtf8(bytes, relativePath);
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

  private static CheckedProperties loadProperties(String relativePath) throws IOException {
    byte[] bytes = resourceBytes(relativePath);
    String text = strictUtf8(bytes, relativePath);
    assertFalse(text.startsWith("\uFEFF"), relativePath + " must not have a BOM");
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
      List<String> pair = splitEscaped(assignment, '=');
      assertEquals(2, pair.size(), "invalid diagnostic field: " + assignment);
      String previous = result.put(unescape(pair.get(0)), unescape(pair.get(1)));
      assertEquals(null, previous, "duplicate diagnostic field: " + pair.get(0));
    }
    return Map.copyOf(result);
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
      if (escaped) {
        escaped = false;
      } else if (character == '\\') {
        escaped = true;
      }
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

  private static List<String> csv(String value) {
    return value.isEmpty() ? List.of() : List.of(value.split(",", -1));
  }

  private static int integer(String value) {
    return Integer.parseInt(value);
  }

  private static Integer optionalInteger(String value) {
    return value == null ? null : integer(value);
  }

  private static boolean booleanValue(String value) {
    if (!value.equals("true") && !value.equals("false")) {
      throw new IllegalArgumentException("boolean must be true or false: " + value);
    }
    return Boolean.parseBoolean(value);
  }

  private static boolean optionalBoolean(String value, boolean defaultValue) {
    return value == null ? defaultValue : booleanValue(value);
  }

  private static String dashToNull(String value) {
    return value.equals("-") ? null : value;
  }

  /** 読み取ったキーを記録し、生成仕様の読み飛ばしを検出するpropertiesラッパーです。 */
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

  public record CaseCatalog(List<CaseSpec> cases, Set<String> syntaxIds, Set<String> staticIds) {}

  public record CaseSpec(
      String id,
      String source,
      String generated,
      List<String> commands,
      Map<String, CommandExpectation> expectations) {}

  public record CommandExpectation(
      int exit,
      boolean stdoutEmpty,
      String stdoutText,
      String stdoutHex,
      String stdoutSource,
      boolean trailingLf,
      boolean stderrEmpty,
      boolean idempotent) {}

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
      long variant,
      String outcome,
      int exit,
      String code,
      long limit,
      long observed) {}
}
