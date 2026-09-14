package jp.bsb.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 実行器からOSと埋込みホストを隔離する、型付き能力の集合です。 */
public final class ExecutionEnvironment {
  private final Optional<ConsoleInput> consoleInput;
  private final Optional<ConsoleOutput> consoleOutput;
  private final Optional<ConsoleOutput> consoleError;
  private final Optional<ProgramControl> programControl;
  private final Optional<ProcessArguments> processArguments;
  private final Optional<ProgramIdentity> programIdentity;
  private final Optional<SleepCapability> sleepCapability;
  private final Optional<MonotonicTime> monotonicTime;
  private final Optional<WallTime> wallTime;
  private final Optional<ConnectionResolver> connectionResolver;
  private final Optional<HttpTransport> httpTransport;
  private final Optional<HttpFinalFailureSink> httpFinalFailureSink;
  private final Optional<WorkspaceResolver> workspaceResolver;
  private final Optional<FileReadCapability> fileReadCapability;
  private final Optional<FileWriteCapability> fileWriteCapability;
  private final boolean redactLogicalConnectionNamesInTrace;
  private final boolean redactWorkspaceNamesInTrace;
  private final MonotonicClock resourceClock;
  private final Set<RuntimeCapability> capabilities;

  private ExecutionEnvironment(Builder builder) {
    consoleInput = Optional.ofNullable(builder.consoleInput);
    consoleOutput = Optional.ofNullable(builder.consoleOutput);
    consoleError = Optional.ofNullable(builder.consoleError);
    programControl = Optional.ofNullable(builder.programControl);
    processArguments = Optional.ofNullable(builder.processArguments);
    programIdentity = Optional.ofNullable(builder.programIdentity);
    sleepCapability = Optional.ofNullable(builder.sleepCapability);
    monotonicTime = Optional.ofNullable(builder.monotonicTime);
    wallTime = Optional.ofNullable(builder.wallTime);
    connectionResolver = Optional.ofNullable(builder.connectionResolver);
    httpTransport = Optional.ofNullable(builder.httpTransport);
    httpFinalFailureSink = Optional.ofNullable(builder.httpFinalFailureSink);
    workspaceResolver = Optional.ofNullable(builder.workspaceResolver);
    fileReadCapability = Optional.ofNullable(builder.fileReadCapability);
    fileWriteCapability = Optional.ofNullable(builder.fileWriteCapability);
    redactLogicalConnectionNamesInTrace = builder.redactLogicalConnectionNamesInTrace;
    redactWorkspaceNamesInTrace = builder.redactWorkspaceNamesInTrace;
    resourceClock = Objects.requireNonNull(builder.resourceClock, "resourceClock");
    var present = EnumSet.noneOf(RuntimeCapability.class);
    consoleInput.ifPresent(ignored -> present.add(RuntimeCapability.CONSOLE_INPUT));
    consoleOutput.ifPresent(ignored -> present.add(RuntimeCapability.CONSOLE_OUTPUT));
    consoleError.ifPresent(ignored -> present.add(RuntimeCapability.CONSOLE_ERROR));
    programControl.ifPresent(ignored -> present.add(RuntimeCapability.PROCESS_EXIT));
    processArguments.ifPresent(ignored -> present.add(RuntimeCapability.PROCESS_ARGUMENTS));
    programIdentity.ifPresent(ignored -> present.add(RuntimeCapability.PROGRAM_IDENTITY));
    sleepCapability.ifPresent(ignored -> present.add(RuntimeCapability.TIME_SLEEP));
    monotonicTime.ifPresent(ignored -> present.add(RuntimeCapability.TIME_MONOTONIC));
    wallTime.ifPresent(ignored -> present.add(RuntimeCapability.TIME_WALL));
    connectionResolver.ifPresent(ignored -> present.add(RuntimeCapability.CONNECTION_RESOLVE));
    httpTransport.ifPresent(ignored -> present.add(RuntimeCapability.HTTP_SEND));
    httpFinalFailureSink.ifPresent(ignored -> present.add(RuntimeCapability.HTTP_FINAL_FAILURE));
    workspaceResolver.ifPresent(ignored -> present.add(RuntimeCapability.WORKSPACE_RESOLVE));
    fileReadCapability.ifPresent(ignored -> present.add(RuntimeCapability.FILE_READ));
    fileWriteCapability.ifPresent(ignored -> present.add(RuntimeCapability.FILE_WRITE));
    capabilities = Collections.unmodifiableSet(present);
  }

