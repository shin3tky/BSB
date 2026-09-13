package jp.bsb.cli;

import java.util.Objects;
import java.util.Optional;

/** 引数検証に失敗しても、JSONモード認識と有効なソース候補を保持する結果です。 */
record CliUsageError(CliOutputFormat outputFormat, Optional<String> source, String message)
    implements CliArgumentResult {
  CliUsageError {
    Objects.requireNonNull(outputFormat, "outputFormat");
    source = Objects.requireNonNull(source, "source");
    Objects.requireNonNull(message, "message");
  }
}
