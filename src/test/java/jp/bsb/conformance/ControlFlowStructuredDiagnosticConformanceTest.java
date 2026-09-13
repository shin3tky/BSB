package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.conformance.ControlFlowConformanceData.CaseSpec;
import jp.bsb.conformance.ControlFlowConformanceData.DiagnosticSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** 表示文字列の手前にある、機械可読な制御フロー診断の全フィールドを検証します。 */
class ControlFlowStructuredDiagnosticConformanceTest {
  @TestFactory
  List<DynamicTest> everyFailureHasTheSpecifiedStructuredDiagnostic() throws IOException {
    Map<String, DiagnosticSpec> diagnostics = ControlFlowConformanceData.loadDiagnostics();
    Map<String, CaseSpec> cases =
        ControlFlowConformanceData.loadCases().cases().stream()
            .collect(java.util.stream.Collectors.toMap(CaseSpec::id, spec -> spec));
    var tests = new ArrayList<DynamicTest>();
    for (DiagnosticSpec spec : diagnostics.values()) {
      String label = spec.id() + '/' + spec.command() + "/structured";
      tests.add(DynamicTest.dynamicTest(label, () -> compare(label, cases.get(spec.id()), spec)));
    }
    return List.copyOf(tests);
  }

  private static void compare(String label, CaseSpec caseSpec, DiagnosticSpec spec)
      throws IOException {
    byte[] source = ControlFlowConformanceData.generateCaseSource(caseSpec).bytes();
    String sourcePath = spec.id() + ".bsb";
    List<Diagnostic> actual;
    if (spec.command().equals("run")) {
      var result =
          new ProgramRunner()
              .run(
                  sourcePath,
                  source,
                  new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));
      actual = result.diagnostics();
    } else {
      actual = new SourceChecker().check(sourcePath, source).diagnostics();
    }

    assertEquals(1, actual.size(), label + ": expected/actual diagnostic count");
    ControlFlowDiagnosticSupport.assertStructuredDiagnostic(
        label, spec, actual.getFirst(), sourcePath);
  }
}
