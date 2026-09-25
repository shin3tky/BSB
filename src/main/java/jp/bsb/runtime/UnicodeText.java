package jp.bsb.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jp.bsb.adapter.IcuUnicodeAdapter;
import jp.bsb.frontend.UnicodeRules;

/** Unicode 16.0の書記素・スカラー境界をUTF-16位置として公開せず扱う不変文字列ビューです。 */
public final class UnicodeText {
  private final String value;
  private int[] graphemeBoundaries;
  private int[] codePointBoundaries;

  private UnicodeText(String value) {
    this.value = Objects.requireNonNull(value, "value");
    validateScalars(value);
  }

  /**
   * Unicodeスカラー値列からビューを作ります。
   *
   * @param value 対象文字列
   * @return 遅延境界表を持つ不変ビュー
   */
  public static UnicodeText of(String value) {
    return new UnicodeText(value);
  }

  /**
   * 元の文字列を返します。
   *
   * @return 元のUnicodeスカラー値列
   */
  public String value() {
    return value;
  }

  /**
   * 書記素クラスタ数を返します。
   *
   * @return Unicode 16.0拡張書記素クラスタ数
   */
  public int graphemeCount() {
    return graphemeBoundaries().length - 1;
  }

  /**
   * コードポイント数を返します。
   *
   * @return Unicodeコードポイント数
   */
  public int codePointCount() {
    return codePointBoundaries().length - 1;
  }

  /**
   * 0始まり書記素位置の文字を返します。
   *
   * @param index 書記素位置
   * @return 1拡張書記素クラスタ
   */
  public String graphemeAt(int index) {
    int[] boundaries = graphemeBoundaries();
    requireElementIndex(index, boundaries.length - 1);
    return value.substring(boundaries[index], boundaries[index + 1]);
  }

  /**
   * 0始まりコードポイント位置の文字を返します。
   *
   * @param index コードポイント位置
   * @return 1Unicodeスカラー値
   */
  public String codePointAt(int index) {
    int[] boundaries = codePointBoundaries();
    requireElementIndex(index, boundaries.length - 1);
    return value.substring(boundaries[index], boundaries[index + 1]);
  }

  /**
   * 書記素位置の半開区間を返します。
   *
   * @param start 開始位置
   * @param end 終了位置
   * @return 指定範囲の文字列
   */
  public String graphemeSlice(int start, int end) {
    return slice(graphemeBoundaries(), start, end);
  }

  /**
   * コードポイント位置の半開区間を返します。
   *
   * @param start 開始位置
   * @param end 終了位置
   * @return 指定範囲の文字列
   */
  public String codePointSlice(int start, int end) {
    return slice(codePointBoundaries(), start, end);
  }

  /**
   * 両端が書記素境界に一致する最初の部分列を探します。
   *
   * @param needle 検索文字列
   * @return 0始まり書記素位置。見つからなければ-1
   */
  public int findAtGraphemeBoundary(String needle) {
    return findAtGraphemeBoundary(needle, 0);
  }

  /**
   * 指定位置以降で、両端が書記素境界に一致する最初の部分列を探します。
   *
   * @param needle 検索文字列
   * @param startIndex 0以上書記素数以下の開始位置
   * @return 0始まり書記素位置。見つからなければ-1
   */
  public int findAtGraphemeBoundary(String needle, int startIndex) {
    validateScalars(Objects.requireNonNull(needle, "needle"));
    int[] boundaries = graphemeBoundaries();
    int count = boundaries.length - 1;
    if (startIndex < 0 || startIndex > count) {
      throw new IndexOutOfBoundsException("a Unicode text search start is outside its boundaries");
    }
    if (needle.isEmpty()) {
      return startIndex;
    }
    for (int index = startIndex; index < count; index++) {
      if (matchingEndBoundary(boundaries, index, needle) >= 0) {
        return index;
      }
    }
    return -1;
  }

  /**
   * 両端が書記素境界に一致する最後の部分列を探します。
   *
   * @param needle 検索文字列
   * @return 0始まり書記素位置。見つからなければ-1
   */
  public int findLastAtGraphemeBoundary(String needle) {
    validateScalars(Objects.requireNonNull(needle, "needle"));
    int[] boundaries = graphemeBoundaries();
    int count = boundaries.length - 1;
    if (needle.isEmpty()) {
      return count;
    }
    for (int index = count - 1; index >= 0; index--) {
      if (matchingEndBoundary(boundaries, index, needle) >= 0) {
        return index;
      }
    }
    return -1;
  }

