package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bsb.json.JsonCodec;
import org.junit.jupiter.api.Test;

class ConnectionConformanceDataTest {
  private static final Path ROOT = Path.of("tests/conformance/logical-connections");

  @Test
  void loadsTheExactCatalogAndEveryIndependentTable() throws Exception {
    var catalog = ConnectionConformanceData.loadCatalog();
    assertEquals(32, catalog.size());
    assertEquals(10, catalog.stream().filter(item -> item.id().startsWith("CONN-N")).count());
    assertEquals(12, catalog.stream().filter(item -> item.id().startsWith("CONN-F")).count());
    assertEquals(10, catalog.stream().filter(item -> item.id().startsWith("CONN-R")).count());

    Set<String> catalogIds =
        catalog.stream().map(ConnectionConformanceData.CatalogSpec::id).collect(Collectors.toSet());
    assertTrue(
        ConnectionConformanceData.loadCases().stream()
            .allMatch(item -> catalogIds.contains(item.id())));
    assertEquals(12, ConnectionConformanceData.loadPolicies().size());
    assertEquals(8, ConnectionConformanceData.loadResolvers().size());
    assertEquals(35, ConnectionConformanceData.loadResources().size());
    assertEquals(21, ConnectionConformanceData.loadDiagnostics().size());
    assertEquals(6, ConnectionConformanceData.loadStates().size());
    assertEquals(6, ConnectionConformanceData.loadTraces().size());

    String expected = ConnectionConformanceData.expectedParameterizedCapabilities();
    assertEquals(expected.stripTrailing(), JsonCodec.serialize(JsonCodec.parse(expected)));
  }

  @Test
  void manifestMatchesTheExactPhysicalInventory() throws Exception {
    var manifest = ConnectionConformanceData.loadManifest();
    Set<String> expectedFiles =
        manifest.stream().map(ConnectionConformanceData.FileHash::path).collect(Collectors.toSet());
    expectedFiles.add("README.md");
    expectedFiles.add("manifest.tsv");
    Set<String> actualFiles;
    try (var paths = Files.walk(ROOT)) {
      actualFiles =
          paths
              .filter(Files::isRegularFile)
              .map(ROOT::relativize)
              .map(Path::toString)
              .collect(Collectors.toSet());
    }
    assertEquals(expectedFiles, actualFiles);
    for (var file : manifest) {
      byte[] bytes = Files.readAllBytes(ROOT.resolve(file.path()));
      assertEquals(file.bytes(), bytes.length, file.path());
      assertEquals(file.sha256(), ConnectionConformanceData.sha256(bytes), file.path());
    }
  }
}
