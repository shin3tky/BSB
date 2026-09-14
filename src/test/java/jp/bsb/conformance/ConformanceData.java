package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;

/** 言語コアの適合データを、欠落や余分な項目を許さず読み込むテスト支援クラスです。 */
public final class ConformanceData {
  private static final String ROOT = "/conformance/language-core/";

  private ConformanceData() {}

  /** ケース一覧と共通規則を読み、properties の全キーが利用されたことまで確認します。 */
  public static CaseCatalog loadCases() throws IOException {
    CheckedProperties properties = loadProperties("cases.properties");
    assertEquals("1", properties.require("schema.version"));
    List<String> ids = csv(properties.require("case.ids"));
    assertEquals(ids.size(), new LinkedHashSet<>(ids).size(), "case.ids contains duplicates");

    Set<String> syntaxIds = Set.copyOf(csv(properties.require("failure.syntax.ids")));
    int syntaxExit = integer(properties.require("failure.syntax.exit"));
    assertTrue(booleanValue(properties.require("failure.syntax.stdout.empty")));
    Set<String> staticIds = Set.copyOf(csv(properties.require("failure.static.ids")));
    int staticCheckExit = integer(properties.require("failure.static.check.exit"));
    int staticRunExit = integer(properties.require("failure.static.run.exit"));
    assertTrue(booleanValue(properties.require("failure.static.stdout.empty")));
    assertEquals(0, integer(properties.require("failure.static.format.exit")));
    assertTrue(booleanValue(properties.require("failure.static.format.stderr.empty")));
    Set<String> warningIds = Set.copyOf(csv(properties.require("failure.warning.ids")));

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
        Boolean bom = optionalBooleanObject(properties.optional(id + '.' + command + ".bom"));

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
                        : staticIds.contains(id) && command.equals("run") ? staticRunExit : 0;
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
                idempotent,
                bom));
      }
      cases.add(new CaseSpec(id, source, generated, commands, Map.copyOf(expectations)));
    }

    assertTrue(ids.containsAll(syntaxIds), "failure.syntax.ids contains an unknown case");
    assertTrue(ids.containsAll(staticIds), "failure.static.ids contains an unknown case");
    assertTrue(ids.containsAll(warningIds), "failure.warning.ids contains an unknown case");
    var classifiedIds = new HashSet<String>();
    assertTrue(classifiedIds.addAll(syntaxIds), "failure.syntax.ids contains duplicates");
    assertTrue(classifiedIds.addAll(staticIds), "failure classifications overlap");
    assertTrue(classifiedIds.addAll(warningIds), "failure classifications overlap");
    properties.assertFullyConsumed();
    return new CaseCatalog(List.copyOf(cases), syntaxIds, staticIds, warningIds);
  }

  /** cases.propertiesから参照されない通常source・canonical資源を拒否します。 */
  public static void validateCaseTextResources(CaseCatalog catalog) throws IOException {
    var expected = new LinkedHashSet<String>();
    for (CaseSpec spec : catalog.cases()) {
      if (spec.source() != null) {
        expected.add(spec.source());
      }
      spec.expectations().values().stream()
          .map(CommandExpectation::stdoutSource)
          .filter(java.util.Objects::nonNull)
          .filter(path -> path.startsWith("canonical/"))
          .forEach(expected::add);
    }

    var present = new LinkedHashSet<String>();
    for (String directory : List.of("sources", "canonical")) {
      Path root = Path.of("tests/conformance/language-core", directory);
      try (var paths = Files.walk(root)) {
        paths
            .filter(Files::isRegularFile)
            .map(Path.of("tests/conformance/language-core")::relativize)
            .map(path -> path.toString().replace(java.io.File.separatorChar, '/'))
            .forEach(present::add);
      }
    }
    assertEquals(expected, present, "unused or missing Core source/canonical resources");
  }

  /** 期待診断TSVを読み、ケースIDごとに一意な診断を返します。 */
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

  /** 資源境界TSVを読み、各行を独立した適合ケースとして返します。 */
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

  /** 適合用メッセージファイルをUTF-8として厳密に読み、全診断コードの存在も検査します。 */
  public static void validateMessages() throws IOException {
    byte[] CoreBytes = resourceBytes("messages.properties");
    byte[] ControlFlowBytes =
        absoluteResourceBytes("/conformance/control-flow/messages.properties");
    byte[] bindingsBytes = absoluteResourceBytes("/conformance/bindings/messages.properties");
    byte[] arraysBytes = absoluteResourceBytes("/conformance/arrays/messages.properties");
    byte[] numericsBytes = absoluteResourceBytes("/conformance/numerics/messages.properties");
    byte[] TextRegexBytes = absoluteResourceBytes("/conformance/text-regex/messages.properties");
    byte[] HostIoBytes = absoluteResourceBytes("/conformance/host-io/messages.properties");
    byte[] jsonBytes = absoluteResourceBytes("/conformance/json/messages.properties");
    byte[] OptionalBytes =
        absoluteResourceBytes("/conformance/optional-values/messages.properties");
    byte[] ResultBytes = absoluteResourceBytes("/conformance/result-values/messages.properties");
    byte[] ConnectionBytes =
        absoluteResourceBytes("/conformance/logical-connections/messages.properties");
    byte[] ByteSequenceBytes =
        absoluteResourceBytes("/conformance/byte-sequences/messages.properties");
    byte[] httpsBytes = absoluteResourceBytes("/conformance/https/messages.properties");
    byte[] NestedArrayBytes =
        absoluteResourceBytes("/conformance/nested-arrays/messages.properties");
    byte[] WorkspaceTableBytes =
        absoluteResourceBytes("/conformance/workspace-tables/messages.properties");
    byte[] jsonShapesBytes = absoluteResourceBytes("/conformance/json-shapes/messages.properties");
    String CoreText = strictUtf8(CoreBytes, "Core/messages.properties");
    String ControlFlowText = strictUtf8(ControlFlowBytes, "ControlFlow/messages.properties");
    String bindingsText = strictUtf8(bindingsBytes, "bindings/messages.properties");
    String arraysText = strictUtf8(arraysBytes, "arrays/messages.properties");
    String numericsText = strictUtf8(numericsBytes, "numerics/messages.properties");
    String TextRegexText = strictUtf8(TextRegexBytes, "TextRegex/messages.properties");
    String HostIoText = strictUtf8(HostIoBytes, "HostIo/messages.properties");
    String jsonText = strictUtf8(jsonBytes, "json/messages.properties");
    String OptionalText = strictUtf8(OptionalBytes, "Optional/messages.properties");
    String ResultText = strictUtf8(ResultBytes, "Result/messages.properties");
    String ConnectionText = strictUtf8(ConnectionBytes, "Connection/messages.properties");
    String ByteSequenceText = strictUtf8(ByteSequenceBytes, "ByteSequence/messages.properties");
    String httpsText = strictUtf8(httpsBytes, "https/messages.properties");
    String NestedArrayText = strictUtf8(NestedArrayBytes, "NestedArray/messages.properties");
    String WorkspaceTableText =
        strictUtf8(WorkspaceTableBytes, "WorkspaceTable/messages.properties");
    String jsonShapesText = strictUtf8(jsonShapesBytes, "json-shapes/messages.properties");
    assertFalse(CoreText.startsWith("\uFEFF"), "Core messages must not have a BOM");
    assertFalse(ControlFlowText.startsWith("\uFEFF"), "ControlFlow messages must not have a BOM");
    assertFalse(bindingsText.startsWith("\uFEFF"), "bindings messages must not have a BOM");
    assertFalse(arraysText.startsWith("\uFEFF"), "arrays messages must not have a BOM");
    assertFalse(numericsText.startsWith("\uFEFF"), "numerics messages must not have a BOM");
    assertFalse(TextRegexText.startsWith("\uFEFF"), "TextRegex messages must not have a BOM");
    assertFalse(HostIoText.startsWith("\uFEFF"), "HostIo messages must not have a BOM");
    assertFalse(jsonText.startsWith("\uFEFF"), "json messages must not have a BOM");
    assertFalse(OptionalText.startsWith("\uFEFF"), "Optional messages must not have a BOM");
    assertFalse(ResultText.startsWith("\uFEFF"), "Result messages must not have a BOM");
    assertFalse(ConnectionText.startsWith("\uFEFF"), "Connection messages must not have a BOM");
    assertFalse(ByteSequenceText.startsWith("\uFEFF"), "ByteSequence messages must not have a BOM");
    assertFalse(httpsText.startsWith("\uFEFF"), "https messages must not have a BOM");
    assertFalse(NestedArrayText.startsWith("\uFEFF"), "NestedArray messages must not have a BOM");
    assertFalse(
        WorkspaceTableText.startsWith("\uFEFF"), "WorkspaceTable messages must not have a BOM");
    assertFalse(jsonShapesText.startsWith("\uFEFF"), "json-shapes messages must not have a BOM");
    try (Reader CoreReader = new java.io.StringReader(CoreText);
        Reader ControlFlowReader = new java.io.StringReader(ControlFlowText);
        Reader bindingsReader = new java.io.StringReader(bindingsText);
        Reader arraysReader = new java.io.StringReader(arraysText);
        Reader numericsReader = new java.io.StringReader(numericsText);
        Reader TextRegexReader = new java.io.StringReader(TextRegexText);
        Reader HostIoReader = new java.io.StringReader(HostIoText);
        Reader jsonReader = new java.io.StringReader(jsonText);
        Reader OptionalReader = new java.io.StringReader(OptionalText);
        Reader ResultReader = new java.io.StringReader(ResultText);
        Reader ConnectionReader = new java.io.StringReader(ConnectionText);
        Reader ByteSequenceReader = new java.io.StringReader(ByteSequenceText);
        Reader httpsReader = new java.io.StringReader(httpsText);
        Reader NestedArrayReader = new java.io.StringReader(NestedArrayText);
        Reader WorkspaceTableReader = new java.io.StringReader(WorkspaceTableText);
        Reader jsonShapesReader = new java.io.StringReader(jsonShapesText)) {
      // 機能グループごとの断片を統合し、重複と全コードの欠落をまとめて検査する。
      DiagnosticMessageCatalog.load(
          CoreReader,
          ControlFlowReader,
          bindingsReader,
          arraysReader,
          numericsReader,
          TextRegexReader,
          HostIoReader,
          jsonReader,
          OptionalReader,
          ResultReader,
          ConnectionReader,
          ByteSequenceReader,
          httpsReader,
          NestedArrayReader,
          WorkspaceTableReader,
          jsonShapesReader);
    }
  }

  private static byte[] absoluteResourceBytes(String resource) throws IOException {
    try (var input = ConformanceData.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing conformance resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  /** クラスパス上の適合資源を生のバイト列で返します。 */
  public static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = ROOT + relativePath;
    try (var input = ConformanceData.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing conformance resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  /** BOMなしの厳密なUTF-8テキスト資源を返します。 */
  public static String resourceText(String relativePath) throws IOException {
    byte[] bytes = resourceBytes(relativePath);
    String text = strictUtf8(bytes, relativePath);
    assertFalse(text.startsWith("\uFEFF"), relativePath + " must not have a BOM");
    return text;
  }

  /** 生成定義から、同じケースに属する1個以上のソース変種を再現します。 */
  public static List<GeneratedSource> generateCaseSource(CaseSpec spec) throws IOException {
    if (spec.source() != null) {
      return List.of(new GeneratedSource("source", resourceBytes(spec.source())));
    }
    CheckedProperties properties = loadProperties(spec.generated());
    String generator = properties.require("generator");
    List<GeneratedSource> result =
        switch (generator) {
          case "concat-bytes" -> generateConcatenatedBytes(properties);
          case "inline-text" -> generateInlineText(properties);
          case "repeat-in-template" -> generateRepeatedText(properties);
          case "line-endings" -> generateLineEndings(properties);
          case "prefix-bytes" -> generatePrefixedBytes(properties);
          default -> throw new IllegalArgumentException("unknown case generator: " + generator);
        };
    consumeCaseGeneratorExpectations(properties, generator, spec);
    properties.assertFullyConsumed();
    return result;
  }

  /** 資源テスト用propertiesを、使用済みキー追跡付きで返します。 */
  public static CheckedProperties loadGeneratedResource(String id) throws IOException {
    return loadProperties("generated/" + id + ".properties");
  }

  /** 16進表記を生のバイト列へ変換します。 */
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

  private static List<GeneratedSource> generateConcatenatedBytes(CheckedProperties properties) {
    byte[] first = properties.require("part.1.text").getBytes(StandardCharsets.UTF_8);
    byte[] second = hexBytes(properties.require("part.2.hex"));
    byte[] third = properties.require("part.3.text").getBytes(StandardCharsets.UTF_8);
    return List.of(new GeneratedSource("generated", concatenate(first, second, third)));
  }

  private static List<GeneratedSource> generateInlineText(CheckedProperties properties) {
    return List.of(
        new GeneratedSource(
            "generated", properties.require("source").getBytes(StandardCharsets.UTF_8)));
  }

  private static List<GeneratedSource> generateRepeatedText(CheckedProperties properties) {
    String text =
        properties.require("prefix")
            + properties.require("repeat.text").repeat(integer(properties.require("repeat.count")))
            + properties.require("suffix");
    return List.of(new GeneratedSource("generated", text.getBytes(StandardCharsets.UTF_8)));
  }

  private static List<GeneratedSource> generateLineEndings(CheckedProperties properties)
      throws IOException {
    String template = resourceText(properties.require("template"));
    assertFalse(template.contains("\r"), "line-ending template must use LF");
    var result = new ArrayList<GeneratedSource>();
    for (String variant : csv(properties.require("variants"))) {
      String separator =
          switch (variant) {
            case "LF" -> "\n";
            case "CR" -> "\r";
            case "CRLF" -> "\r\n";
            default -> throw new IllegalArgumentException("unknown line ending: " + variant);
          };
      result.add(
          new GeneratedSource(
              variant, template.replace("\n", separator).getBytes(StandardCharsets.UTF_8)));
    }
    return List.copyOf(result);
  }

  private static List<GeneratedSource> generatePrefixedBytes(CheckedProperties properties)
      throws IOException {
    byte[] prefix = hexBytes(properties.require("prefix.hex"));
    byte[] template = resourceBytes(properties.require("template"));
    return List.of(new GeneratedSource("prefixed", concatenate(prefix, template)));
  }

  private static void consumeCaseGeneratorExpectations(
      CheckedProperties properties, String generator, CaseSpec spec) throws IOException {
    if (generator.equals("line-endings")) {
      assertEquals(spec.commands(), csv(properties.require("expect.commands")));
      assertEquals(
          spec.expectations().get("check").exit(),
          integer(properties.require("expect.check.exit")));
      assertEquals(
          spec.expectations().get("format").exit(),
          integer(properties.require("expect.format.exit")));
      assertEquals(
          spec.expectations().get("format").stdoutSource(),
          properties.require("expect.format.template"));
    } else if (generator.equals("prefix-bytes")) {
      assertEquals(
          spec.expectations().get("check").exit(),
          integer(properties.require("expect.check.exit")));
      assertEquals(
          spec.expectations().get("format").exit(),
          integer(properties.require("expect.format.exit")));
      assertEquals(
          spec.expectations().get("format").bom(),
          booleanValue(properties.require("expect.format.bom")));
    } else {
      assertEquals(spec.commands(), csv(properties.require("expect.commands")));
      int exit = integer(properties.require("expect.exit"));
      spec.expectations().values().forEach(expectation -> assertEquals(exit, expectation.exit()));
      DiagnosticSpec diagnostic = loadDiagnostics().get(spec.id());
      assertEquals(diagnostic.code(), properties.require("expect.diagnostic"));
      properties.optional("expect.utf8Offset");
      String line = properties.optional("expect.line");
      String column = properties.optional("expect.column");
      if (line != null) {
        assertEquals(diagnostic.line(), integer(line));
        assertEquals(diagnostic.column(), integer(column));
      }
      String limit = properties.optional("expect.limit");
      String observed = properties.optional("expect.observed");
      if (limit != null) {
        assertEquals(diagnostic.fields().get("limit"), limit);
        assertEquals(diagnostic.fields().get("observed"), observed);
      }
    }
  }

  private static CheckedProperties loadProperties(String relativePath) throws IOException {
    byte[] bytes = resourceBytes(relativePath);
    String text = strictUtf8(bytes, relativePath);
    assertFalse(text.startsWith("\uFEFF"), relativePath + " must not have a BOM");
    var values = new DuplicateRejectingProperties(relativePath);
    try (var reader =
        new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
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

  private static byte[] concatenate(byte[]... parts) {
    int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, result, offset, part.length);
      offset += part.length;
    }
    return result;
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

  private static Boolean optionalBooleanObject(String value) {
    return value == null ? null : booleanValue(value);
  }

  private static String dashToNull(String value) {
    return value.equals("-") ? null : value;
  }

  /** 取得したキーを記録し、生成仕様の読み飛ばしを検出するPropertiesラッパーです。 */
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

  public record CaseCatalog(
      List<CaseSpec> cases, Set<String> syntaxIds, Set<String> staticIds, Set<String> warningIds) {}

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
      boolean idempotent,
      Boolean bom) {}

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
