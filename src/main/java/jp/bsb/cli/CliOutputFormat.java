package jp.bsb.cli;

/** CLIが処理結果を人間向けまたは機械可読形式のどちらで返すかを表します。 */
enum CliOutputFormat {
  HUMAN,
  CHECK_JSON,
  EXPLAIN_JSON;

  boolean json() {
    return this != HUMAN;
  }
}
