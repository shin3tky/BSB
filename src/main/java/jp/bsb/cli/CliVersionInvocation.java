package jp.bsb.cli;

import java.util.Optional;

/** 引数を取らない、人間向けの版表示呼出しです。 */
record CliVersionInvocation() implements CliArgumentResult {
  @Override
  public CliOutputFormat outputFormat() {
    return CliOutputFormat.HUMAN;
  }

  @Override
  public Optional<String> source() {
    return Optional.empty();
  }
}
