package jp.bsb.runtime;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** テストと埋込みで決定的な入力イベント列を返すメモリ入力能力です。 */
public final class MemoryConsoleInput implements ConsoleInput {
  private final ArrayDeque<InputEvent> events;

  /**
   * イベント列を作ります。列を使い切った後は終端を返します。
   *
   * @param events 入力イベント列
   */
  public MemoryConsoleInput(List<InputEvent> events) {
    Objects.requireNonNull(events, "events");
    if (events.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("input event");
    }
    this.events = new ArrayDeque<>(events);
  }

  /** UTF-8検査前の生行をイベントにします。 */
  public static InputEvent.Line rawLine(byte[] bytes, int terminatorBytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (terminatorBytes < 0 || terminatorBytes > 2) {
      throw new IllegalArgumentException("terminatorBytes must be between zero and two");
    }
    return new InputEvent.Line(bytes, bytes.length + terminatorBytes);
  }

  @Override
  public InputEvent readLine() {
    return events.isEmpty() ? new InputEvent.End() : events.removeFirst();
  }
}
