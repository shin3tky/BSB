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

class HttpReliabilityConformanceDataTest {
  @Test
  void manifestFixesEveryIndependentResource() throws IOException, NoSuchAlgorithmException {
    List<String> lines = resource("manifest.tsv");
    assertEquals("path\tsha256\tbytes", lines.getFirst());
    assertEquals(
        List.of("README.md", "catalog.tsv", "diagnostics.tsv", "timing.tsv"),
        lines.stream().skip(1).map(line -> line.split("\\t", -1)[0]).toList());
    for (String line : lines.subList(1, lines.size())) {
      String[] fields = line.split("\\t", -1);
      byte[] payload = resourceBytes(fields[0]);
      assertEquals(Long.parseLong(fields[2]), payload.length, fields[0]);
      assertEquals(
          fields[1],
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)),
          fields[0]);
    }
  }

  @Test
  void catalogContainsAllThirtyOneIdsInNormativeOrder() throws IOException {
    List<String> lines = resource("catalog.tsv");
    assertEquals("id\tfamily\tevidence", lines.getFirst());
    var actual = lines.stream().skip(1).map(line -> line.split("\\t", -1)[0]).toList();
    var expected = new ArrayList<String>();
    for (int i = 1; i <= 12; i++) expected.add("HTTPREL-N%03d".formatted(i));
    for (int i = 1; i <= 11; i++) expected.add("HTTPREL-F%03d".formatted(i));
    for (int i = 1; i <= 8; i++) expected.add("HTTPREL-R%03d".formatted(i));
    assertEquals(expected, actual);
  }

  @Test
  void timingRowsMatchIndependentIntegerOracle() throws IOException {
    List<String> lines = resource("timing.tsv");
    for (String line : lines.subList(1, lines.size())) {
      String[] fields = line.split("\\t", -1);
      long delay = Long.parseLong(fields[1]);
      long maximum = Long.parseLong(fields[2]);
      int multiplier = Integer.parseInt(fields[3]);
      int attempt = Integer.parseInt(fields[4]);
      for (int index = 2; index < attempt; index++) delay = Math.min(maximum, delay * multiplier);
      long observed =
          Math.max(delay, Math.max(Long.parseLong(fields[5]), Long.parseLong(fields[6])));
      assertEquals(Long.parseLong(fields[7]), observed, fields[0]);
    }
  }

  private static List<String> resource(String name) throws IOException {
    byte[] payload = resourceBytes(name);
    String path = "/conformance/http-reliability/" + name;
    String text = new String(payload, StandardCharsets.UTF_8);
    if (text.startsWith("\ufeff") || text.contains("\r") || !text.endsWith("\n"))
      throw new IOException("invalid text envelope " + path);
    return text.lines().toList();
  }

  private static byte[] resourceBytes(String name) throws IOException {
    String path = "/conformance/http-reliability/" + name;
    try (var input = HttpReliabilityConformanceDataTest.class.getResourceAsStream(path)) {
      if (input == null) throw new IOException("missing " + path);
      return input.readAllBytes();
    }
  }
}
