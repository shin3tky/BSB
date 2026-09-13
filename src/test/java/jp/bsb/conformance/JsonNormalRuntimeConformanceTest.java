package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.cli.BsbCli;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticRenderer;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.JsonRuntimeValue;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.RuntimeValue;
import jp.bsb.runtime.StringValue;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class JsonNormalRuntimeConformanceTest {
  @TempDir Path temporaryDirectory;

  @TestFactory
  List<DynamicTest> everyCliCaseMatchesItsOwnSourceCommandAndExpectation() throws Exception {
    var diagnostics =
        JsonConformanceData.loadDiagnostics().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    JsonConformanceData.DiagnosticSpec::id, spec -> spec));
    var tests = new ArrayList<DynamicTest>();
    for (var spec : JsonConformanceData.loadCases().cases()) {
      if (!spec.fixture().equals("cli")) {
        continue;
      }
      for (String command : spec.commands()) {
        String label = spec.id() + '/' + command;
        tests.add(
            DynamicTest.dynamicTest(
                label, () -> executeCliCase(label, spec, command, diagnostics)));
      }
    }
    assertEquals(78, tests.size());
    return List.copyOf(tests);
  }

  @Test
  void jsonBindingsReassignmentBranchJoinAndUserEffectsRemainConcrete() {
    String source =
        "大域値は 変数 JSONヌル。\n\n"
            + "JSONを保つとは （JSON -- JSON）\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    局所値は 変数 JSONヌル。\n"
            + "    「1」を JSONを解析する を 局所値 に 入れる\n"
            + "    はい ならば\n"
            + "        局所値 を JSONを保つ JSONを文字列に変換する 一行表示する\n"
            + "    さもなければ\n"
            + "        JSONヌル JSONを文字列に変換する 一行表示する\n"
            + "    つぎに\n"
            + "こと。\n";
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    var checked = new SourceChecker().check("json-bindings.bsb", bytes);
    var output = new MemoryOutputSink();
    var result =
        new ProgramRunner()
            .run(
                "json-bindings.bsb",
                bytes,
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(checked.successful(), codes(checked.diagnostics()).toString());
    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("1\n", output.utf8Text());
  }

  @Test
  void unreachableJsonFailureProducesOnlyTheExistingReachabilityWarning() {
    String source =
        "止めるとは （--）\n"
            + "    戻る\n"
            + "    JSONヌル JSONから整数を取り出す 一行表示する\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    止める\n"
            + "こと。\n";
    var result =
        new SourceChecker().check("unreachable-json.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(List.of(DiagnosticCode.W_UNREACHABLE_CODE), codes(result.diagnostics()));
  }

  private static List<DiagnosticCode> codes(List<Diagnostic> diagnostics) {
    return diagnostics.stream().map(Diagnostic::code).toList();
  }

  private void executeCliCase(
      String label,
      JsonConformanceData.CaseSpec spec,
      String command,
      Map<String, JsonConformanceData.DiagnosticSpec> diagnostics)
      throws Exception {
    Path sourcePath = Path.of("tests/conformance/json").resolve(spec.source());
    byte[] original = Files.readAllBytes(sourcePath);
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(new String[] {command, sourcePath.toString()}, stdout, stderr);
    var expected = spec.expectations().get(command);

    assertEquals(expected.exit(), exit, label + ": exit");
    byte[] expectedStdout =
        expected.stdoutPath() == null
            ? new byte[0]
            : JsonConformanceData.resourceBytes(expected.stdoutPath());
    assertArrayEquals(expectedStdout, stdout.toByteArray(), label + ": stdout");
    byte[] expectedStderr =
        expected.diagnosticId() == null
            ? new byte[0]
            : renderedDiagnostic(diagnostics.get(expected.diagnosticId()), sourcePath);
    assertArrayEquals(expectedStderr, stderr.toByteArray(), label + ": stderr");
    assertArrayEquals(original, Files.readAllBytes(sourcePath), label + ": source rewrite");

    if (command.equals("format")) {
      Path formatted = temporaryDirectory.resolve(spec.id() + ".formatted.bsb");
      Files.write(formatted, stdout.toByteArray());
      var secondStdout = new ByteArrayOutputStream();
      var secondStderr = new ByteArrayOutputStream();
      int secondExit =
          new BsbCli()
              .run(new String[] {"format", formatted.toString()}, secondStdout, secondStderr);
      assertEquals(0, secondExit, label + ": second format exit");
      assertArrayEquals(stdout.toByteArray(), secondStdout.toByteArray(), label + ": idempotency");
      assertArrayEquals(new byte[0], secondStderr.toByteArray(), label + ": second stderr");
    }
    if (spec.normal() && command.equals("run")) {
      assertNormalExecutionState(spec, sourcePath, expectedStdout);
    }
    if (expected.diagnosticId() != null) {
      assertStructuredDiagnostic(spec, diagnostics.get(expected.diagnosticId()), sourcePath);
    }
  }

  private static void assertNormalExecutionState(
      JsonConformanceData.CaseSpec spec, Path sourcePath, byte[] expectedStdout) throws Exception {
    var output = new MemoryOutputSink();
    var result =
        new ProgramRunner()
            .run(
                sourcePath.toString(),
                Files.readAllBytes(sourcePath),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));
    var expected = spec.runExpectation();
    assertTrue(result.successful(), spec.id() + ": " + result.diagnostics());
    assertArrayEquals(expectedStdout, output.bytes(), spec.id() + ": ProgramRunner stdout");
    assertEquals(expected.finalStackEmpty(), result.finalDataStack().isEmpty(), spec.id());
    assertEquals(expected.jsonConstruction(), result.jsonConstructionUnits(), spec.id());
    assertEquals(expected.jsonWork(), result.jsonWorkUnits(), spec.id());
    assertTrue(!expected.observes().isBlank(), spec.id());
  }

  private static byte[] renderedDiagnostic(JsonConformanceData.DiagnosticSpec spec, Path sourcePath)
      throws Exception {
    String rendered =
        new DiagnosticRenderer(DiagnosticMessageCatalog.loadDefault())
            .render(List.of(diagnosticFrom(spec, sourcePath.toString())));
    return rendered.getBytes(StandardCharsets.UTF_8);
  }

  private static Diagnostic diagnosticFrom(
      JsonConformanceData.DiagnosticSpec spec, String sourcePath) {
    var builder =
        Diagnostic.builder(
            DiagnosticCode.valueOf(spec.code()),
            Severity.ERROR,
            DiagnosticStage.RUNTIME,
            sourcePath,
            new SourcePosition(0, spec.line(), spec.column()));
    spec.fields()
        .forEach(
            (key, value) -> {
              if (!Set.of("limitName", "limit", "observed").contains(key)) {
                builder.field(key, value);
              }
            });
    if (spec.fields().containsKey("limitName")) {
      builder.limit(
          spec.fields().get("limitName"),
          spec.fields().get("limit"),
          spec.fields().get("observed"));
    }
    builder.expected(spec.expected()).actual(spec.actual()).fix(spec.fix());
    return builder.build();
  }

  private static void assertStructuredDiagnostic(
      JsonConformanceData.CaseSpec caseSpec,
      JsonConformanceData.DiagnosticSpec expected,
      Path sourcePath)
      throws Exception {
    var output = new MemoryOutputSink();
    var result =
        new ProgramRunner()
            .run(
                sourcePath.toString(),
                Files.readAllBytes(sourcePath),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));
    assertEquals(1, result.diagnostics().size(), caseSpec.id());
    Diagnostic actual = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.valueOf(expected.code()), actual.code(), caseSpec.id());
    assertEquals(Severity.ERROR, actual.severity(), caseSpec.id());
    assertEquals(DiagnosticStage.RUNTIME, actual.stage(), caseSpec.id());
    assertEquals(sourcePath.toString(), actual.sourcePath(), caseSpec.id());
    assertEquals(expectedFields(expected), actual.fields(), caseSpec.id());
    assertEquals(expected.expected(), actual.expected().orElse(null), caseSpec.id());
    assertEquals(expected.actual(), actual.actual().orElse(null), caseSpec.id());
    assertEquals(List.of(expected.fix()), actual.fixes(), caseSpec.id());
    assertEquals(
        expected.fields().get("limitName"), actual.limitName().orElse(null), caseSpec.id());
    assertEquals(expected.fields().get("limit"), actual.limit().orElse(null), caseSpec.id());
    assertEquals(expected.fields().get("observed"), actual.observed().orElse(null), caseSpec.id());
    SourceSpan span = (SourceSpan) actual.location();
    assertEquals(expected.line(), span.start().line(), caseSpec.id());
    assertEquals(expected.column(), span.start().column(), caseSpec.id());
    assertEquals(expected.line(), span.end().line(), caseSpec.id());
    String word = expected.fields().get("word");
    assertEquals(
        expected.column() + word.codePointCount(0, word.length()),
        span.end().column(),
        caseSpec.id());
    var failureState = caseSpec.failureExpectation();
    assertEquals(
        expected.fields().get("dataStack"),
        result.finalDataStack().stream().map(RuntimeValue::traceText).toList().toString(),
        caseSpec.id());
    assertEquals(failureState.jsonConstruction(), result.jsonConstructionUnits(), caseSpec.id());
    assertEquals(failureState.jsonWork(), result.jsonWorkUnits(), caseSpec.id());
    assertEquals(
        failureState.finalGlobals(),
        result.finalGlobalValues().stream()
            .map(value -> value.map(JsonNormalRuntimeConformanceTest::globalSummary).orElse("-"))
            .toList(),
        caseSpec.id());
    assertTrue(output.bytes().length == 0, caseSpec.id());
  }

  private static String globalSummary(RuntimeValue value) {
    if (value instanceof StringValue string) {
      int utf8Bytes = string.value().getBytes(StandardCharsets.UTF_8).length;
      return utf8Bytes > 64 ? "文字列:utf8Bytes=" + utf8Bytes : string.traceText();
    }
    if (value instanceof JsonRuntimeValue json) {
      return "JSON:" + json.displayText();
    }
    return value.traceText();
  }

  private static Map<String, String> expectedFields(JsonConformanceData.DiagnosticSpec spec) {
    var fields = new java.util.LinkedHashMap<>(spec.fields());
    fields.keySet().removeAll(Set.of("limitName", "limit", "observed"));
    return Map.copyOf(fields);
  }
}
