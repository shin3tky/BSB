package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourcePosition;

/**
 * raw正規表現リテラルのflagsと、パターン内コードポイント境界からソース位置への対応です。
 *
 * @param flags 入力に書かれた順序のflags
 * @param patternPositions パターン先頭から末尾までの各コードポイント境界位置
 */
public record RegexLiteralMetadata(String flags, List<SourcePosition> patternPositions) {
  /** flagsと位置表を検証して不変化します。 */
  public RegexLiteralMetadata {
    Objects.requireNonNull(flags, "flags");
    if (!flags
            .codePoints()
            .allMatch(codePoint -> codePoint == 'i' || codePoint == 'm' || codePoint == 's')
        || flags.codePoints().distinct().count() != flags.length()) {
      throw new IllegalArgumentException("regex flags must be unique ASCII i, m, or s");
    }
    patternPositions = List.copyOf(patternPositions);
    if (patternPositions.isEmpty() || patternPositions.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("regex pattern positions must include both boundaries");
    }
  }

  /**
   * flagsを規範順{@code ims}へ並べます。
   *
   * @return 正規順のflags
   */
  public String canonicalFlags() {
    var result = new StringBuilder(3);
    if (flags.indexOf('i') >= 0) {
      result.append('i');
    }
    if (flags.indexOf('m') >= 0) {
      result.append('m');
    }
    if (flags.indexOf('s') >= 0) {
      result.append('s');
    }
    return result.toString();
  }

  /**
   * rawパターン内の0始まりコードポイント境界をソース位置へ変換します。
   *
   * @param patternOffset 0からパターンコードポイント数までの境界位置
   * @return 元ソース上の位置
   */
  public SourcePosition sourcePositionAt(int patternOffset) {
    return patternPositions.get(patternOffset);
  }
}
