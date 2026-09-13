package jp.bsb.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/** バイトストリームをLF、CRLF、CRで区切る標準一行入力アダプタです。 */
public final class StreamConsoleInput implements ConsoleInput {
  private final InputStream input;
  private int pending = -1;
  private boolean ended;

  /**
   * アダプタを作ります。
   *
   * @param input 読み取るバイトストリーム
   */
  public StreamConsoleInput(InputStream input) {
    this.input = Objects.requireNonNull(input, "input");
  }

  @Override
  public InputEvent readLine() throws CapabilityException {
    if (ended) {
      return new InputEvent.End();
    }
    byte[] line = new byte[256];
    int length = 0;
    int consumed = 0;
    while (true) {
      int current = readByte();
      if (current < 0) {
        ended = true;
        return length == 0
            ? new InputEvent.End()
            : InputEvent.Line.owned(Arrays.copyOf(line, length), consumed);
      }
      consumed++;
      if (current == '\n') {
        return InputEvent.Line.owned(Arrays.copyOf(line, length), consumed);
      }
      if (current == '\r') {
        int next = readByte();
        if (next == '\n') {
          consumed++;
        } else if (next < 0) {
          ended = true;
        } else {
          pending = next;
        }
        return InputEvent.Line.owned(Arrays.copyOf(line, length), consumed);
      }
      if (length == line.length) {
        int maximumBuffered = RuntimeLimits.INPUT_LINE_UTF8_BYTES + 1;
        int grown = Math.min(maximumBuffered, Math.max(line.length + 1, line.length * 2));
        line = Arrays.copyOf(line, grown);
      }
      line[length++] = (byte) current;
      if (length > RuntimeLimits.INPUT_LINE_UTF8_BYTES) {
        return InputEvent.Line.owned(line, consumed);
      }
    }
  }

  private int readByte() throws CapabilityException {
    if (pending >= 0) {
      int result = pending;
      pending = -1;
      return result;
    }
    try {
      return input.read();
    } catch (IOException | RuntimeException failure) {
      throw CapabilityException.failure(RuntimeCapability.CONSOLE_INPUT, "readLine");
    }
  }
}
