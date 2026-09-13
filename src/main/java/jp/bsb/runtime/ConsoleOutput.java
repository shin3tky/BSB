package jp.bsb.runtime;

/** 完全なUTF-8バイト列を1回で受け取る出力能力です。 */
@FunctionalInterface
public interface ConsoleOutput {
  /**
   * 1回の組み込み語が生成した完全なバイト列を書きます。
   *
   * @param bytes 完全なUTF-8バイト列
   * @throws CapabilityException 能力が出力を完了できない場合
   */
  void write(byte[] bytes) throws CapabilityException;

  /**
   * 従来の失敗しない出力先を能力へ変換します。
   *
   * @param output 従来出力先
   * @return 標準出力能力
   */
  static ConsoleOutput fromOutputSink(OutputSink output) {
    return output::write;
  }
}
