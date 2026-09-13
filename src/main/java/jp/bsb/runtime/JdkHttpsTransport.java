package jp.bsb.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;

/** JDK標準クライアントで、検証済み要求だけを同期HTTPS送信する能力です。 */
public final class JdkHttpsTransport implements HttpTransport {
  private static final String RETRY_DISABLED = "jdk.httpclient.disableRetryConnect";
  private static final String ALL_METHOD_RETRY = "jdk.httpclient.enableAllMethodRetry";
  private static final String HOSTNAME_VERIFICATION_DISABLED =
      "jdk.internal.httpclient.disableHostnameVerification";
  private static final String HTTP_LOG = "jdk.httpclient.HttpClient.log";

  private final ClientAdapter client;
  private final IdentityHashMap<CredentialReference, ApiKey> apiKeys;
  private final Set<CredentialReference> deniedCredentials;

  private JdkHttpsTransport(
      ClientAdapter client,
      IdentityHashMap<CredentialReference, ApiKey> apiKeys,
      Set<CredentialReference> deniedCredentials) {
    this.client = Objects.requireNonNull(client, "client");
    this.apiKeys = new IdentityHashMap<>(apiKeys);
    var denied = Collections.newSetFromMap(new IdentityHashMap<CredentialReference, Boolean>());
    denied.addAll(deniedCredentials);
    this.deniedCredentials = Collections.unmodifiableSet(denied);
  }

  /** 既定PKIX設定だけを使うtransportビルダーを作ります。 */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public HttpTransportResult send(HttpTransportRequest request) {
    Objects.requireNonNull(request, "request");
    if (!"https".equals(request.targetUri().getScheme())) {
      throw new IllegalStateException("HTTP transport received a non-HTTPS target");
    }
    List<HttpTransportHeader> headers;
    try {
      headers = authenticatedHeaders(request);
    } catch (CredentialProblem problem) {
      return HttpTransportResult.credentialInvalid(
          request.connectionName(), request.method(), problem.reason);
    }
    if (headers == null) {
      CredentialReference reference = request.policy().credentialReference().orElseThrow();
      if (deniedCredentials.contains(reference)) {
        return HttpTransportResult.credentialDenied(request.connectionName(), request.method());
      }
      return HttpTransportResult.credentialNotConfigured(
          request.connectionName(), request.method());
    }
    try (RawResponse response = client.send(request, headers)) {
      return consume(request, response);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      return HttpTransportResult.cancelled(request.connectionName(), request.method());
    } catch (IOException failure) {
      return HttpTransportResult.failure(
          request.connectionName(), request.method(), classify(failure));
    }
  }

  private List<HttpTransportHeader> authenticatedHeaders(HttpTransportRequest request) {
    if (request.policy().authenticationKind().equals("none")) {
      return request.headers();
    }
    if (!request.policy().authenticationKind().equals("apiKey")) {
      throw new IllegalStateException("HTTP transport received unsupported authentication");
    }
    CredentialReference reference = request.policy().credentialReference().orElseThrow();
    if (deniedCredentials.contains(reference)) return null;
    ApiKey apiKey = apiKeys.get(reference);
    if (apiKey == null) return null;
    String nameProblem = HttpRequestSupport.headerNameProblem(apiKey.headerName);
    if (nameProblem != null || HttpRequestSupport.RESERVED_HEADERS.contains(apiKey.headerName)) {
      return credentialFailure(request, "HEADER_NAME_INVALID");
    }
    String valueProblem = HttpRequestSupport.headerValueProblem(apiKey.headerValue);
    if (apiKey.headerValue.isEmpty() || valueProblem != null) {
      return credentialFailure(request, "HEADER_VALUE_INVALID");
    }
    var result = new ArrayList<HttpTransportHeader>();
    for (HttpTransportHeader header : request.headers()) {
      if (!header.name().equals(apiKey.headerName)) result.add(header);
    }
    result.add(new HttpTransportHeader(apiKey.headerName, apiKey.headerValue));
    return List.copyOf(result);
  }

