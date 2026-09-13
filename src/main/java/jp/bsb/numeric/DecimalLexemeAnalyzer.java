package jp.bsb.numeric;

import java.util.Objects;

/**
 * ASCIIの10進数値字句を、任意精度数値へ変換せずに構文解析して資源量を測ります。
 *
 * <p>【コンピュータ科学の観点：入力検査と値構築の分離】指数部をそのまま {@code Integer.parseInt} や {@code BigDecimal(String)}
 * へ渡すと、入力の大きさや指数値に応じた例外・巨大確保が値構築中に発生します。この解析器は文字列を1回走査し、指数を有限幅へ飽和させ、値構築前に数字数とスケールを判定できる結果を返します。
 */
public final class DecimalLexemeAnalyzer {
  private DecimalLexemeAnalyzer() {}

  /** 有効な数値字句の分類です。 */
  public enum Kind {
    /** 小数点も指数部もない整数字句です。 */
    INTEGER,
    /** 小数点または指数部を持つ小数字句です。 */
    DECIMAL
  }

  /** 指数を含む不正な数値候補へ付与する安定reasonです。 */
  public enum InvalidReason {
    /** 指数記号または指数符号の後に数字がありません。 */
    MISSING_EXPONENT_DIGITS("missingExponentDigits"),
    /** 仮数の整数部または小数部が不完全です。 */
    INVALID_MANTISSA("invalidMantissa"),
    /** リテラル全体の先頭に正符号があります。 */
    LEADING_PLUS("leadingPlus"),
    /** 整数部が不要な0から始まります。 */
    LEADING_ZERO("leadingZero"),
    /** 指数記号が2個以上あります。 */
    REPEATED_EXPONENT("repeatedExponent");

    private final String stableId;

    InvalidReason(String stableId) {
      this.stableId = stableId;
    }

    /** 公開診断へ格納するlowerCamelCase識別子を返します。 */
    public String stableId() {
      return stableId;
    }
  }

  /** 解析結果の共通型です。 */
  public sealed interface Result permits Valid, Invalid {
    /** 符号、小数点、指数記号、指数符号を除く全ASCII数字数を返します。 */
    int inputDigits();
  }

  /**
   * 文法的に有効な字句の有限幅解析結果です。
   *
   * @param kind 整数または小数の字句分類
   * @param negative リテラル全体が負符号を持つか
   * @param exponentPresent 指数部を持つか
   * @param coefficientDigits 小数点と符号を除いた仮数数字列
   * @param normalizedCoefficientDigits 先頭0と、0以外では末尾0も除いた係数数字列。0は{@code "0"}
   * @param inputDigits 仮数と指数部を合算した入力数字数
   * @param fractionDigits 小数部の数字数
   * @param exponent 有限幅へ飽和した符号つき指数
   * @param rawScale 構築前スケール。絶対値上限の1超過へ飽和する
   * @param normalizedScale 係数末尾0を除いたスケール。絶対値上限の1超過へ飽和する
   * @param precision 正規化係数の有効桁数。0は1
   */
  public record Valid(
      Kind kind,
      boolean negative,
      boolean exponentPresent,
      String coefficientDigits,
      String normalizedCoefficientDigits,
      int inputDigits,
      int fractionDigits,
      long exponent,
      long rawScale,
      long normalizedScale,
      int precision)
      implements Result {
    /** 結果の内部整合性を検査します。 */
    public Valid {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(coefficientDigits, "coefficientDigits");
      Objects.requireNonNull(normalizedCoefficientDigits, "normalizedCoefficientDigits");
      if (coefficientDigits.isEmpty()
          || normalizedCoefficientDigits.isEmpty()
          || inputDigits < coefficientDigits.length()
          || fractionDigits < 0
          || precision < 1) {
        throw new IllegalArgumentException("inconsistent decimal lexeme metrics");
      }
    }

    /** 正規化係数が0かを返します。 */
    public boolean zero() {
      return normalizedCoefficientDigits.equals("0");
    }
  }

  /**
   * 文法に一致しない字句の解析結果です。
   *
   * @param reason 安定した不正文法reason
   * @param inputDigits 不正位置までを含めて字句全体に現れたASCII数字数
   */
  public record Invalid(InvalidReason reason, int inputDigits) implements Result {
    /** 結果の内部整合性を検査します。 */
    public Invalid {
      Objects.requireNonNull(reason, "reason");
      if (inputDigits < 0) {
        throw new IllegalArgumentException("input digit count must not be negative");
      }
    }
  }

