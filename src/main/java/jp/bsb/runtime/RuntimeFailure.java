package jp.bsb.runtime;

import jp.bsb.diagnostics.Diagnostic;

/** Java例外処理を使って、深い補助処理から構造化実行時診断を実行ループへ戻します。 */
final class RuntimeFailure extends Exception {
  private final Diagnostic diagnostic;

  RuntimeFailure(Diagnostic diagnostic) {
    super(diagnostic.code().name(), null, false, false);
    this.diagnostic = diagnostic;
  }

  Diagnostic diagnostic() {
    return diagnostic;
  }
}
