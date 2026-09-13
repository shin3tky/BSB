package jp.bsb.frontend;

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
import java.util.OptionalInt;

/**
 * Unicode 16.0.0 規格に基づき、識別子の正規化・文字種別判定・不可視文字検査・書記素クラスタ分割を提供するクラスです。
 *
 * <p>【コンピュータ科学の観点：Unicodeの基礎とプログラミング言語設計】 日本語などの自然言語を扱うプログラミング言語において、Unicodeの厳密な制御は極めて重要です：
 *
 * <ul>
 *   <li><b>固定バージョン（Unicode 16.0.0）</b>: 実行環境（OSやJVM）のバージョンによって文字の分類や正規化結果が変わると、
 *       あるマシンでは動くコードが別のマシンでエラーになる「環境依存」が生じます。 本処理系では ICU4J 76.1 を通じて Unicode 16.0.0
 *       のUnicodeデータ版へ固定しています。
 *   <li><b>全角英数字のASCII変換とNFC正規化</b>: 全角英数字（例: <code>ＡＢＣ</code>, <code>１２３</code>）を半角ASCII（<code>ABC
 *       </code>, <code>123</code>）へ変換後、 Unicode正規化形式C（NFC: Normalization Form C）を適用します。 これにより、分解表現（
 *       <code>か</code> + U+3099 COMBINING KATAKANA-HIRAGANA VOICED SOUND MARK）で入力された識別子も、 結合済みの表現（
 *       <code>が</code>）へ統一されます。
 *   <li><b>セキュリティと不可視文字の排除</b>: ソースコード中にゼロ幅スペース（Zero-Width Space）、双方向テキスト制御文字（Bidi Control:
 *       アラビア語等で文字の並び順を反転させる制御文字）、 異体字セレクタ（Variation Selector）などの「見た目で判別できない文字」が混入すると、
 *       悪意あるコードの隠蔽（トロイの木馬攻撃 / Trojan Source）に繋がります。そのため、これらを識別子内で厳格に禁止しています。
 *   <li><b>ホモグリフ・混同可能文字（Confusable Skeletons / UTS #39）</b>: 例えばキリル文字の 'а'（U+0430）とラテン文字の
 *       'a'（U+0061）はフォントによって見分けにくい場合があります。ICU4J の {@link SpoofChecker}
 *       を利用して「骨格文字列（Skeleton）」を抽出し、後続の名前検査が紛らわしい識別子の警告判定に利用します。
 * </ul>
 */
public final class UnicodeRules {
  /** 本処理系が準拠する Unicode の固定バージョン文字列 */
  public static final String UNICODE_VERSION = "16.0.0";

  /** バージョン検証用の ICU VersionInfo インスタンス */
  private static final VersionInfo REQUIRED_VERSION = VersionInfo.getInstance(16, 0, 0, 0);

  /** Unicode正規化形式C（NFC）のノーマライザ */
  private static final Normalizer2 NFC = Normalizer2.getNFCInstance();

  /** デフォルトで無視されるコードポイント集合（Default_Ignorable_Code_Point） */
  private static final UnicodeSet DEFAULT_IGNORABLE =
      new UnicodeSet("[:Default_Ignorable_Code_Point:]").freeze();

  /** 双方向テキスト制御文字集合（Bidi_Control） */
  private static final UnicodeSet BIDI_CONTROL = new UnicodeSet("[:Bidi_Control:]").freeze();

  /** 異体字セレクタ集合（Variation_Selector） */
  private static final UnicodeSet VARIATION_SELECTOR =
      new UnicodeSet("[:Variation_Selector:]").freeze();

  /** 混同可能文字・なりすまし検出チェッカー（UTS #39） */
  private static final SpoofChecker SPOOF_CHECKER =
      new SpoofChecker.Builder().setChecks(SpoofChecker.ALL_CHECKS).build();

