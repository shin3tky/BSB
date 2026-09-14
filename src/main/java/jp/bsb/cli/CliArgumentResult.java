package jp.bsb.cli;

import java.util.Optional;

/** 引数解析の成功または使用法エラーを、認識済み出力形式とともに保持します。 */
sealed interface CliArgumentResult
    permits CliInvocation, CliUsageError, CliVersionInvocation, WorkspaceCliConfigError {
  /** 引数列から認識した出力形式です。 */
  CliOutputFormat outputFormat();

  /** 構文上有効と確定できた利用者指定ソース文字列です。 */
  Optional<String> source();
}
