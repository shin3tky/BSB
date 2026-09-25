package jp.bsb.lsp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/** BSBの解析器をLanguage Server Protocolへ公開する最小サーバーです。 */
public final class BsbLanguageServer implements LanguageServer, LanguageClientAware {
  private final BsbTextDocumentService textDocuments;
  private final BsbWorkspaceService workspace = new BsbWorkspaceService();
  private final IntConsumer exitHandler;
  private volatile boolean shutdownRequested;

  /** 標準メッセージカタログを読み込み、終了通知でプロセスを終了するサーバーを作ります。 */
  public BsbLanguageServer() {
    this(loadMessages(), System::exit);
  }

  BsbLanguageServer(DiagnosticMessageCatalog messages, IntConsumer exitHandler) {
    textDocuments = new BsbTextDocumentService(messages);
    this.exitHandler = exitHandler;
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    var synchronization = new TextDocumentSyncOptions();
    synchronization.setOpenClose(true);
    synchronization.setChange(TextDocumentSyncKind.Full);

    var capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(synchronization);
    capabilities.setPositionEncoding("utf-16");

    var result = new InitializeResult(capabilities);
    result.setServerInfo(new ServerInfo("BSB Language Server"));
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    shutdownRequested = true;
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void exit() {
    exitHandler.accept(shutdownRequested ? 0 : 1);
  }

  @Override
  @JsonDelegate
  public TextDocumentService getTextDocumentService() {
    return textDocuments;
  }

  @Override
  @JsonDelegate
  public WorkspaceService getWorkspaceService() {
    return workspace;
  }

  @Override
  public void connect(LanguageClient client) {
    textDocuments.connect(client);
  }

  private static DiagnosticMessageCatalog loadMessages() {
    try {
      return DiagnosticMessageCatalog.loadDefault();
    } catch (IOException exception) {
      throw new IllegalStateException("failed to load diagnostic messages", exception);
    }
  }
}
