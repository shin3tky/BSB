package jp.bsb.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** explain JSONケースと期待文書を不足・重複・未使用なしで読み込みます。 */
final class ExplainJsonConformanceData {
  private static final String RESOURCE_ROOT = "/conformance/explain-json/";
  private static final Path FILE_ROOT = Path.of("tests/conformance/explain-json");
  private static final Set<String> KINDS =
      Set.of("file", "usage", "missing", "synthetic-internal", "synthetic-output-limit");

  private ExplainJsonConformanceData() {}

  static List<CaseSpec> load() throws IOException {
    CheckedProperties properties = loadProperties("cases.properties");
    require("1".equals(properties.take("schema.version")), "unknown schema version");
    List<String> ids = csv(properties.take("case.ids"));
    require(ids.size() == new LinkedHashSet<>(ids).size(), "duplicate case id");
    var expectedPaths = new LinkedHashSet<String>();
    var result = new ArrayList<CaseSpec>();
    for (String id : ids) {
      String kind = properties.take(id + ".kind");
      require(KINDS.contains(kind), id + ": unknown kind");
      String source = properties.optional(id + ".source");
      if (kind.equals("usage")) {
        require(source == null, id + ": usage must not declare source");
      } else {
        require(source != null && !source.isBlank(), id + ": missing source");
      }
      if (kind.equals("file")) {
        require(Files.isRegularFile(Path.of(source)), id + ": source does not exist");
      }
      if (kind.equals("missing")) {
        require(!Files.exists(Path.of(source)), id + ": missing source unexpectedly exists");
      }
      String expected = properties.take(id + ".expected");
      require(expectedPaths.add(expected), id + ": duplicate expected resource");
      int exitCode = Integer.parseInt(properties.take(id + ".exit"));
      byte[] expectedBytes = resourceBytes(expected);
      validatePhysicalJson(expected, expectedBytes);
      StrictJsonParser.parse(expectedBytes);
      result.add(new CaseSpec(id, kind, source, exitCode, expectedBytes));
    }
    properties.assertEmpty();

    Set<String> present = new LinkedHashSet<>();
    try (var paths = Files.list(FILE_ROOT.resolve("expected"))) {
      paths
          .filter(Files::isRegularFile)
          .forEach(path -> present.add("expected/" + path.getFileName()));
    }
    require(
        present.equals(expectedPaths),
        "unused or missing expected resources: " + difference(present, expectedPaths));
    return List.copyOf(result);
  }

  private static void validatePhysicalJson(String name, byte[] bytes) {
    require(bytes.length > 1, name + ": empty JSON resource");
    require(
        !(bytes.length >= 3
            && bytes[0] == (byte) 0xEF
            && bytes[1] == (byte) 0xBB
            && bytes[2] == (byte) 0xBF),
        name + ": BOM is forbidden");
    require(bytes[bytes.length - 1] == '\n', name + ": missing trailing LF");
    for (int index = 0; index < bytes.length - 1; index++) {
      require(bytes[index] != '\n' && bytes[index] != '\r', name + ": JSON must be one line");
    }
  }

  private static Set<String> difference(Set<String> left, Set<String> right) {
    var result = new LinkedHashSet<>(left);
    result.removeAll(right);
    var missing = new LinkedHashSet<>(right);
    missing.removeAll(left);
    result.addAll(missing);
    return result;
  }

  private static CheckedProperties loadProperties(String relativePath) throws IOException {
    var values = new DuplicateRejectingProperties();
    try (Reader reader =
        new InputStreamReader(resourceStream(relativePath), StandardCharsets.UTF_8)) {
      values.load(reader);
    }
    return new CheckedProperties(relativePath, values);
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = resourceStream(relativePath)) {
      return input.readAllBytes();
    }
  }

  private static java.io.InputStream resourceStream(String relativePath) throws IOException {
    var input = ExplainJsonConformanceData.class.getResourceAsStream(RESOURCE_ROOT + relativePath);
    if (input == null) {
      throw new IOException("missing explain-json resource: " + relativePath);
    }
    return input;
  }

  private static List<String> csv(String value) {
    return List.of(value.split(",", -1));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  record CaseSpec(String id, String kind, String source, int exitCode, byte[] expectedBytes) {
    CaseSpec {
      expectedBytes = expectedBytes.clone();
    }

    @Override
    public byte[] expectedBytes() {
      return expectedBytes.clone();
    }

    @Override
    public String toString() {
      return id;
    }
  }

  private static final class CheckedProperties {
    private final String name;
    private final Properties values;
    private final Set<String> consumed = new LinkedHashSet<>();

    private CheckedProperties(String name, Properties values) {
      this.name = name;
      this.values = values;
    }

    private String take(String key) {
      String value = optional(key);
      require(value != null, name + ": missing property " + key);
      return value;
    }

    private String optional(String key) {
      consumed.add(key);
      return values.getProperty(key);
    }

    private void assertEmpty() {
      var remaining = new LinkedHashSet<>(values.stringPropertyNames());
      remaining.removeAll(consumed);
      require(remaining.isEmpty(), name + ": unused properties " + remaining);
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
