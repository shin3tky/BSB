package jp.bsb.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/** 検証済みの最終status、同名header値列、完全本文を保持する不変HTTP応答です。 */
public final class HttpResponseValue implements RuntimeValue {
  private final int status;
  private final Map<String, List<String>> headers;
  private final ByteSequenceValue body;

  HttpResponseValue(int status, Map<String, List<String>> headers, ByteSequenceValue body) {
    if (status < 200 || status > 599) {
      throw new IllegalArgumentException("an HTTP response status must be between 200 and 599");
    }
    this.status = status;
    var copy = new LinkedHashMap<String, List<String>>();
    Objects.requireNonNull(headers, "headers")
        .forEach(
            (name, values) -> {
              String normalized = HttpRequestSupport.normalizeHeaderName(name);
              if (copy.put(normalized, List.copyOf(values)) != null) {
                throw new IllegalArgumentException("duplicate normalized response header");
              }
            });
    this.headers = Map.copyOf(copy);
    this.body = Objects.requireNonNull(body, "body");
  }

  HttpResponseValue(int status, List<HttpTransportHeader> orderedHeaders, ByteSequenceValue body) {
    if (status < 200 || status > 599) {
      throw new IllegalArgumentException("an HTTP response status must be between 200 and 599");
    }
    var copy = new LinkedHashMap<String, List<String>>();
    for (HttpTransportHeader header : orderedHeaders) {
      String normalized = HttpRequestSupport.normalizeHeaderName(header.name());
      var values = new java.util.ArrayList<>(copy.getOrDefault(normalized, List.of()));
      values.add(header.value());
      copy.put(normalized, List.copyOf(values));
    }
    this.status = status;
    this.headers = Map.copyOf(copy);
    this.body = Objects.requireNonNull(body, "body");
  }

  int status() {
    return status;
  }

  List<String> headerValues(String name) {
    return headers.getOrDefault(HttpRequestSupport.normalizeHeaderName(name), List.of());
  }

  ByteSequenceValue body() {
    return body;
  }

  @Override
  public ValueType type() {
    return ValueType.HTTP_RESPONSE;
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
