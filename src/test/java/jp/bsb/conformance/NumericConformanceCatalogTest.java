package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jp.bsb.conformance.NumericConformanceData.CaseSpec;
import org.junit.jupiter.api.Test;

/** N6、F6、R6と参照成果物を欠落・重複なく発見できることを固定します。 */
class NumericConformanceCatalogTest {
  @Test
  void discoversAllFortySixIdsAndEveryReferencedArtifact() throws IOException {
    ConformanceData.validateMessages();
    var catalog = NumericConformanceData.loadCases();
    var resources = NumericConformanceData.loadResources();
    List<String> caseIds = catalog.cases().stream().map(CaseSpec::id).toList();
    List<String> resourceIds =
        resources.stream().map(NumericConformanceData.ResourceSpec::id).distinct().toList();
    List<String> expectedCases = Stream.concat(ids("NUM-N", 20), ids("NUM-F", 20)).toList();
    List<String> expectedResources = ids("NUM-R", 6).toList();

    assertEquals(expectedCases, caseIds);
    assertEquals(expectedResources, resourceIds);
    var allIds = new LinkedHashSet<String>();
    allIds.addAll(caseIds);
    allIds.addAll(resourceIds);
    assertEquals(46, allIds.size());
    assertEquals(20, NumericConformanceData.loadDiagnostics().size());

    for (CaseSpec spec : catalog.cases()) {
      assertTrue(NumericConformanceData.generateCaseSource(spec).bytes().length > 0, spec.id());
      if (spec.traceSource() != null) {
        assertTrue(NumericConformanceData.resourceBytes(spec.traceSource()).length > 0, spec.id());
      }
    }
    assertEquals(
        List.of("NUM-N019", "NUM-N020"),
        catalog.cases().stream()
            .filter(spec -> spec.traceSource() != null)
            .map(CaseSpec::id)
            .toList());
  }

  private static Stream<String> ids(String prefix, int count) {
    return IntStream.rangeClosed(1, count).mapToObj(value -> "%s%03d".formatted(prefix, value));
  }
}