  /** 両端が書記素境界に一致する部分列を含むかを返します。 */
  public boolean containsAtGraphemeBoundary(String needle) {
    return findAtGraphemeBoundary(needle) >= 0;
  }

  /** 書記素境界に一致する指定文字列で始まるかを返します。 */
  public boolean startsWithAtGraphemeBoundary(String prefix) {
    validateScalars(Objects.requireNonNull(prefix, "prefix"));
    if (prefix.isEmpty()) {
      return true;
    }
    return matchingEndBoundary(graphemeBoundaries(), 0, prefix) >= 0;
  }

  /** 書記素境界に一致する指定文字列で終わるかを返します。 */
  public boolean endsWithAtGraphemeBoundary(String suffix) {
    validateScalars(Objects.requireNonNull(suffix, "suffix"));
    int[] boundaries = graphemeBoundaries();
    if (suffix.isEmpty()) {
      return true;
    }
    int start = value.length() - suffix.length();
    int startIndex = Arrays.binarySearch(boundaries, start);
    return startIndex >= 0
        && value.regionMatches(start, suffix, 0, suffix.length())
        && matchingEndBoundary(boundaries, startIndex, suffix) == boundaries.length - 1;
  }

  /**
   * コードポイント列として最初の部分列を探します。
   *
   * @param needle 検索文字列
   * @return 0始まりコードポイント位置。見つからなければ-1
   */
  public int findAtCodePointBoundary(String needle) {
    validateScalars(Objects.requireNonNull(needle, "needle"));
    int utf16Index = value.indexOf(needle);
    return utf16Index < 0 ? -1 : value.codePointCount(0, utf16Index);
  }

  /**
   * 書記素境界に一致する重ならない部分列を左から右へ数えます。
   *
   * @param needle 空でない検索文字列
   * @return 一致数
   */
  public int countNonOverlappingGraphemeMatches(String needle) {
    requireNonEmptyNeedle(needle);
    int[] boundaries = graphemeBoundaries();
    int count = 0;
    for (int index = 0; index < boundaries.length - 1; ) {
      int endIndex = matchingEndBoundary(boundaries, index, needle);
      if (endIndex >= 0) {
        count++;
        index = endIndex;
      } else {
        index++;
      }
    }
    return count;
  }

  /**
   * 書記素境界に一致する重ならない全出現を置き換えます。
   *
   * @param needle 空でない検索文字列
   * @param replacement 置換文字列
   * @return 置換後の文字列
   */
  public String replaceAtGraphemeBoundaries(String needle, String replacement) {
    requireNonEmptyNeedle(needle);
    validateScalars(Objects.requireNonNull(replacement, "replacement"));
    int[] boundaries = graphemeBoundaries();
    var result = new StringBuilder(value.length());
    int copiedUntil = 0;
    for (int index = 0; index < boundaries.length - 1; ) {
      int endIndex = matchingEndBoundary(boundaries, index, needle);
      if (endIndex >= 0) {
        int start = boundaries[index];
        result.append(value, copiedUntil, start).append(replacement);
        copiedUntil = boundaries[endIndex];
        index = endIndex;
      } else {
        index++;
      }
    }
    return result.append(value, copiedUntil, value.length()).toString();
  }

  /**
   * 書記素境界に一致する重ならない区切りで分割し、空欄も保持します。
   *
   * @param delimiter 空でない区切り文字列
   * @param expectedParts 事前に求めた結果要素数
   * @return 分割後の文字列列
   */
  public List<String> splitAtGraphemeBoundaries(String delimiter, int expectedParts) {
    requireNonEmptyNeedle(delimiter);
    if (expectedParts < 1) {
      throw new IllegalArgumentException("a split must produce at least one part");
    }
    int[] boundaries = graphemeBoundaries();
    var result = new ArrayList<String>(expectedParts);
    int copiedUntil = 0;
    for (int index = 0; index < boundaries.length - 1; ) {
      int endIndex = matchingEndBoundary(boundaries, index, delimiter);
      if (endIndex >= 0) {
        result.add(value.substring(copiedUntil, boundaries[index]));
        copiedUntil = boundaries[endIndex];
        index = endIndex;
      } else {
        index++;
      }
    }
    result.add(value.substring(copiedUntil));
    if (result.size() != expectedParts) {
      throw new IllegalStateException("the preflight split count changed during construction");
    }
    return List.copyOf(result);
  }

