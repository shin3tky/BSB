package jp.bsb.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/** 最小サーバーでは状態を持たないワークスペース通知の受け口です。 */
final class BsbWorkspaceService implements WorkspaceService {
  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {}

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}