  /**
   * APIキーheader名と値を送信時と同じ規則で検証します。
   *
   * @return 有効なら空、無効なら秘密を含まない安定理由
   */
  public static Optional<String> apiKeyValidationProblem(String headerName, String headerValue) {
    Objects.requireNonNull(headerName, "headerName");
    Objects.requireNonNull(headerValue, "headerValue");
    String normalized = HttpRequestSupport.normalizeHeaderName(headerName);
    if (HttpRequestSupport.headerNameProblem(normalized) != null
        || HttpRequestSupport.RESERVED_HEADERS.contains(normalized)) {
      return Optional.of("HEADER_NAME_INVALID");
    }
    if (headerValue.isEmpty() || HttpRequestSupport.headerValueProblem(headerValue) != null) {
      return Optional.of("HEADER_VALUE_INVALID");
    }
    return Optional.empty();
  }

  private static List<HttpTransportHeader> credentialFailure(
      HttpTransportRequest request, String reason) {
    Objects.requireNonNull(request, "request");
    throw new CredentialProblem(reason);
  }

  private static HttpTransportResult consume(HttpTransportRequest request, RawResponse response)
      throws IOException {
    int status = response.status();
    if (status < 200 || status > 599) {
      return HttpTransportResult.failure(
          request.connectionName(), request.method(), "protocolFailure");
    }
    HeaderCheck checked = checkResponseHeaders(response.headers());
    if (checked.failureKind != null) {
      return HttpTransportResult.failure(
          request.connectionName(), request.method(), checked.failureKind);
    }
    OptionalLong contentLength = contentLength(checked.headers);
    if (contentLength == null) {
      return HttpTransportResult.failure(
          request.connectionName(), request.method(), "protocolFailure");
    }
    if (contentLength.isPresent()) {
      long declared = contentLength.orElseThrow();
      if (declared > request.policy().maximumResponseBytes()) {
        return HttpTransportResult.failure(
            request.connectionName(), request.method(), "responseTooLarge");
      }
      if (declared > request.responseBodyLimit()) {
        return HttpTransportResult.knownResponseTotalExceeded(
            request.connectionName(), request.method(), declared);
      }
    }
    byte[] body = readBody(response.body(), request.responseBodyLimit());
    if (body.length > request.responseBodyLimit()) {
      return HttpTransportResult.failure(
          request.connectionName(), request.method(), "responseTooLarge", body.length);
    }
    return HttpTransportResult.response(
        request.connectionName(), request.method(), status, checked.headers, body);
  }

  private static HeaderCheck checkResponseHeaders(List<HttpTransportHeader> headers) {
    if (headers.size() > HttpLimits.MAX_RESPONSE_HEADER_VALUES) {
      return new HeaderCheck(List.of(), "responseHeadersTooLarge");
    }
    long bytes = 0;
    var normalized = new ArrayList<HttpTransportHeader>(headers.size());
    for (HttpTransportHeader header : headers) {
      String name = HttpRequestSupport.normalizeHeaderName(header.name());
      if (HttpRequestSupport.headerNameProblem(header.name()) != null
          || HttpRequestSupport.headerValueProblem(header.value()) != null) {
        return new HeaderCheck(List.of(), "protocolFailure");
      }
      bytes += HttpRequestSupport.headerFieldBytes(name, header.value());
      if (bytes > HttpLimits.MAX_RESPONSE_HEADER_BYTES) {
        return new HeaderCheck(List.of(), "responseHeadersTooLarge");
      }
      normalized.add(new HttpTransportHeader(name, header.value()));
    }
    return new HeaderCheck(List.copyOf(normalized), null);
  }

  private static OptionalLong contentLength(List<HttpTransportHeader> headers) {
    var values =
        headers.stream()
            .filter(header -> header.name().equals("content-length"))
            .map(HttpTransportHeader::value)
            .toList();
    if (values.isEmpty()) return OptionalLong.empty();
    if (values.size() != 1 || values.getFirst().isEmpty()) return null;
    long value = 0;
    for (int index = 0; index < values.getFirst().length(); index++) {
      char character = values.getFirst().charAt(index);
      if (character < '0' || character > '9' || value > (Long.MAX_VALUE - 9) / 10) return null;
      value = value * 10 + character - '0';
    }
    return OptionalLong.of(value);
  }

