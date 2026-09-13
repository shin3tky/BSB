package jp.bsb.runtime;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** テストや埋込み利用で出力をメモリへ保存する出力先です。 */
public final class MemoryOutputSink implements OutputSink {
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  /** 空のメモリ出力先を作ります。 */
  public MemoryOutputSink() {}

  @Override
  public void write(byte[] value) {
    bytes.writeBytes(value);
  }

  /**
   * 現在までの出力をコピーして返します。
   *
   * @return 出力済みバイト列
   */
  public byte[] bytes() {
    return bytes.toByteArray();
  }

  /**
   * 現在までの出力をUTF-8文字列として返します。
   *
   * @return 出力済みUTF-8文字列
   */
  public String utf8Text() {
    return bytes.toString(StandardCharsets.UTF_8);
  }
}
