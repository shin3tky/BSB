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
import jp.bsb.conformance.NumericConformanceData.CaseCatalog;
import jp.bsb.conformance.NumericConformanceData.CaseSpec;
import jp.bsb.conformance.NumericConformanceData.CommandExpectation;
import jp.bsb.conformance.NumericConformanceData.DiagnosticSpec;
import jp.bsb.conformance.NumericConformanceData.GeneratedSource;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticRenderer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** N6/F6の全コマンドを公開CLIへ通す、数値演算の中央適合ハーネスです。 */
class NumericCliConformanceTest {
  @TempDir Path temporaryDirectory;

  @TestFactory
  List<DynamicTest> allNormalFailureAndWarningCasesMatchTheNormativeData() throws IOException {
    ConformanceData.validateMessages();
    CaseCatalog catalog = NumericConformanceData.loadCases();
    Map<String, DiagnosticSpec> diagnostics = NumericConformanceData.loadDiagnostics();
    assertEquals(20, diagnostics.size(), "diagnostics.tsv must cover every F6 case");
    assertEquals(
        catalog.cases().stream().map(CaseSpec::id).filter(id -> id.startsWith("NUM-F")).toList(),
        diagnostics.keySet().stream().toList(),
        "diagnostics.tsv must preserve the complete F6 order");

    var tests = new ArrayList<DynamicTest>();
    for (CaseSpec spec : catalog.cases()) {
      GeneratedSource source = NumericConformanceData.generateCaseSource(spec);
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
        expectedStderr(
            catalog, diagnostics.get(spec.id()), command, sourcePath.toAbsolutePath().toString());

    assertEquals(expectation.exit(), actual.exitCode(), label + ": exit code");
    assertArrayEquals(expectedStdout, actual.stdout(), label + ": stdout bytes");
    assertArrayEquals(expectedStderr, actual.stderr(), label + ": stderr bytes");
    assertArrayEquals(
        original, Files.readAllBytes(sourcePath), label + ": CLI must not rewrite input");

    if (command.equals("format") && actual.exitCode() == 0) {
      assertFalse(
          startsWithBom(actual.stdout()), label + ": formatter output must not contain a BOM");
      Path formattedPath = temporaryDirectory.resolve(spec.id() + "-formatted.bsb");
      Files.write(formattedPath, actual.stdout());
      Invocation second = invoke("format", formattedPath);
      assertEquals(0, second.exitCode(), label + ": second format exit code");
      assertArrayEquals(actual.stdout(), second.stdout(), label + ": format idempotent bytes");
      assertArrayEquals(new byte[0], second.stderr(), label + ": second format stderr bytes");
    }
  }

  private static byte[] expectedStdout(CommandExpectation expectation) throws IOException {
    if (expectation.stdoutText() != null) {
      String suffix = expectation.trailingLf() ? "\n" : "";
      return (expectation.stdoutText() + suffix).getBytes(StandardCharsets.UTF_8);
    }
    if (expectation.stdoutHex() != null) {
      return NumericConformanceData.hexBytes(expectation.stdoutHex());
    }
    if (expectation.stdoutSource() != null) {
      return NumericConformanceData.resourceBytes(expectation.stdoutSource());
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
            .render(List.of(NumericDiagnosticSupport.expectedDiagnostic(spec, sourcePath)));
    return rendered.getBytes(StandardCharsets.UTF_8);
  }

  private static boolean shouldDiagnose(CaseCatalog catalog, DiagnosticSpec spec, String command) {
    if (spec == null || command.equals("format")) {
      return false;
    }
    if (catalog.runtimeIds().contains(spec.id())) {
      return command.equals("run") && spec.command().equals("run");
    }
    return spec.command().equals("check");
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
