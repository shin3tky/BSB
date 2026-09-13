package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TimeZone;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.format.FormatResult;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ProgramRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplainJsonBoundaryTest {
  private static final JsonSpan SPAN =
      new JsonSpan(new JsonLineColumn(1, 1), new JsonLineColumn(1, 1), 0, 1);

  @TempDir Path temporaryDirectory;

  @Test
  void preservesNormalizationOriginalSpellingAndComplexPhysicalPositionsDeterministically()
      throws IOException {
    String source = "\uFEFFか\u3099値は 定数 1。\r\n\r\nメインとは （--）\r\n\tが値 を 一行表示する\r\nこと。\r\n";
    String sourceArgument = "引用\"逆\\補助𠮷👩‍💻.bsb";
    AnalysisResult result =
        new SourceChecker().check(sourceArgument, source.getBytes(StandardCharsets.UTF_8));
    assertTrue(result.successful(), result.diagnostics().toString());
    BsbCli cli = new BsbCli(backendReturning(result), DiagnosticMessageCatalog.loadDefault());
    Locale originalLocale = Locale.getDefault();
    TimeZone originalTimeZone = TimeZone.getDefault();

    Invocation first = invoke(cli, "explain", sourceArgument);
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
      Invocation changedDefaults = invoke(cli, "explain", sourceArgument);
      assertArrayEquals(first.stdout(), changedDefaults.stdout());
    } finally {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimeZone);
    }

    String json = new String(first.stdout(), StandardCharsets.UTF_8);
    assertEquals(0, first.exitCode());
    assertEquals(0, first.stderr().length);
    assertTrue(json.contains("\"name\":\"が値\",\"spelling\":\"が値\""));
    assertTrue(json.contains("\"source\":\"引用\\\"逆\\\\補助𠮷👩‍💻.bsb\""));
    assertTrue(json.contains("\"line\":4,\"column\":5"));
  }

  @Test
  void diagnosticsMatchCheckAndExplanationArraysStayEmptyForEveryFailureStage() throws IOException {
    for (String source :
        List.of(
            "tests/conformance/language-core/sources/CORE-F004.bsb",
            "tests/conformance/language-core/sources/CORE-F011.bsb",
            "tests/conformance/language-core/sources/CORE-F020.bsb",
            "tests/conformance/language-core/sources/CORE-F022.bsb",
            "tests/conformance/language-core/sources/CORE-F021.bsb",
            "tests/conformance/language-core/sources/CORE-F026.bsb")) {
      Invocation checked = invoke(new BsbCli(), "check", source);
      Invocation explained = invoke(new BsbCli(), "explain", source);
      Map<String, Object> check = object(StrictJsonParser.parse(checked.stdout()));
      Map<String, Object> explain = object(StrictJsonParser.parse(explained.stdout()));

      assertEquals(check.get("diagnostics"), explain.get("diagnostics"), source);
      assertEquals(checked.exitCode(), explained.exitCode(), source);
      if (explained.exitCode() != 0) {
        assertFalse(explain.containsKey("summary"));
        assertEquals(List.of(), explain.get("builtinWords"));
        assertEquals(List.of(), explain.get("userWords"));
        assertEquals(List.of(), explain.get("scopes"));
        assertEquals(List.of(), explain.get("bindings"));
      }
    }

    Path invalidUtf8 = temporaryDirectory.resolve("invalid-utf8.bsb");
    Files.write(invalidUtf8, new byte[] {(byte) 0xC3, 0x28});
    Invocation checked = invoke(new BsbCli(), "check", invalidUtf8.toString());
    Invocation explained = invoke(new BsbCli(), "explain", invalidUtf8.toString());
    assertEquals(
        object(StrictJsonParser.parse(checked.stdout())).get("diagnostics"),
        object(StrictJsonParser.parse(explained.stdout())).get("diagnostics"));
  }

  @Test
  void incompleteSuccessfulBackendSnapshotBecomesARedactedInternalProblem() throws IOException {
    AnalysisResult complete =
        new SourceChecker()
            .check("complete.bsb", "メインとは （--）\nこと。\n".getBytes(StandardCharsets.UTF_8));
    AnalysisResult incomplete =
        new AnalysisResult(complete.analyzedProgram(), complete.diagnostics(), Optional.empty());
    Invocation invocation =
        invoke(
            new BsbCli(backendReturning(incomplete), DiagnosticMessageCatalog.loadDefault()),
            "explain",
            "incomplete.bsb");
    String json = new String(invocation.stdout(), StandardCharsets.UTF_8);

    assertEquals(BsbCli.EXIT_INTERNAL, invocation.exitCode());
    assertTrue(json.contains("\"kind\":\"internal\""));
    assertFalse(json.contains("snapshot"));
    assertFalse(json.contains("IllegalStateException"));
    assertEquals(0, invocation.stderr().length);
  }

  @Test
  void capsOneHundredDiagnosticsInExactlyTheSameOrderAsCheck() throws IOException {
    var collector = new DiagnosticCollector();
    for (int index = 0; index < 101; index++) {
      collector.add(
          Diagnostic.builder(
                  DiagnosticCode.W_PARTICLE_POSITION,
                  Severity.WARNING,
                  DiagnosticStage.TYPE_AND_STACK,
                  "many.bsb",
                  new SourcePosition(index, 1, index + 1))
              .expected("本体先頭以外")
              .actual("を")
              .fix("先頭のをを削除してください")
              .build());
    }
    AnalysisResult result =
        new AnalysisResult(Optional.empty(), collector.diagnostics(), Optional.empty());
    BsbCli cli = new BsbCli(backendReturning(result), DiagnosticMessageCatalog.loadDefault());

    Invocation checked = invoke(cli, "check", "many.bsb");
    Invocation explained = invoke(cli, "explain", "many.bsb");
    List<?> checkDiagnostics =
        (List<?>) object(StrictJsonParser.parse(checked.stdout())).get("diagnostics");
    Map<String, Object> explain = object(StrictJsonParser.parse(explained.stdout()));

    assertEquals(100, checkDiagnostics.size());
    assertEquals(checkDiagnostics, explain.get("diagnostics"));
    assertEquals(List.of(), explain.get("builtinWords"));
    assertEquals(8, explained.exitCode());
  }

  @Test
  void turnsSyntheticReadFailureIntoAStableIoProblem() throws IOException {
    CliBackend backend =
        new CliBackend() {
          @Override
          public AnalysisResult check(Path path) throws IOException {
            throw new IOException("host path detail must remain secret");
          }

          @Override
          public ProgramRunResult run(Path path, ExecutionContext context) {
            throw new UnsupportedOperationException();
          }

          @Override
          public FormatResult format(Path path) {
            throw new UnsupportedOperationException();
          }
        };

    Invocation invocation =
        invoke(
            new BsbCli(backend, DiagnosticMessageCatalog.loadDefault()),
            "explain",
            "unreadable.bsb");
    String json = new String(invocation.stdout(), StandardCharsets.UTF_8);

    assertEquals(BsbCli.EXIT_IO, invocation.exitCode());
    assertTrue(json.contains("\"kind\":\"io\""));
    assertFalse(json.contains("host path"));
    assertEquals(0, invocation.stderr().length);
  }

  @Test
  void handlesTenThousandWordsAndADeepCallChainWithoutJavaRecursion() throws IOException {
    String source = tenThousandWordChain();
    AnalysisResult analysis =
        new SourceChecker().check("deep.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    Invocation invocation =
        invoke(
            new BsbCli(backendReturning(analysis), DiagnosticMessageCatalog.loadDefault()),
            "explain",
            "deep.bsb");
    String json = new String(invocation.stdout(), StandardCharsets.UTF_8);

    assertEquals(0, invocation.exitCode());
    assertEquals(10_000, occurrences(json, "\"reachableFromMain\":true"));
    assertTrue(json.contains("\"capabilities\":[\"console.input\"]"));
    assertTrue(invocation.stdout().length <= ExplainJsonRenderer.MAX_OUTPUT_BYTES);
    assertEquals(0, invocation.stderr().length);
  }

  @Test
  void rendersSixtyFiveThousandBindingsAndALargeUseListWithinTheDistributionHeap() {
    var uses = new ArrayList<ExplainBindingUseJson>(100_000);
    for (int index = 0; index < 100_000; index++) {
      uses.add(new ExplainBindingUseJson("値", index % 2 == 0 ? "read" : "write", SPAN));
    }
    var bindings = new ArrayList<ExplainBindingJson>(65_536);
    for (int index = 0; index < 65_536; index++) {
      bindings.add(
          new ExplainBindingJson(
              "B" + (index + 1),
              "値" + index,
              "値" + index,
              "constant",
              "global",
              "S1",
              "整数",
              OptionalInt.of(index + 1),
              SPAN,
              index == 0 ? uses : List.of()));
    }
    var explanation =
        new ExplainJson(
            new ExplainSummaryJson("メイン", List.of("メイン"), List.of(), List.of(), List.of()),
            List.of(),
            List.of(),
            List.of(new ExplainScopeJson("S1", "global", Optional.empty(), Optional.empty(), SPAN)),
            bindings);

    ExplainJsonRenderer.RenderResult rendered =
        new ExplainJsonRenderer()
            .render(ExplainJsonDocument.success("large.bsb", List.of(), explanation));
    String json = new String(rendered.bytes(), StandardCharsets.UTF_8);

    assertEquals(0, rendered.exitCode());
    assertTrue(json.contains("\"id\":\"B65536\""));
    assertEquals(100_001, occurrences(json, "\"location\":{\"span\":"));
    assertTrue(rendered.bytes().length <= ExplainJsonRenderer.MAX_OUTPUT_BYTES);
  }

  private static String tenThousandWordChain() {
    var source = new StringBuilder(500_000);
    for (int index = 0; index < 9_999; index++) {
      source.append(word(index)).append("とは （--）\n    ");
      if (index == 9_998) {
        source.append("一行を入力する 入力結果を捨てる");
      } else {
        source.append(word(index + 1));
      }
      source.append("\nこと。\n\n");
    }
    source.append("メインとは （--）\n    ").append(word(0)).append("\nこと。\n");
    return source.toString();
  }

  private static String word(int index) {
    return "語" + index;
  }

  private static int occurrences(String text, String fragment) {
    int count = 0;
    int offset = 0;
    while ((offset = text.indexOf(fragment, offset)) >= 0) {
      count++;
      offset += fragment.length();
    }
    return count;
  }

  private static Invocation invoke(BsbCli cli, String command, String source) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = cli.run(new String[] {command, "--json", source}, stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private static CliBackend backendReturning(AnalysisResult result) {
    return new CliBackend() {
      @Override
      public AnalysisResult check(Path path) {
        return result;
      }

      @Override
      public ProgramRunResult run(Path path, ExecutionContext context) {
        throw new UnsupportedOperationException();
      }

      @Override
      public FormatResult format(Path path) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
