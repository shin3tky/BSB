package jp.bsb.frontend;

import java.util.Objects;

/**
 * UTF-16位置による半開区間 {@code [start, end)} です。
 *
 * @param start 範囲の開始位置
 * @param end 範囲の終了位置
 */
public record Utf16Range(Utf16Position start, Utf16Position end) {
  /** 必須値と位置順を検証します。 */
  public Utf16Range {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (compare(end, start) < 0) {
      throw new IllegalArgumentException("end must not precede start");
    }
  }

  private static int compare(Utf16Position left, Utf16Position right) {
    int lineComparison = Integer.compare(left.line(), right.line());
    return lineComparison != 0
        ? lineComparison
        : Integer.compare(left.character(), right.character());
  }
}
