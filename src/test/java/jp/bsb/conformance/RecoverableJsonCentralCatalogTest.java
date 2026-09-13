package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecoverableJsonCentralCatalogTest {
  private static final Path ROOT = Path.of("tests/conformance/recoverable-json");

  @Test
  void consumesAllThirtyTwoIdsAndEveryDetailedExpectation() throws Exception {
    var catalog = RecoverableJsonConformanceData.loadCatalog();
    assertEquals(32, catalog.size());
    assertEquals(10, catalog.stream().filter(item -> item.id().startsWith("RJSON-N")).count());
    assertEquals(10, catalog.stream().filter(item -> item.id().startsWith("RJSON-F")).count());
    assertEquals(12, catalog.stream().filter(item -> item.id().startsWith("RJSON-R")).count());

    Set<String> caseIds =
        RecoverableJsonConformanceData.loadCases().stream()
            .map(RecoverableJsonConformanceData.CaseSpec::id)
            .collect(Collectors.toSet());
    Set<String> diagnosticIds =
        RecoverableJsonConformanceData.loadDiagnostics().stream()
            .map(RecoverableJsonConformanceData.DiagnosticSpec::id)
            .collect(Collectors.toSet());
    Set<String> resourceIds =
        RecoverableJsonConformanceData.loadResources().stream()
            .map(RecoverableJsonConformanceData.ResourceSpec::id)
            .collect(Collectors.toSet());
    assertTrue(caseIds.containsAll(Set.of("RJSON-N001", "RJSON-N002", "RJSON-N003", "RJSON-F009")));
    assertEquals(
        Set.of(
            "RJSON-F001",
            "RJSON-F002",
            "RJSON-F003",
            "RJSON-F004",
            "RJSON-F005",
            "RJSON-F006",
            "RJSON-F007",
            "RJSON-F008",
            "RJSON-F009",
            "RJSON-F010"),
        diagnosticIds);
    assertEquals(
        java.util.stream.IntStream.rangeClosed(1, 12)
            .mapToObj(number -> "RJSON-R%03d".formatted(number))
            .collect(Collectors.toSet()),
        resourceIds);
    assertEquals(6, RecoverableJsonConformanceData.loadStates().size());
  }

  @Test
  void manifestMatchesEveryListedPhysicalAsset() throws Exception {
    var manifest = RecoverableJsonConformanceData.loadManifest();
    for (var file : manifest) {
      byte[] bytes = Files.readAllBytes(ROOT.resolve(file.path()));
      assertEquals(file.bytes(), bytes.length, file.path());
      assertEquals(file.sha256(), RecoverableJsonConformanceData.sha256(bytes), file.path());
    }
  }
}
