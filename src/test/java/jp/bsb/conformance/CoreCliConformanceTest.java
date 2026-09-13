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
import jp.bsb.conformance.ConformanceData.CaseCatalog;
import jp.bsb.conformance.ConformanceData.CaseSpec;
import jp.bsb.conformance.ConformanceData.CommandExpectation;
import jp.bsb.conformance.ConformanceData.DiagnosticSpec;
import jp.bsb.conformance.ConformanceData.GeneratedSource;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticRenderer;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** N/Fケースを利用者が実際に使う公開CLIへ通す、言語コアの中央適合ハーネスです。 */
class CoreCliConformanceTest {
  @TempDir Path temporaryDirectory;

  @TestFactory
  List<DynamicTest> allNormalAndFailureCasesMatchTheNormativeData() throws IOException {
    ConformanceData.validateMessages();
    CaseCatalog catalog = ConformanceData.loadCases();
    ConformanceData.validateCaseTextResources(catalog);
    Map<String, DiagnosticSpec> diagnostics = ConformanceData.loadDiagnostics();
    assertEquals(
        catalog.syntaxIds().size() + catalog.staticIds().size() + catalog.warningIds().size(),
        diagnostics.size(),
        "diagnostics.tsv must cover every F case exactly once");

    var tests = new ArrayList<DynamicTest>();
    for (CaseSpec spec : catalog.cases()) {
      for (GeneratedSource source : ConformanceData.generateCaseSource(spec)) {
        for (String command : spec.commands()) {
          // F-025のcheck/runは数値演算で正常機能へ昇格した。旧字句を保つformat回帰だけを継続する。
          if (spec.id().equals("CORE-F025") && !command.equals("format")) {
            continue;
          }
          String label = spec.id() + '/' + source.variant() + '/' + command;
          tests.add(
              DynamicTest.dynamicTest(
                  label,
                  () -> executeAndCompare(label, catalog, diagnostics, spec, source, command)));
        }
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
    Path sourcePath =
        temporaryDirectory.resolve(
            (spec.id() + '-' + source.variant()).replaceAll("[^A-Za-z0-9-]", "_") + ".bsb");
    Files.write(sourcePath, source.bytes());
    byte[] original = Files.readAllBytes(sourcePath);

    Invocation actual = invoke(command, sourcePath);
    CommandExpectation expectation = spec.expectations().get(command);
    byte[] expectedStdout = expectedStdout(expectation);
    byte[] expectedStderr =
        expectedStderr(catalog, diagnostics.get(spec.id()), spec.id(), command, sourcePath);

    assertEquals(expectation.exit(), actual.exitCode(), label + ": exit code");
    assertArrayEquals(expectedStdout, actual.stdout(), label + ": stdout bytes");
    assertEquals(
        new String(expectedStderr, StandardCharsets.UTF_8),
        new String(actual.stderr(), StandardCharsets.UTF_8),
        label + ": stderr UTF-8 bytes");
    assertArrayEquals(
        original, Files.readAllBytes(sourcePath), label + ": CLI must not rewrite input");

    if (command.equals("format") && actual.exitCode() == 0) {
      assertFalse(
          startsWithBom(actual.stdout()), label + ": formatter output must not contain a BOM");
      Path formattedPath = temporaryDirectory.resolve(sourcePath.getFileName() + ".formatted.bsb");
      Files.write(formattedPath, actual.stdout());
      Invocation second = invoke("format", formattedPath);
      assertEquals(0, second.exitCode(), label + ": second format exit code");
      assertArrayEquals(actual.stdout(), second.stdout(), label + ": format must be idempotent");
      assertArrayEquals(new byte[0], second.stderr(), label + ": second format stderr");
    }
  }

  private static byte[] expectedStdout(CommandExpectation expectation) throws IOException {
    if (expectation.stdoutText() != null) {
      String suffix = expectation.trailingLf() ? "\n" : "";
      return (expectation.stdoutText() + suffix).getBytes(StandardCharsets.UTF_8);
    }
    if (expectation.stdoutHex() != null) {
      return ConformanceData.hexBytes(expectation.stdoutHex());
    }
    if (expectation.stdoutSource() != null) {
      return ConformanceData.resourceBytes(expectation.stdoutSource());
    }
    return new byte[0];
  }

  private static byte[] expectedStderr(
      CaseCatalog catalog, DiagnosticSpec spec, String caseId, String command, Path sourcePath)
      throws IOException {
    boolean shouldDiagnose =
        spec != null
            && (catalog.syntaxIds().contains(caseId)
                || (!command.equals("format")
                    && (catalog.staticIds().contains(caseId)
                        || catalog.warningIds().contains(caseId))));
    if (!shouldDiagnose) {
      return new byte[0];
    }

    Diagnostic diagnostic = diagnosticFrom(spec, sourcePath.toString());
    String rendered =
        new DiagnosticRenderer(DiagnosticMessageCatalog.loadDefault()).render(List.of(diagnostic));
    return rendered.getBytes(StandardCharsets.UTF_8);
  }

  private static Diagnostic diagnosticFrom(DiagnosticSpec spec, String sourcePath) {
    DiagnosticCode code = DiagnosticCode.valueOf(spec.code());
    Severity severity =
        switch (spec.severity()) {
          case "error" -> Severity.ERROR;
          case "warning" -> Severity.WARNING;
          default -> throw new IllegalArgumentException("unknown severity: " + spec.severity());
        };
    var location = new SourcePosition(0, spec.line(), spec.column());
    var builder = Diagnostic.builder(code, severity, stageFor(code), sourcePath, location);

    for (Map.Entry<String, String> field : spec.fields().entrySet()) {
      if (!field.getKey().equals("limit") && !field.getKey().equals("observed")) {
        builder.field(field.getKey(), field.getValue());
      }
    }
    if (spec.fields().containsKey("limit")) {
      builder.limit("numberDigits", spec.fields().get("limit"), spec.fields().get("observed"));
    }
    if (spec.expected() != null) {
      builder.expected(spec.expected());
    }
    if (spec.actual() != null) {
      builder.actual(spec.actual());
    }
    if (spec.fix() != null) {
      builder.fix(spec.fix());
    }
    if (spec.related() != null) {
      String[] positioned = spec.related().split(" ", 2);
      if (positioned.length == 2 && positioned[0].matches("[0-9]+:[0-9]+")) {
        String[] coordinates = positioned[0].split(":");
        builder.relatedLocation(
            new RelatedLocation(
                sourcePath,
                new SourcePosition(
                    0, Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])),
                positioned[1]));
      } else {
        builder.relatedLocation(RelatedLocation.outsideSource(spec.related()));
      }
    }
    return builder.build();
  }

