package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/** 論理名、path、例外、部分内容を持たない閉じたファイル読取失敗です。 */
public final class FileReadFailureValue implements RuntimeValue {
  /** 規範順の安定した失敗種類です。 */
  public static final List<String> KINDS =
      List.of("notFound", "notRegularFile", "tooLarge", "ioFailure");

  private final String kind;

  /** 閉じた種類だけから値を作ります。 */
  public FileReadFailureValue(String kind) {
    this.kind = Objects.requireNonNull(kind, "kind");
    if (!KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown file read failure kind");
    }
  }

  public String kind() {
    return kind;
  }

  @Override
  public ValueType type() {
    return ValueType.FILE_READ_FAILURE;
  }

  @Override
  public String displayText() {
    return "<redacted>";
  }

  @Override
  public String toString() {
    return traceText();
  }
}
