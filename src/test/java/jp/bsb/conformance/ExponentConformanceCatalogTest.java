package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExponentConformanceCatalogTest {
  @Test
  void consumesAllThirtyTwoIdsAndEveryArtifactWithoutSurplus() throws Exception {
    var catalog = ExponentConformanceData.loadCatalog();

    assertEquals(24, catalog.cases().size());
    assertEquals(8, catalog.resourceIds().size());
    assertEquals(18, catalog.artifactPaths().size());
    assertEquals(25, ExponentConformanceData.loadLiterals().size());
    assertEquals(12, ExponentConformanceData.loadDiagnostics().size());
    assertEquals(16, ExponentConformanceData.loadResources().size());

    Path root = Path.of("tests/conformance/exponent-literals");
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
  void materializesEveryGeneratedLiteralWithItsIndependentHashAndDigitCount() throws Exception {
    var generator = ExponentConformanceData.loadGenerator();
    assertEquals(15, generator.literals().size());

    for (var entry : generator.literals().entrySet()) {
      String literal = entry.getValue().literal();
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(literal.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      assertEquals(entry.getValue().sha256(), hash, entry.getKey());
      long digits =
          literal.chars().filter(character -> character >= '0' && character <= '9').count();
      if (entry.getKey().startsWith("EXP-R001.exact")) {
        assertEquals(4096, digits, entry.getKey());
      } else if (entry.getKey().startsWith("EXP-R001.over")) {
        assertEquals(4097, digits, entry.getKey());
      } else if (entry.getKey().startsWith("EXP-R002.exact")) {
        assertEquals(4096, digits, entry.getKey());
      } else if (entry.getKey().startsWith("EXP-R002.over")) {
        assertEquals(4097, digits, entry.getKey());
      } else if (entry.getKey().equals("EXP-R007.literal")) {
        assertEquals(4096, digits, entry.getKey());
      }
    }
    assertEquals(65537, generator.expectedObserved());
    assertEquals(65539, generator.expectedCodePoints());
  }
}
