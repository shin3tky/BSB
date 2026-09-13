package jp.bsb.cli;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/** CLI公開DTOを上限つきの決定的なBOMなしUTF-8 JSONへ書く共通機構です。 */
final class JsonOutput {
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private final int maximumBytes;
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);

  JsonOutput(int maximumBytes) {
    if (maximumBytes < 0) {
      throw new IllegalArgumentException("maximumBytes must not be negative");
    }
    this.maximumBytes = maximumBytes;
  }

  void ascii(char value) {
    ensure(1);
    bytes.write(value);
  }

  void ascii(String value) {
    Objects.requireNonNull(value, "ASCII string");
    ensure(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character > 0x7F) {
        throw new IllegalArgumentException("non-ASCII character passed to ascii output");
      }
      bytes.write(character);
    }
  }

  void number(long value) {
    ascii(Long.toString(value));
  }

  void string(String value) {
    Objects.requireNonNull(value, "JSON string");
    ascii('"');
    for (int index = 0; index < value.length(); ) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        ascii('\\');
        ascii(character);
        index++;
      } else if (character <= 0x1F) {
        control(character);
        index++;
      } else {
        int codePoint;
        if (Character.isHighSurrogate(character)) {
          if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
            throw new IllegalArgumentException("unpaired high surrogate in JSON string");
          }
          codePoint = Character.toCodePoint(character, value.charAt(index + 1));
          index += 2;
        } else if (Character.isLowSurrogate(character)) {
          throw new IllegalArgumentException("unpaired low surrogate in JSON string");
        } else {
          codePoint = character;
          index++;
        }
        utf8(codePoint);
      }
    }
    ascii('"');
  }

  byte[] toByteArray() {
    return bytes.toByteArray();
  }

  private void control(char character) {
    char shortEscape =
        switch (character) {
          case '\b' -> 'b';
          case '\t' -> 't';
          case '\n' -> 'n';
          case '\f' -> 'f';
          case '\r' -> 'r';
          default -> 0;
        };
    if (shortEscape != 0) {
      ascii('\\');
      ascii(shortEscape);
      return;
    }
    ensure(6);
    bytes.write('\\');
    bytes.write('u');
    bytes.write('0');
    bytes.write('0');
    bytes.write(HEX[(character >>> 4) & 0xF]);
    bytes.write(HEX[character & 0xF]);
  }

  private void utf8(int codePoint) {
    if (codePoint <= 0x7F) {
      ascii((char) codePoint);
    } else if (codePoint <= 0x7FF) {
      ensure(2);
      bytes.write(0xC0 | (codePoint >>> 6));
      bytes.write(0x80 | (codePoint & 0x3F));
    } else if (codePoint <= 0xFFFF) {
      ensure(3);
      bytes.write(0xE0 | (codePoint >>> 12));
      bytes.write(0x80 | ((codePoint >>> 6) & 0x3F));
      bytes.write(0x80 | (codePoint & 0x3F));
    } else {
      ensure(4);
      bytes.write(0xF0 | (codePoint >>> 18));
      bytes.write(0x80 | ((codePoint >>> 12) & 0x3F));
      bytes.write(0x80 | ((codePoint >>> 6) & 0x3F));
      bytes.write(0x80 | (codePoint & 0x3F));
    }
  }

  private void ensure(int additionalBytes) {
    if (additionalBytes > maximumBytes - bytes.size()) {
      throw new JsonOutputLimitException();
    }
  }

  /** 候補JSONが設定上限へ収まらないことを表します。 */
  static final class JsonOutputLimitException extends RuntimeException {}
}