  static {
    // 実行環境の ICU4J の Unicode バージョンが 16.0.0 であることを起動時に厳密に検証
    VersionInfo actual = UCharacter.getUnicodeVersion();
    if (!REQUIRED_VERSION.equals(actual)) {
      throw new ExceptionInInitializerError(
          "ICU Unicode version must be " + REQUIRED_VERSION + " but was " + actual);
    }
  }

  private UnicodeRules() {}

  /**
   * 識別子候補文字列に対して、全角英数字のASCII変換を行い、その後にNFC正規化を適用します。
   *
   * @param identifier 未正規化の識別子文字列
   * @return 正規化後の識別子文字列
   */
  public static String normalizeIdentifier(String identifier) {
    Objects.requireNonNull(identifier, "identifier");
    var converted = new StringBuilder(identifier.length());
    identifier
        .codePoints()
        .map(UnicodeRules::convertFullwidthAlphanumeric)
        .forEach(converted::appendCodePoint);
    return NFC.normalize(converted);
  }

  /**
   * コードポイントが識別子の先頭文字（Identifier Start）として使用可能かを判定します。
   *
   * <p>Unicode Letter（大文字・小文字・タイトルケース・修飾文字・その他の文字：漢字・ひらがな・カタカナ等）を受理します。
   *
   * @param codePoint 対象のUnicodeコードポイント
   * @return 先頭文字として有効な場合は true
   */
  public static boolean isIdentifierStart(int codePoint) {
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

  /**
   * コードポイントが識別子の2文字目以降（Identifier Continue）として使用可能かを判定します。
   *
   * <p>先頭文字に加え、結合文字（Mark）、数字（Number）、アンダースコア（_）、中黒（・）を受理します。
   *
   * @param codePoint 対象のUnicodeコードポイント
   * @return 継続文字として有効な場合は true
   */
  public static boolean isIdentifierContinue(int codePoint) {
    if (isIdentifierStart(codePoint) || codePoint == '_' || codePoint == '・') {
      return true;
    }
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

  /**
   * コードポイントが識別子内で禁止されている特殊文字（不可視文字・制御文字・サロゲート等）であるかを判定します。
   *
   * @param codePoint 対象のUnicodeコードポイント
   * @return 禁止文字であれば true
   */
  public static boolean isForbiddenIdentifierCodePoint(int codePoint) {
    int type = UCharacter.getType(codePoint);
    return DEFAULT_IGNORABLE.contains(codePoint)
        || BIDI_CONTROL.contains(codePoint)
        || VARIATION_SELECTOR.contains(codePoint)
        || type == UCharacterCategory.CONTROL
        || type == UCharacterCategory.FORMAT
        || type == UCharacterCategory.SURROGATE
        || codePoint == 0x200C // ゼロ幅非接合子（ZWNJ）
        || codePoint == 0x200D; // ゼロ幅接合子（ZWJ）
  }

  /**
   * コードポイントが Unicode 規格で定義された空白文字（White_Space プロパティを持つ文字）であるかを判定します。
   *
   * @param codePoint 対象のUnicodeコードポイント
   * @return 空白文字であれば true
   */
  public static boolean isUnicodeWhitespace(int codePoint) {
    return UCharacter.hasBinaryProperty(codePoint, UProperty.WHITE_SPACE);
  }

  /**
   * 文字列内に禁止コードポイントが含まれている場合、最初に出現する UTF-16 インデックスを返します。
   *
   * @param identifier 検査対象の文字列
   * @return 禁止文字の位置。存在しない場合は {@link OptionalInt#empty()}
   */
  public static OptionalInt firstForbiddenIdentifierIndex(String identifier) {
    Objects.requireNonNull(identifier, "identifier");
    for (int index = 0; index < identifier.length(); ) {
      int codePoint = identifier.codePointAt(index);
      if (isForbiddenIdentifierCodePoint(codePoint)) {
        return OptionalInt.of(index);
      }
      index += Character.charCount(codePoint);
    }
    return OptionalInt.empty();
  }

  /**
   * 正規化後の文字列が、識別子としての文法規則（先頭文字・継続文字・禁止文字の非含有）を完全に満たしているかを検証します。
   *
   * @param normalizedIdentifier 正規化済みの識別子文字列
   * @return 有効な識別子であれば true
   */
  public static boolean isValidIdentifier(String normalizedIdentifier) {
    Objects.requireNonNull(normalizedIdentifier, "normalizedIdentifier");
    if (normalizedIdentifier.isEmpty()) {
      return false;
    }
    int first = normalizedIdentifier.codePointAt(0);
    if (!isIdentifierStart(first) || isForbiddenIdentifierCodePoint(first)) {
      return false;
    }
    for (int index = Character.charCount(first); index < normalizedIdentifier.length(); ) {
      int codePoint = normalizedIdentifier.codePointAt(index);
      if (!isIdentifierContinue(codePoint) || isForbiddenIdentifierCodePoint(codePoint)) {
        return false;
      }
      index += Character.charCount(codePoint);
    }
    return true;
  }

  /**
   * UAX #29（Unicode Standard Annex #29）の規則に基づき、文字列を書記素クラスタ（Grapheme Cluster）に分割した境界インデックスのリストを返します。
   *
   * @param value 対象文字列
   * @return 境界インデックス（先頭の 0 と末尾の length() を含む）のリスト
   */
  public static List<Integer> graphemeBoundaries(String value) {
    Objects.requireNonNull(value, "value");
    BreakIterator iterator = BreakIterator.getCharacterInstance(ULocale.ROOT);
    iterator.setText(value);
    var boundaries = new ArrayList<Integer>();
    for (int boundary = iterator.first();
        boundary != BreakIterator.DONE;
        boundary = iterator.next()) {
      boundaries.add(boundary);
    }
    return Collections.unmodifiableList(boundaries);
  }

  /**
   * 文字列に含まれる書記素クラスタ（人間から見た文字数）の総数を返します。
   *
   * @param value 対象文字列
   * @return 書記素クラスタ数（0以上の整数）
   */
  public static int graphemeClusterCount(String value) {
    return Math.max(0, graphemeBoundaries(value).size() - 1);
  }

  /**
   * UTS #39 に従い、紛らわしい文字列（ホモグリフ）同士で同一となる「骨格（Skeleton）」文字列を計算します。
   *
   * @param normalizedIdentifier 正規化済み識別子
   * @return 骨格文字列
   */
  public static String confusableSkeleton(String normalizedIdentifier) {
    Objects.requireNonNull(normalizedIdentifier, "normalizedIdentifier");
    return SPOOF_CHECKER.getSkeleton(normalizedIdentifier);
  }

  /**
   * コードポイントの Unicode 公式名称（例: "HIRAGANA LETTER A"）を返します。
   *
   * @param codePoint 対象のコードポイント
   * @return 文字名称
   */
  public static String unicodeName(int codePoint) {
    String name = UCharacter.getName(codePoint);
    return name == null ? "<unassigned>" : name;
  }

  /**
   * コードポイントを "U+XXXX" 形式の16進数文字列表記（例: "U+3042"）に変換します。
   *
   * @param codePoint 対象のコードポイント
   * @return 16進コード表記
   */
  public static String codePointNotation(int codePoint) {
    String hexadecimal = Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
    return "U+" + "0".repeat(Math.max(0, 4 - hexadecimal.length())) + hexadecimal;
  }

  /** 全角の英数字（U+FF10〜U+FF19, U+FF21〜U+FF3A, U+FF41〜U+FF5A）を対応する半角ASCII文字に変換します。 */
  private static int convertFullwidthAlphanumeric(int codePoint) {
    if ((codePoint >= 0xFF10 && codePoint <= 0xFF19)
        || (codePoint >= 0xFF21 && codePoint <= 0xFF3A)
        || (codePoint >= 0xFF41 && codePoint <= 0xFF5A)) {
      return codePoint - 0xFEE0;
    }
    return codePoint;
  }
}
