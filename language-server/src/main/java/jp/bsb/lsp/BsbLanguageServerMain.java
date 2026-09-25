package jp.bsb.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;

/** 標準入出力上でBSB Language Serverを起動します。 */
public final class BsbLanguageServerMain {
  private BsbLanguageServerMain() {}

  /**
   * Language Serverを起動し、クライアントが接続を閉じるまで待機します。
   *
   * @param args 現在は使用しないコマンドライン引数
   * @throws Exception JSON-RPCの待機処理が失敗した場合
   */
  public static void main(String[] args) throws Exception {
    var server = new BsbLanguageServer();
    var launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
    server.connect(launcher.getRemoteProxy());
    launcher.startListening().get();
  }
}
