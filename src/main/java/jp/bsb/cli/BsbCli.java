package jp.bsb.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticRenderer;
import jp.bsb.explain.ProgramExplainer;
import jp.bsb.runtime.CapabilityException;
import jp.bsb.runtime.ConsoleOutput;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.ExecutionLimits;
import jp.bsb.runtime.MonotonicClock;
import jp.bsb.runtime.MonotonicTime;
import jp.bsb.runtime.OutputSink;
import jp.bsb.runtime.ProcessArguments;
import jp.bsb.runtime.ProgramControl;
import jp.bsb.runtime.ProgramIdentity;
import jp.bsb.runtime.ProgramMetadata;
import jp.bsb.runtime.RuntimeCapability;
import jp.bsb.runtime.SleepCapability;
import jp.bsb.runtime.StreamConsoleInput;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.WallTime;

/**
 * ホスト入出力のコマンドライン引数、実行環境能力、出力チャネル、終了コードを管理します。
 *
 * <p>【コンピュータ科学の観点：機構と方針の分離】字句解析や実行の「機構」は既存パイプラインへ任せ、このクラスは、どの処理を呼ぶか、結果をどのストリームへ書くか、
 * どの終了コードを返すかというCLIの「方針」だけを担当します。こうすると {@link System#exit(int)} を呼ばずに入出力と終了コードを単体試験できます。
 */
public final class BsbCli {
  /** コマンドまたは引数の使用法エラーです。 */
  public static final int EXIT_USAGE = 2;

  /** ソースや出力ストリームのI/Oエラーです。 */
  public static final int EXIT_IO = 3;

  /** BSBプログラムではなく処理系自身の内部エラーです。 */
  public static final int EXIT_INTERNAL = 70;

  /** 明示された接続設定ファイルが利用できない場合です。 */
  public static final int EXIT_CONFIG = 78;

  private static final String USAGE =
      "使い方: bsb version\n"
          + "        bsb check <ソース.bsb>\n"
          + "        bsb check --json <ソース.bsb>\n"
          + "        bsb explain --json <ソース.bsb>\n"
          + "        bsb run <ソース.bsb>\n"
          + "        bsb run --instruction-limit <命令数> <ソース.bsb>\n"
          + "        bsb run --file <作業領域名> <論理名> <read|write|read-write> <OSパス> [--file ...] <ソース.bsb>\n"
          + "        bsb run --workspaces <設定.toml> <ソース.bsb>\n"
          + "        bsb run --connections <設定.toml> <ソース.bsb>\n"
          + "        bsb run <ソース.bsb> -- [引数...]\n"
          + "        bsb run --connections <設定.toml> <ソース.bsb> -- [引数...]\n"
          + "        bsb format <ソース.bsb>\n";

  private final CliBackend backend;
  private final DiagnosticRenderer diagnosticRenderer;
  private final DiagnosticJsonMapper diagnosticJsonMapper;
  private final CliJsonRenderer checkJsonRenderer;
  private final ExplainJsonRenderer explainJsonRenderer;
  private final ProgramExplainer programExplainer;
  private final ExplainJsonMapper explainJsonMapper;
  private final ConnectionConfigLoader connectionConfigLoader;
  private final WorkspaceConfigLoader workspaceConfigLoader;

  /** 標準パイプラインと規範メッセージカタログを使うCLIを作ります。 */
  public BsbCli() {
    this(new DefaultCliBackend(), loadMessageCatalog());
  }

  BsbCli(CliBackend backend, DiagnosticMessageCatalog messages) {
    this(backend, messages, new CliJsonRenderer(), new ExplainJsonRenderer());
  }

  BsbCli(CliBackend backend, DiagnosticMessageCatalog messages, CliJsonRenderer jsonRenderer) {
    this(backend, messages, jsonRenderer, new ExplainJsonRenderer());
  }

  BsbCli(
      CliBackend backend,
      DiagnosticMessageCatalog messages,
      CliJsonRenderer checkJsonRenderer,
      ExplainJsonRenderer explainJsonRenderer) {
    this(
        backend,
        messages,
        checkJsonRenderer,
        explainJsonRenderer,
        ConnectionConfigLoader.systemEnvironment(),
        WorkspaceConfigLoader.systemFiles());
  }

  BsbCli(
      CliBackend backend,
      DiagnosticMessageCatalog messages,
      CliJsonRenderer checkJsonRenderer,
      ExplainJsonRenderer explainJsonRenderer,
      ConnectionConfigLoader connectionConfigLoader) {
    this(
        backend,
        messages,
        checkJsonRenderer,
        explainJsonRenderer,
        connectionConfigLoader,
        WorkspaceConfigLoader.systemFiles());
  }

