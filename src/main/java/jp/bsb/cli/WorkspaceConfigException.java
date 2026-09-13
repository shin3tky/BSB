package jp.bsb.cli;

/** OS path、TOML本文、論理名を保持しない作業領域設定失敗です。 */
final class WorkspaceConfigException extends Exception {
  WorkspaceConfigException(String message) {
    super(message, null, false, false);
  }
}
