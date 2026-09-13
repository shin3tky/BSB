package jp.bsb.runtime;

/** プログラム指定終了を埋込みホストへ通知する能力です。 */
@FunctionalInterface
public interface ProgramControl {
  /**
   * 検証済みの指定終了コードを通知します。
   *
   * @param exitCode 0から255の終了コード
   * @throws CapabilityException 能力が通知を受理できない場合
   */
  void exit(int exitCode) throws CapabilityException;

  /**
   * @return 通知を受理するだけの標準能力
   */
  static ProgramControl accepting() {
    return ignored -> {};
  }
}
