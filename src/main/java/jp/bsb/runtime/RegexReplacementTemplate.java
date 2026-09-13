package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import jp.bsb.regex.RegexMatch;

/** 文字列・正規表現の置換参照だけを検証し、巨大な部品リストを作らず再走査する不変テンプレートです。 */
final class RegexReplacementTemplate {
  private final String source;

  private RegexReplacementTemplate(String source) {
    this.source = source;
  }

  static RegexReplacementTemplate parse(String source, int groupCount, List<String> names)
      throws InvalidTemplate {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(names, "names");
    scan(source, groupCount, names, null, null, Long.MAX_VALUE);
    return new RegexReplacementTemplate(source);
  }

  long measureExpansion(RegexMatch match, long maximum) {
    try {
      return scan(
          source,
          match.numberedCaptures().size(),
          List.copyOf(match.namedCaptures().keySet()),
          match,
          null,
          maximum);
    } catch (InvalidTemplate impossible) {
      throw new IllegalStateException("a validated replacement template changed", impossible);
    }
  }

  void appendExpansion(StringBuilder output, RegexMatch match) {
    try {
      scan(
          source,
          match.numberedCaptures().size(),
          List.copyOf(match.namedCaptures().keySet()),
          match,
          output,
          Long.MAX_VALUE);
    } catch (InvalidTemplate impossible) {
      throw new IllegalStateException("a validated replacement template changed", impossible);
    }
  }

  private static long scan(
      String source,
      int groupCount,
      List<String> names,
      RegexMatch match,
      StringBuilder output,
      long maximum)
      throws InvalidTemplate {
    long bytes = 0;
    for (int index = 0; index < source.length(); ) {
      int codePoint = source.codePointAt(index);
      if (codePoint != '$') {
        if (output != null) {
          output.appendCodePoint(codePoint);
        }
        bytes += utf8Bytes(codePoint);
        if (bytes > maximum) {
          return bytes;
        }
        index += Character.charCount(codePoint);
        continue;
      }

      int templateOffset = source.codePointCount(0, index);
      if (index + 1 >= source.length()) {
        throw new InvalidTemplate(templateOffset, "$", "$", groupCount, names);
      }
      char next = source.charAt(index + 1);
      if (next == '$') {
        if (output != null) {
          output.append('$');
        }
        bytes++;
        index += 2;
        continue;
      }
      if (next == '{') {
        int close = source.indexOf('}', index + 2);
        if (close < 0) {
          throw new InvalidTemplate(
              templateOffset, source.substring(index), source.substring(index), groupCount, names);
        }
        String name = source.substring(index + 2, close);
        String expression = source.substring(index, close + 1);
        if (name.isEmpty() || !names.contains(name)) {
          throw new InvalidTemplate(templateOffset, name, expression, groupCount, names);
        }
        if (match != null) {
          String captured = match.namedCaptures().get(name).orElse("");
          bytes = addText(output, captured, bytes, maximum);
          if (bytes > maximum) {
            return bytes;
          }
        }
        index = close + 1;
        continue;
      }
      if (next >= '0' && next <= '9') {
        int end = index + 2;
        if (next != '0'
            && end < source.length()
            && source.charAt(end) >= '0'
            && source.charAt(end) <= '9') {
          end++;
        }
        String digits = source.substring(index + 1, end);
        int group = Integer.parseInt(digits);
        String expression = source.substring(index, end);
        if (group > groupCount) {
          throw new InvalidTemplate(templateOffset, digits, expression, groupCount, names);
        }
        if (match != null) {
          String captured =
              group == 0 ? match.entire() : match.numberedCaptures().get(group - 1).orElse("");
          bytes = addText(output, captured, bytes, maximum);
          if (bytes > maximum) {
            return bytes;
          }
        }
        index = end;
        continue;
      }
      throw new InvalidTemplate(
          templateOffset,
          Character.toString(next),
          source.substring(index, index + 2),
          groupCount,
          names);
    }
    return bytes;
  }

  private static long addText(StringBuilder output, String text, long used, long maximum) {
    if (output != null) {
      output.append(text);
    }
    long remaining = Math.max(0, maximum - Math.min(maximum, used));
    return used + Utf8Length.measureUpTo(text, remaining).bytes();
  }

  private static int utf8Bytes(int codePoint) {
    return codePoint <= 0x7F ? 1 : codePoint <= 0x7FF ? 2 : codePoint <= 0xFFFF ? 3 : 4;
  }

  static final class InvalidTemplate extends Exception {
    private final int templateOffset;
    private final String reference;
    private final String expression;
    private final int groupCount;
    private final List<String> availableNames;

    private InvalidTemplate(
        int templateOffset,
        String reference,
        String expression,
        int groupCount,
        List<String> availableNames) {
      this.templateOffset = templateOffset;
      this.reference = reference;
      this.expression = expression;
      this.groupCount = groupCount;
      this.availableNames = List.copyOf(availableNames);
    }

    int templateOffset() {
      return templateOffset;
    }

    String reference() {
      return reference;
    }

    String expression() {
      return expression;
    }

    int groupCount() {
      return groupCount;
    }

    List<String> availableNames() {
      return availableNames;
    }
  }
}
