package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonConformanceCatalogTest {
  @Test
  void consumesEveryCaseDiagnosticResourceGeneratorAndArtifactExactly() throws Exception {
    var catalog = JsonConformanceData.loadCases();

    assertEquals(58, catalog.cases().size());
    assertEquals(30, catalog.cases().stream().filter(JsonConformanceData.CaseSpec::normal).count());
    assertEquals(28, catalog.cases().stream().filter(spec -> !spec.normal()).count());
    assertEquals(9, JsonConformanceData.loadNormalCorpus().size());
    assertEquals(32, JsonConformanceData.loadFailureCorpus().size());
    assertEquals(28, JsonConformanceData.loadDiagnostics().size());
    assertEquals(36, JsonConformanceData.loadResources().size());
    assertEquals(18, JsonConformanceData.loadGenerator().ids().size());
    assertEquals(512, JsonConformanceData.loadGenerator().heapMiB());

    Path root = Path.of("tests/conformance/json");
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
    expected.add("messages.properties");
    assertEquals(expected, present);
  }
}
