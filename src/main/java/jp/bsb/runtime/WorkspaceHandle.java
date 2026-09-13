package jp.bsb.runtime;

/** 埋込みホストだけが対応関係を知る、不透明な作業領域参照です。 */
public final class WorkspaceHandle {
  private WorkspaceHandle() {}

  /** 新しい不透明参照を作ります。参照同一性でだけ比較されます。 */
  public static WorkspaceHandle opaque() {
    return new WorkspaceHandle();
  }

  /** ホスト側の参照内容を公開しない安全表現です。 */
  @Override
  public String toString() {
    return "<workspace-handle>";
  }
}
