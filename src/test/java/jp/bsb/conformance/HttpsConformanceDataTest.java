package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HttpsConformanceDataTest {
  private static final Path ROOT = Path.of("tests/conformance/https");

  @Test
  void loadsTheExactCatalogAndEveryIndependentTable() throws Exception {
    var catalog = HttpsConformanceData.loadCatalog();
    assertEquals(36, catalog.size());
    assertEquals(12, catalog.stream().filter(item -> item.id().startsWith("HTTPS-N")).count());
    assertEquals(14, catalog.stream().filter(item -> item.id().startsWith("HTTPS-F")).count());
    assertEquals(10, catalog.stream().filter(item -> item.id().startsWith("HTTPS-R")).count());
    assertEquals(9, HttpsConformanceData.loadUriVectors().size());
    assertEquals(16, HttpsConformanceData.loadHeaderVectors().size());
    assertEquals(14, HttpsConformanceData.loadRequests().size());
    assertEquals(9, HttpsConformanceData.loadResponses().size());
    assertEquals(35, HttpsConformanceData.loadTransports().size());
    assertEquals(33, HttpsConformanceData.loadResources().size());
    assertEquals(26, HttpsConformanceData.loadDiagnostics().size());
    assertEquals(6, HttpsConformanceData.loadTraces().size());
  }

  @Test
  void fixedBoundariesRemainIndependentOfProductionConstants() throws Exception {
    var resources = HttpsConformanceData.loadResources();
    assertTrue(hasBoundary(resources, "httpPathBytes", "8192", "8193"));
    assertTrue(hasBoundary(resources, "httpQueryItems", "256", "257"));
    assertTrue(hasBoundary(resources, "httpTargetUriBytes", "65536", "65537"));
    assertTrue(hasBoundary(resources, "httpSendCalls", "1024", "1025"));
    assertTrue(hasBoundary(resources, "httpResponseReceivedBytes", "134217728", "134217729"));
  }

  @Test
  void manifestMatchesTheExactPhysicalInventory() throws Exception {
    var manifest = HttpsConformanceData.loadManifest();
    Set<String> expectedFiles =
        manifest.stream().map(HttpsConformanceData.FileHash::path).collect(Collectors.toSet());
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
      assertEquals(file.sha256(), HttpsConformanceData.sha256(bytes), file.path());
    }
  }

  private static boolean hasBoundary(
      java.util.List<HttpsConformanceData.RowSpec> rows,
      String metric,
      String limit,
      String observed) {
    return rows.stream()
        .anyMatch(
            row ->
                row.value("metric").equals(metric)
                    && row.value("limit").equals(limit)
                    && row.value("observed").equals(observed));
  }
}
