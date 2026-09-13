package jp.bsb.runtime;

/** 完全な一行、終端、取消のいずれかを返す入力能力です。 */
@FunctionalInterface
public interface ConsoleInput {
  /**
   * 次の入力イベントを1個返します。
   *
   * @return 完全な入力イベント
   * @throws CapabilityException 能力が入力を完了できない場合
   */
  InputEvent readLine() throws CapabilityException;
}
