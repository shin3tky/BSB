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

/** バイト列の全34 ID、独立変換ベクトル、操作・資源期待、manifestを厳密に読みます。 */
public final class ByteSequenceConformanceData {
  private static final String ROOT = "/conformance/byte-sequences/";

  private ByteSequenceConformanceData() {}

  public static List<CatalogSpec> loadCatalog() throws IOException {
    var rows = table("catalog.tsv", "case_id", "kind", "evidence", "scope");
    var result = new ArrayList<CatalogSpec>();
    var seen = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      var item =
          new CatalogSpec(
              row.get("case_id"), row.get("kind"), row.get("evidence"), row.get("scope"));
      require(seen.add(item.id()), "duplicate catalog id: " + item.id());
      require(
          Set.of("normal", "failure", "warning", "resource").contains(item.kind()), "catalog kind");
      require(Set.of("public", "internal").contains(item.scope()), "catalog scope");
      resourceBytes(item.evidence());
      result.add(item);
    }
    var expected = new ArrayList<String>();
    expected.addAll(ids("BYTES-N", 12));
    expected.addAll(ids("BYTES-F", 12));
    expected.addAll(ids("BYTES-R", 10));
    requireEquals(expected, result.stream().map(CatalogSpec::id).toList(), "catalog ids");
    return List.copyOf(result);
  }

  public static List<Utf8Vector> loadUtf8Vectors() throws IOException {
    return table("vectors/utf8.tsv", "case_id", "vector", "code_points", "utf8_hex", "text_json")
        .stream()
        .map(Utf8Vector::new)
        .toList();
  }

  public static List<Utf8Failure> loadUtf8Failures() throws IOException {
    var result =
        table("vectors/utf8-failures.tsv", "case_id", "variant", "input_hex", "kind", "offset")
            .stream()
            .map(Utf8Failure::new)
            .toList();
    requireEquals(
        Set.of(
            "invalidLeadingByte",
            "invalidContinuationByte",
            "truncatedSequence",
            "overlongEncoding",
            "surrogateCodePoint",
            "codePointOutOfRange"),
        result.stream().map(Utf8Failure::kind).collect(java.util.stream.Collectors.toSet()),
        "UTF-8 failure kinds");
    return result;
  }

  public static List<Base64Vector> loadBase64Vectors() throws IOException {
    return table("vectors/base64.tsv", "case_id", "vector", "input_hex", "encoded").stream()
        .map(Base64Vector::new)
        .toList();
  }

  public static List<Base64Failure> loadBase64Failures() throws IOException {
    var result =
        table(
                "vectors/base64-failures.tsv",
                "case_id",
                "variant",
                "input_utf8_hex",
                "kind",
                "position")
            .stream()
            .map(Base64Failure::new)
            .toList();
    requireEquals(
        Set.of("invalidCharacter", "invalidLength", "invalidPadding", "nonZeroPadBits"),
        result.stream().map(Base64Failure::kind).collect(java.util.stream.Collectors.toSet()),
        "Base64 failure kinds");
    return result;
  }

  public static List<SliceSpec> loadSlices() throws IOException {
    return table(
            "operations/slices.tsv",
            "case_id",
            "variant",
            "input_hex",
            "start",
            "end",
            "outcome",
            "output_hex",
            "diagnostic",
            "construction",
            "work")
        .stream()
        .map(SliceSpec::new)
        .toList();
  }

  public static List<EqualitySpec> loadEquality() throws IOException {
    return table(
            "operations/equality.tsv",
            "case_id",
            "variant",
            "left_hex",
            "right_hex",
            "wrapper",
            "result",
            "compared_bytes",
            "construction")
        .stream()
        .map(EqualitySpec::new)
        .toList();
  }

  public static List<ResourceSpec> loadResources() throws IOException {
    var result =
        table(
                "resources.tsv",
                "case_id",
                "variant",
                "metric",
                "recipe",
                "limit",
                "used",
                "requested",
                "observed",
                "outcome",
                "diagnostic",
                "construction_after",
                "work_after",
                "state_preserved")
            .stream()
            .map(ResourceSpec::new)
            .toList();
    requireEquals(
        IntStream.rangeClosed(1, 10)
            .mapToObj(number -> "BYTES-R%03d".formatted(number))
            .collect(java.util.stream.Collectors.toSet()),
        result.stream()
            .map(ResourceSpec::id)
            .filter(id -> id.startsWith("BYTES-R"))
            .collect(java.util.stream.Collectors.toSet()),
        "resource ids");
    require(result.stream().allMatch(ResourceSpec::statePreserved), "resource atomicity");
    return result;
  }

  public static List<DiagnosticSpec> loadDiagnostics() throws IOException {
    var result =
        table(
                "expected/diagnostics.tsv",
                "case_id",
                "variant",
                "code",
                "stage",
                "target_lexeme",
                "fields_json",
                "expected",
                "actual")
            .stream()
            .map(DiagnosticSpec::new)
            .toList();
    requireEquals(
        Set.of(
            "E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS",
            "E_BYTE_SEQUENCE_SIZE_LIMIT",
            "E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT",
            "E_BYTE_SEQUENCE_WORK_LIMIT"),
        result.stream().map(DiagnosticSpec::code).collect(java.util.stream.Collectors.toSet()),
        "diagnostic codes");
    require(result.stream().allMatch(item -> item.stage().equals("runtime")), "diagnostic stage");
    return result;
  }

  public static List<StateSpec> loadStates() throws IOException {
    return table(
            "expected/states.tsv",
            "case_id",
            "variant",
            "operation",
            "input_fixture",
            "outcome",
            "result_type",
            "result_state",
            "kind",
            "position",
            "stack_replaced",
            "stdout_utf8_hex",
            "stderr_utf8_hex",
            "construction",
            "work")
        .stream()
        .map(StateSpec::new)
        .toList();
  }

  public static List<TraceSpec> loadTraces() throws IOException {
    var result =
        table(
                "expected/trace.tsv",
                "case_id",
                "variant",
                "type",
                "expected_trace",
                "forbidden_markers")
            .stream()
            .map(TraceSpec::new)
            .toList();
    require(
        result.stream().allMatch(item -> item.expectedTrace().equals(item.type() + ":<redacted>")),
        "trace redaction");
    return result;
  }

  public static List<FileHash> loadManifest() throws IOException {
    var rows = table("manifest.tsv", "path", "sha256", "bytes");
    var result = new ArrayList<FileHash>();
    var paths = new LinkedHashSet<String>();
    for (Map<String, String> row : rows) {
      require(paths.add(row.get("path")), "duplicate manifest path");
      byte[] bytes = resourceBytes(row.get("path"));
      int length = Integer.parseInt(row.get("bytes"));
      requireEquals(length, bytes.length, row.get("path") + ": bytes");
      requireEquals(row.get("sha256"), sha256(bytes), row.get("path") + ": sha256");
      result.add(new FileHash(row.get("path"), row.get("sha256"), length));
    }
    return List.copyOf(result);
  }

  public static byte[] resourceBytes(String relativePath) throws IOException {
    try (var input = ByteSequenceConformanceData.class.getResourceAsStream(ROOT + relativePath)) {
      if (input == null) throw new IOException("missing ByteSequence resource: " + relativePath);
      byte[] bytes = input.readAllBytes();
      require(bytes.length > 0, "empty resource: " + relativePath);
      require(
          !(bytes.length >= 3
              && bytes[0] == (byte) 0xef
              && bytes[1] == (byte) 0xbb
              && bytes[2] == (byte) 0xbf),
          "BOM is forbidden: " + relativePath);
      require(bytes[bytes.length - 1] == '\n', "final LF is required: " + relativePath);
      require(new String(bytes, StandardCharsets.UTF_8).indexOf('\r') < 0, "CR is forbidden");
      return bytes;
    }
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static List<Map<String, String>> table(String path, String... columns)
      throws IOException {
    byte[] bytes = resourceBytes(path);
    String text = new String(bytes, StandardCharsets.UTF_8);
    String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
    requireEquals(List.of(columns), List.of(lines[0].split("\t", -1)), path + ": header");
    var result = new ArrayList<Map<String, String>>();
    for (int line = 1; line < lines.length; line++) {
      require(!lines[line].isEmpty(), path + ": blank row");
      String[] values = lines[line].split("\t", -1);
      requireEquals(columns.length, values.length, path + ": columns");
      var row = new LinkedHashMap<String, String>();
      for (int column = 0; column < columns.length; column++)
        row.put(columns[column], values[column]);
      result.add(Map.copyOf(row));
    }
    return List.copyOf(result);
  }

  private static List<String> ids(String prefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(number -> prefix + "%03d".formatted(number))
        .toList();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private static void requireEquals(Object expected, Object actual, String label) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(label + ": expected=" + expected + ", actual=" + actual);
    }
  }

  public record CatalogSpec(String id, String kind, String evidence, String scope) {}

  public record Utf8Vector(
      String id, String vector, String codePoints, String utf8Hex, String textJson) {
    Utf8Vector(Map<String, String> row) {
      this(
          row.get("case_id"),
          row.get("vector"),
          row.get("code_points"),
          row.get("utf8_hex"),
          row.get("text_json"));
    }
  }

  public record Utf8Failure(String id, String variant, String inputHex, String kind, int offset) {
    Utf8Failure(Map<String, String> row) {
      this(
          row.get("case_id"),
          row.get("variant"),
          row.get("input_hex"),
          row.get("kind"),
          Integer.parseInt(row.get("offset")));
    }
  }

  public record Base64Vector(String id, String vector, String inputHex, String encoded) {
    Base64Vector(Map<String, String> row) {
      this(row.get("case_id"), row.get("vector"), row.get("input_hex"), row.get("encoded"));
    }
  }

  public record Base64Failure(
      String id, String variant, String inputUtf8Hex, String kind, int position) {
    Base64Failure(Map<String, String> row) {
      this(
          row.get("case_id"),
          row.get("variant"),
          row.get("input_utf8_hex"),
          row.get("kind"),
          Integer.parseInt(row.get("position")));
    }
  }

  public record SliceSpec(Map<String, String> fields) {
    public SliceSpec {
      fields = Map.copyOf(fields);
    }
  }

  public record EqualitySpec(Map<String, String> fields) {
    public EqualitySpec {
      fields = Map.copyOf(fields);
    }
  }

  public record ResourceSpec(Map<String, String> fields) {
    public ResourceSpec {
      fields = Map.copyOf(fields);
    }

    public String id() {
      return fields.get("case_id");
    }

    public boolean statePreserved() {
      return fields.get("state_preserved").equals("true");
    }
  }

  public record DiagnosticSpec(Map<String, String> fields) {
    public DiagnosticSpec {
      fields = Map.copyOf(fields);
    }

    public String code() {
      return fields.get("code");
    }

    public String stage() {
      return fields.get("stage");
    }
  }

  public record StateSpec(Map<String, String> fields) {
    public StateSpec {
      fields = Map.copyOf(fields);
    }
  }

  public record TraceSpec(Map<String, String> fields) {
    public TraceSpec {
      fields = Map.copyOf(fields);
    }

    public String type() {
      return fields.get("type");
    }

    public String expectedTrace() {
      return fields.get("expected_trace");
    }
  }

  public record FileHash(String path, String sha256, int bytes) {}
}
