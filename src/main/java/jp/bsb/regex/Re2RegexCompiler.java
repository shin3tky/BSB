package jp.bsb.regex;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import jp.bsb.adapter.IcuUnicodeAdapter;
import jp.bsb.diagnostics.DiagnosticCode;

/** Unicode 16.0適合層で検査・展開した後、RE2/Jを照合核として使うコンパイラです。 */
public final class Re2RegexCompiler implements RegexCompiler {
  /** 状態を持たないコンパイラを作ります。 */
  public Re2RegexCompiler() {}

  @Override
  public RegexCompilationResult compile(String rawPattern, String canonicalFlags) {
    if (rawPattern == null || canonicalFlags == null) {
      throw new NullPointerException("regex compiler inputs must not be null");
    }
    if (!canonicalFlags.matches("i?m?s?")) {
      throw new IllegalArgumentException("regex flags must be canonical and unique");
    }
    long patternBytes = utf8LengthUpTo(rawPattern, RegexLimits.MAX_PATTERN_UTF8_BYTES);
    if (patternBytes > RegexLimits.MAX_PATTERN_UTF8_BYTES) {
      return failure(
          DiagnosticCode.E_REGEX_PATTERN_LIMIT, 0, "patternUtf8Bytes", Long.toString(patternBytes));
    }

    Validation validation = validate(rawPattern);
    if (validation.failure() != null) {
      return validation.failure();
    }

    final String translated;
    try {
      translated = translate(rawPattern, canonicalFlags.indexOf('i') >= 0);
    } catch (PropertyFailure failure) {
      return failure(
          DiagnosticCode.E_REGEX_SYNTAX,
          failure.patternOffset,
          "reason",
          "unknownUnicodeProperty",
          "property",
          failure.property,
          "patternOffset",
          Integer.toString(failure.patternOffset));
    }

    try {
      Pattern compiled = Pattern.compile(translated, re2Flags(canonicalFlags));
      int instructions = compiled.programSize();
      if (instructions > RegexLimits.MAX_PROGRAM_INSTRUCTIONS) {
        return failure(
            DiagnosticCode.E_REGEX_PROGRAM_LIMIT,
            0,
            "programInstructions",
            Integer.toString(instructions));
      }
      return new RegexCompilationResult.Success(
          new Re2Program(
              compiled, instructions, validation.captureCount(), validation.namedCaptures()));
    } catch (PatternSyntaxException exception) {
      return failure(
          DiagnosticCode.E_REGEX_SYNTAX, 0, "reason", "invalidSyntax", "patternOffset", "0");
    }
  }

