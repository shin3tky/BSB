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
import jp.bsb.conformance.BindingConformanceData.CaseCatalog;
import jp.bsb.conformance.BindingConformanceData.CaseSpec;
import jp.bsb.conformance.BindingConformanceData.CommandExpectation;
import jp.bsb.conformance.BindingConformanceData.DiagnosticSpec;
import jp.bsb.conformance.BindingConformanceData.GeneratedSource;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticRenderer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** N4/F4を利用者が実際に呼ぶ公開CLIへ通す、束縛の中央適合ハーネスです。 */
class BindingCliConformanceTest {
  @TempDir Path temporaryDirectory;

  @TestFactory
  List<DynamicTest> allNormalFailureAndWarningCasesMatchTheNormativeData() throws IOException {
    ConformanceData.validateMessages();
    CaseCatalog catalog = BindingConformanceData.loadCases();
    Map<String, List<DiagnosticSpec>> diagnostics = BindingConformanceData.loadDiagnostics();
    long failureCases =
        catalog.cases().stream().filter(spec -> spec.id().startsWith("BIND-F")).count();
    assertEquals(failureCases, diagnostics.size(), "diagnostics.tsv must cover every F4 case");
    assertEquals(
        failureCases + 1,
        diagnostics.values().stream().mapToLong(List::size).sum(),
        "only BIND-F011 has two diagnostics");

    var tests = new ArrayList<DynamicTest>();
    for (CaseSpec spec : catalog.cases()) {
      GeneratedSource source = BindingConformanceData.generateCaseSource(spec);
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
      Map<String, List<DiagnosticSpec>> diagnostics,
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
            catalog,
            diagnostics.getOrDefault(spec.id(), List.of()),
            command,
            sourcePath.toString());

    assertEquals(expectation.exit(), actual.exitCode(), label + ": expected/actual exit code");
    assertArrayEquals(expectedStdout, actual.stdout(), label + ": expected/actual stdout bytes");
    assertArrayEquals(expectedStderr, actual.stderr(), label + ": expected/actual stderr bytes");
    assertArrayEquals(
        original, Files.readAllBytes(sourcePath), label + ": CLI must not rewrite input");

    // 成功するformatは規範上すべて冪等であり、個別指定なしでも2回目を必ず検査する。
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
      return BindingConformanceData.hexBytes(expectation.stdoutHex());
    }
    if (expectation.stdoutSource() != null) {
      return BindingConformanceData.resourceBytes(expectation.stdoutSource());
    }
    return new byte[0];
  }

  private static byte[] expectedStderr(
      CaseCatalog catalog, List<DiagnosticSpec> specs, String command, String sourcePath)
      throws IOException {
    if (specs.isEmpty()
        || (command.equals("format") && !catalog.syntaxIds().contains(specs.getFirst().id()))) {
      return new byte[0];
    }
    var expected =
        specs.stream()
            .map(spec -> BindingDiagnosticSupport.expectedDiagnostic(spec, sourcePath))
            .toList();
    String rendered =
        new DiagnosticRenderer(DiagnosticMessageCatalog.loadDefault()).render(expected);
    return rendered.getBytes(StandardCharsets.UTF_8);
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
