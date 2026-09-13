package jp.bsb.runtime;

import java.util.OptionalInt;

/** 埋込みAPIが通常完了、指定終了、診断失敗を区別する終了値です。 */
public record ExecutionTermination(Kind kind, OptionalInt programExitCode) {
  /** 実行終了の原因です。 */
  public enum Kind {
    /** メインの通常出口へ到達しました。 */
    COMPLETED,
    /** プログラムが0から255のコードを指定して終了しました。 */
    PROGRAM_EXIT,
    /** 実行時診断により失敗しました。 */
    DIAGNOSTIC_FAILURE
  }

  /** 終了値の整合性を検証します。 */
  public ExecutionTermination {
    if (kind == null || programExitCode == null) {
      throw new NullPointerException("termination fields");
    }
    if (kind == Kind.PROGRAM_EXIT) {
      if (programExitCode.isEmpty()
          || programExitCode.getAsInt() < 0
          || programExitCode.getAsInt() > 255) {
        throw new IllegalArgumentException("program exit code must be between 0 and 255");
      }
    } else if (programExitCode.isPresent()) {
      throw new IllegalArgumentException("only programExit may carry an exit code");
    }
  }

  /**
   * @return 通常完了
   */
  public static ExecutionTermination completed() {
    return new ExecutionTermination(Kind.COMPLETED, OptionalInt.empty());
  }

  /**
   * @return 指定終了
   */
  public static ExecutionTermination programExit(int exitCode) {
    return new ExecutionTermination(Kind.PROGRAM_EXIT, OptionalInt.of(exitCode));
  }

  /**
   * @return 診断失敗
   */
  public static ExecutionTermination diagnosticFailure() {
    return new ExecutionTermination(Kind.DIAGNOSTIC_FAILURE, OptionalInt.empty());
  }
}
