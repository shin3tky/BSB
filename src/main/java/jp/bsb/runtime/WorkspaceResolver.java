package jp.bsb.runtime;

/** 静的作業領域名を操作ごとに不透明参照と方針へ解決する能力です。 */
@FunctionalInterface
public interface WorkspaceResolver {
  /** 1回だけ解決し、閉じた応答または能力失敗を返します。 */
  WorkspaceResolution resolve(String workspaceName, String operation) throws CapabilityException;
}
