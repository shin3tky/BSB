package jp.bsb.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

class BsbLanguageServerTest {
  private static final String URI = "file:///workspace/位置.bsb";
  private static final String INVALID_SOURCE = "メインとは （ -- ）\n    「😀」 未定義\nこと。\n";
  private static final String VALID_SOURCE = "メインとは （ -- ）\nこと。\n";

  @Test
  void advertisesFullUtf16DocumentSynchronization() throws Exception {
    var server = new BsbLanguageServer();

    var result = server.initialize(new InitializeParams()).get();
    var capabilities = result.getCapabilities();

    assertEquals("utf-16", capabilities.getPositionEncoding());
    assertTrue(capabilities.getTextDocumentSync().isRight());
    assertTrue(capabilities.getTextDocumentSync().getRight().getOpenClose());
    assertEquals(
        TextDocumentSyncKind.Full, capabilities.getTextDocumentSync().getRight().getChange());
    assertEquals("BSB Language Server", result.getServerInfo().getName());
  }

  @Test
  void publishesDiagnosticsForOpenAndFullChangesAndClearsThemOnClose() throws Exception {
    var server = new BsbLanguageServer();
    var client = new RecordingClient();
    server.connect(client);
    var documents = server.getTextDocumentService();

    documents.didOpen(
        new DidOpenTextDocumentParams(new TextDocumentItem(URI, "bsb", 1, INVALID_SOURCE)));

    PublishDiagnosticsParams opened = client.lastDiagnostics();
    assertEquals(URI, opened.getUri());
    assertEquals(1, opened.getVersion());
    assertEquals(1, opened.getDiagnostics().size());
    var diagnostic = opened.getDiagnostics().getFirst();
    assertEquals("E_UNDEFINED_WORD", diagnostic.getCode().getLeft());
    assertEquals("「未定義」は定義されていない単語です。", diagnostic.getMessage());
    assertEquals(1, diagnostic.getRange().getStart().getLine());
    assertEquals(9, diagnostic.getRange().getStart().getCharacter());
    assertEquals(12, diagnostic.getRange().getEnd().getCharacter());

    documents.didChange(change(2, VALID_SOURCE));
    PublishDiagnosticsParams changed = client.lastDiagnostics();
    assertEquals(2, changed.getVersion());
    assertTrue(changed.getDiagnostics().isEmpty());

    documents.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(URI)));
    PublishDiagnosticsParams closed = client.lastDiagnostics();
    assertTrue(closed.getDiagnostics().isEmpty());
  }

  @Test
  void ignoresAnOlderDocumentVersion() throws Exception {
    var server = new BsbLanguageServer();
    var client = new RecordingClient();
    server.connect(client);
    var documents = server.getTextDocumentService();
    documents.didOpen(
        new DidOpenTextDocumentParams(new TextDocumentItem(URI, "bsb", 2, VALID_SOURCE)));

    documents.didChange(change(1, INVALID_SOURCE));

    assertEquals(1, client.diagnostics.size());
    assertEquals(2, client.lastDiagnostics().getVersion());
    assertTrue(client.lastDiagnostics().getDiagnostics().isEmpty());
  }

  @Test
  void exitsWithTheProtocolStatus() throws Exception {
    var exitStatus = new AtomicInteger(-1);
    var server = new BsbLanguageServer(DiagnosticMessageCatalog.loadDefault(), exitStatus::set);

    server.exit();
    assertEquals(1, exitStatus.get());

    server.shutdown().get();
    server.exit();
    assertEquals(0, exitStatus.get());
  }

  private static DidChangeTextDocumentParams change(int version, String text) {
    return new DidChangeTextDocumentParams(
        new VersionedTextDocumentIdentifier(URI, version),
        List.of(new TextDocumentContentChangeEvent(text)));
  }

  private static final class RecordingClient implements LanguageClient {
    private final List<PublishDiagnosticsParams> diagnostics = new ArrayList<>();

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
      diagnostics.add(params);
    }

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public java.util.concurrent.CompletableFuture<MessageActionItem> showMessageRequest(
        ShowMessageRequestParams requestParams) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}

    private PublishDiagnosticsParams lastDiagnostics() {
      return diagnostics.getLast();
    }
  }
}
