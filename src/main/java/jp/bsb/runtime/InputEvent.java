package jp.bsb.runtime;

import java.util.Arrays;

/** 一行入力能力が1回の委譲で返す、生バイトの完全な入力イベントです。 */
public sealed interface InputEvent permits InputEvent.Line, InputEvent.End, InputEvent.Cancel {
  /** 行終端を除く生UTF-8候補バイトです。 */
  final class Line implements InputEvent {
    private final byte[] bytes;
    private final int consumedBytes;

    /**
     * 行イベントを作ります。
     *
     * @param bytes 行終端を除くバイト列
     * @param consumedBytes 行終端を含め能力が消費した生バイト数
     */
    public Line(byte[] bytes, int consumedBytes) {
      this(bytes, consumedBytes, false);
    }

    private Line(byte[] bytes, int consumedBytes, boolean takeOwnership) {
      this.bytes = takeOwnership ? bytes : Arrays.copyOf(bytes, bytes.length);
      if (consumedBytes < bytes.length) {
        throw new IllegalArgumentException("consumedBytes must include all line bytes");
      }
      this.consumedBytes = consumedBytes;
    }

    static Line owned(byte[] bytes, int consumedBytes) {
      return new Line(bytes, consumedBytes, true);
    }

    /**
     * @return 防御的複製した行バイト
     */
    public byte[] bytes() {
      return Arrays.copyOf(bytes, bytes.length);
    }

    byte[] ownedBytes() {
      return bytes;
    }

    /**
     * @return 行終端を含む消費生バイト数
     */
    public int consumedBytes() {
      return consumedBytes;
    }
  }

  /** 入力終端です。 */
  record End() implements InputEvent {}

  /** 入力取消です。 */
  record Cancel() implements InputEvent {}
}
