package jp.bsb.runtime;

/** 実行器がUTF-8バイト列を渡す、差し替え可能な標準出力です。 */
@FunctionalInterface
public interface OutputSink {
  /**
   * 1回の組み込み語が生成した完全なバイト列を書きます。
   *
   * @param bytes 完全なUTF-8バイト列
   */
  void write(byte[] bytes);
}
