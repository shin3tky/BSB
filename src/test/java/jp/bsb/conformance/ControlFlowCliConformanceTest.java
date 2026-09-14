package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.cli.BsbCli;
import jp.bsb.conformance.ControlFlowConformanceData.CaseCatalog;
import jp.bsb.conformance.ControlFlowConformanceData.CaseSpec;
import jp.bsb.conformance.ControlFlowConformanceData.CommandExpectation;
import jp.bsb.conformance.ControlFlowConformanceData.DiagnosticSpec;
import jp.bsb.conformance.ControlFlowConformanceData.GeneratedSource;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticRenderer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** N3/F3を利用者が実際に呼ぶ公開CLIへ通す、制御フローの中央適合ハーネスです。 */
class ControlFlowCliConformanceTest {
  @TempDir Path temporaryDirectory;

  @TestFactory
  List<DynamicTest> allNormalAndFailureCasesMatchTheNormativeData() throws IOException {
    // 制御フローのメッセージ断片は実行時に統合されるため、重複と欠落も一緒に検査する。
    ConformanceData.validateMessages();
    CaseCatalog catalog = ControlFlowConformanceData.loadCases();
    Map<String, DiagnosticSpec> diagnostics = ControlFlowConformanceData.loadDiagnostics();
    long failureCases =
        catalog.cases().stream().filter(spec -> spec.id().startsWith("FLOW-F")).count();
    assertEquals(failureCases, diagnostics.size(), "diagnostics.tsv must cover every F3 case");

    var tests = new ArrayList<DynamicTest>();
    for (CaseSpec spec : catalog.cases()) {
      GeneratedSource source = ControlFlowConformanceData.generateCaseSource(spec);
      for (String command : spec.commands()) {
        String label = spec.id() + '/' + source.variant() + '/' + command;
        tests.add(
            DynamicTest.dynamicTest(
                label,
                () -> executeAndCompare(label, catalog, diagnostics, spec, source, command)));
      }
    }
    return List.copyOf(tests);
  }

  private void executeAndCompare(
      String label,
      CaseCatalog catalog,
      Map<String, DiagnosticSpec> diagnostics,
      CaseSpec spec,
      GeneratedSource source,
      String command)
      throws IOException {
    Path sourcePath = temporaryDirectory.resolve(spec.id() + '-' + source.variant() + ".bsb");
    Files.write(sourcePath, source.bytes());
    byte[] original = Files.readAllBytes(sourcePath);

    Invocation actual = invoke(command, sourcePath);
    CommandExpectation expectation = spec.expectations().get(command);
    byte[] expectedStdout = expectedStdout(expectation);
    byte[] expectedStderr =
        expectedStderr(catalog, diagnostics.get(spec.id()), command, sourcePath.toString());

    assertEquals(expectation.exit(), actual.exitCode(), label + ": expected/actual exit code");
    assertArrayEquals(expectedStdout, actual.stdout(), label + ": expected/actual stdout bytes");
    assertArrayEquals(expectedStderr, actual.stderr(), label + ": expected/actual stderr bytes");
    assertArrayEquals(
        original, Files.readAllBytes(sourcePath), label + ": CLI must not rewrite input");

    // 正常終了したformatは、個別フラグの有無にかかわらず全件を自動で2回適用する。
    if (command.equals("format") && actual.exitCode() == 0) {
      assertFalse(
          startsWithBom(actual.stdout()), label + ": formatter output must not contain a BOM");
      Path formattedPath = temporaryDirectory.resolve(spec.id() + "-formatted.bsb");
      Files.write(formattedPath, actual.stdout());
      Invocation second = invoke("format", formattedPath);
      assertEquals(0, second.exitCode(), label + ": second format expected/actual exit code");
      assertArrayEquals(
          actual.stdout(), second.stdout(), label + ": format expected/actual idempotent bytes");
      assertArrayEquals(
          new byte[0], second.stderr(), label + ": second format expected/actual stderr bytes");
    }
  }

  private static byte[] expectedStdout(CommandExpectation expectation) throws IOException {
    if (expectation.stdoutText() != null) {
      String suffix = expectation.trailingLf() ? "\n" : "";
      return (expectation.stdoutText() + suffix).getBytes(StandardCharsets.UTF_8);
    }
    if (expectation.stdoutHex() != null) {
      return ControlFlowConformanceData.hexBytes(expectation.stdoutHex());
    }
    if (expectation.stdoutSource() != null) {
      return ControlFlowConformanceData.resourceBytes(expectation.stdoutSource());
    }
    return new byte[0];
  }

  private static byte[] expectedStderr(
      CaseCatalog catalog, DiagnosticSpec spec, String command, String sourcePath)
      throws IOException {
    if (!shouldDiagnose(catalog, spec, command)) {
      return new byte[0];
    }
    String rendered =
        new DiagnosticRenderer(DiagnosticMessageCatalog.loadDefault())
            .render(List.of(ControlFlowDiagnosticSupport.expectedDiagnostic(spec, sourcePath)));
    return rendered.getBytes(StandardCharsets.UTF_8);
  }

  private static boolean shouldDiagnose(CaseCatalog catalog, DiagnosticSpec spec, String command) {
    if (spec == null) {
      return false;
    }
    if (catalog.syntaxIds().contains(spec.id())) {
      return true;
    }
    if (command.equals("format")) {
      return false;
    }
    // checkで発見される静的診断・警告はrunでも発見され、実行時診断はrunだけに現れる。
    return spec.command().equals("check") || command.equals("run");
  }

  private static Invocation invoke(String command, Path sourcePath) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(new String[] {command, sourcePath.toString()}, stdout, stderr);
    return new Invocation(exit, stdout.toByteArray(), stderr.toByteArray());
  }

  private static boolean startsWithBom(byte[] bytes) {
    return bytes.length >= 3
        && bytes[0] == (byte) 0xEF
        && bytes[1] == (byte) 0xBB
        && bytes[2] == (byte) 0xBF;
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
