package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.format.FormatResult;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ProgramRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BsbCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void publicCommandsCompleteN001WithSeparatedOutputChannels() throws IOException {
    Path source = copyResource("sources/CORE-N001.bsb", "CORE-N001.bsb");

    Invocation checked = invoke("check", source.toString());
    Invocation run = invoke("run", source.toString());
    Invocation formatted = invoke("format", source.toString());

    assertEquals(0, checked.exitCode());
    assertEquals("", checked.stdout());
    assertEquals("", checked.stderr());
    assertEquals(0, run.exitCode());
    assertEquals("42\n", run.stdout());
    assertEquals("", run.stderr());
    assertEquals(0, formatted.exitCode());
    assertEquals(resourceText("sources/CORE-N001.bsb"), formatted.stdout());
    assertEquals("", formatted.stderr());
  }

  @Test
  void jsonCheckSuccessUsesOnlyStandardOutput() throws IOException {
    Path source = copyResource("sources/CORE-N001.bsb", "CORE-N001.bsb");

    Invocation checked = invoke("check", "--json", source.toString());

    assertEquals(0, checked.exitCode());
    assertEquals(
        "{\"schemaVersion\":1,\"command\":\"check\",\"source\":\""
            + source
            + "\",\"success\":true,\"exitCode\":0,\"diagnostics\":[]}\n",
        checked.stdout());
    assertEquals("", checked.stderr());
  }

  @Test
  void jsonExplainSuccessUsesOnlyStandardOutputAndContainsTheWholeExplanation() throws IOException {
    Path source = copyResource("sources/CORE-N001.bsb", "CORE-N001.bsb");

    Invocation explained = invoke("explain", "--json", source.toString());

    assertEquals(0, explained.exitCode());
    assertTrue(explained.stdout().startsWith("{\"schemaVersion\":1,\"command\":\"explain\""));
    assertTrue(explained.stdout().contains("\"summary\":{\"entryPoint\":\"メイン\""));
    assertEquals(217, occurrences(explained.stdout(), "\"featureGroup\":"));
    assertTrue(explained.stdout().contains("\"userWords\":[{\"name\":\"二倍\""));
    assertTrue(explained.stdout().contains("\"name\":\"メイン\",\"spelling\":\"メイン\""));
    assertTrue(explained.stdout().contains("\"scopes\":["));
    assertTrue(explained.stdout().contains("\"bindings\":["));
    assertTrue(explained.stdout().endsWith("\n"));
    assertEquals("", explained.stderr());
  }

  @Test
  void jsonExplainReportsWarningsAndLanguageErrorsWithoutPartialExplanation() throws IOException {
    Path warning = copyResource("sources/CORE-F026.bsb", "CORE-F026.bsb");
    Path syntax = copyResource("sources/CORE-F011.bsb", "CORE-F011.bsb");
    Path name = copyResource("sources/CORE-F020.bsb", "CORE-F020.bsb");

    Invocation warningResult = invoke("explain", "--json", warning.toString());
    Invocation syntaxResult = invoke("explain", "--json", syntax.toString());
    Invocation nameResult = invoke("explain", "--json", name.toString());

    assertExplainDiagnostic(warningResult, 0, true, "W_PARTICLE_POSITION");
    assertTrue(warningResult.stdout().contains("\"summary\":"));
    assertExplainDiagnostic(syntaxResult, 9, false, "E_MIXED_STACK_PARENTHESES");
    assertExplainDiagnostic(nameResult, 8, false, "E_UNDEFINED_WORD");
    for (Invocation failure : List.of(syntaxResult, nameResult)) {
      assertFalse(failure.stdout().contains("\"summary\":"));
      assertTrue(failure.stdout().contains("\"builtinWords\":[]"));
      assertTrue(failure.stdout().contains("\"userWords\":[]"));
      assertTrue(failure.stdout().contains("\"scopes\":[]"));
      assertTrue(failure.stdout().contains("\"bindings\":[]"));
    }
  }

  @Test
  void jsonExplainReportsUsageIoInternalAndOutputLimitProblems() throws IOException {
    Invocation usage = invoke("explain", "--json");
    Path missing = temporaryDirectory.resolve("存在しない.bsb");
    Invocation io = invoke("explain", "--json", missing.toString());
    CliBackend brokenBackend =
        new CliBackend() {
          @Override
          public AnalysisResult check(Path path) {
            throw new IllegalStateException("secret explain failure");
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
    var internalOut = new ByteArrayOutputStream();
    var internalErr = new ByteArrayOutputStream();
    int internalExit =
        new BsbCli(brokenBackend, DiagnosticMessageCatalog.loadDefault())
            .run(new String[] {"explain", "--json", "input.bsb"}, internalOut, internalErr);
    Path source = copyResource("sources/CORE-N001.bsb", "limit.bsb");
    var limitOut = new ByteArrayOutputStream();
    var limitErr = new ByteArrayOutputStream();
    int limitExit =
        new BsbCli(
                new DefaultCliBackend(),
                DiagnosticMessageCatalog.loadDefault(),
                new CliJsonRenderer(),
                new ExplainJsonRenderer(300))
            .run(new String[] {"explain", "--json", source.toString()}, limitOut, limitErr);

    assertExplainProblem(usage, BsbCli.EXIT_USAGE, "usage");
    assertExplainProblem(io, BsbCli.EXIT_IO, "io");
    assertEquals(BsbCli.EXIT_INTERNAL, internalExit);
    assertTrue(internalOut.toString(StandardCharsets.UTF_8).contains("\"kind\":\"internal\""));
    assertFalse(internalOut.toString(StandardCharsets.UTF_8).contains("secret"));
    assertEquals("", internalErr.toString(StandardCharsets.UTF_8));
    assertEquals(BsbCli.EXIT_INTERNAL, limitExit);
    assertTrue(limitOut.toString(StandardCharsets.UTF_8).contains("\"kind\":\"outputLimit\""));
    assertEquals("", limitErr.toString(StandardCharsets.UTF_8));
  }

  @Test
  void jsonCheckReportsWarningsAndBothStaticExitClasses() throws IOException {
    Path warning = copyResource("sources/CORE-F026.bsb", "CORE-F026.bsb");
    Path syntax = copyResource("sources/CORE-F011.bsb", "CORE-F011.bsb");
    Path name = copyResource("sources/CORE-F020.bsb", "CORE-F020.bsb");

    Invocation warningResult = invoke("check", "--json", warning.toString());
    Invocation syntaxResult = invoke("check", "--json", syntax.toString());
    Invocation nameResult = invoke("check", "--json", name.toString());

    assertJsonDiagnostic(warningResult, 0, true, "W_PARTICLE_POSITION");
    assertJsonDiagnostic(syntaxResult, 9, false, "E_MIXED_STACK_PARENTHESES");
    assertJsonDiagnostic(nameResult, 8, false, "E_UNDEFINED_WORD");
  }

  @Test
  void jsonCheckReportsInvalidUtf8AsLanguageDiagnostic() throws IOException {
    Path source = temporaryDirectory.resolve("invalid-utf8.bsb");
    Files.write(source, new byte[] {(byte) 0xC3, 0x28});

    Invocation result = invoke("check", "--json", source.toString());

    assertJsonDiagnostic(result, 9, false, "E_INVALID_UTF8");
    assertTrue(result.stdout().contains("\"point\":{\"line\":1,\"column\":1,\"utf8Offset\":0}"));
  }

  @Test
  void jsonCheckReportsUsageAndIoProblemsWithoutStandardError() {
    Invocation usage = invoke("check", "--json");
    Path missing = temporaryDirectory.resolve("存在しない.bsb");
    Invocation io = invoke("check", "--json", missing.toString());

    assertEquals(BsbCli.EXIT_USAGE, usage.exitCode());
    assertEquals(
        "{\"schemaVersion\":1,\"command\":\"check\",\"success\":false,\"exitCode\":2,"
            + "\"diagnostics\":[],\"problem\":{\"kind\":\"usage\","
            + "\"message\":\"ソースファイルを1つ指定してください。\"}}\n",
        usage.stdout());
    assertEquals("", usage.stderr());
    assertEquals(BsbCli.EXIT_IO, io.exitCode());
    assertTrue(io.stdout().contains("\"source\":\"" + missing + "\""));
    assertTrue(io.stdout().contains("\"kind\":\"io\""));
    assertFalse(io.stdout().contains("NoSuchFile"));
    assertEquals("", io.stderr());
  }

  @Test
  void checkAndRunReturnStaticFailureWithoutProgramOutput() throws IOException {
    Path source = copyResource("sources/CORE-F020.bsb", "CORE-F020.bsb");

    Invocation checked = invoke("check", source.toString());
    Invocation run = invoke("run", source.toString());

    assertEquals(8, checked.exitCode());
    assertEquals("", checked.stdout());
    assertTrue(checked.stderr().contains("エラー[E_UNDEFINED_WORD]"));
    assertEquals(8, run.exitCode());
    assertEquals("", run.stdout());
    assertEquals(checked.stderr(), run.stderr());
  }

  @Test
  void formatStopsAtSyntaxErrorsAndDoesNotEmitPartialSource() throws IOException {
    Path source = copyResource("sources/CORE-F011.bsb", "CORE-F011.bsb");

    Invocation result = invoke("format", source.toString());

    assertEquals(9, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("エラー[E_MIXED_STACK_PARENTHESES]"));
  }

  @Test
  void runReturnsRuntimeExitForAnOverdeepBsbCallStack() throws IOException {
    String sourceText =
        "循環するとは （--）\n" + "    循環する\n" + "こと。\n\n" + "メインとは （--）\n" + "    循環する\n" + "こと。\n";
    Path source = temporaryDirectory.resolve("runtime-error.bsb");
    Files.writeString(source, sourceText, StandardCharsets.UTF_8);

    Invocation result = invoke("run", source.toString());

    assertEquals(10, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("エラー[E_CALL_STACK_LIMIT]"));
  }

  @Test
  void runAcceptsAnExplicitInstructionLimitAndKeepsTheDefaultOtherwise() throws IOException {
    Path source = copyResource("sources/CORE-N001.bsb", "instruction-limit.bsb");

    Invocation limited = invoke("run", "--instruction-limit", "1", source.toString());
    Invocation raised = invoke("run", "--instruction-limit", "100", source.toString());
    Invocation defaulted = invoke("run", source.toString());

    assertEquals(10, limited.exitCode());
    assertEquals("", limited.stdout());
    assertTrue(limited.stderr().contains("エラー[E_INSTRUCTION_LIMIT]"));
    assertTrue(limited.stderr().contains("上限: executedInstructions=1"));
    assertEquals(0, raised.exitCode());
    assertEquals("42\n", raised.stdout());
    assertEquals("", raised.stderr());
    assertEquals(0, defaulted.exitCode());
    assertEquals("42\n", defaulted.stdout());
    assertEquals("", defaulted.stderr());
  }

  @Test
  void warningsUseStderrButKeepSuccessfulCheckAndRunExitCodes() throws IOException {
    Path source = copyResource("sources/CORE-F026.bsb", "CORE-F026.bsb");

    Invocation checked = invoke("check", source.toString());
    Invocation run = invoke("run", source.toString());

    assertEquals(0, checked.exitCode());
    assertEquals("", checked.stdout());
    assertTrue(checked.stderr().contains("警告[W_PARTICLE_POSITION]"));
    assertEquals(0, run.exitCode());
    assertEquals("10", run.stdout());
    assertEquals(checked.stderr(), run.stderr());
  }

  @Test
  void versionPrintsTheBuildVersionToStandardOutput() {
    Invocation result = invoke("version");

    assertEquals(0, result.exitCode());
    assertTrue(
        result
            .stdout()
            .matches("bsb 0\\.1\\.0-SNAPSHOT \\(commit ([0-9a-f]{40}|[0-9a-f]{64}|unknown)\\)\\n"));
    assertEquals("", result.stderr());
  }

  @Test
  void formatAllowsStaticFailuresAndNeverOverwritesTheInputFile() throws IOException {
    Path source = copyResource("sources/CORE-F020.bsb", "CORE-F020.bsb");
    byte[] original = Files.readAllBytes(source);

    Invocation result = invoke("format", source.toString());

    assertEquals(0, result.exitCode());
    assertEquals(resourceText("sources/CORE-F020.bsb"), result.stdout());
    assertEquals("", result.stderr());
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @ParameterizedTest
  @MethodSource("invalidArguments")
  void rejectsInvalidCommandLinesWithUsageExit(String[] arguments, String expectedMessage) {
    Invocation result = invoke(arguments);

    assertEquals(BsbCli.EXIT_USAGE, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains(expectedMessage));
    assertTrue(result.stderr().contains("使い方: bsb version"));
  }

  @Test
  void reportsSourceReadFailureAsIoExit() {
    Path missing = temporaryDirectory.resolve("存在しない.bsb");

    Invocation result = invoke("check", missing.toString());

    assertEquals(BsbCli.EXIT_IO, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().startsWith("I/Oエラー:"));
    assertTrue(result.stderr().contains(missing.toString()));
  }

  @Test
  void reportsProgramOutputFailureAsARuntimeCapabilityDiagnostic() throws IOException {
    Path source = copyResource("sources/CORE-N001.bsb", "CORE-N001.bsb");
    var stderr = new ByteArrayOutputStream();
    OutputStream brokenOutput =
        new OutputStream() {
          @Override
          public void write(int value) throws IOException {
            throw new IOException("synthetic output failure");
          }

          @Override
          public void write(byte[] bytes) throws IOException {
            throw new IOException("synthetic output failure");
          }
        };

    int exitCode = new BsbCli().run(new String[] {"run", source.toString()}, brokenOutput, stderr);

    assertEquals(10, exitCode);
    assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("E_CAPABILITY_FAILURE"));
  }

  @Test
  void separatesProgramErrorFromFollowingDiagnosticsWithExactlyOneLf() throws IOException {
    Path withoutLf = temporaryDirectory.resolve("error-without-lf.bsb");
    Files.writeString(
        withoutLf,
        "メインとは （--）\n"
            + "    「記録」を エラー表示する\n"
            + "    一行を入力する\n"
            + "    入力行を取り出す\n"
            + "    一行表示する\n"
            + "こと。\n",
        StandardCharsets.UTF_8);
    Path withLf = temporaryDirectory.resolve("error-with-lf.bsb");
    Files.writeString(
        withLf,
        "メインとは （--）\n"
            + "    「記録」を エラー一行表示する\n"
            + "    一行を入力する\n"
            + "    入力行を取り出す\n"
            + "    一行表示する\n"
            + "こと。\n",
        StandardCharsets.UTF_8);

    Invocation first = invoke("run", withoutLf.toString());
    Invocation second = invoke("run", withLf.toString());

    assertEquals(10, first.exitCode());
    assertEquals(10, second.exitCode());
    assertTrue(first.stderr().startsWith("記録\n"), first.stderr());
    assertTrue(second.stderr().startsWith("記録\n"), second.stderr());
    assertFalse(first.stderr().startsWith("記録\n\n"), first.stderr());
    assertFalse(second.stderr().startsWith("記録\n\n"), second.stderr());
    assertTrue(first.stderr().contains("エラー[E_INPUT_RESULT_NOT_LINE]"));
    assertTrue(second.stderr().contains("エラー[E_INPUT_RESULT_NOT_LINE]"));
  }

  @Test
  void publicRunInjectsStdinAndReturnsProgramSelectedExitCodes() throws IOException {
    Path interactive = copyHostIoResource("sources/IO-N015.bsb", "IO-N015.bsb");
    Path exiting = copyHostIoResource("sources/IO-N012.bsb", "IO-N012.bsb");

    Invocation inputRun = invokeWithInput("山田 太郎\n", "run", interactive.toString());
    Invocation exitRun = invokeWithInput("", "run", exiting.toString());

    assertEquals(0, inputRun.exitCode());
    assertEquals("お名前は？こんにちは、山田 太郎さん\n", inputRun.stdout());
    assertEquals("", inputRun.stderr());
    assertEquals(42, exitRun.exitCode());
    assertEquals("前\n", exitRun.stdout());
    assertEquals("記録\n", exitRun.stderr());
  }

  @Test
  void publicRunPassesOnlyArgumentsAfterSeparator() throws IOException {
    Path source = copyHostIoResource("sources/IO-N016.bsb", "arguments.bsb");

    Invocation withArguments = invoke("run", source.toString(), "--", "最初", "", "𠮷");
    Invocation withoutSeparator = invoke("run", source.toString());

    assertEquals(0, withArguments.exitCode());
    assertEquals("最初\n\n𠮷\n", withArguments.stdout());
    assertEquals("", withArguments.stderr());
    assertEquals(0, withoutSeparator.exitCode());
    assertEquals("", withoutSeparator.stdout());
  }

  @Test
  void publicRunExposesLogicalNameAndNormalizedFileUri() throws IOException {
    Path source = copyHostIoResource("sources/IO-N018.bsb", "日本 語.bsb");
    Path relative = Path.of("").toAbsolutePath().relativize(source);

    Invocation result = invoke("run", relative.toString());

    assertEquals(0, result.exitCode());
    assertEquals(
        "日本 語.bsb\n" + source.toAbsolutePath().normalize().toUri().toASCIIString() + "\n",
        result.stdout());
    assertEquals("", result.stderr());
  }

  @Test
  void publicRunInjectsExplicitTomlConnectionsWithoutJavaEmbedding() throws IOException {
    Path source = temporaryDirectory.resolve("connection.bsb");
    Files.writeString(
        source,
        "顧客管理APIは 論理接続。\n" + "メインとは （--）\n" + "    論理接続を確認する<顧客管理API>\n" + "こと。\n",
        StandardCharsets.UTF_8);
    Path config = temporaryDirectory.resolve("connections.toml");
    Files.writeString(
        config,
        "schema-version = 1\n"
            + "[connections.\"顧客管理API\"]\n"
            + "base-uri = \"https://api.example.test/\"\n"
            + "allowed-methods = [\"POST\"]\n"
            + "[connections.\"顧客管理API\".authentication]\n"
            + "kind = \"none\"\n",
        StandardCharsets.UTF_8);

    Invocation result = invoke("run", "--connections", config.toString(), source.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stdout());
    assertEquals("", result.stderr());
  }

  @Test
  void publicRunReportsConnectionConfigFailuresBeforeProgramExecution() throws IOException {
    String secret = "CLI_CONFIG_SECRET";
    Path source = copyResource("sources/CORE-N001.bsb", "not-executed.bsb");
    Path invalid = temporaryDirectory.resolve("invalid.toml");
    Files.writeString(
        invalid, "schema-version = 1\nvalue = \"" + secret + "\n", StandardCharsets.UTF_8);

    Invocation malformed = invoke("run", "--connections", invalid.toString(), source.toString());
    Invocation missing =
        invoke(
            "run",
            "--connections",
            temporaryDirectory.resolve("missing.toml").toString(),
            source.toString());

    for (Invocation result : List.of(malformed, missing)) {
      assertEquals(BsbCli.EXIT_CONFIG, result.exitCode());
      assertEquals("", result.stdout());
      assertTrue(result.stderr().startsWith("接続設定エラー:"));
      assertFalse(result.stderr().contains(secret));
    }
  }

  @Test
  void convertsUnexpectedBackendExceptionToInternalExit() throws IOException {
    CliBackend brokenBackend =
        new CliBackend() {
          @Override
          public AnalysisResult check(Path path) {
            throw new IllegalStateException("synthetic internal failure");
          }

          @Override
          public ProgramRunResult run(Path path, ExecutionContext context) {
            throw new IllegalStateException("synthetic internal failure");
          }

          @Override
          public FormatResult format(Path path) {
            throw new IllegalStateException("synthetic internal failure");
          }
        };
    var messages = DiagnosticMessageCatalog.loadDefault();
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();

    int exitCode =
        new BsbCli(brokenBackend, messages)
            .run(new String[] {"check", "input.bsb"}, stdout, stderr);

    assertEquals(BsbCli.EXIT_INTERNAL, exitCode);
    assertEquals("", stdout.toString(StandardCharsets.UTF_8));
    assertEquals("内部エラー: 処理系で予期しない問題が発生しました。\n", stderr.toString(StandardCharsets.UTF_8));
  }

  @Test
  void jsonCheckRedactsUnexpectedBackendExceptions() throws IOException {
    CliBackend brokenBackend =
        new CliBackend() {
          @Override
          public AnalysisResult check(Path path) {
            throw new IllegalStateException("secret synthetic internal failure");
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
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();

    int exitCode =
        new BsbCli(brokenBackend, DiagnosticMessageCatalog.loadDefault())
            .run(new String[] {"check", "--json", "input.bsb"}, stdout, stderr);

    assertEquals(BsbCli.EXIT_INTERNAL, exitCode);
    assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("\"kind\":\"internal\""));
    assertFalse(stdout.toString(StandardCharsets.UTF_8).contains("secret"));
    assertFalse(stdout.toString(StandardCharsets.UTF_8).contains("IllegalStateException"));
    assertEquals("", stderr.toString(StandardCharsets.UTF_8));
  }

  @Test
  void jsonStdoutWriteAndFlushFailuresUseOnlyBestEffortHumanIoError() throws IOException {
    Path source = copyResource("sources/CORE-N001.bsb", "CORE-N001.bsb");
    OutputStream writeFailure =
        new OutputStream() {
          @Override
          public void write(int value) throws IOException {
            throw new IOException("synthetic write failure");
          }

          @Override
          public void write(byte[] bytes) throws IOException {
            throw new IOException("synthetic write failure");
          }
        };
    OutputStream flushFailure =
        new ByteArrayOutputStream() {
          @Override
          public void flush() throws IOException {
            throw new IOException("synthetic flush failure");
          }
        };

    for (String command : List.of("check", "explain")) {
      for (OutputStream output : List.of(writeFailure, flushFailure)) {
        var stderr = new ByteArrayOutputStream();
        int exitCode =
            new BsbCli().run(new String[] {command, "--json", source.toString()}, output, stderr);

        assertEquals(BsbCli.EXIT_IO, exitCode);
        assertEquals(
            "I/Oエラー: 入出力に失敗しました（対象: " + source + "）\n", stderr.toString(StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  void mainEntryPointDelegatesWithoutCallingSystemExitInTheTestableMethod() throws IOException {
    Path source = copyResource("sources/CORE-N001.bsb", "CORE-N001.bsb");
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();

    int exitCode = BsbMain.execute(new String[] {"run", source.toString()}, stdout, stderr);

    assertEquals(0, exitCode);
    assertEquals("42\n", stdout.toString(StandardCharsets.UTF_8));
    assertEquals("", stderr.toString(StandardCharsets.UTF_8));
  }

  private Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(
        exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private Invocation invokeWithInput(String input, String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode =
        new BsbCli()
            .run(
                arguments,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                stdout,
                stderr);
    return new Invocation(
        exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private static void assertJsonDiagnostic(
      Invocation result, int exitCode, boolean success, String code) {
    assertEquals(exitCode, result.exitCode());
    assertTrue(result.stdout().startsWith("{\"schemaVersion\":1,\"command\":\"check\""));
    assertTrue(result.stdout().contains("\"success\":" + success));
    assertTrue(result.stdout().contains("\"exitCode\":" + exitCode));
    assertTrue(result.stdout().contains("\"code\":\"" + code + "\""));
    assertTrue(result.stdout().endsWith("\n"));
    assertFalse(result.stdout().endsWith("\n\n"));
    assertEquals("", result.stderr());
  }

  private static void assertExplainDiagnostic(
      Invocation result, int exitCode, boolean success, String code) {
    assertEquals(exitCode, result.exitCode());
    assertTrue(result.stdout().startsWith("{\"schemaVersion\":1,\"command\":\"explain\""));
    assertTrue(result.stdout().contains("\"success\":" + success));
    assertTrue(result.stdout().contains("\"exitCode\":" + exitCode));
    assertTrue(result.stdout().contains("\"code\":\"" + code + "\""));
    assertTrue(result.stdout().endsWith("\n"));
    assertEquals("", result.stderr());
  }

  private static void assertExplainProblem(Invocation result, int exitCode, String kind) {
    assertEquals(exitCode, result.exitCode());
    assertTrue(result.stdout().startsWith("{\"schemaVersion\":1,\"command\":\"explain\""));
    assertTrue(result.stdout().contains("\"kind\":\"" + kind + "\""));
    assertTrue(result.stdout().contains("\"builtinWords\":[]"));
    assertEquals("", result.stderr());
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

  private Path copyResource(String relativePath, String targetName) throws IOException {
    Path target = temporaryDirectory.resolve(targetName);
    Files.write(target, resourceBytes(relativePath));
    return target;
  }

  private Path copyHostIoResource(String relativePath, String targetName) throws IOException {
    Path target = temporaryDirectory.resolve(targetName);
    String resource = "/conformance/host-io/" + relativePath;
    try (var input = BsbCliTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      Files.write(target, input.readAllBytes());
    }
    return target;
  }

  private static Stream<Arguments> invalidArguments() {
    return Stream.of(
        Arguments.of(new String[] {}, "サブコマンドとソースファイルを1つずつ指定してください。"),
        Arguments.of(new String[] {"check"}, "サブコマンドとソースファイルを1つずつ指定してください。"),
        Arguments.of(new String[] {"check", "input.bsb", "extra"}, "余分な引数があります。"),
        Arguments.of(new String[] {"check", "input.bsb", "--"}, "余分な引数があります。"),
        Arguments.of(new String[] {"format", "input.bsb", "--", "arg"}, "余分な引数があります。"),
        Arguments.of(new String[] {"run", "input.bsb", "arg"}, "runの起動引数の前に -- を指定してください。"),
        Arguments.of(
            new String[] {"run", "--instruction-limit"}, "--instruction-limit の後に正の整数を1つ指定してください。"),
        Arguments.of(
            new String[] {"run", "--instruction-limit", "0", "input.bsb"},
            "--instruction-limit には正の10進整数を指定してください。"),
        Arguments.of(
            new String[] {"run", "--instruction-limit", "abc", "input.bsb"},
            "--instruction-limit には正の10進整数を指定してください。"),
        Arguments.of(
            new String[] {"run", "--instruction-limit", "9223372036854775808", "input.bsb"},
            "--instruction-limit には正の10進整数を指定してください。"),
        Arguments.of(
            new String[] {
              "run", "--instruction-limit", "10", "--instruction-limit", "20", "input.bsb"
            },
            "--instruction-limit は1回だけ指定できます。"),
        Arguments.of(
            new String[] {"check", "--instruction-limit", "10", "input.bsb"},
            "--instruction-limit は run でだけ使用できます。"),
        Arguments.of(new String[] {"inspect"}, "未知のサブコマンドです: inspect"),
        Arguments.of(new String[] {"inspect", "input.bsb"}, "未知のサブコマンドです: inspect"),
        Arguments.of(
            new String[] {"--json", "input.bsb"}, "--json は check または explain の直後にだけ指定できます。"),
        Arguments.of(new String[] {"check", "input.bsb", "--json"}, "余分な引数があります。"),
        Arguments.of(new String[] {"run", "--json"}, "--json は check または explain の直後にだけ指定できます。"),
        Arguments.of(
            new String[] {"format", "--json"}, "--json は check または explain の直後にだけ指定できます。"));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = "/conformance/language-core/" + relativePath;
    try (var input = BsbCliTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
