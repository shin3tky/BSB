package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptionalConformanceCatalogTest {
  @Test
  void consumesAllFiftyTwoIdsAndEveryPhysicalArtifactWithoutSurplus() throws Exception {
    var cases = OptionalConformanceData.loadCases();
    var diagnostics = OptionalConformanceData.loadDiagnostics();
    var resources = OptionalConformanceData.loadResources();
    var hashes = OptionalConformanceData.loadGeneratedHashes();

    assertEquals(42, cases.size());
    assertEquals(29, diagnostics.size());
    assertEquals(20, resources.size());
    assertEquals(9, hashes.size());

    var expected = new LinkedHashSet<String>();
    expected.addAll(
        Set.of(
            "cases.tsv",
            "diagnostics.tsv",
            "resources.tsv",
            "messages.properties",
            "generated/resources.tsv",
            "chapter/optional-values-chapter.bsb",
            "chapter/optional-values-chapter.stdout",
            "chapter/optional-values-chapter.trace.tsv",
            "canonical/optional-values-chapter.bsb",
            "explain/OPT-N015.json",
            "explain/OPT-N016.json"));
    for (var spec : cases) {
      for (String path : java.util.List.of(spec.source(), spec.canonical(), spec.stdout())) {
        if (!path.equals("-")) {
          expected.add(path);
        }
      }
    }

    Path root = Path.of("tests/conformance/optional-values");
    var present = new LinkedHashSet<String>();
    try (var paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .map(root::relativize)
          .map(Path::toString)
          .forEach(present::add);
    }
    assertEquals(expected, present);

    for (String path : present) {
      byte[] bytes = Files.readAllBytes(root.resolve(path));
      assertFalse(
          bytes.length >= 3
              && bytes[0] == (byte) 0xef
              && bytes[1] == (byte) 0xbb
              && bytes[2] == (byte) 0xbf,
          path + ": BOM");
      assertTrue(bytes.length > 0 && bytes[bytes.length - 1] == '\n', path + ": final LF");
      assertFalse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains("\r"), path);
    }
  }

  @Test
  void everyGeneratedBoundaryMatchesItsIndependentHash() throws Exception {
    for (var entry : OptionalConformanceData.loadGeneratedHashes().entrySet()) {
      assertEquals(
          entry.getValue(),
          OptionalConformanceData.sha256(OptionalConformanceData.generatedInput(entry.getKey())),
          entry.getKey());
    }
  }
}