  /**
   * 能力集合を構築します。
   *
   * @param resourceClock 能動実行時間の計測時計
   * @return 新しいビルダー
   */
  public static Builder builder(MonotonicClock resourceClock) {
    return new Builder(resourceClock);
  }

  /** 従来の実行コンテキストと同じ能力を持つ環境を作ります。 */
  public static ExecutionEnvironment legacy(OutputSink output, MonotonicClock clock) {
    return builder(clock)
        .consoleOutput(ConsoleOutput.fromOutputSink(output))
        .programControl(ProgramControl.accepting())
        .build();
  }

  /**
   * @return 一行入力能力
   */
  public Optional<ConsoleInput> consoleInput() {
    return consoleInput;
  }

  /**
   * @return プログラム標準出力能力
   */
  public Optional<ConsoleOutput> consoleOutput() {
    return consoleOutput;
  }

  /**
   * @return プログラム標準エラー能力
   */
  public Optional<ConsoleOutput> consoleError() {
    return consoleError;
  }

  /**
   * @return プログラム指定終了能力
   */
  public Optional<ProgramControl> programControl() {
    return programControl;
  }

  /**
   * @return 起動引数能力
   */
  public Optional<ProcessArguments> processArguments() {
    return processArguments;
  }

  /**
   * @return プログラム識別情報能力
   */
  public Optional<ProgramIdentity> programIdentity() {
    return programIdentity;
  }

  /**
   * @return 待機能力
   */
  public Optional<SleepCapability> sleepCapability() {
    return sleepCapability;
  }

  /**
   * @return 公開単調時計能力
   */
  public Optional<MonotonicTime> monotonicTime() {
    return monotonicTime;
  }

  /**
   * @return 現在日時能力
   */
  public Optional<WallTime> wallTime() {
    return wallTime;
  }

  /**
   * @return 論理接続解決能力
   */
  public Optional<ConnectionResolver> connectionResolver() {
    return connectionResolver;
  }

  /**
   * @return HTTP同期送信能力
   */
  public Optional<HttpTransport> httpTransport() {
    return httpTransport;
  }

  /**
   * @return HTTP最終失敗記録能力
   */
  public Optional<HttpFinalFailureSink> httpFinalFailureSink() {
    return httpFinalFailureSink;
  }

  /**
   * @return 作業領域解決能力
   */
  public Optional<WorkspaceResolver> workspaceResolver() {
    return workspaceResolver;
  }

  /**
   * @return ファイル全内容読取能力
   */
  public Optional<FileReadCapability> fileReadCapability() {
    return fileReadCapability;
  }

  /**
   * @return ファイル原子的全内容書込能力
   */
  public Optional<FileWriteCapability> fileWriteCapability() {
    return fileWriteCapability;
  }

  /**
   * @return 能力イベントで論理接続名を伏せる場合はtrue
   */
  public boolean redactLogicalConnectionNamesInTrace() {
    return redactLogicalConnectionNamesInTrace;
  }

  /**
   * @return 能力イベントで作業領域名を伏せる場合はtrue
   */
  public boolean redactWorkspaceNamesInTrace() {
    return redactWorkspaceNamesInTrace;
  }

  /**
   * @return 能動実行時間の互換時計
   */
  public MonotonicClock resourceClock() {
    return resourceClock;
  }

