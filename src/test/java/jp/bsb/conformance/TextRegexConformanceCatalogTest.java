package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jp.bsb.conformance.TextRegexConformanceData.CaseSpec;
import jp.bsb.conformance.TextRegexConformanceData.CheckedProperties;
import org.junit.jupiter.api.Test;

/** N7、F7、R7と全参照・生成成果物を欠落・重複・未使用なく発見できることを固定します。 */
class TextRegexConformanceCatalogTest {
  @Test
  void discoversAllSixtyFourIdsAndEveryReferencedArtifact() throws IOException {
    ConformanceData.validateMessages();
    var catalog = TextRegexConformanceData.loadCases();
    var diagnostics = TextRegexConformanceData.loadDiagnostics();
    var resources = TextRegexConformanceData.loadResources();
    List<String> caseIds = catalog.cases().stream().map(CaseSpec::id).toList();
    List<String> resourceIds =
        resources.stream().map(TextRegexConformanceData.ResourceSpec::id).distinct().toList();
    List<String> expectedCases = Stream.concat(ids("TEXT-N", 24), ids("TEXT-F", 30)).toList();
    List<String> expectedResources = ids("TEXT-R", 10).toList();

    assertEquals(expectedCases, caseIds);
    assertEquals(expectedResources, resourceIds);
    var allIds = new LinkedHashSet<String>();
    allIds.addAll(caseIds);
    allIds.addAll(resourceIds);
    assertEquals(64, allIds.size());
    assertEquals(30, diagnostics.size());
    assertEquals(25, resources.size());

    for (CaseSpec spec : catalog.cases()) {
      byte[] first = TextRegexConformanceData.generateCaseSource(spec).bytes();
      byte[] second = TextRegexConformanceData.generateCaseSource(spec).bytes();
      assertTrue(first.length > 0, spec.id());
      assertArrayEquals(first, second, spec.id() + ": deterministic source");
      if (spec.traceSource() != null) {
        assertTrue(
            TextRegexConformanceData.resourceBytes(spec.traceSource()).length > 0, spec.id());
      }
      for (var expectation : spec.expectations().values()) {
        if (expectation.stdoutSource() != null) {
          assertTrue(
              TextRegexConformanceData.resourceBytes(expectation.stdoutSource()).length > 0,
              spec.id());
        }
      }
    }
    assertEquals(
        List.of("TEXT-N022", "TEXT-N024"),
        catalog.cases().stream()
            .filter(spec -> spec.traceSource() != null)
            .map(CaseSpec::id)
            .toList());
  }

  @Test
  void consumesEveryKeyFromAllTenGeneratedDefinitions() throws IOException {
    Map<String, List<String>> keys =
        Map.ofEntries(
            Map.entry(
                "TEXT-R001", List.of("generator", "variants", "encoding", "preflight.required")),
            Map.entry(
                "TEXT-R002", List.of("generator", "variants", "match.count", "precommit.required")),
            Map.entry(
                "TEXT-R003", List.of("generator", "variants", "preserve.empty", "array.limit")),
            Map.entry(
                "TEXT-R004",
                List.of("generator", "variants", "encoding", "compile.afterLimitCheck")),
            Map.entry("TEXT-R005", List.of("generator", "variants", "unicode.version", "engine")),
            Map.entry("TEXT-R006", List.of("generator", "variants", "names", "limit")),
            Map.entry(
                "TEXT-R007",
                List.of(
                    "generator",
                    "variants",
                    "accepted.programInstructions",
                    "accepted.inputCodePoints",
                    "rejected.programInstructions",
                    "rejected.inputCodePoints",
                    "formula")),
            Map.entry(
                "TEXT-R008",
                List.of(
                    "generator",
                    "variants",
                    "accepted.before",
                    "accepted.add",
                    "rejected.before",
                    "rejected.add")),
            Map.entry(
                "TEXT-R009",
                List.of(
                    "generator",
                    "scalar.codePointVariants",
                    "array.elementVariants",
                    "arrayElement.codePointVariants",
                    "redacted.scalar",
                    "redacted.regex",
                    "redacted.array")),
            Map.entry(
                "TEXT-R010", List.of("generator", "variants", "value.types", "mismatch.outcome")));

    assertEquals(ids("TEXT-R", 10).toList(), keys.keySet().stream().sorted().toList());
    for (String id : ids("TEXT-R", 10).toList()) {
      CheckedProperties properties = TextRegexConformanceData.loadGeneratedResource(id);
      keys.get(id).forEach(properties::require);
      properties.assertFullyConsumed();
    }
  }

  private static Stream<String> ids(String prefix, int count) {
    return IntStream.rangeClosed(1, count).mapToObj(value -> "%s%03d".formatted(prefix, value));
  }
}
