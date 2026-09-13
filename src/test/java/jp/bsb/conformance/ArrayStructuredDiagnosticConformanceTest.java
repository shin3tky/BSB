package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.conformance.ArrayConformanceData.CaseSpec;
import jp.bsb.conformance.ArrayConformanceData.DiagnosticSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** 表示文字列の手前にある、機械可読な配列診断の全フィールドを検証します。 */
class ArrayStructuredDiagnosticConformanceTest {
  @TestFactory
  List<DynamicTest> everyFailureAndWarningHasTheSpecifiedStructuredDiagnostic() throws IOException {
    Map<String, DiagnosticSpec> diagnostics = ArrayConformanceData.loadDiagnostics();
    Map<String, CaseSpec> cases =
        ArrayConformanceData.loadCases().cases().stream()
            .collect(java.util.stream.Collectors.toMap(CaseSpec::id, spec -> spec));
    var tests = new ArrayList<DynamicTest>();
    diagnostics.forEach(
        (id, spec) -> {
          String label = id + '/' + spec.command() + "/structured";
          tests.add(DynamicTest.dynamicTest(label, () -> compare(label, cases.get(id), spec)));
        });
    return List.copyOf(tests);
  }

  private static void compare(String label, CaseSpec caseSpec, DiagnosticSpec spec)
      throws IOException {
    byte[] source = ArrayConformanceData.generateCaseSource(caseSpec).bytes();
    String sourcePath = spec.id() + ".bsb";
    List<Diagnostic> actual;
    if (spec.command().equals("run")) {
      actual =
          new ProgramRunner()
              .run(
                  sourcePath,
                  source,
                  new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()))
              .diagnostics();
    } else {
      actual = new SourceChecker().check(sourcePath, source).diagnostics();
    }

    assertEquals(1, actual.size(), label + ": diagnostic count");
    ArrayDiagnosticSupport.assertStructuredDiagnostic(label, spec, actual.getFirst(), sourcePath);
  }
}
