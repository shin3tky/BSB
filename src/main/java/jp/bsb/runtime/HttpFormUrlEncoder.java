package jp.bsb.runtime;

import java.nio.charset.StandardCharsets;

/** application/x-www-form-urlencodedの決定的なUTF-8 encoderです。 */
final class HttpFormUrlEncoder {
  static final int MAX_ITEMS = 256;

  record Measurement(long outputBytes, long workBytes) {}

  private HttpFormUrlEncoder() {}

  static Measurement measure(ArrayValue table) {
    long output = 0;
    long input = 0;
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      var row = (ArrayValue) table.get(rowIndex);
      if (rowIndex != 0) output = saturatedAdd(output, 1);
      byte[] name = ((StringValue) row.get(0)).value().getBytes(StandardCharsets.UTF_8);
      byte[] value = ((StringValue) row.get(1)).value().getBytes(StandardCharsets.UTF_8);
      input = saturatedAdd(input, name.length);
      input = saturatedAdd(input, value.length);
      output = saturatedAdd(output, encodedLength(name));
      output = saturatedAdd(output, 1);
      output = saturatedAdd(output, encodedLength(value));
    }
    return new Measurement(output, saturatedAdd(input, output));
  }

  static String encode(ArrayValue table, long measuredOutputBytes) {
    if (measuredOutputBytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("form output does not fit a Java string");
    }
    var result = new StringBuilder((int) measuredOutputBytes);
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      var row = (ArrayValue) table.get(rowIndex);
      if (rowIndex != 0) result.append('&');
      append(result, ((StringValue) row.get(0)).value());
      result.append('=');
      append(result, ((StringValue) row.get(1)).value());
    }
    if (result.length() != measuredOutputBytes) {
      throw new IllegalStateException("form measurement and encoder disagree");
    }
    return result.toString();
  }

  private static long encodedLength(byte[] bytes) {
    long result = 0;
    for (byte signed : bytes) {
      int octet = Byte.toUnsignedInt(signed);
      result = saturatedAdd(result, literal(octet) || octet == 0x20 ? 1 : 3);
    }
    return result;
  }

  private static void append(StringBuilder output, String value) {
    for (byte signed : value.getBytes(StandardCharsets.UTF_8)) {
      int octet = Byte.toUnsignedInt(signed);
      if (literal(octet)) {
        output.append((char) octet);
      } else if (octet == 0x20) {
        output.append('+');
      } else {
        output.append('%');
        output.append(Character.toUpperCase(Character.forDigit(octet >>> 4, 16)));
        output.append(Character.toUpperCase(Character.forDigit(octet & 15, 16)));
      }
    }
  }

  private static boolean literal(int octet) {
    return octet >= 'A' && octet <= 'Z'
        || octet >= 'a' && octet <= 'z'
        || octet >= '0' && octet <= '9'
        || octet == '*'
        || octet == '-'
        || octet == '.'
        || octet == '_';
  }

  private static long saturatedAdd(long first, long second) {
    if (first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
    return first + second;
  }
}
