package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ByteSequenceConformanceDataTest {
  private static final Path ROOT = Path.of("tests/conformance/byte-sequences");

  @Test
  void loadsTheExactCatalogAndEveryIndependentTable() throws Exception {
    var catalog = ByteSequenceConformanceData.loadCatalog();
    assertEquals(34, catalog.size());
    assertEquals(12, catalog.stream().filter(item -> item.id().startsWith("BYTES-N")).count());
    assertEquals(12, catalog.stream().filter(item -> item.id().startsWith("BYTES-F")).count());
    assertEquals(10, catalog.stream().filter(item -> item.id().startsWith("BYTES-R")).count());
    assertEquals(19, ByteSequenceConformanceData.loadUtf8Vectors().size());
    assertEquals(12, ByteSequenceConformanceData.loadUtf8Failures().size());
    assertEquals(9, ByteSequenceConformanceData.loadBase64Vectors().size());
    assertEquals(12, ByteSequenceConformanceData.loadBase64Failures().size());
    assertEquals(7, ByteSequenceConformanceData.loadSlices().size());
    assertEquals(7, ByteSequenceConformanceData.loadEquality().size());
    assertEquals(20, ByteSequenceConformanceData.loadResources().size());
    assertEquals(4, ByteSequenceConformanceData.loadDiagnostics().size());
    assertEquals(7, ByteSequenceConformanceData.loadStates().size());
    assertEquals(6, ByteSequenceConformanceData.loadTraces().size());
  }

  @Test
  void fixedBoundaryRowsRemainIndependentOfProductionConstants() throws Exception {
    var resources = ByteSequenceConformanceData.loadResources();
    assertTrue(hasBoundary(resources, "byteSequenceBytes", "67108864", "67108865"));
    assertTrue(hasBoundary(resources, "byteSequenceConstructionBytes", "134217728", "134217729"));
    assertTrue(hasBoundary(resources, "byteSequenceWorkBytes", "268435456", "268435457"));
    assertTrue(hasBoundary(resources, "stringUtf8Bytes", "16777216", "16777220"));
  }

  @Test
  void manifestMatchesTheExactPhysicalInventory() throws Exception {
    var manifest = ByteSequenceConformanceData.loadManifest();
    Set<String> expectedFiles =
        manifest.stream()
            .map(ByteSequenceConformanceData.FileHash::path)
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
      assertEquals(file.sha256(), ByteSequenceConformanceData.sha256(bytes), file.path());
    }
  }

  private static boolean hasBoundary(
      java.util.List<ByteSequenceConformanceData.ResourceSpec> rows,
      String metric,
      String limit,
      String observed) {
    return rows.stream()
        .anyMatch(
            row ->
                row.fields().get("metric").equals(metric)
                    && row.fields().get("limit").equals(limit)
                    && row.fields().get("observed").equals(observed));
  }
}
