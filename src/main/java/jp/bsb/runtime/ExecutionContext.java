package jp.bsb.runtime;

import java.util.Objects;

/**
 * 実行器の外部依存をまとめた注入可能な実行環境です。
 *
 * @param output UTF-8バイト出力先
 * @param clock 単調増加時計
 * @param trace 命令トレース出力先
 * @param environment 型付き能力を保持する実行環境
 */
public record ExecutionContext(
    OutputSink output, MonotonicClock clock, TraceSink trace, ExecutionEnvironment environment) {
  /** 従来APIを型付き実行環境へ適応します。 */
  public ExecutionContext(OutputSink output, MonotonicClock clock, TraceSink trace) {
    this(output, clock, trace, ExecutionEnvironment.legacy(output, clock));
  }

  /** 依存がすべて存在することを検証します。 */
  public ExecutionContext {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(trace, "trace");
    Objects.requireNonNull(environment, "environment");
  }

  /**
   * 指定出力、実時計、トレースなしの通常実行環境を作ります。
   *
   * @param output UTF-8バイト出力先
   * @return 標準的な実行環境
   */
  public static ExecutionContext standard(OutputSink output) {
    return new ExecutionContext(output, MonotonicClock.system(), TraceSink.none());
  }
}