  private static DiagnosticStage stageFor(DiagnosticCode code) {
    return switch (code) {
      case E_INVALID_UTF8, E_SOURCE_SIZE_LIMIT -> DiagnosticStage.UTF8;
      case E_DISALLOWED_WHITESPACE,
          E_INVISIBLE_CHARACTER,
          E_IDENTIFIER_TOO_LONG,
          E_TOKEN_LIMIT,
          E_INVALID_IDENTIFIER,
          E_UNEXPECTED_CHARACTER,
          E_MISSING_SEPARATOR,
          E_INVALID_NUMBER_LITERAL,
          E_NUMBER_LIMIT,
          E_UNTERMINATED_STRING,
          E_INVALID_ESCAPE,
          E_INVALID_UNICODE_SCALAR,
          E_CHARACTER_LENGTH,
          E_STRING_LIMIT ->
          DiagnosticStage.LEXICAL;
      case E_MIXED_STACK_PARENTHESES,
          E_EXPECTED_STACK_SEPARATOR,
          E_COMMENT_NOT_ALLOWED,
          E_DEFINITION_ADJACENCY,
          E_EXPECTED_WORD_END,
          E_EXPECTED_DEFINITION_END_MARK,
          E_UNEXPECTED_TOP_LEVEL,
          E_DEFINITION_LIMIT ->
          DiagnosticStage.SYNTAX;
      case E_RESERVED_NAME,
          E_DUPLICATE_NAME,
          E_MISSING_MAIN,
          E_INVALID_MAIN_EFFECT,
          E_UNDEFINED_WORD,
          E_NAME_NOT_CALLABLE,
          E_FEATURE_NOT_AVAILABLE,
          E_TYPE_CONSTRAINT_NOT_ALLOWED,
          E_UNKNOWN_TYPE ->
          DiagnosticStage.NAME;
      case E_STACK_UNDERFLOW,
          E_TYPE_MISMATCH,
          E_WORD_EFFECT_MISMATCH,
          E_MAIN_STACK_NOT_EMPTY,
          E_DIAGNOSTIC_LIMIT,
          W_PARTICLE_POSITION ->
          DiagnosticStage.TYPE_AND_STACK;
      case W_CONFUSABLE_IDENTIFIER -> DiagnosticStage.NAME;
      case E_IR_LIMIT -> DiagnosticStage.IR;
      case E_INTEGER_RESULT_LIMIT,
          E_DATA_STACK_LIMIT,
          E_CALL_STACK_LIMIT,
          E_INSTRUCTION_LIMIT,
          E_EXECUTION_TIMEOUT,
          E_OUTPUT_LIMIT ->
          DiagnosticStage.RUNTIME;
      default -> throw new IllegalArgumentException("not a Core diagnostic: " + code);
    };
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
