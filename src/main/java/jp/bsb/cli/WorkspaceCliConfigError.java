package jp.bsb.cli;

import java.util.Objects;
import java.util.Optional;

/** ソース実行前に確定した、stdoutへ何も出さない作業領域設定エラーです。 */
record WorkspaceCliConfigError(Optional<String> source, String message)
    implements CliArgumentResult {
  WorkspaceCliConfigError {
    source = Objects.requireNonNull(source, "source");
    Objects.requireNonNull(message, "message");
  }

  @Override
  public CliOutputFormat outputFormat() {
    return CliOutputFormat.HUMAN;
  }
}
