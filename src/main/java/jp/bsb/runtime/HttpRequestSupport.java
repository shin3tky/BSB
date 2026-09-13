package jp.bsb.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** HTTPSの要求metadata検証と、JDKに依存しないUTF-8 percent encodingです。 */
final class HttpRequestSupport {
  static final int MAX_PATH_BYTES = 8_192;
  static final int MAX_QUERY_ITEMS = 256;
  static final int MAX_QUERY_BYTES = 65_536;
  static final int MAX_HEADER_NAME_BYTES = 256;
  static final int MAX_HEADER_VALUE_BYTES = 8_192;
  static final int MAX_HEADER_VALUES = 128;
  static final int MAX_HEADER_BYTES = 65_536;
  static final int MAX_TARGET_URI_BYTES = 65_536;

  static final Set<String> RESERVED_HEADERS =
      Set.of(
          "authorization",
          "connection",
          "content-length",
          "expect",
          "host",
          "proxy-authorization",
          "te",
          "trailer",
          "transfer-encoding",
          "upgrade");

  private static final String TOKEN = "!#$%&'*+-.^_`|~";

  private HttpRequestSupport() {}

  static String pathProblem(String path) {
    if (path.startsWith("/")) return "absolutePath";
    if (path.indexOf('?') >= 0) return "queryDelimiter";
    if (path.indexOf('#') >= 0) return "fragmentDelimiter";
    if (path.indexOf('\0') >= 0) return "nul";
    for (String segment : path.split("/", -1)) {
      if (segment.equals(".") || segment.equals("..")) return "dotSegment";
    }
    return null;
  }

  static String headerNameProblem(String name) {
    if (name.isEmpty()) return "emptyName";
    if (name.length() > MAX_HEADER_NAME_BYTES) return "nameTooLong";
    for (int index = 0; index < name.length(); index++) {
      char character = name.charAt(index);
      if (!(character >= '0' && character <= '9')
          && !(character >= 'A' && character <= 'Z')
          && !(character >= 'a' && character <= 'z')
          && TOKEN.indexOf(character) < 0) {
        return "invalidNameCharacter";
      }
    }
    return null;
  }

  static String headerValueProblem(String value) {
    if (value.length() > MAX_HEADER_VALUE_BYTES) return "valueTooLong";
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character != '\t' && (character < 0x20 || character > 0x7e)) {
        return "invalidValueCharacter";
      }
    }
    return null;
  }

  static String normalizeHeaderName(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  static int utf8Length(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  static String percentEncode(String value, boolean preserveSlash) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    var result = new StringBuilder(percentEncodedLength(bytes, preserveSlash, Integer.MAX_VALUE));
    for (byte signed : bytes) {
      int valueByte = Byte.toUnsignedInt(signed);
      if (unreserved(valueByte) || preserveSlash && valueByte == '/') {
        result.append((char) valueByte);
      } else {
        result.append('%');
        result.append(Character.toUpperCase(Character.forDigit(valueByte >>> 4, 16)));
        result.append(Character.toUpperCase(Character.forDigit(valueByte & 15, 16)));
      }
    }
    return result.toString();
  }

  static int encodedQueryLength(java.util.List<HttpRequestValue.QueryItem> query) {
    long length = 0;
    for (var item : query) {
      if (length != 0) length++;
      length += percentEncodedLength(item.name(), false, Integer.MAX_VALUE);
      length++;
      length += percentEncodedLength(item.value(), false, Integer.MAX_VALUE);
      if (length > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    }
    return (int) length;
  }

  static String encodeQuery(java.util.List<HttpRequestValue.QueryItem> query) {
    var result = new StringBuilder(encodedQueryLength(query));
    for (var item : query) {
      if (!result.isEmpty()) result.append('&');
      result.append(percentEncode(item.name(), false));
      result.append('=');
      result.append(percentEncode(item.value(), false));
    }
    return result.toString();
  }

  static int percentEncodedLength(String value, boolean preserveSlash, int limit) {
    return percentEncodedLength(value.getBytes(StandardCharsets.UTF_8), preserveSlash, limit);
  }

  static int headerBytes(java.util.List<HttpRequestValue.Header> headers) {
    long length = 0;
    for (var header : headers) {
      length += headerFieldBytes(header.name(), header.value());
      if (length > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    }
    return (int) length;
  }

  static int headerFieldBytes(String normalizedName, String value) {
    return normalizedName.length() + value.length() + 3;
  }

  private static boolean unreserved(int value) {
    return value >= 'A' && value <= 'Z'
        || value >= 'a' && value <= 'z'
        || value >= '0' && value <= '9'
        || value == '-'
        || value == '.'
        || value == '_'
        || value == '~';
  }

  private static int percentEncodedLength(byte[] bytes, boolean preserveSlash, int limit) {
    long length = 0;
    for (byte signed : bytes) {
      int valueByte = Byte.toUnsignedInt(signed);
      length += unreserved(valueByte) || preserveSlash && valueByte == '/' ? 1 : 3;
      if (length > limit) {
        return limit == Integer.MAX_VALUE ? Integer.MAX_VALUE : limit + 1;
      }
    }
    return (int) length;
  }
}