  private static Validation validate(String pattern) {
    int captures = 0;
    var names = new LinkedHashMap<String, NameSite>();
    boolean inClass = false;
    for (int index = 0; index < pattern.length(); ) {
      int codePoint = pattern.codePointAt(index);
      int offset = pattern.codePointCount(0, index);
      int width = Character.charCount(codePoint);
      if (codePoint == '\\') {
        if (index + 1 >= pattern.length()) {
          return Validation.failed(syntax(offset, "trailingEscape"));
        }
        char escaped = pattern.charAt(index + 1);
        if (escaped >= '0' && escaped <= '9') {
          return Validation.failed(
              failure(
                  DiagnosticCode.E_REGEX_UNSUPPORTED_CONSTRUCT,
                  offset,
                  "construct",
                  "backreference",
                  "token",
                  "\\" + escaped,
                  "patternOffset",
                  Integer.toString(offset)));
        }
        if (escaped == 'k' || escaped == 'g') {
          return Validation.failed(
              unsupported(offset, "backreference", pattern.substring(index, index + 2)));
        }
        if (isAsciiLetter(escaped) && "nrtfaxdDsSwWpP".indexOf(escaped) < 0) {
          return Validation.failed(
              unsupported(offset, "escape", pattern.substring(index, index + 2)));
        }
        if ((escaped == 'p' || escaped == 'P' || escaped == 'x')
            && index + 2 < pattern.length()
            && pattern.charAt(index + 2) == '{') {
          int close = pattern.indexOf('}', index + 3);
          if (close < 0) {
            return Validation.failed(syntax(offset, "unterminatedEscape"));
          }
          index = close + 1;
          continue;
        }
        index += 2;
        continue;
      }
      if (codePoint == '[') {
        inClass = true;
        index += width;
        continue;
      }
      if (codePoint == ']' && inClass) {
        inClass = false;
        index += width;
        continue;
      }
      if (!inClass && codePoint == '(') {
        if (pattern.startsWith("(?=", index) || pattern.startsWith("(?!", index)) {
          return Validation.failed(
              unsupported(offset, "lookahead", pattern.substring(index, index + 3)));
        }
        if (pattern.startsWith("(?<=", index) || pattern.startsWith("(?<!", index)) {
          return Validation.failed(
              unsupported(offset, "lookbehind", pattern.substring(index, index + 4)));
        }
        if (pattern.startsWith("(?>", index)) {
          return Validation.failed(unsupported(offset, "atomicGroup", "(?>"));
        }
        if (pattern.startsWith("(?:", index)) {
          index += 3;
          continue;
        }
        if (pattern.startsWith("(?<", index)) {
          int nameStart = index + 3;
          int close = pattern.indexOf('>', nameStart);
          if (close < 0) {
            return Validation.failed(syntax(offset, "unterminatedGroupName"));
          }
          String name = pattern.substring(nameStart, close);
          int nameOffset = pattern.codePointCount(0, nameStart);
          if (!validGroupName(name)) {
            return Validation.failed(
                failure(
                    DiagnosticCode.E_REGEX_SYNTAX,
                    nameOffset,
                    "reason",
                    "invalidGroupName",
                    "name",
                    name,
                    "patternOffset",
                    Integer.toString(nameOffset)));
          }
          NameSite previous = names.get(name);
          if (previous != null) {
            return Validation.failed(
                failure(
                    DiagnosticCode.E_REGEX_SYNTAX,
                    offset,
                    "reason",
                    "duplicateGroupName",
                    "name",
                    name,
                    "firstPatternOffset",
                    Integer.toString(previous.groupOffset()),
                    "patternOffset",
                    Integer.toString(offset),
                    "_locationOffset",
                    Integer.toString(pattern.codePointCount(0, nameStart)),
                    "_relatedOffset",
                    Integer.toString(previous.nameOffset()),
                    "_relatedLabel",
                    name));
          }
          names.put(name, new NameSite(offset, nameOffset));
          captures++;
          if (captures > RegexLimits.MAX_CAPTURES) {
            return Validation.failed(captureLimit(offset, captures));
          }
          index = close + 1;
          continue;
        }
        if (pattern.startsWith("(?", index)) {
          return Validation.failed(unsupported(offset, "groupExtension", "(?"));
        }
        captures++;
        if (captures > RegexLimits.MAX_CAPTURES) {
          return Validation.failed(captureLimit(offset, captures));
        }
      }
      if (!inClass && codePoint == '{') {
        int close = pattern.indexOf('}', index + 1);
        if (close < 0) {
          return Validation.failed(syntax(offset, "unterminatedQuantifier"));
        }
        String body = pattern.substring(index + 1, close);
        String[] parts = body.split(",", -1);
        if (parts.length > 2 || parts[0].isEmpty() || !asciiDigits(parts[0])) {
          return Validation.failed(syntax(offset, "invalidQuantifier"));
        }
        long minimum = parseBound(parts[0]);
        Long maximum = null;
        if (parts.length == 1) {
          maximum = minimum;
        } else if (!parts[1].isEmpty()) {
          if (!asciiDigits(parts[1])) {
            return Validation.failed(syntax(offset, "invalidQuantifier"));
          }
          maximum = parseBound(parts[1]);
        }
        if (minimum > 1_000 || (maximum != null && maximum > 1_000)) {
          long observed = Math.max(minimum, maximum == null ? 0 : maximum);
          return Validation.failed(
              failure(
                  DiagnosticCode.E_REGEX_SYNTAX,
                  offset,
                  "reason",
                  "quantifierLimit",
                  "minimum",
                  Long.toString(observed),
                  "limit",
                  "1000",
                  "patternOffset",
                  Integer.toString(offset)));
        }
        if (maximum != null && maximum < minimum) {
          return Validation.failed(syntax(offset, "quantifierRange"));
        }
        if (close + 1 < pattern.length() && pattern.charAt(close + 1) == '+') {
          return Validation.failed(unsupported(offset, "possessiveQuantifier", body + "}+"));
        }
        index = close + 1;
        continue;
      }
      if (!inClass
          && (codePoint == '*' || codePoint == '+' || codePoint == '?')
          && index + width < pattern.length()
          && pattern.charAt(index + width) == '+') {
        return Validation.failed(
            unsupported(
                offset, "possessiveQuantifier", pattern.substring(index, index + width + 1)));
      }
      index += width;
    }
    if (inClass) {
      return Validation.failed(
          syntax(pattern.codePointCount(0, pattern.length()), "unterminatedClass"));
    }
    return new Validation(captures, List.copyOf(names.keySet()), null);
  }