  BsbCli(
      CliBackend backend,
      DiagnosticMessageCatalog messages,
      CliJsonRenderer checkJsonRenderer,
      ExplainJsonRenderer explainJsonRenderer,
      ConnectionConfigLoader connectionConfigLoader,
      WorkspaceConfigLoader workspaceConfigLoader) {
    this.backend = Objects.requireNonNull(backend, "backend");
    Objects.requireNonNull(messages, "messages");
    diagnosticRenderer = new DiagnosticRenderer(messages);
    diagnosticJsonMapper = new DiagnosticJsonMapper(messages);
    this.checkJsonRenderer = Objects.requireNonNull(checkJsonRenderer, "checkJsonRenderer");
    this.explainJsonRenderer = Objects.requireNonNull(explainJsonRenderer, "explainJsonRenderer");
    programExplainer = new ProgramExplainer();
    explainJsonMapper = new ExplainJsonMapper();
    this.connectionConfigLoader =
        Objects.requireNonNull(connectionConfigLoader, "connectionConfigLoader");
    this.workspaceConfigLoader =
        Objects.requireNonNull(workspaceConfigLoader, "workspaceConfigLoader");
  }

  /**
   * コマンドを実行し、プロセスへ返す終了コードを返します。
   *
   * @param arguments サブコマンドとソースパス
   * @param standardOutput プログラム出力または正規化ソースの出力先
   * @param standardError 診断、警告、CLIエラーの出力先
   * @return 0、2、3、8、9、10、70、78のいずれか
   */
  public int run(String[] arguments, OutputStream standardOutput, OutputStream standardError) {
    return run(arguments, InputStream.nullInputStream(), standardOutput, standardError);
  }

  /** stdinを差し替えてコマンドを実行します。 */
  public int run(
      String[] arguments,
      InputStream standardInput,
      OutputStream standardOutput,
      OutputStream standardError) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(standardInput, "standardInput");
    Objects.requireNonNull(standardOutput, "standardOutput");
    Objects.requireNonNull(standardError, "standardError");

