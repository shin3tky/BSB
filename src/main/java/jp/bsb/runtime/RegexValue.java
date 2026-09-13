package jp.bsb.runtime;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import jp.bsb.regex.RegexLimits;
import jp.bsb.regex.RegexProgram;
import jp.bsb.stdlib.ValueType;

/**
 * rawパターン、正規順フラグ、Unicode 16.0適合のコンパイル済みプログラムを持つ正規表現値です。
 *
 * @param rawPattern 通常文字列のエスケープを適用していないパターン
 * @param canonicalFlags {@code i}、{@code m}、{@code s}の正規順部分列
 * @param program エンジン固有型を隠したコンパイル済みプログラム
 */
public record RegexValue(String rawPattern, String canonicalFlags, RegexProgram program)
    implements RuntimeValue {
  /** 値境界でもパターン、フラグ、コンパイル量の不変条件を検証します。 */
  public RegexValue {
    Objects.requireNonNull(rawPattern, "rawPattern");
    Objects.requireNonNull(canonicalFlags, "canonicalFlags");
    Objects.requireNonNull(program, "program");
    if (!canonicalFlags.matches("i?m?s?")) {
      throw new IllegalArgumentException("regex flags must be unique and in canonical ims order");
    }
    if (rawPattern.indexOf('\r') >= 0
        || rawPattern.indexOf('\n') >= 0
        || rawPattern.indexOf('」') >= 0) {
      throw new IllegalArgumentException("a raw regex pattern contains a forbidden literal");
    }
    if (rawPattern.getBytes(StandardCharsets.UTF_8).length > RegexLimits.MAX_PATTERN_UTF8_BYTES) {
      throw new IllegalArgumentException("a raw regex pattern exceeds the normative limit");
    }
    if (program.instructionCount() < 1
        || program.instructionCount() > RegexLimits.MAX_PROGRAM_INSTRUCTIONS) {
      throw new IllegalArgumentException("a regex program has an invalid instruction count");
    }
    if (program.captureCount() < 0 || program.captureCount() > RegexLimits.MAX_CAPTURES) {
      throw new IllegalArgumentException("a regex program has an invalid capture count");
    }
    List<String> names = List.copyOf(program.namedCaptures());
    if (names.size() > program.captureCount()
        || names.size() != new HashSet<>(names).size()
        || names.stream().anyMatch(name -> !isValidCaptureName(name))) {
      throw new IllegalArgumentException("a regex program has invalid named captures");
    }
  }

  @Override
  public ValueType type() {
    return ValueType.REGEX;
  }

  @Override
  public String displayText() {
    return "正規表現「" + rawPattern + "」" + canonicalFlags;
  }

  private static boolean isValidCaptureName(String name) {
    if (name == null || name.isEmpty() || name.length() > 64 || !isAsciiLetter(name.charAt(0))) {
      return false;
    }
    for (int index = 1; index < name.length(); index++) {
      char character = name.charAt(index);
      if (!isAsciiLetter(character)
          && !(character >= '0' && character <= '9')
          && character != '_') {
        return false;
      }
    }
    return true;
  }

  private static boolean isAsciiLetter(char character) {
    return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z');
  }
}
