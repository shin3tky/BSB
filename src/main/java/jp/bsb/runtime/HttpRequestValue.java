package jp.bsb.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.stdlib.ValueType;

/** 接続名とmethodを含まず、設定ごとに新しい値を返す不変HTTP要求です。 */
public final class HttpRequestValue implements RuntimeValue {
  /** 本文の設定元です。 */
  public enum BodyKind {
    JSON,
    STRING,
    BYTES
  }

  /** 順序と重複を保つ問い合わせ項目です。 */
  public record QueryItem(String name, String value) {
    public QueryItem {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(value, "value");
    }
  }

  /** ASCII小文字へ正規化済みの要求ヘッダーです。 */
  public record Header(String name, String value) {
    public Header {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(value, "value");
    }
  }

  /** 長さ0と不在を区別する不変本文です。 */
  public record Body(BodyKind kind, ByteSequenceValue bytes) {
    public Body {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(bytes, "bytes");
    }
  }

  private static final HttpRequestValue EMPTY =
      new HttpRequestValue("", List.of(), List.of(), null);

  private final String path;
  private final List<QueryItem> query;
  private final List<Header> headers;
  private final Body body;

  private HttpRequestValue(String path, List<QueryItem> query, List<Header> headers, Body body) {
    this.path = Objects.requireNonNull(path, "path");
    this.query = List.copyOf(query);
    this.headers = List.copyOf(headers);
    this.body = body;
  }

  /** 空の要求を返します。 */
  public static HttpRequestValue empty() {
    return EMPTY;
  }

  HttpRequestValue withPath(String replacement) {
    return new HttpRequestValue(replacement, query, headers, body);
  }

  HttpRequestValue withQueryItem(String name, String value) {
    var replacement = new ArrayList<QueryItem>(query.size() + 1);
    replacement.addAll(query);
    replacement.add(new QueryItem(name, value));
    return new HttpRequestValue(path, replacement, headers, body);
  }

  HttpRequestValue withHeader(String normalizedName, String value) {
    var replacement = new ArrayList<Header>(headers.size() + 1);
    boolean replaced = false;
    for (Header header : headers) {
      if (header.name().equals(normalizedName)) {
        replacement.add(new Header(normalizedName, value));
        replaced = true;
      } else {
        replacement.add(header);
      }
    }
    if (!replaced) replacement.add(new Header(normalizedName, value));
    return new HttpRequestValue(path, query, replacement, body);
  }

  HttpRequestValue withBody(BodyKind kind, ByteSequenceValue bytes) {
    return new HttpRequestValue(path, query, headers, new Body(kind, bytes));
  }

  String path() {
    return path;
  }

  List<QueryItem> query() {
    return query;
  }

  List<Header> headers() {
    return headers;
  }

  Optional<Body> body() {
    return Optional.ofNullable(body);
  }

  @Override
  public ValueType type() {
    return ValueType.HTTP_REQUEST;
  }

  @Override
  public String displayText() {
    return "<redacted>";
  }

  @Override
  public String toString() {
    return traceText();
  }
}
