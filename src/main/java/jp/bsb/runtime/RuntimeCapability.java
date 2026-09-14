package jp.bsb.runtime;

/** 実行環境が個別に提供できる外部能力です。 */
public enum RuntimeCapability {
  /** 一行入力です。 */
  CONSOLE_INPUT("console.input"),
  /** プログラム標準出力です。 */
  CONSOLE_OUTPUT("console.output"),
  /** プログラム標準エラーです。 */
  CONSOLE_ERROR("console.error"),
  /** プログラム指定終了です。 */
  PROCESS_EXIT("process.exit"),
  /** 起動引数です。 */
  PROCESS_ARGUMENTS("process.arguments"),
  /** プログラム識別情報です。 */
  PROGRAM_IDENTITY("program.identity"),
  /** 待機です。 */
  TIME_SLEEP("time.sleep"),
  /** 利用者向け単調時計です。 */
  TIME_MONOTONIC("time.monotonic"),
  /** 現在日時です。 */
  TIME_WALL("time.wall"),
  /** 論理接続定義の解決です。 */
  CONNECTION_RESOLVE("connection.resolve"),
  /** 検証済みHTTPS要求の同期送信です。 */
  HTTP_SEND("http.send"),
  /** 使い切ったHTTP送信の閉じた記録です。 */
  HTTP_FINAL_FAILURE("http.final-failure"),
  /** 作業領域定義の解決です。 */
  WORKSPACE_RESOLVE("workspace.resolve"),
  /** 論理ファイルの全内容読取です。 */
  FILE_READ("file.read"),
  /** 論理ファイルの原子的全内容書込です。 */
  FILE_WRITE("file.write");

  private final String sourceName;

  RuntimeCapability(String sourceName) {
    this.sourceName = sourceName;
  }

  /**
   * 仕様上の能力名を返します。
   *
   * @return ASCIIの能力名
   */
  public String sourceName() {
    return sourceName;
  }

  @Override
  public String toString() {
    return sourceName;
  }
}
