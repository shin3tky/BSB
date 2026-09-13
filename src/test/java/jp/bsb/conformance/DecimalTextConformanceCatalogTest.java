package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DecimalTextConformanceCatalogTest {
  @Test
  void consumesAllTwentyEightIdsAndEveryArtifactWithoutSurplus() throws Exception {
    var catalog = DecimalTextConformanceData.loadCatalog();

    assertEquals(20, catalog.caseIds().size());
    assertEquals(8, catalog.resourceIds().size());
    assertEquals(8, catalog.normalIds().size());
    assertEquals(12, catalog.failureIds().size());
    assertEquals(21, DecimalTextConformanceData.loadDecimalTexts().size());
    assertEquals(12, DecimalTextConformanceData.loadDiagnostics().size());
    assertEquals(16, DecimalTextConformanceData.loadResources().size());
    assertEquals(15, DecimalTextConformanceData.loadGeneratedLiterals().size());

    Path root = Path.of("tests/conformance/decimal-text");
    Set<String> present = new LinkedHashSet<>();
    try (var paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .map(root::relativize)
          .map(Path::toString)
          .forEach(present::add);
    }
    var expected = new LinkedHashSet<>(catalog.artifactPaths());
    expected.add("cases.properties");
    assertEquals(expected, present);
  }

  @Test
  void materializesEveryGeneratedInputWithItsIndependentHashAndDigitCount() throws Exception {
    for (var entry : DecimalTextConformanceData.loadGeneratedLiterals().entrySet()) {
      String literal = entry.getValue().literal();
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(literal.getBytes(StandardCharsets.UTF_8)));
      assertEquals(entry.getValue().sha256(), hash, entry.getKey());
      long digits =
          literal.chars().filter(character -> character >= '0' && character <= '9').count();
      if (entry.getKey().startsWith("DTXT-R001.exact")
          || entry.getKey().startsWith("DTXT-R002.exact")
          || entry.getKey().equals("DTXT-R007.literal")) {
        assertEquals(4_096, digits, entry.getKey());
      } else if (entry.getKey().startsWith("DTXT-R001.over")
          || entry.getKey().startsWith("DTXT-R002.over")) {
        assertEquals(4_097, digits, entry.getKey());
      }
    }
  }
}