  private static byte[] readBody(InputStream input, long limit) throws IOException {
    int maximum = Math.toIntExact(limit + 1);
    var output = new ByteArrayOutputStream(Math.min(maximum, 8_192));
    byte[] buffer = new byte[8_192];
    while (output.size() < maximum) {
      int requested = Math.min(buffer.length, maximum - output.size());
      int read = input.read(buffer, 0, requested);
      if (read < 0) break;
      if (read == 0) {
        int single = input.read();
        if (single < 0) break;
        output.write(single);
      } else {
        output.write(buffer, 0, read);
      }
    }
    return output.toByteArray();
  }

  private static String classify(IOException failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof HttpConnectTimeoutException) return "connectTimeout";
      if (current instanceof HttpTimeoutException) return "responseTimeout";
      if (current instanceof SSLHandshakeException || current instanceof SSLException) {
        return "tlsFailure";
      }
      if (current instanceof UnknownHostException
          || current instanceof UnresolvedAddressException) {
        return "nameResolutionFailure";
      }
      if (current instanceof ConnectException || current instanceof NoRouteToHostException) {
        return "connectionFailure";
      }
      if (current instanceof java.net.ProtocolException) return "protocolFailure";
    }
    return "transportFailure";
  }

  static void checkProcessProperties(Map<String, String> properties) {
    if (!"true".equals(properties.get(RETRY_DISABLED))
        || !absentOrFalse(properties.get(ALL_METHOD_RETRY))
        || !absentOrFalse(properties.get(HOSTNAME_VERIFICATION_DISABLED))
        || !absentOrEmpty(properties.get(HTTP_LOG))) {
      throw new IllegalStateException("JDK HTTPS transport safety preflight failed");
    }
  }

  private static boolean absentOrFalse(String value) {
    return value == null || value.equals("false");
  }

  private static boolean absentOrEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private static Map<String, String> processProperties() {
    var result = new LinkedHashMap<String, String>();
    for (String name :
        List.of(RETRY_DISABLED, ALL_METHOD_RETRY, HOSTNAME_VERIFICATION_DISABLED, HTTP_LOG)) {
      String value = System.getProperty(name);
      if (value != null) result.put(name, value);
    }
    return result;
  }

  static HttpClient newClient(long connectTimeoutMilliseconds) {
    var ssl = new SSLParameters();
    ssl.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
    ssl.setEndpointIdentificationAlgorithm("HTTPS");
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMilliseconds))
        .followRedirects(HttpClient.Redirect.NEVER)
        .proxy(NoProxySelector.INSTANCE)
        .sslParameters(ssl)
        .build();
  }

  static HttpRequest newRequest(HttpTransportRequest request, List<HttpTransportHeader> headers) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(request.targetUri())
            .timeout(Duration.ofMillis(request.policy().responseTimeoutMilliseconds()));
    headers.forEach(header -> builder.header(header.name(), header.value()));
    HttpRequest.BodyPublisher publisher =
        request
            .bodyBytes()
            .map(HttpRequest.BodyPublishers::ofByteArray)
            .orElseGet(HttpRequest.BodyPublishers::noBody);
    return builder.method(request.method(), publisher).build();
  }

  /** JDK HTTPS transportの構築器です。 */
  public static final class Builder {
    private final IdentityHashMap<CredentialReference, ApiKey> apiKeys = new IdentityHashMap<>();
    private final Set<CredentialReference> deniedCredentials =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private ClientAdapter client = new JdkClientAdapter();

    private Builder() {}

    /** 不透明参照へAPIキーヘッダーを対応させます。 */
    public Builder apiKey(CredentialReference reference, String headerName, String headerValue) {
      Objects.requireNonNull(reference, "reference");
      String normalized =
          HttpRequestSupport.normalizeHeaderName(Objects.requireNonNull(headerName, "headerName"));
      apiKeys.put(
          reference, new ApiKey(normalized, Objects.requireNonNull(headerValue, "headerValue")));
      deniedCredentials.remove(reference);
      return this;
    }

    /** 不透明参照の資格情報利用を明示的に拒否します。 */
    public Builder denyCredential(CredentialReference reference) {
      Objects.requireNonNull(reference, "reference");
      apiKeys.remove(reference);
      deniedCredentials.add(reference);
      return this;
    }

    Builder client(ClientAdapter value) {
      client = Objects.requireNonNull(value, "value");
      return this;
    }

    /** process-wide安全条件を検査し、transportを構築します。 */
    public JdkHttpsTransport build() {
      checkProcessProperties(processProperties());
      return new JdkHttpsTransport(client, apiKeys, deniedCredentials);
    }

    /**
     * CLIのようにJVM全体を所有するホスト向けに、未設定の接続再試行を安全値へ固定して構築します。
     *
     * <p>既に設定された値や他の安全条件は上書きせず、通常のpreflightで拒否します。
     */
    public JdkHttpsTransport buildForOwnedProcess() {
      if (System.getProperty(RETRY_DISABLED) == null) {
        System.setProperty(RETRY_DISABLED, "true");
      }
      return build();
    }

    JdkHttpsTransport buildForTesting() {
      return new JdkHttpsTransport(client, apiKeys, deniedCredentials);
    }
  }

  interface ClientAdapter {
    RawResponse send(HttpTransportRequest request, List<HttpTransportHeader> headers)
        throws IOException, InterruptedException;
  }

  record RawResponse(int status, List<HttpTransportHeader> headers, InputStream body)
      implements AutoCloseable {
    RawResponse {
      headers = List.copyOf(headers);
      Objects.requireNonNull(body, "body");
    }

    @Override
    public void close() throws IOException {
      body.close();
    }
  }

  static List<HttpTransportHeader> adaptResponseHeaders(Map<String, List<String>> source) {
    Objects.requireNonNull(source, "source");
    var responseHeaders = new ArrayList<HttpTransportHeader>();
    source.entrySet().stream()
        .filter(entry -> !entry.getKey().startsWith(":"))
        .sorted(Comparator.comparing(entry -> entry.getKey().toLowerCase(java.util.Locale.ROOT)))
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        value ->
                            responseHeaders.add(new HttpTransportHeader(entry.getKey(), value))));
    return List.copyOf(responseHeaders);
  }

  private static final class JdkClientAdapter implements ClientAdapter {
    @Override
    public RawResponse send(HttpTransportRequest request, List<HttpTransportHeader> headers)
        throws IOException, InterruptedException {
      HttpResponse<InputStream> response =
          newClient(request.policy().connectTimeoutMilliseconds())
              .send(newRequest(request, headers), HttpResponse.BodyHandlers.ofInputStream());
      return new RawResponse(
          response.statusCode(), adaptResponseHeaders(response.headers().map()), response.body());
    }
  }

  private static final class NoProxySelector extends ProxySelector {
    private static final NoProxySelector INSTANCE = new NoProxySelector();

    private NoProxySelector() {}

    @Override
    public List<Proxy> select(URI uri) {
      Objects.requireNonNull(uri, "uri");
      return List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
      Objects.requireNonNull(uri, "uri");
      Objects.requireNonNull(address, "address");
      Objects.requireNonNull(failure, "failure");
    }
  }

  private record ApiKey(String headerName, String headerValue) {
    private ApiKey {
      Objects.requireNonNull(headerName, "headerName");
      Objects.requireNonNull(headerValue, "headerValue");
    }

    @Override
    public String toString() {
      return "<api-key>";
    }
  }

  private record HeaderCheck(List<HttpTransportHeader> headers, String failureKind) {}

  private static final class CredentialProblem extends RuntimeException {
    private final String reason;

    private CredentialProblem(String reason) {
      super("invalid API key credential", null, false, false);
      this.reason = reason;
    }
  }
}
