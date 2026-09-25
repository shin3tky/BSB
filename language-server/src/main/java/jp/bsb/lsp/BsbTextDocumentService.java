package jp.bsb.lsp;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

/** 開いているBSB文書を保持し、全文変更ごとに静的診断を配信します。 */
final class BsbTextDocumentService implements TextDocumentService {
  private final Map<String, DocumentSnapshot> documents = new ConcurrentHashMap<>();
  private final SourceChecker checker = new SourceChecker();
  private final LspDiagnosticMapper diagnosticMapper;
  private volatile LanguageClient client;

  BsbTextDocumentService(DiagnosticMessageCatalog messages) {
    diagnosticMapper = new LspDiagnosticMapper(messages);
  }

  void connect(LanguageClient value) {
    client = Objects.requireNonNull(value, "client");
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var document = params.getTextDocument();
    var snapshot = new DocumentSnapshot(document.getVersion(), document.getText());
    documents.put(document.getUri(), snapshot);
    analyze(document.getUri(), snapshot);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    if (params.getContentChanges().isEmpty()) {
      return;
    }
    String uri = params.getTextDocument().getUri();
    String text = params.getContentChanges().getLast().getText();
    var snapshot = new DocumentSnapshot(params.getTextDocument().getVersion(), text);
    documents.compute(
        uri,
        (ignored, current) ->
            current == null || snapshot.version() > current.version() ? snapshot : current);
    if (!snapshot.equals(documents.get(uri))) {
      return;
    }
    analyze(uri, snapshot);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    String uri = params.getTextDocument().getUri();
    documents.remove(uri);
    publish(new PublishDiagnosticsParams(uri, java.util.List.of()));
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {}

  private void analyze(String uri, DocumentSnapshot snapshot) {
    var result = checker.check(uri, snapshot.text().getBytes(StandardCharsets.UTF_8));
    if (!snapshot.equals(documents.get(uri))) {
      return;
    }
    var diagnostics = new PublishDiagnosticsParams(uri, diagnosticMapper.map(result, uri));
    diagnostics.setVersion(snapshot.version());
    publish(diagnostics);
  }

  private void publish(PublishDiagnosticsParams diagnostics) {
    LanguageClient connected = client;
    if (connected != null) {
      connected.publishDiagnostics(diagnostics);
    }
  }

  private record DocumentSnapshot(int version, String text) {
    private DocumentSnapshot {
      Objects.requireNonNull(text, "text");
    }
  }
}
