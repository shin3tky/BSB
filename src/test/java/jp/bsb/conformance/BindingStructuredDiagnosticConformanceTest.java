package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.conformance.BindingConformanceData.CaseSpec;
import jp.bsb.conformance.BindingConformanceData.DiagnosticSpec;
import jp.bsb.diagnostics.Diagnostic;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** 表示文字列の手前にある、機械可読な束縛診断の全フィールドを検証します。 */
class BindingStructuredDiagnosticConformanceTest {
  @TestFactory
  List<DynamicTest> everyFailureAndWarningHasTheSpecifiedStructuredDiagnostics()
      throws IOException {
    Map<String, List<DiagnosticSpec>> diagnostics = BindingConformanceData.loadDiagnostics();
    Map<String, CaseSpec> cases =
        BindingConformanceData.loadCases().cases().stream()
            .collect(java.util.stream.Collectors.toMap(CaseSpec::id, spec -> spec));
    var tests = new ArrayList<DynamicTest>();
    diagnostics.forEach(
        (id, expected) -> {
          String label = id + "/check/structured";
          tests.add(DynamicTest.dynamicTest(label, () -> compare(label, cases.get(id), expected)));
        });
    return List.copyOf(tests);
  }

  private static void compare(String label, CaseSpec caseSpec, List<DiagnosticSpec> expected)
      throws IOException {
    byte[] source = BindingConformanceData.generateCaseSource(caseSpec).bytes();
    String sourcePath = caseSpec.id() + ".bsb";
    List<Diagnostic> actual = new SourceChecker().check(sourcePath, source).diagnostics();

    assertEquals(expected.size(), actual.size(), label + ": expected/actual diagnostic count");
    for (int index = 0; index < expected.size(); index++) {
      BindingDiagnosticSupport.assertStructuredDiagnostic(
          label + '/' + (index + 1), expected.get(index), actual.get(index), sourcePath);
    }
  }
}
