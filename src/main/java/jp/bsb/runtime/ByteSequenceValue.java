package jp.bsb.runtime;

import java.util.Arrays;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/** 任意の0〜255列を、変更不能な論理値として保持します。 */
public final class ByteSequenceValue implements RuntimeValue {
  private static final ByteSequenceValue EMPTY = new ByteSequenceValue(new byte[0], 0, 0);

  private final byte[] storage;
  private final int offset;
  private final int length;

  private ByteSequenceValue(byte[] storage, int offset, int length) {
    this.storage = Objects.requireNonNull(storage, "storage");
    Objects.checkFromIndexSize(offset, length, storage.length);
    if (length > ByteSequenceLimits.MAX_VALUE_BYTES) {
      throw new IllegalArgumentException("a byte sequence exceeds the value byte limit");
    }
    this.offset = offset;
    this.length = length;
  }

  /** 空のバイト列を返します。 */
  public static ByteSequenceValue empty() {
    return EMPTY;
  }

  /** 可変配列を防御コピーして不変値を作ります。 */
  public static ByteSequenceValue copyOf(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return bytes.length == 0
        ? EMPTY
        : new ByteSequenceValue(Arrays.copyOf(bytes, bytes.length), 0, bytes.length);
  }

  /** 実装内部で所有権を移した配列を、追加コピーせず不変値にします。 */
  static ByteSequenceValue takeOwnership(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return bytes.length == 0 ? EMPTY : new ByteSequenceValue(bytes, 0, bytes.length);
  }

  /** 論理バイト数を返します。 */
  public int length() {
    return length;
  }

  /** 指定した半開区間を、元の不変ストレージを共有する値として返します。 */
  public ByteSequenceValue slice(int start, int end) {
    Objects.checkFromToIndex(start, end, length);
    if (start == end) {
      return EMPTY;
    }
    if (start == 0 && end == length) {
      return this;
    }
    return new ByteSequenceValue(storage, offset + start, end - start);
  }

  /** 実装内部のcodecが指定位置を読みます。 */
  byte byteAt(int index) {
    Objects.checkIndex(index, length);
    return storage[offset + index];
  }

  /** 実装内部のcodecへ、論理範囲だけを新しい配列として渡します。 */
  byte[] copyBytes() {
    return Arrays.copyOfRange(storage, offset, offset + length);
  }

  @Override
  public ValueType type() {
    return ValueType.BYTE_SEQUENCE;
  }

  /** この型は表示不能であり、防御的な直接呼出しでも内容と長さを開示しません。 */
  @Override
  public String displayText() {
    return "<redacted>";
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ByteSequenceValue sequence) || length != sequence.length) {
      return false;
    }
    for (int index = 0; index < length; index++) {
      if (storage[offset + index] != sequence.storage[sequence.offset + index]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int index = 0; index < length; index++) {
      result = 31 * result + storage[offset + index];
    }
    return result;
  }

  @Override
  public String toString() {
    return traceText();
  }
}