    CliArgumentResult parsed = CliArgumentParser.parse(arguments);
    if (parsed.outputFormat().json()) {
      return runJson(parsed, standardOutput, standardError);
    }
    return runHuman(parsed, standardInput, standardOutput, standardError);
  }

  private int runHuman(
      CliArgumentResult parsed,
      InputStream standardInput,
      OutputStream standardOutput,
      OutputStream standardError) {
    try {
      int exitCode = dispatchHuman(parsed, standardInput, standardOutput, standardError);
      standardOutput.flush();
      standardError.flush();
      return exitCode;
    } catch (UncheckedIOException exception) {
      return reportIoError(standardError, parsed.source().orElse("<不明>"));
    } catch (IOException exception) {
      return reportIoError(standardError, parsed.source().orElse("<不明>"));
    } catch (RuntimeException exception) {
      writeBestEffort(standardError, "内部エラー: 処理系で予期しない問題が発生しました。\n");
      return EXIT_INTERNAL;
    }
  }

  private int runJson(
      CliArgumentResult parsed, OutputStream standardOutput, OutputStream standardError) {
    RenderedJson rendered;
    try {
      if (parsed instanceof CliUsageError error) {
        rendered =
            renderProblem(
                parsed.outputFormat(),
                error.source(),
                EXIT_USAGE,
                CliJsonProblemKind.USAGE,
                error.message());
      } else {
        CliInvocation invocation = (CliInvocation) parsed;
        rendered =
            switch (invocation.command()) {
              case CHECK -> renderCheck(invocation);
              case EXPLAIN -> renderExplain(invocation);
              case RUN, FORMAT ->
                  throw new IllegalStateException("human command reached JSON dispatch");
            };
      }
    } catch (UncheckedIOException | IOException exception) {
      rendered =
          renderProblem(
              parsed.outputFormat(),
              parsed.source(),
              EXIT_IO,
              CliJsonProblemKind.IO,
              "ソースファイルの読取りに失敗しました。");
    } catch (RuntimeException exception) {
      rendered =
          renderProblem(
              parsed.outputFormat(),
              java.util.Optional.empty(),
              EXIT_INTERNAL,
              CliJsonProblemKind.INTERNAL,
              "処理系で予期しない問題が発生しました。");
    }

    try {
      standardOutput.write(rendered.bytes());
      standardOutput.flush();
      return rendered.exitCode();
    } catch (IOException | RuntimeException exception) {
      return reportIoError(standardError, parsed.source().orElse("<不明>"));
    }
  }

  private RenderedJson renderCheck(CliInvocation invocation) throws IOException {
    var result = backend.check(invocation.sourcePath());
    var document =
        CliJsonDocument.analysis(
            invocation.sourceArgument(), result.exitCode(), diagnosticJsonMapper.map(result));
    var rendered = checkJsonRenderer.render(document);
    return new RenderedJson(rendered.bytes(), rendered.exitCode());
  }

  private RenderedJson renderExplain(CliInvocation invocation) throws IOException {
    var result = backend.explain(invocation.sourcePath());
    List<DiagnosticJson> diagnostics = diagnosticJsonMapper.map(result);
    ExplainJsonDocument document;
    if (result.successful()) {
      var source =
          result
              .source()
              .orElseThrow(() -> new IllegalStateException("source snapshot is missing"));
      var explanation = programExplainer.explain(result.programForIrGeneration());
      document =
          ExplainJsonDocument.success(
              invocation.sourceArgument(), diagnostics, explainJsonMapper.map(explanation, source));
    } else {
      document =
          ExplainJsonDocument.diagnostics(
              invocation.sourceArgument(), result.exitCode(), diagnostics);
    }
    var rendered = explainJsonRenderer.render(document);
    return new RenderedJson(rendered.bytes(), rendered.exitCode());
  }

  private RenderedJson renderProblem(
      CliOutputFormat outputFormat,
      java.util.Optional<String> source,
      int exitCode,
      CliJsonProblemKind kind,
      String message) {
    return switch (outputFormat) {
      case CHECK_JSON -> {
        var rendered =
            checkJsonRenderer.render(CliJsonDocument.problem(source, exitCode, kind, message));
        yield new RenderedJson(rendered.bytes(), rendered.exitCode());
      }
      case EXPLAIN_JSON -> {
        var rendered =
            explainJsonRenderer.render(
                ExplainJsonDocument.problem(source, exitCode, kind, message));
        yield new RenderedJson(rendered.bytes(), rendered.exitCode());
      }
      case HUMAN -> throw new IllegalStateException("human output reached JSON problem dispatch");
    };
  }

  private int dispatchHuman(
      CliArgumentResult parsed,
      InputStream standardInput,
      OutputStream standardOutput,
      OutputStream standardError)
      throws IOException {
    if (parsed instanceof CliUsageError error) {
      writeUtf8(standardError, "エラー: " + error.message() + '\n' + USAGE);
      return EXIT_USAGE;
    }
    if (parsed instanceof WorkspaceCliConfigError error) {
      return reportWorkspaceConfigError(standardError, Optional.empty(), error.message());
    }
    if (parsed instanceof CliVersionInvocation) {
      writeUtf8(
          standardOutput,
          "bsb " + BsbVersion.current() + " (commit " + BsbVersion.commit() + ")\n");
      return 0;
    }
    CliInvocation invocation = (CliInvocation) parsed;

    return switch (invocation.command()) {
      case CHECK -> check(invocation.sourcePath(), standardError);
      case EXPLAIN -> throw new IllegalStateException("explain requires JSON output");
      case RUN ->
          runProgram(
              invocation.sourcePath(),
              invocation.connectionConfigPath(),
              invocation.workspaceConfigPath(),
              invocation.directFiles(),
              invocation.programArguments(),
              invocation.instructionLimit(),
              standardInput,
              standardOutput,
              standardError);
      case FORMAT -> format(invocation.sourcePath(), standardOutput, standardError);
    };
  }

  private int check(Path sourcePath, OutputStream standardError) throws IOException {
    var result = backend.check(sourcePath);
    writeDiagnostics(standardError, result.diagnostics());
    return result.exitCode();
  }

  private int runProgram(
      Path sourcePath,
      Optional<Path> connectionConfigPath,
      Optional<Path> workspaceConfigPath,
      List<DirectFileOption> directFiles,
      List<String> programArguments,
      OptionalLong instructionLimit,
      InputStream standardInput,
      OutputStream standardOutput,
      OutputStream standardError)
      throws IOException {
    Optional<LoadedConnectionConfig> connections;
    try {
      connections =
          connectionConfigPath.isPresent()
              ? Optional.of(connectionConfigLoader.load(connectionConfigPath.orElseThrow()))
              : Optional.empty();
    } catch (ConnectionConfigException failure) {
      return reportConfigError(
          standardError, connectionConfigPath.orElseThrow().toString(), failure.getMessage());
    }
    Optional<LoadedWorkspaceConfig> workspaces;
    try {
      if (workspaceConfigPath.isPresent()) {
        workspaces = Optional.of(workspaceConfigLoader.loadToml(workspaceConfigPath.orElseThrow()));
      } else if (!directFiles.isEmpty()) {
        workspaces =
            Optional.of(
                workspaceConfigLoader.loadDirect(
                    directFiles, Path.of("").toAbsolutePath().normalize()));
      } else {
        workspaces = Optional.empty();
      }
    } catch (WorkspaceConfigException failure) {
      return reportWorkspaceConfigError(
          standardError, workspaceConfigPath.map(Path::toString), failure.getMessage());
    }
    ConsoleOutput consoleOutput =
        bytes -> {
          try {
            standardOutput.write(bytes);
          } catch (IOException exception) {
            throw CapabilityException.failure(RuntimeCapability.CONSOLE_OUTPUT, "write");
          }
        };
    int[] errorLastByte = {-1};
    ConsoleOutput consoleError =
        bytes -> {
          try {
            standardError.write(bytes);
            if (bytes.length > 0) {
              errorLastByte[0] = Byte.toUnsignedInt(bytes[bytes.length - 1]);
            }
          } catch (IOException exception) {
            throw CapabilityException.failure(RuntimeCapability.CONSOLE_ERROR, "write");
          }
        };
    MonotonicClock clock = MonotonicClock.system();
    Path absoluteSource = sourcePath.toAbsolutePath().normalize();
    String programName =
        sourcePath.getFileName() == null
            ? sourcePath.toString()
            : sourcePath.getFileName().toString();
    ExecutionEnvironment.Builder environmentBuilder =
        ExecutionEnvironment.builder(clock)
            .consoleInput(new StreamConsoleInput(standardInput))
            .consoleOutput(consoleOutput)
            .consoleError(consoleError)
            .programControl(ProgramControl.accepting())
            .processArguments(ProcessArguments.fixed(programArguments))
            .programIdentity(
                ProgramIdentity.fixed(
                    ProgramMetadata.located(programName, absoluteSource.toUri().toASCIIString())))
            .sleepCapability(SleepCapability.system())
            .monotonicTime(MonotonicTime.elapsed(clock))
            .wallTime(WallTime.systemUtc());
    connections.ifPresent(
        configured ->
            environmentBuilder
                .connectionResolver(configured.resolver())
                .httpTransport(configured.transport())
                .redactLogicalConnectionNamesInTrace());
    workspaces.ifPresent(
        configured ->
            environmentBuilder
                .workspaceResolver(configured.resolver())
                .fileReadCapability(configured.reader())
                .fileWriteCapability(configured.writer())
                .redactWorkspaceNamesInTrace());
    ExecutionEnvironment environment = environmentBuilder.build();
    OutputSink compatibilityOutput = bytes -> {};
    var context = new ExecutionContext(compatibilityOutput, clock, TraceSink.none(), environment);
    var limits =
        instructionLimit.isPresent()
            ? new ExecutionLimits(instructionLimit.getAsLong())
            : ExecutionLimits.defaults();
    var result = backend.run(sourcePath, context, limits);
    if (!result.diagnostics().isEmpty() && errorLastByte[0] >= 0 && errorLastByte[0] != '\n') {
      standardError.write('\n');
    }
    writeDiagnostics(standardError, result.diagnostics());
    return result.exitCode();
  }

  private int format(Path sourcePath, OutputStream standardOutput, OutputStream standardError)
      throws IOException {
    var result = backend.format(sourcePath);
    if (result.successful()) {
      writeUtf8(standardOutput, result.outputForStandardOutput());
      return 0;
    }
    writeDiagnostics(standardError, result.diagnostics());
    return 9;
  }

  private void writeDiagnostics(OutputStream standardError, List<Diagnostic> diagnostics)
      throws IOException {
    writeUtf8(standardError, diagnosticRenderer.render(diagnostics));
  }

  private static int reportIoError(OutputStream standardError, String sourcePath) {
    writeBestEffort(standardError, "I/Oエラー: 入出力に失敗しました（対象: " + sourcePath + "）\n");
    return EXIT_IO;
  }

  private static int reportConfigError(
      OutputStream standardError, String configPath, String message) {
    writeBestEffort(standardError, "接続設定エラー: " + message + "（対象: " + configPath + "）\n");
    return EXIT_CONFIG;
  }

  private static int reportWorkspaceConfigError(
      OutputStream standardError, Optional<String> configPath, String message) {
    String target = configPath.map(path -> "（対象: " + path + "）").orElse("");
    writeBestEffort(standardError, "作業領域設定エラー: " + message + target + "\n");
    return EXIT_CONFIG;
  }

  private static DiagnosticMessageCatalog loadMessageCatalog() {
    try {
      return DiagnosticMessageCatalog.loadDefault();
    } catch (IOException exception) {
      throw new IllegalStateException("failed to load diagnostic messages", exception);
    }
  }

  private static void writeUtf8(OutputStream output, String text) throws IOException {
    output.write(text.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeBestEffort(OutputStream output, String text) {
    try {
      writeUtf8(output, text);
      output.flush();
    } catch (IOException | RuntimeException ignored) {
      // 失敗している標準エラーへさらに診断を書く方法はないため、終了コードだけを返します。
    }
  }

  private record RenderedJson(byte[] bytes, int exitCode) {
    private RenderedJson {
      Objects.requireNonNull(bytes, "bytes");
    }
  }
}
