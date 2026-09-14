package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonShapeConformanceDataTest {
  @Test
  void manifestFixesEveryIndependentResource() throws IOException, NoSuchAlgorithmException {
    List<String> lines = resource("manifest.tsv");
    assertEquals("path\tsha256\tbytes", lines.getFirst());
    assertEquals(
        List.of(
            "README.md",
            "catalog.tsv",
            "diagnostics.tsv",
            "messages.properties",
            "resources.tsv",
            "vectors.tsv",
            "chapter/json-shapes-chapter.bsb",
            "chapter/json-shapes-chapter.stdout"),
        lines.stream().skip(1).map(line -> line.split("\t", -1)[0]).toList());
    for (String line : lines.subList(1, lines.size())) {
      String[] fields = line.split("\t", -1);
      byte[] payload = resourceBytes(fields[0]);
      assertEquals(Long.parseLong(fields[2]), payload.length, fields[0]);
      assertEquals(
          fields[1],
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)),
          fields[0]);
    }
  }

  @Test
  void catalogContainsAllFortyIdsInNormativeOrder() throws IOException {
    List<String> lines = resource("catalog.tsv");
    assertEquals("id\tfamily\tevidence", lines.getFirst());
    List<String> actual = lines.stream().skip(1).map(line -> line.split("\t", -1)[0]).toList();
    var expected = new ArrayList<String>();
    for (int index = 1; index <= 16; index++) expected.add("JSHAPE-N%03d".formatted(index));
    for (int index = 1; index <= 12; index++) expected.add("JSHAPE-F%03d".formatted(index));
    for (int index = 1; index <= 12; index++) expected.add("JSHAPE-R%03d".formatted(index));
    assertEquals(expected, actual);
  }

  @Test
  void diagnosticsAndResourcesKeepClosedFiniteTables() throws IOException {
    List<String> diagnostics = resource("diagnostics.tsv");
    assertEquals(6, diagnostics.size());
    assertEquals(
        List.of(
            "E_JSON_SHAPE_OBJECT_REQUIRED",
            "E_JSON_SHAPE_DEPTH_LIMIT",
            "E_JSON_SHAPE_NODE_LIMIT",
            "E_JSON_SHAPE_WORK_LIMIT",
            "E_JSON_SHAPE_PATH_LIMIT"),
        diagnostics.stream().skip(1).map(line -> line.split("\t", -1)[0]).toList());
    assertEquals(6, resource("resources.tsv").size());
    assertEquals(9, resource("vectors.tsv").size());
  }

  private static List<String> resource(String name) throws IOException {
    byte[] payload = resourceBytes(name);
    String path = "/conformance/json-shapes/" + name;
    String text = new String(payload, StandardCharsets.UTF_8);
    if (text.startsWith("\ufeff") || text.contains("\r") || !text.endsWith("\n")) {
      throw new IOException("invalid text envelope " + path);
    }
    return text.lines().toList();
  }

  private static byte[] resourceBytes(String name) throws IOException {
    String path = "/conformance/json-shapes/" + name;
    try (var input = JsonShapeConformanceDataTest.class.getResourceAsStream(path)) {
      if (input == null) throw new IOException("missing " + path);
      return input.readAllBytes();
    }
  }
}
