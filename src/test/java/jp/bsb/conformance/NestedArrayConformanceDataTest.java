package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NestedArrayConformanceDataTest {
  private static final Path ROOT = Path.of("tests/conformance/nested-arrays");

  @Test
  void loadsTheExactCatalogAndEveryIndependentTable() throws Exception {
    var catalog = NestedArrayConformanceData.loadCatalog();
    assertEquals(34, catalog.size());
    assertEquals(12, catalog.stream().filter(item -> item.id().startsWith("NARRAY-N")).count());
    assertEquals(12, catalog.stream().filter(item -> item.id().startsWith("NARRAY-F")).count());
    assertEquals(10, catalog.stream().filter(item -> item.id().startsWith("NARRAY-R")).count());
    assertEquals(15, NestedArrayConformanceData.loadStates().size());
    assertEquals(12, NestedArrayConformanceData.loadDiagnostics().size());
    assertEquals(6, NestedArrayConformanceData.loadTraces().size());
    assertEquals(17, NestedArrayConformanceData.loadResources().size());
  }

  @Test
  void fixedBoundariesAndClosedFailureSetRemainIndependent() throws Exception {
    var resources = NestedArrayConformanceData.loadResources();
    assertTrue(hasBoundary(resources, "arrayDirectElements", "65536", "65537"));
    assertTrue(hasBoundary(resources, "arrayNestedLeafElements", "1000000", "1000001"));
    assertTrue(hasBoundary(resources, "arrayConstructionUnits", "1000000", "1000001"));
    assertTrue(hasBoundary(resources, "arrayElementOperationUnits", "10000000", "10000001"));
    assertEquals(
        Set.of(
            "E_NESTED_ARRAY_NOT_AVAILABLE",
            "E_ARRAY_ELEMENT_TYPE_MISMATCH",
            "E_TYPE_MISMATCH",
            "E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED",
            "E_EMPTY_ARRAY_TYPE_REQUIRED",
            "E_EXPECTED_ARRAY_TYPE_END",
            "INTERNAL"),
        NestedArrayConformanceData.loadDiagnostics().stream()
            .map(row -> row.value("code"))
            .collect(Collectors.toSet()));
  }

  @Test
  void manifestMatchesTheExactPhysicalInventory() throws Exception {
    var manifest = NestedArrayConformanceData.loadManifest();
    Set<String> expectedFiles =
        manifest.stream()
            .map(NestedArrayConformanceData.FileHash::path)
            .collect(Collectors.toSet());
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
      assertEquals(file.sha256(), NestedArrayConformanceData.sha256(bytes), file.path());
    }
  }

  private static boolean hasBoundary(
      java.util.List<NestedArrayConformanceData.RowSpec> rows,
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