  /**
   * @return 仕様順の不変能力集合
   */
  public Set<RuntimeCapability> capabilities() {
    return capabilities;
  }

  /** 実行環境のビルダーです。 */
  public static final class Builder {
    private final MonotonicClock resourceClock;
    private ConsoleInput consoleInput;
    private ConsoleOutput consoleOutput;
    private ConsoleOutput consoleError;
    private ProgramControl programControl;
    private ProcessArguments processArguments;
    private ProgramIdentity programIdentity;
    private SleepCapability sleepCapability;
    private MonotonicTime monotonicTime;
    private WallTime wallTime;
    private ConnectionResolver connectionResolver;
    private HttpTransport httpTransport;
    private HttpFinalFailureSink httpFinalFailureSink;
    private WorkspaceResolver workspaceResolver;
    private FileReadCapability fileReadCapability;
    private FileWriteCapability fileWriteCapability;
    private boolean redactLogicalConnectionNamesInTrace;
    private boolean redactWorkspaceNamesInTrace;

    private Builder(MonotonicClock resourceClock) {
      this.resourceClock = Objects.requireNonNull(resourceClock, "resourceClock");
    }

    /**
     * @return 入力能力を追加したこのビルダー
     */
    public Builder consoleInput(ConsoleInput value) {
      consoleInput = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 標準出力能力を追加したこのビルダー
     */
    public Builder consoleOutput(ConsoleOutput value) {
      consoleOutput = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 標準エラー能力を追加したこのビルダー
     */
    public Builder consoleError(ConsoleOutput value) {
      consoleError = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 指定終了能力を追加したこのビルダー
     */
    public Builder programControl(ProgramControl value) {
      programControl = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 起動引数能力を追加したこのビルダー
     */
    public Builder processArguments(ProcessArguments value) {
      processArguments = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return プログラム識別情報能力を追加したこのビルダー
     */
    public Builder programIdentity(ProgramIdentity value) {
      programIdentity = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 待機能力を追加したこのビルダー
     */
    public Builder sleepCapability(SleepCapability value) {
      sleepCapability = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 公開単調時計能力を追加したこのビルダー
     */
    public Builder monotonicTime(MonotonicTime value) {
      monotonicTime = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 現在日時能力を追加したこのビルダー
     */
    public Builder wallTime(WallTime value) {
      wallTime = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 論理接続解決能力を追加したこのビルダー
     */
    public Builder connectionResolver(ConnectionResolver value) {
      connectionResolver = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return HTTP同期送信能力を追加したこのビルダー
     */
    public Builder httpTransport(HttpTransport value) {
      httpTransport = Objects.requireNonNull(value, "value");
      return this;
    }

    /** HTTP最終失敗記録能力を追加します。 */
    public Builder httpFinalFailureSink(HttpFinalFailureSink value) {
      httpFinalFailureSink = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return 作業領域解決能力を追加したこのビルダー
     */
    public Builder workspaceResolver(WorkspaceResolver value) {
      workspaceResolver = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return ファイル読取能力を追加したこのビルダー
     */
    public Builder fileReadCapability(FileReadCapability value) {
      fileReadCapability = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * @return ファイル書込能力を追加したこのビルダー
     */
    public Builder fileWriteCapability(FileWriteCapability value) {
      fileWriteCapability = Objects.requireNonNull(value, "value");
      return this;
    }

    /**
     * 能力イベント中の論理接続名を伏せます。
     *
     * @return このビルダー
     */
    public Builder redactLogicalConnectionNamesInTrace() {
      redactLogicalConnectionNamesInTrace = true;
      return this;
    }

    /** 作業領域能力イベント中の静的作業領域名を伏せます。 */
    public Builder redactWorkspaceNamesInTrace() {
      redactWorkspaceNamesInTrace = true;
      return this;
    }

    /**
     * @return 構築した実行環境
     */
    public ExecutionEnvironment build() {
      return new ExecutionEnvironment(this);
    }
  }
}
