package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/** 回復可能なCSV/TSV解析失敗について、仕様で公開した4フィールドだけを保持する不変値です。 */
public record DelimitedTextParseFailureValue(String kind, long utf8Offset, int line, int column)
    implements RuntimeValue {
  /** 公開する失敗種別を仕様順に並べた閉じた集合です。 */
  public static final List<String> KINDS =
      List.of(
          "unexpectedQuote",
          "unexpectedCharacterAfterQuote",
          "unterminatedQuotedField",
          "nulCharacter",
          "columnCountMismatch");

  /** 公開フィールドの範囲と失敗種別を検証します。 */
  public DelimitedTextParseFailureValue {
    Objects.requireNonNull(kind, "kind");
    if (!KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown delimited text parse failure kind: " + kind);
    }
    if (utf8Offset < 0) {
      throw new IllegalArgumentException(
          "a delimited text parse failure offset must be non-negative");
    }
    if (line < 1 || column < 1) {
      throw new IllegalArgumentException(
          "delimited text parse failure line and column must be positive");
    }
  }

  @Override
  public ValueType type() {
    return ValueType.DELIMITED_TEXT_PARSE_FAILURE;
  }

  /** この型は表示不能であり、防御的な直接呼出しでもフィールドを開示しません。 */
  @Override
  public String displayText() {
    return "<redacted>";
  }
}
