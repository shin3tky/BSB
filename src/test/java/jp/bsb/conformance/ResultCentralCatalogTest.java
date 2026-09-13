package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ResultCentralCatalogTest {
  private static final Path ROOT = Path.of("tests/conformance/result-values");

  @Test
  void strictCatalogCoversAllSeventyIdsAndEveryFixedAsset() throws Exception {
    var catalog = ResultConformanceData.loadCatalog();
    assertEquals(70, catalog.size());
    assertEquals(20, catalog.stream().filter(item -> item.id().startsWith("RESULT-N")).count());
    assertEquals(36, catalog.stream().filter(item -> item.id().startsWith("RESULT-F")).count());
    assertEquals(14, catalog.stream().filter(item -> item.id().startsWith("RESULT-R")).count());
    assertEquals(1, catalog.stream().filter(item -> item.kind().equals("warning")).count());
    assertEquals(
        "RESULT-F032",
        catalog.stream()
            .filter(item -> item.kind().equals("warning"))
            .findFirst()
            .orElseThrow()
            .id());

    var hashes = ResultConformanceData.loadFileHashes();
    assertEquals(34, hashes.size());
    for (var entry : hashes.entrySet()) {
      byte[] bytes = Files.readAllBytes(ROOT.resolve(entry.getKey()));
      assertEquals(entry.getValue().bytes(), bytes.length, entry.getKey());
      assertEquals(entry.getValue().sha256(), ResultConformanceData.sha256(bytes), entry.getKey());
    }
  }

  @Test
  void publicBoundaryHasExactlyTwentyFourSourcesAndFiveCommandsEach() throws Exception {
    var catalog = ResultConformanceData.loadCatalog();
    Set<String> sources =
        catalog.stream()
            .filter(item -> item.commands().equals("public"))
            .map(ResultConformanceData.CatalogSpec::evidence)
            .collect(Collectors.toSet());
    assertEquals(24, sources.size());
    var commands = ResultConformanceData.loadCommandHashes();
    assertEquals(120, commands.size());
    assertEquals(
        sources,
        commands.stream()
            .map(ResultConformanceData.CommandSpec::source)
            .collect(Collectors.toSet()));
    assertTrue(
        catalog.stream()
            .filter(item -> item.commands().equals("internal"))
            .allMatch(item -> item.id().startsWith("RESULT-R")));
  }
}