  private static String translate(String pattern, boolean asciiCaseInsensitive) {
    var result = new StringBuilder(pattern.length());
    for (int index = 0; index < pattern.length(); ) {
      int codePoint = pattern.codePointAt(index);
      int offset = pattern.codePointCount(0, index);
      int width = Character.charCount(codePoint);
      if (codePoint == '\\' && index + 2 < pattern.length()) {
        char kind = pattern.charAt(index + 1);
        if ((kind == 'p' || kind == 'P') && pattern.charAt(index + 2) == '{') {
          int close = pattern.indexOf('}', index + 3);
          if (close < 0) {
            throw new PropertyFailure(offset, pattern.substring(index + 3));
          }
          result
              .append('[')
              .append(
                  unicodePropertyRanges(
                      pattern.substring(index + 3, close),
                      kind == 'P',
                      asciiCaseInsensitive,
                      offset))
              .append(']');
          index = close + 1;
          continue;
        }
        result.appendCodePoint(codePoint).append(pattern.charAt(index + 1));
        index += 2;
        continue;
      }
      if (codePoint == '[') {
        int close = classEnd(pattern, index + 1);
        if (close < 0) {
          result.append(pattern.substring(index));
          break;
        }
        result.append(translateClass(pattern, index + 1, close, asciiCaseInsensitive));
        index = close + 1;
        continue;
      }
      if (pattern.startsWith("(?<", index)) {
        int close = pattern.indexOf('>', index + 3);
        result.append(pattern, index, close + 1);
        index = close + 1;
        continue;
      }
      if (asciiCaseInsensitive && isAsciiLetter(codePoint)) {
        result
            .append('[')
            .appendCodePoint(codePoint)
            .appendCodePoint(swapAsciiCase(codePoint))
            .append(']');
      } else {
        result.appendCodePoint(codePoint);
      }
      index += width;
    }
    return result.toString();
  }

  private static String translateClass(
      String pattern, int start, int end, boolean asciiCaseInsensitive) {
    boolean negated = start < end && pattern.charAt(start) == '^';
    int contentStart = negated ? start + 1 : start;
    var base = new StringBuilder();
    var additions = new StringBuilder();
    for (int index = contentStart; index < end; ) {
      if (pattern.charAt(index) == '\\' && index + 2 < end) {
        char kind = pattern.charAt(index + 1);
        if ((kind == 'p' || kind == 'P') && pattern.charAt(index + 2) == '{') {
          int close = pattern.indexOf('}', index + 3);
          if (close < 0 || close >= end) {
            throw new PropertyFailure(
                pattern.codePointCount(0, index), pattern.substring(index + 3, end));
          }
          int offset = pattern.codePointCount(0, index);
          base.append(
              unicodePropertyRanges(
                  pattern.substring(index + 3, close), kind == 'P', asciiCaseInsensitive, offset));
          index = close + 1;
          continue;
        }
        base.append(pattern, index, index + 2);
        index += 2;
        continue;
      }
      int codePoint = pattern.codePointAt(index);
      int width = Character.charCount(codePoint);
      int next = index + width;
      if (asciiCaseInsensitive
          && isAsciiLetter(codePoint)
          && next + 1 < end
          && pattern.charAt(next) == '-'
          && isAsciiLetter(pattern.codePointAt(next + 1))) {
        int rangeEnd = pattern.codePointAt(next + 1);
        base.appendCodePoint(codePoint).append('-').appendCodePoint(rangeEnd);
        if (sameAsciiCase(codePoint, rangeEnd)) {
          additions
              .appendCodePoint(swapAsciiCase(codePoint))
              .append('-')
              .appendCodePoint(swapAsciiCase(rangeEnd));
        }
        index = next + 1 + Character.charCount(rangeEnd);
        continue;
      }
      base.appendCodePoint(codePoint);
      if (asciiCaseInsensitive && isAsciiLetter(codePoint)) {
        additions.appendCodePoint(swapAsciiCase(codePoint));
      }
      index = next;
    }
    return "[" + (negated ? "^" : "") + base + additions + "]";
  }

