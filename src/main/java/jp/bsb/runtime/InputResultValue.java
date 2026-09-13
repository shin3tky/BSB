package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.stdlib.ValueType;

/** 一行入力の行、終端、取消を文字列と混同せず保持する不変値です。 */
public final class InputResultValue implements RuntimeValue {
  /** 入力結果の状態です。 */
  public enum State {
    /** Unicode行を保持します。 */
    LINE,
    /** 入力が終了しました。 */
    END,
    /** 入力要求が取り消されました。 */
    CANCEL
  }

  private static final InputResultValue END = new InputResultValue(State.END, null);
  private static final InputResultValue CANCEL = new InputResultValue(State.CANCEL, null);

  private final State state;
  private final String line;

  private InputResultValue(State state, String line) {
    this.state = Objects.requireNonNull(state, "state");
    this.line = line;
  }

  /**
   * 行結果を作ります。
   *
   * @param line 行終端を除くUnicodeスカラー値列
   * @return 行結果
   */
  public static InputResultValue line(String line) {
    Objects.requireNonNull(line, "line");
    UnicodeText.of(line);
    return new InputResultValue(State.LINE, line);
  }

  /**
   * @return 入力終端
   */
  public static InputResultValue end() {
    return END;
  }

  /**
   * @return 入力取消
   */
  public static InputResultValue cancel() {
    return CANCEL;
  }

  /**
   * @return 結果状態
   */
  public State state() {
    return state;
  }

  /**
   * @return 行結果なら行、それ以外は空
   */
  public Optional<String> line() {
    return Optional.ofNullable(line);
  }

  @Override
  public ValueType type() {
    return ValueType.INPUT_RESULT;
  }

  /**
   * 入力本文を誤って出力しない安全な説明を返します。
   *
   * <p>この型は表示可能制約から除外されるため、言語の表示語からは呼ばれません。
   */
  @Override
  public String displayText() {
    return switch (state) {
      case LINE -> "<入力行>";
      case END -> "<入力終端>";
      case CANCEL -> "<入力取消>";
    };
  }

  @Override
  public String traceText() {
    return type().sourceName() + ":" + displayText();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InputResultValue result
        && state == result.state
        && Objects.equals(line, result.line);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, line);
  }

  @Override
  public String toString() {
    return traceText();
  }
}
