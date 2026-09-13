package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JdkHttpsTransportTest {
  @Test
  void safetyPreflightAcceptsOnlyRetryHostnameAndLoggingSafeProperties() {
    Map<String, String> valid = Map.of("jdk.httpclient.disableRetryConnect", "true");
    JdkHttpsTransport.checkProcessProperties(valid);
    for (Map<String, String> invalid :
        List.of(
            Map.<String, String>of(),
            Map.of("jdk.httpclient.disableRetryConnect", "false"),
            Map.of(
                "jdk.httpclient.disableRetryConnect",
                "true",
                "jdk.httpclient.enableAllMethodRetry",
                "true"),
            Map.of(
                "jdk.httpclient.disableRetryConnect",
                "true",
                "jdk.internal.httpclient.disableHostnameVerification",
                "true"),
            Map.of(
                "jdk.httpclient.disableRetryConnect",
                "true",
                "jdk.httpclient.HttpClient.log",
                "headers"))) {
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class, () -> JdkHttpsTransport.checkProcessProperties(invalid));
      assertEquals("JDK HTTPS transport safety preflight failed", failure.getMessage());
      assertFalse(failure.toString().contains("headers"));
    }
  }

  @Test
  void configuresRedirectProxyTlsAndBothTimeoutsWithoutImplicitHeaders() {
    HttpClient client = JdkHttpsTransport.newClient(1_234);
    assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
    assertEquals(1_234, client.connectTimeout().orElseThrow().toMillis());
    assertEquals(
        List.of(Proxy.NO_PROXY),
        client.proxy().orElseThrow().select(URI.create("https://api.example.test/")));
    assertArrayEquals(new String[] {"TLSv1.3", "TLSv1.2"}, client.sslParameters().getProtocols());
    assertEquals("HTTPS", client.sslParameters().getEndpointIdentificationAlgorithm());

    HttpTransportRequest source =
        request(
            "none",
            Optional.empty(),
            List.of(new HttpTransportHeader("accept", "application/json")),
            8,
            8,
            false);
    HttpRequest request = JdkHttpsTransport.newRequest(source, source.headers());
    assertEquals("POST", request.method());
    assertEquals(2_000, request.timeout().orElseThrow().toMillis());
    assertEquals(List.of("application/json"), request.headers().allValues("accept"));
    assertTrue(request.headers().allValues("accept-encoding").isEmpty());
    assertEquals(4, request.bodyPublisher().orElseThrow().contentLength());
  }

  @Test
  void removesHttp2PseudoHeadersAtTheJdkAdapterBoundary() {
    assertEquals(
        List.of(
            new HttpTransportHeader("content-type", "application/json"),
            new HttpTransportHeader("Set-Cookie", "a=1"),
            new HttpTransportHeader("Set-Cookie", "b=2")),
        JdkHttpsTransport.adaptResponseHeaders(
            Map.of(
                ":status", List.of("200"),
                "Set-Cookie", List.of("a=1", "b=2"),
                "content-type", List.of("application/json"))));
  }

  @Test
  void appliesApiKeyByIdentityOnlyAtTheClientBoundaryAndReplacesUserHeader() {
    String secret = "SECRET_API_KEY_TEST";
    CredentialReference reference = CredentialReference.opaque();
    var captured = new AtomicReference<List<HttpTransportHeader>>();
    JdkHttpsTransport transport =
        JdkHttpsTransport.builder()
            .apiKey(reference, "X-API-Key", secret)
            .client(
                (request, headers) -> {
                  captured.set(headers);
                  return response(200, List.of(), new byte[0]);
                })
            .buildForTesting();
    HttpTransportResult result =
        transport.send(
            request(
                "apiKey",
                Optional.of(reference),
                List.of(new HttpTransportHeader("x-api-key", "public")),
                64,
                64,
                false));

    assertEquals(HttpTransportResult.State.RESPONSE, result.state());
    assertEquals(List.of(new HttpTransportHeader("x-api-key", secret)), captured.get());
    assertFalse(result.toString().contains(secret));
    assertFalse(transport.toString().contains(secret));
  }

  @Test
  void keepsCredentialMissingDeniedAndInvalidOutsideTransportFailures() {
    CredentialReference missing = CredentialReference.opaque();
    CredentialReference denied = CredentialReference.opaque();
    CredentialReference badName = CredentialReference.opaque();
    CredentialReference badValue = CredentialReference.opaque();
    JdkHttpsTransport transport =
        JdkHttpsTransport.builder()
            .denyCredential(denied)
            .apiKey(badName, "Host", "value")
            .apiKey(badValue, "x-api-key", "bad\nvalue")
            .client((request, headers) -> response(200, List.of(), new byte[0]))
            .buildForTesting();

    assertEquals(
        HttpTransportResult.State.CREDENTIAL_NOT_CONFIGURED,
        transport.send(request("apiKey", Optional.of(missing), List.of(), 1, 1, false)).state());
    assertEquals(
        HttpTransportResult.State.CREDENTIAL_DENIED,
        transport.send(request("apiKey", Optional.of(denied), List.of(), 1, 1, false)).state());
    HttpTransportResult name =
        transport.send(request("apiKey", Optional.of(badName), List.of(), 1, 1, false));
    assertEquals(HttpTransportResult.State.CREDENTIAL_INVALID, name.state());
    assertEquals("HEADER_NAME_INVALID", name.credentialInvalidReason().orElseThrow());
    HttpTransportResult value =
        transport.send(request("apiKey", Optional.of(badValue), List.of(), 1, 1, false));
    assertEquals("HEADER_VALUE_INVALID", value.credentialInvalidReason().orElseThrow());
  }

  @Test
  void preservesFinalStatusDuplicateHeadersAndBinaryBody() {
    byte[] body = {0, (byte) 0xff, 1};
    JdkHttpsTransport transport =
        transport(
            (request, headers) ->
                response(
                    503,
                    List.of(
                        new HttpTransportHeader("Set-Cookie", "a=1"),
                        new HttpTransportHeader("set-cookie", "b=2")),
                    body));

    HttpTransportResult result =
        transport.send(request("none", Optional.empty(), List.of(), 8, 8, false));

    assertEquals(HttpTransportResult.State.RESPONSE, result.state());
    assertEquals(503, result.status().orElseThrow());
    assertEquals("set-cookie", result.headers().getFirst().name());
    assertArrayEquals(body, result.body().orElseThrow().copyBytes());
    assertEquals(3, result.receivedBodyBytes());
  }

  @Test
  void boundsHeadersContentLengthAndStreamingWithoutKeepingPartialResponses() {
    var tooMany = new ArrayList<HttpTransportHeader>();
    for (int index = 0; index <= HttpLimits.MAX_RESPONSE_HEADER_VALUES; index++) {
      tooMany.add(new HttpTransportHeader("x-" + index, "v"));
    }
    HttpTransportResult headers =
        transport((request, sent) -> response(200, tooMany, new byte[0]))
            .send(request("none", Optional.empty(), List.of(), 8, 8, false));
    assertFailure(headers, "responseHeadersTooLarge", 0);

    HttpTransportResult policyLength =
        transport(
                (request, sent) ->
                    response(
                        200, List.of(new HttpTransportHeader("content-length", "9")), new byte[0]))
            .send(request("none", Optional.empty(), List.of(), 8, 8, false));
    assertFailure(policyLength, "responseTooLarge", 0);

    HttpTransportResult totalLength =
        transport(
                (request, sent) ->
                    response(
                        200, List.of(new HttpTransportHeader("content-length", "8")), new byte[0]))
            .send(request("none", Optional.empty(), List.of(), 16, 7, true));
    assertTrue(totalLength.knownResponseTotalBytes().isPresent());
    assertEquals(8, totalLength.knownResponseTotalBytes().orElseThrow());
    assertEquals(0, totalLength.receivedBodyBytes());

    HttpTransportResult streamed =
        transport((request, sent) -> response(200, List.of(), new byte[9]))
            .send(request("none", Optional.empty(), List.of(), 8, 8, true));
    assertFailure(streamed, "responseTooLarge", 9);
  }

  @Test
  void acceptsExactResponseHeaderLimitsAndRejectsTheNextByte() {
    var exact = new ArrayList<HttpTransportHeader>();
    for (int index = 0; index < 7; index++) {
      exact.add(new HttpTransportHeader("x", "v".repeat(8_192)));
    }
    exact.add(new HttpTransportHeader("x", "v".repeat(8_160)));
    HttpTransportRequest request = request("none", Optional.empty(), List.of(), 1, 1, false);

    HttpTransportResult accepted =
        transport((ignored, sent) -> response(200, exact, new byte[0])).send(request);
    assertEquals(HttpTransportResult.State.RESPONSE, accepted.state());
    assertEquals(8, accepted.headers().size());

    exact.set(7, new HttpTransportHeader("x", "v".repeat(8_161)));
    HttpTransportResult rejected =
        transport((ignored, sent) -> response(200, exact, new byte[0])).send(request);
    assertFailure(rejected, "responseHeadersTooLarge", 0);
  }

  @Test
  void streamsTheMaximumBodyAndStopsAtTheFirstExcessByteWithinTheHeapBoundary() {
    int maximum = ByteSequenceLimits.MAX_VALUE_BYTES;
    HttpTransportRequest request =
        request("none", Optional.empty(), List.of(), maximum, maximum, false);
    HttpTransportResult accepted =
        transport(
                (ignored, sent) ->
                    new JdkHttpsTransport.RawResponse(
                        200, List.of(), new RepeatedByteInput(maximum)))
            .send(request);
    assertEquals(HttpTransportResult.State.RESPONSE, accepted.state());
    assertEquals(maximum, accepted.body().orElseThrow().length());

    HttpTransportResult rejected =
        transport(
                (ignored, sent) ->
                    new JdkHttpsTransport.RawResponse(
                        200, List.of(), new RepeatedByteInput((long) maximum + 1)))
            .send(request);
    assertFailure(rejected, "responseTooLarge", (long) maximum + 1);
  }

  @ParameterizedTest
  @MethodSource("ioFailures")
  void classifiesOnlyKnownExceptionTypesWithoutUsingTheirMessages(
      IOException failure, String expectedKind) {
    JdkHttpsTransport transport =
        transport(
            (request, headers) -> {
              throw failure;
            });
    HttpTransportResult result =
        transport.send(request("none", Optional.empty(), List.of(), 1, 1, false));
    assertFailure(result, expectedKind, 0);
    assertFalse(result.toString().contains("SECRET_EXCEPTION"));
  }

  @Test
  void interruptionBecomesCancellationAndRestoresTheInterruptFlag() {
    Thread.interrupted();
    JdkHttpsTransport transport =
        transport(
            (request, headers) -> {
              throw new InterruptedException("SECRET_INTERRUPTION");
            });

    HttpTransportResult result =
        transport.send(request("none", Optional.empty(), List.of(), 1, 1, false));

    assertEquals(HttpTransportResult.State.CANCELLED, result.state());
    assertTrue(Thread.interrupted());
    assertFalse(result.toString().contains("SECRET_INTERRUPTION"));
  }

  @Test
  void rejectsNonHttpsTargetsBeforeCallingTheClient() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    JdkHttpsTransport transport =
        transport(
            (request, headers) -> {
              calls.incrementAndGet();
              return response(200, List.of(), new byte[0]);
            });
    HttpTransportRequest request =
        new HttpTransportRequest(
            "接続",
            "POST",
            URI.create("http://api.example.test/"),
            List.of(),
            Optional.empty(),
            policy("none", Optional.empty(), 1),
            1,
            false);

    assertThrows(IllegalStateException.class, () -> transport.send(request));
    assertEquals(0, calls.get());
  }

  private static Stream<Arguments> ioFailures() {
    return Stream.of(
        Arguments.of(new HttpConnectTimeoutException("SECRET_EXCEPTION"), "connectTimeout"),
        Arguments.of(new HttpTimeoutException("SECRET_EXCEPTION"), "responseTimeout"),
        Arguments.of(new SSLHandshakeException("SECRET_EXCEPTION"), "tlsFailure"),
        Arguments.of(new UnknownHostException("SECRET_EXCEPTION"), "nameResolutionFailure"),
        Arguments.of(new ConnectException("SECRET_EXCEPTION"), "connectionFailure"),
        Arguments.of(new ProtocolException("SECRET_EXCEPTION"), "protocolFailure"),
        Arguments.of(new IOException("SECRET_EXCEPTION"), "transportFailure"));
  }

  private static JdkHttpsTransport transport(JdkHttpsTransport.ClientAdapter adapter) {
    return JdkHttpsTransport.builder().client(adapter).buildForTesting();
  }

  private static JdkHttpsTransport.RawResponse response(
      int status, List<HttpTransportHeader> headers, byte[] body) {
    return new JdkHttpsTransport.RawResponse(
        status, headers, new ByteArrayInputStream(body.clone()));
  }

  private static HttpTransportRequest request(
      String authentication,
      Optional<CredentialReference> credential,
      List<HttpTransportHeader> headers,
      long policyLimit,
      long effectiveLimit,
      boolean executionTotal) {
    return new HttpTransportRequest(
        "顧客管理API",
        "POST",
        URI.create("https://api.example.test/v1/items"),
        headers,
        Optional.of(ByteSequenceValue.copyOf("body".getBytes(StandardCharsets.UTF_8))),
        policy(authentication, credential, policyLimit),
        effectiveLimit,
        executionTotal);
  }

  private static ConnectionPolicy policy(
      String authentication, Optional<CredentialReference> credential, long responseLimit) {
    return new ConnectionPolicy(
        "https://api.example.test/",
        List.of("https://api.example.test"),
        List.of("POST"),
        authentication,
        credential,
        1_000,
        2_000,
        64,
        responseLimit,
        "deny",
        "none");
  }

  private static void assertFailure(HttpTransportResult result, String kind, long received) {
    assertEquals(HttpTransportResult.State.FAILURE, result.state());
    assertEquals(kind, result.failureKind().orElseThrow());
    assertEquals(received, result.receivedBodyBytes());
    assertTrue(result.body().isEmpty());
  }

  private static final class RepeatedByteInput extends InputStream {
    private long remaining;

    private RepeatedByteInput(long remaining) {
      this.remaining = remaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (remaining == 0) return -1;
      int count = (int) Math.min(remaining, length);
      java.util.Arrays.fill(buffer, offset, offset + count, (byte) 0x5a);
      remaining -= count;
      return count;
    }

    @Override
    public int read() {
      if (remaining == 0) return -1;
      remaining--;
      return 0x5a;
    }
  }
}