  /**
   * Unicodeスカラー値の数値順で辞書式比較します。
   *
   * @param left 第1文字列
   * @param right 第2文字列
   * @return 第1文字列が前なら負、同じなら0、後なら正
   */
  public static int compareScalars(String left, String right) {
    validateScalars(Objects.requireNonNull(left, "left"));
    validateScalars(Objects.requireNonNull(right, "right"));
    int leftIndex = 0;
    int rightIndex = 0;
    while (leftIndex < left.length() && rightIndex < right.length()) {
      int leftCodePoint = left.codePointAt(leftIndex);
      int rightCodePoint = right.codePointAt(rightIndex);
      if (leftCodePoint != rightCodePoint) {
        return Integer.compare(leftCodePoint, rightCodePoint);
      }
      leftIndex += Character.charCount(leftCodePoint);
      rightIndex += Character.charCount(rightCodePoint);
    }
    return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
  }

  /**
   * Unicode 16.0 White_Spaceを前後から除きます。
   *
   * @param input 対象文字列
   * @return 内部空白を保持した結果
   */
  public static String trimUnicodeWhitespace(String input) {
    validateScalars(Objects.requireNonNull(input, "input"));
    int start = 0;
    while (start < input.length()) {
      int codePoint = input.codePointAt(start);
      if (!UnicodeRules.isUnicodeWhitespace(codePoint)) {
        break;
      }
      start += Character.charCount(codePoint);
    }
    int end = input.length();
    while (end > start) {
      int codePoint = input.codePointBefore(end);
      if (!UnicodeRules.isUnicodeWhitespace(codePoint)) {
        break;
      }
      end -= Character.charCount(codePoint);
    }
    return input.substring(start, end);
  }

  /** Unicode 16.0 White_Spaceだけからなるか、空である場合にtrueを返します。 */
  public static boolean isUnicodeBlank(String input) {
    validateScalars(Objects.requireNonNull(input, "input"));
    for (int index = 0; index < input.length(); ) {
      int codePoint = input.codePointAt(index);
      if (!UnicodeRules.isUnicodeWhitespace(codePoint)) {
        return false;
      }
      index += Character.charCount(codePoint);
    }
    return true;
  }

  private String slice(int[] boundaries, int start, int end) {
    int count = boundaries.length - 1;
    if (start < 0 || start > end || end > count) {
      throw new IndexOutOfBoundsException("a Unicode text range is outside its boundaries");
    }
    return value.substring(boundaries[start], boundaries[end]);
  }

  private int matchingEndBoundary(int[] boundaries, int startIndex, String needle) {
    int start = boundaries[startIndex];
    int end = start + needle.length();
    if (end > value.length() || !value.regionMatches(start, needle, 0, needle.length())) {
      return -1;
    }
    int endIndex = Arrays.binarySearch(boundaries, startIndex + 1, boundaries.length, end);
    return endIndex >= 0 ? endIndex : -1;
  }

  private static void requireNonEmptyNeedle(String needle) {
    validateScalars(Objects.requireNonNull(needle, "needle"));
    if (needle.isEmpty()) {
      throw new IllegalArgumentException("a boundary operation requires non-empty text");
    }
  }

  private int[] graphemeBoundaries() {
    if (graphemeBoundaries == null) {
      int maximum = value.codePointCount(0, value.length()) + 1;
      int[] result = new int[maximum];
      int count = 0;
      var iterator = IcuUnicodeAdapter.graphemeCursor(value);
      for (int boundary = iterator.first();
          boundary != IcuUnicodeAdapter.DONE;
          boundary = iterator.next()) {
        result[count++] = boundary;
      }
      graphemeBoundaries = count == result.length ? result : Arrays.copyOf(result, count);
    }
    return graphemeBoundaries;
  }

  private int[] codePointBoundaries() {
    if (codePointBoundaries == null) {
      int count = value.codePointCount(0, value.length());
      int[] result = new int[count + 1];
      int offset = 0;
      for (int index = 0; index < count; index++) {
        result[index] = offset;
        offset += Character.charCount(value.codePointAt(offset));
      }
      result[count] = value.length();
      codePointBoundaries = result;
    }
    return codePointBoundaries;
  }

  private static void requireElementIndex(int index, int count) {
    if (index < 0 || index >= count) {
      throw new IndexOutOfBoundsException("a Unicode text index is outside its boundaries");
    }
  }

  private static void validateScalars(String value) {
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isHighSurrogate(current)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new IllegalArgumentException("a Unicode text contains an isolated high surrogate");
        }
        index++;
      } else if (Character.isLowSurrogate(current)) {
        throw new IllegalArgumentException("a Unicode text contains an isolated low surrogate");
      }
    }
  }
}