  private static String unicodePropertyRanges(
      String alias, boolean complement, boolean asciiCaseInsensitive, int patternOffset) {
    try {
      return IcuUnicodeAdapter.regexPropertyRanges(alias, complement, asciiCaseInsensitive);
    } catch (IllegalArgumentException failure) {
      throw new PropertyFailure(patternOffset, alias);
    }
  }

  private static int classEnd(String pattern, int start) {
    for (int index = start; index < pattern.length(); index++) {
      if (pattern.charAt(index) == '\\') {
        index++;
      } else if (pattern.charAt(index) == ']') {
        return index;
      }
    }
    return -1;
  }

  private static int re2Flags(String flags) {
    int result = Pattern.DISABLE_UNICODE_GROUPS;
    if (flags.indexOf('m') >= 0) {
      result |= Pattern.MULTILINE;
    }
    if (flags.indexOf('s') >= 0) {
      result |= Pattern.DOTALL;
    }
    return result;
  }

  private static long utf8LengthUpTo(String value, long maximum) {
    long bytes = 0;
    for (int index = 0; index < value.length(); ) {
      int codePoint = value.codePointAt(index);
      bytes += codePoint <= 0x7F ? 1 : codePoint <= 0x7FF ? 2 : codePoint <= 0xFFFF ? 3 : 4;
      if (bytes > maximum) {
        return bytes;
      }
      index += Character.charCount(codePoint);
    }
    return bytes;
  }

  private static long parseBound(String digits) {
    return digits.length() > 4 ? 1_001 : Long.parseLong(digits);
  }

