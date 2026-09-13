package jp.bsb.runtime;

/** 論理接続名をホスト定義へ解決する、差し替え可能な能力です。 */
@FunctionalInterface
public interface ConnectionResolver {
  /**
   * 指定された論理接続操作を1回解決します。
   *
   * @throws CapabilityException 能力が呼出しを完了できない場合
   */
  ConnectionResolution resolve(String connectionName, String operation) throws CapabilityException;
}
