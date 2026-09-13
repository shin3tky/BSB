package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorkspaceTableConformanceDataTest {
  private static final Path ROOT = Path.of("tests/conformance/workspace-tables");

  @Test
  void loadsTheExactFeatureCatalogAndIndependentTables() throws Exception {
    var catalog = WorkspaceTableConformanceData.loadCatalog();
    assertEquals(70, catalog.size());
    assertEquals(22, catalog.stream().filter(item -> item.id().startsWith("WST-N")).count());
    assertEquals(30, catalog.stream().filter(item -> item.id().startsWith("WST-F")).count());
    assertEquals(18, catalog.stream().filter(item -> item.id().startsWith("WST-R")).count());
    assertEquals(40, catalog.stream().filter(item -> item.part().equals("files")).count());
    assertEquals(30, catalog.stream().filter(item -> item.part().equals("delimited")).count());
    assertEquals(14, WorkspaceTableConformanceData.loadLogicalNames().size());
    assertEquals(49, WorkspaceTableConformanceData.loadOperations().size());
    assertEquals(19, WorkspaceTableConformanceData.loadDiagnostics().size());
    assertEquals(22, WorkspaceTableConformanceData.loadCli().size());
    assertEquals(3, WorkspaceTableConformanceData.loadExplain().size());
    assertEquals(7, WorkspaceTableConformanceData.loadTraces().size());
    assertEquals(21, WorkspaceTableConformanceData.loadResources().size());
    assertEquals(29, WorkspaceTableConformanceData.loadDelimitedParses().size());
    assertEquals(12, WorkspaceTableConformanceData.loadDelimitedWrites().size());
    assertEquals(5, WorkspaceTableConformanceData.loadDelimitedDiagnostics().size());
    assertEquals(3, WorkspaceTableConformanceData.loadDelimitedTraces().size());
    assertEquals(17, WorkspaceTableConformanceData.loadDelimitedResources().size());
  }

  @Test
  void closedFailuresAndFixedBoundariesRemainIndependent() throws Exception {
    var operations = WorkspaceTableConformanceData.loadOperations();
    assertEquals(
        Set.of("notFound", "notRegularFile", "tooLarge", "ioFailure"),
        failureKinds(operations, "WST-N005"));
    assertEquals(
        Set.of(
            "parentNotFound",
            "targetNotRegularFile",
            "tooLarge",
            "atomicReplacementUnavailable",
            "ioFailure"),
        failureKinds(operations, "WST-N008"));
    var resources = WorkspaceTableConformanceData.loadResources();
    assertTrue(hasBoundary(resources, "fileOperations", "4096", "4097"));
    assertTrue(hasBoundary(resources, "fileReadBytes", "134217728", "134217729"));
    assertTrue(hasBoundary(resources, "fileWriteAttemptBytes", "134217728", "134217729"));
    assertTrue(hasBoundary(resources, "fileReadPolicy", "67108864", "67108865"));
    assertTrue(hasBoundary(resources, "dataStackItems", "65536", "65537"));
    var delimited = WorkspaceTableConformanceData.loadDelimitedResources();
    assertTrue(hasBoundary(delimited, "stringUtf8Bytes", "16777216", "16777217"));
    assertTrue(hasBoundary(delimited, "delimitedTextWorkUnits", "134217728", "134217729"));
    assertTrue(hasBoundary(delimited, "arrayDirectElements", "65536", "65537"));
    assertTrue(hasBoundary(delimited, "arrayNestedElements", "1000000", "1000001"));
    assertTrue(hasBoundary(delimited, "arrayConstructionUnits", "1000000", "1000001"));
    assertTrue(hasBoundary(delimited, "delimitedTextOutputUtf8Bytes", "16777216", "16777217"));
  }

  @Test
  void manifestMatchesTheExactPhysicalInventory() throws Exception {
    var manifest = WorkspaceTableConformanceData.loadManifest();
    Set<String> expectedFiles =
        manifest.stream()
            .map(WorkspaceTableConformanceData.FileHash::path)
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
      assertEquals(file.sha256(), WorkspaceTableConformanceData.sha256(bytes), file.path());
    }
  }

  private static Set<String> failureKinds(
      java.util.List<WorkspaceTableConformanceData.RowSpec> rows, String caseId) {
    return rows.stream()
        .filter(row -> row.value("case_id").equals(caseId))
        .map(row -> row.value("failure_kind"))
        .collect(Collectors.toSet());
  }

  private static boolean hasBoundary(
      java.util.List<WorkspaceTableConformanceData.RowSpec> rows,
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