  private static boolean asciiDigits(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) < '0' || value.charAt(index) > '9') {
        return false;
      }
    }
    return true;
  }

  private static boolean validGroupName(String name) {
    if (name.isEmpty() || name.length() > 64 || !isAsciiLetter(name.charAt(0))) {
      return false;
    }
    for (int index = 1; index < name.length(); index++) {
      char current = name.charAt(index);
      if (!isAsciiLetter(current) && (current < '0' || current > '9') && current != '_') {
        return false;
      }
    }
    return true;
  }

  private static boolean isAsciiLetter(int codePoint) {
    return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z');
  }

  private static int swapAsciiCase(int codePoint) {
    return codePoint >= 'A' && codePoint <= 'Z' ? codePoint + ('a' - 'A') : codePoint - ('a' - 'A');
  }

  private static boolean sameAsciiCase(int first, int second) {
    return (first >= 'A' && first <= 'Z' && second >= 'A' && second <= 'Z')
        || (first >= 'a' && first <= 'z' && second >= 'a' && second <= 'z');
  }

  private static RegexCompilationResult.Failure unsupported(
      int offset, String construct, String token) {
    return failure(
        DiagnosticCode.E_REGEX_UNSUPPORTED_CONSTRUCT,
        offset,
        "construct",
        construct,
        "token",
        token,
        "patternOffset",
        Integer.toString(offset));
  }

  private static RegexCompilationResult.Failure syntax(int offset, String reason) {
    return failure(
        DiagnosticCode.E_REGEX_SYNTAX,
        offset,
        "reason",
        reason,
        "patternOffset",
        Integer.toString(offset));
  }

  private static RegexCompilationResult.Failure captureLimit(int offset, int captures) {
    return failure(
        DiagnosticCode.E_REGEX_CAPTURE_LIMIT,
        offset,
        "regexCaptures",
        Integer.toString(captures),
        "patternOffset",
        Integer.toString(offset));
  }

  private static RegexCompilationResult.Failure failure(
      DiagnosticCode code, int offset, String... fieldPairs) {
    var fields = new LinkedHashMap<String, String>();
    for (int index = 0; index < fieldPairs.length; index += 2) {
      fields.put(fieldPairs[index], fieldPairs[index + 1]);
    }
    return new RegexCompilationResult.Failure(code, offset, fields);
  }

  /** RE2/J固有値はこの非公開実装から外へ出しません。 */
  private record Re2Program(
      Pattern compiled, int instructionCount, int captureCount, List<String> namedCaptures)
      implements RegexProgram {
    private Re2Program {
      namedCaptures = List.copyOf(namedCaptures);
    }

    @Override
    public boolean matchesEntire(String input) {
      return compiled.matcher(input).matches();
    }

    @Override
    public boolean containsMatch(String input) {
      return compiled.matcher(input).find();
    }

    @Override
    public Optional<RegexMatch> firstMatch(String input) {
      var matcher = compiled.matcher(input);
      if (!matcher.find()) {
        return Optional.empty();
      }
      var numbered = new java.util.ArrayList<Optional<String>>(captureCount);
      for (int index = 1; index <= captureCount; index++) {
        numbered.add(Optional.ofNullable(matcher.group(index)));
      }
      var named = new LinkedHashMap<String, Optional<String>>();
      for (String name : namedCaptures) {
        named.put(name, Optional.ofNullable(matcher.group(name)));
      }
      return Optional.of(new RegexMatch(matcher.group(), numbered, named));
    }

    @Override
    public RegexMatchCursor matchCursor(String input) {
      return new Re2MatchCursor(compiled, input, captureCount, namedCaptures);
    }
  }

  /** 1個のMatcherを1カーソルだけが所有し、UTF-16位置を文字列片へ変換して閉じ込めます。 */
  private static final class Re2MatchCursor implements RegexMatchCursor {
    private final com.google.re2j.Matcher matcher;
    private final String input;
    private final int captureCount;
    private final List<String> namedCaptures;
    private int previousEnd;
    private int nextSearchStart;
    private boolean current;
    private boolean exhausted;

    private Re2MatchCursor(
        Pattern compiled, String input, int captureCount, List<String> namedCaptures) {
      matcher = compiled.matcher(input);
      this.input = input;
      this.captureCount = captureCount;
      this.namedCaptures = namedCaptures;
    }

    @Override
    public boolean advance() {
      if (exhausted) {
        return false;
      }
      if (current) {
        previousEnd = matcher.end();
        if (matcher.start() == previousEnd) {
          if (previousEnd == input.length()) {
            current = false;
            exhausted = true;
            return false;
          }
          nextSearchStart = input.offsetByCodePoints(previousEnd, 1);
        } else {
          nextSearchStart = previousEnd;
        }
      }
      current = matcher.find(nextSearchStart);
      exhausted = !current;
      return current;
    }

    @Override
    public String textBeforeMatch() {
      requireCurrent();
      return input.substring(previousEnd, matcher.start());
    }

    @Override
    public RegexMatch match() {
      requireCurrent();
      var numbered = new java.util.ArrayList<Optional<String>>(captureCount);
      for (int index = 1; index <= captureCount; index++) {
        numbered.add(Optional.ofNullable(matcher.group(index)));
      }
      var named = new LinkedHashMap<String, Optional<String>>();
      for (String name : namedCaptures) {
        named.put(name, Optional.ofNullable(matcher.group(name)));
      }
      return new RegexMatch(matcher.group(), numbered, named);
    }

    @Override
    public String textAfterMatches() {
      if (!exhausted) {
        throw new IllegalStateException("all regex matches must be read before the tail");
      }
      return input.substring(previousEnd);
    }

    private void requireCurrent() {
      if (!current) {
        throw new IllegalStateException("a regex cursor has no current match");
      }
    }
  }

  private record Validation(
      int captureCount, List<String> namedCaptures, RegexCompilationResult.Failure failure) {
    private static Validation failed(RegexCompilationResult.Failure failure) {
      return new Validation(0, List.of(), failure);
    }
  }

  private record NameSite(int groupOffset, int nameOffset) {}

  private static final class PropertyFailure extends RuntimeException {
    private final int patternOffset;
    private final String property;

    private PropertyFailure(int patternOffset, String property) {
      this.patternOffset = patternOffset;
      this.property = property;
    }
  }
}
