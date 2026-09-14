package jp.bsb.adapter;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UCharacterCategory;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.SpoofChecker;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.VersionInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * ICU4JをBSB固有のUnicode操作へ変換する境界です。
 *
 * <p>ICU4Jの型、定数、例外はこのクラスから外へ公開しません。
 */
public final class IcuUnicodeAdapter {
  /** 境界カーソルが終端へ達したことを表します。 */
  public static final int DONE = -1;

  private static final VersionInfo REQUIRED_VERSION = VersionInfo.getInstance(16, 0, 0, 0);
  private static final Normalizer2 NFC = Normalizer2.getNFCInstance();
  private static final UnicodeSet DEFAULT_IGNORABLE =
      new UnicodeSet("[:Default_Ignorable_Code_Point:]").freeze();
  private static final UnicodeSet BIDI_CONTROL = new UnicodeSet("[:Bidi_Control:]").freeze();
  private static final UnicodeSet VARIATION_SELECTOR =
      new UnicodeSet("[:Variation_Selector:]").freeze();
  private static final SpoofChecker SPOOF_CHECKER =
      new SpoofChecker.Builder().setChecks(SpoofChecker.ALL_CHECKS).build();

  static {
    VersionInfo actual = UCharacter.getUnicodeVersion();
    if (!REQUIRED_VERSION.equals(actual)) {
      throw new ExceptionInInitializerError(
          "ICU Unicode version must be " + REQUIRED_VERSION + " but was " + actual);
    }
  }

  private IcuUnicodeAdapter() {}

  public static String normalizeNfc(CharSequence value) {
    return NFC.normalize(Objects.requireNonNull(value, "value"));
  }

  public static boolean isLetter(int codePoint) {
    return switch (UCharacter.getType(codePoint)) {
      case UCharacterCategory.UPPERCASE_LETTER,
          UCharacterCategory.LOWERCASE_LETTER,
          UCharacterCategory.TITLECASE_LETTER,
          UCharacterCategory.MODIFIER_LETTER,
          UCharacterCategory.OTHER_LETTER ->
          true;
      default -> false;
    };
  }

  public static boolean isMarkOrNumber(int codePoint) {
    return switch (UCharacter.getType(codePoint)) {
      case UCharacterCategory.NON_SPACING_MARK,
          UCharacterCategory.COMBINING_SPACING_MARK,
          UCharacterCategory.ENCLOSING_MARK,
          UCharacterCategory.DECIMAL_DIGIT_NUMBER,
          UCharacterCategory.LETTER_NUMBER,
          UCharacterCategory.OTHER_NUMBER ->
          true;
      default -> false;
    };
  }

  public static boolean isInvisibleOrNonScalarCategory(int codePoint) {
    int type = UCharacter.getType(codePoint);
    return DEFAULT_IGNORABLE.contains(codePoint)
        || BIDI_CONTROL.contains(codePoint)
        || VARIATION_SELECTOR.contains(codePoint)
        || type == UCharacterCategory.CONTROL
        || type == UCharacterCategory.FORMAT
        || type == UCharacterCategory.SURROGATE;
  }

  public static boolean isWhitespace(int codePoint) {
    return UCharacter.hasBinaryProperty(codePoint, UProperty.WHITE_SPACE);
  }

  public static List<Integer> graphemeBoundaries(String value) {
    GraphemeCursor cursor = graphemeCursor(value);
    var boundaries = new ArrayList<Integer>();
    for (int boundary = cursor.first(); boundary != DONE; boundary = cursor.next()) {
      boundaries.add(boundary);
    }
    return Collections.unmodifiableList(boundaries);
  }

  public static GraphemeCursor graphemeCursor(String value) {
    return new GraphemeCursor(value);
  }

  public static String confusableSkeleton(String value) {
    return SPOOF_CHECKER.getSkeleton(Objects.requireNonNull(value, "value"));
  }

  public static String unicodeName(int codePoint) {
    String name = UCharacter.getName(codePoint);
    return name == null ? "<unassigned>" : name;
  }

  /**
   * General_CategoryまたはScript別名をRE2/J文字クラス用の範囲列へ変換します。
   *
   * @throws IllegalArgumentException どちらのUnicodeプロパティ別名でもない場合
   */
  public static String regexPropertyRanges(
      String alias, boolean complement, boolean asciiCaseInsensitive) {
    UnicodeSet set;
    try {
      set = new UnicodeSet().applyPropertyAlias("General_Category", alias);
    } catch (IllegalArgumentException ignored) {
      set = new UnicodeSet().applyPropertyAlias("Script", alias);
    }
    if (complement) {
      set.complement(0, 0x10FFFF).remove(0xD800, 0xDFFF);
    }
    if (asciiCaseInsensitive) {
      for (int letter = 'A'; letter <= 'Z'; letter++) {
        if (set.contains(letter) || set.contains(letter + ('a' - 'A'))) {
          set.add(letter).add(letter + ('a' - 'A'));
        }
      }
    }
    var result = new StringBuilder();
    for (int index = 0; index < set.getRangeCount(); index++) {
      int start = set.getRangeStart(index);
      int end = set.getRangeEnd(index);
      appendHexEscape(result, start);
      if (end != start) {
        result.append('-');
        appendHexEscape(result, end);
      }
    }
    return result.toString();
  }

  private static void appendHexEscape(StringBuilder result, int codePoint) {
    result
        .append("\\x{")
        .append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT))
        .append('}');
  }

  /** ICU4JのBreakIteratorを漏らさない、UTF-16境界位置だけのカーソルです。 */
  public static final class GraphemeCursor {
    private final BreakIterator delegate;

    private GraphemeCursor(String value) {
      delegate = BreakIterator.getCharacterInstance(ULocale.ROOT);
      delegate.setText(Objects.requireNonNull(value, "value"));
    }

    public int first() {
      return delegate.first();
    }

    public int next() {
      return delegate.next();
    }

    public int following(int offset) {
      return delegate.following(offset);
    }
  }
}