  /**
   * BSBソース数値文法で字句を解析します。
   *
   * @param lexeme 候補全体
   * @param absoluteScaleLimit 呼出し側が許可するスケール絶対値
   * @return 有効字句の計測結果、または不正reason
   */
  public static Result analyze(String lexeme, int absoluteScaleLimit) {
    Objects.requireNonNull(lexeme, "lexeme");
    if (absoluteScaleLimit < 0) {
      throw new IllegalArgumentException("absolute scale limit is out of range");
    }

    int inputDigits = countAsciiDigits(lexeme);
    if (lexeme.isEmpty()) {
      return new Invalid(InvalidReason.INVALID_MANTISSA, inputDigits);
    }

    int index = 0;
    boolean negative = false;
    char first = lexeme.charAt(index);
    if (first == '+') {
      return new Invalid(InvalidReason.LEADING_PLUS, inputDigits);
    }
    if (first == '-') {
      negative = true;
      index++;
    }
    if (index >= lexeme.length() || !isAsciiDigit(lexeme.charAt(index))) {
      return new Invalid(InvalidReason.INVALID_MANTISSA, inputDigits);
    }

    int integerStart = index;
    while (index < lexeme.length() && isAsciiDigit(lexeme.charAt(index))) {
      index++;
    }
    int integerEnd = index;
    if (lexeme.charAt(integerStart) == '0' && integerEnd - integerStart > 1) {
      return new Invalid(InvalidReason.LEADING_ZERO, inputDigits);
    }

    boolean fractionPresent = false;
    int fractionStart = index;
    if (index < lexeme.length() && lexeme.charAt(index) == '.') {
      fractionPresent = true;
      index++;
      fractionStart = index;
      while (index < lexeme.length() && isAsciiDigit(lexeme.charAt(index))) {
        index++;
      }
      if (fractionStart == index) {
        return new Invalid(InvalidReason.INVALID_MANTISSA, inputDigits);
      }
    }
    int fractionDigits = index - fractionStart;

    boolean exponentPresent = false;
    boolean exponentNegative = false;
    int exponentStart = index;
    if (index < lexeme.length() && isExponentMarker(lexeme.charAt(index))) {
      exponentPresent = true;
      index++;
      if (index < lexeme.length() && (lexeme.charAt(index) == '+' || lexeme.charAt(index) == '-')) {
        exponentNegative = lexeme.charAt(index) == '-';
        index++;
      }
      exponentStart = index;
      while (index < lexeme.length() && isAsciiDigit(lexeme.charAt(index))) {
        index++;
      }
      if (exponentStart == index) {
        if (index < lexeme.length() && isExponentMarker(lexeme.charAt(index))) {
          return new Invalid(InvalidReason.REPEATED_EXPONENT, inputDigits);
        }
        return new Invalid(InvalidReason.MISSING_EXPONENT_DIGITS, inputDigits);
      }
    }

    if (index != lexeme.length()) {
      for (int rest = index; rest < lexeme.length(); rest++) {
        if (isExponentMarker(lexeme.charAt(rest))) {
          return new Invalid(InvalidReason.REPEATED_EXPONENT, inputDigits);
        }
      }
      return new Invalid(InvalidReason.INVALID_MANTISSA, inputDigits);
    }

    String integerDigits = lexeme.substring(integerStart, integerEnd);
    String fractionDigitsText =
        fractionPresent ? lexeme.substring(fractionStart, fractionStart + fractionDigits) : "";
    String coefficientDigits = integerDigits + fractionDigitsText;
    String normalizedCoefficientDigits = normalizeCoefficientDigits(coefficientDigits);
    int trailingZeros =
        normalizedCoefficientDigits.equals("0")
            ? 0
            : coefficientDigits.length() - trailingNonZeroEnd(coefficientDigits);

    long exponent = 0;
    if (exponentPresent) {
      long exponentSaturation =
          saturatingAdd((long) absoluteScaleLimit + 1, (long) fractionDigits + trailingZeros);
      exponent = parseSaturatedUnsigned(lexeme, exponentStart, lexeme.length(), exponentSaturation);
      if (exponentNegative) {
        exponent = -exponent;
      }
    }
    long sentinel = (long) absoluteScaleLimit + 1;
    long rawScale = clampSubtract(fractionDigits, exponent, sentinel);
    long normalizedScale =
        normalizedCoefficientDigits.equals("0")
            ? 0
            : clampSubtract(rawScale, trailingZeros, sentinel);
    Kind kind = fractionPresent || exponentPresent ? Kind.DECIMAL : Kind.INTEGER;
    return new Valid(
        kind,
        negative,
        exponentPresent,
        coefficientDigits,
        normalizedCoefficientDigits,
        inputDigits,
        fractionDigits,
        exponent,
        rawScale,
        normalizedScale,
        normalizedCoefficientDigits.equals("0") ? 1 : normalizedCoefficientDigits.length());
  }

  private static int countAsciiDigits(String text) {
    int count = 0;
    for (int index = 0; index < text.length(); index++) {
      if (isAsciiDigit(text.charAt(index))) {
        count++;
      }
    }
    return count;
  }

  private static boolean isAsciiDigit(char value) {
    return value >= '0' && value <= '9';
  }

  private static boolean isExponentMarker(char value) {
    return value == 'e' || value == 'E';
  }

  private static String normalizeCoefficientDigits(String digits) {
    int firstNonZero = 0;
    while (firstNonZero < digits.length() && digits.charAt(firstNonZero) == '0') {
      firstNonZero++;
    }
    if (firstNonZero == digits.length()) {
      return "0";
    }
    return digits.substring(firstNonZero, trailingNonZeroEnd(digits));
  }

  private static int trailingNonZeroEnd(String digits) {
    int end = digits.length();
    while (end > 0 && digits.charAt(end - 1) == '0') {
      end--;
    }
    return end;
  }

  private static long parseSaturatedUnsigned(String text, int start, int end, long saturation) {
    long value = 0;
    for (int index = start; index < end; index++) {
      int digit = text.charAt(index) - '0';
      if (value > (saturation - digit) / 10) {
        return saturation;
      }
      value = value * 10 + digit;
    }
    return value;
  }

  private static long saturatingAdd(long left, long right) {
    return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
  }

  private static long clamp(long value, long sentinel) {
    if (value > sentinel) {
      return sentinel;
    }
    if (value < -sentinel) {
      return -sentinel;
    }
    return value;
  }

  private static long clampSubtract(long left, long right, long sentinel) {
    if (right > 0 && left < Long.MIN_VALUE + right) {
      return -sentinel;
    }
    if (right < 0 && left > Long.MAX_VALUE + right) {
      return sentinel;
    }
    return clamp(left - right, sentinel);
  }
}
