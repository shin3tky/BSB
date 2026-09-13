package jp.bsb.diagnostics;

import java.io.IOException;

final class DiagnosticTestSupport {
  private DiagnosticTestSupport() {}

  static DiagnosticMessageCatalog loadMessages() throws IOException {
    return DiagnosticMessageCatalog.loadDefault();
  }
}
