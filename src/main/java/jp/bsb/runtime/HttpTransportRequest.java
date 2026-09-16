package jp.bsb.runtime;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 検証済みの対象・方針・要求内容をHTTP能力へ渡す不変値です。 */
public final class HttpTransportRequest {
  private final String connectionName;
  private final String method;
  private final URI targetUri;
  private final List<HttpTransportHeader> headers;
  private final Optional<ByteSequenceValue> body;
  private final ConnectionPolicy policy;
  private final long responseBodyLimit;
  private final long decodedBodyLimit;
  private final boolean responseLimitIsExecutionTotal;

  HttpTransportRequest(
      String connectionName,
      String method,
      URI targetUri,
      List<HttpTransportHeader> headers,
      Optional<ByteSequenceValue> body,
      ConnectionPolicy policy,
      long responseBodyLimit,
      long decodedBodyLimit,
      boolean responseLimitIsExecutionTotal) {
    this.connectionName = Objects.requireNonNull(connectionName, "connectionName");
    this.method = Objects.requireNonNull(method, "method");
    this.targetUri = Objects.requireNonNull(targetUri, "targetUri");
    this.headers = List.copyOf(headers);
    this.body = Objects.requireNonNull(body, "body");
    this.policy = Objects.requireNonNull(policy, "policy");
    if (responseBodyLimit < 0) {
      throw new IllegalArgumentException("response body limit must not be negative");
    }
    this.responseBodyLimit = responseBodyLimit;
    if (decodedBodyLimit < 0) {
      throw new IllegalArgumentException("decoded body limit must not be negative");
    }
    this.decodedBodyLimit = decodedBodyLimit;
    this.responseLimitIsExecutionTotal = responseLimitIsExecutionTotal;
  }

  HttpTransportRequest(
      String connectionName,
      String method,
      URI targetUri,
      List<HttpTransportHeader> headers,
      Optional<ByteSequenceValue> body,
      ConnectionPolicy policy,
      long responseBodyLimit,
      boolean responseLimitIsExecutionTotal) {
    this(
        connectionName,
        method,
        targetUri,
        headers,
        body,
        policy,
        responseBodyLimit,
        policy.maximumResponseBytes(),
        responseLimitIsExecutionTotal);
  }

  /**
   * @return ソース上の論理接続名
   */
  public String connectionName() {
    return connectionName;
  }

  /**
   * @return 大文字ASCIIの静的HTTP method
   */
  public String method() {
    return method;
  }

  /**
   * @return 検証済み絶対HTTPS対象
   */
  public URI targetUri() {
    return targetUri;
  }

  /**
   * @return 正規化済み利用者ヘッダーの不変リスト
   */
  public List<HttpTransportHeader> headers() {
    return headers;
  }

  /**
   * @return 本文がある場合、その防御コピー
   */
  public Optional<byte[]> bodyBytes() {
    return body.map(ByteSequenceValue::copyBytes);
  }

  Optional<ByteSequenceValue> bodyValue() {
    return body;
  }

  /**
   * @return 再検証済み接続方針
   */
  public ConnectionPolicy policy() {
    return policy;
  }

  /**
   * @return この呼出しで読取りを許す応答本文バイト数
   */
  public long responseBodyLimit() {
    return responseBodyLimit;
  }

  /**
   * @return 自動展開後にBSBへ渡してよい本文バイト数
   */
  public long decodedBodyLimit() {
    return decodedBodyLimit;
  }

  /**
   * @return 同値を含め、応答本文上限を全実行受信残量が決めた場合はtrue
   */
  public boolean responseLimitIsExecutionTotal() {
    return responseLimitIsExecutionTotal;
  }

  /** URI・header・本文・方針を漏らさない安全表現だけを返します。 */
  @Override
  public String toString() {
    return "HttpTransportRequest[connectionName=" + connectionName + ", method=" + method + "]";
  }
}
