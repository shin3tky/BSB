package jp.bsb.cli;

import java.util.Objects;
import jp.bsb.runtime.FileReadCapability;
import jp.bsb.runtime.FileWriteCapability;
import jp.bsb.runtime.WorkspaceResolver;

/** directまたはTOMLから同じ不変定義へ構築した3個の作業領域能力です。 */
record LoadedWorkspaceConfig(
    WorkspaceResolver resolver, FileReadCapability reader, FileWriteCapability writer) {
  LoadedWorkspaceConfig {
    Objects.requireNonNull(resolver, "resolver");
    Objects.requireNonNull(reader, "reader");
    Objects.requireNonNull(writer, "writer");
  }

  @Override
  public String toString() {
    return "<loaded-workspace-config>";
  }
}
