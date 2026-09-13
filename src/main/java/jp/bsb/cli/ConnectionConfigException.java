package jp.bsb.cli;

/** 秘密値やTOML本文を含まない、公開CLI接続設定の検証失敗です。 */
final class ConnectionConfigException extends Exception {
  ConnectionConfigException(String message) {
    super(message, null, false, false);
  }
}
