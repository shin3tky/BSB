package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.ToolProvider;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.HttpTransportHeader;
import jp.bsb.runtime.HttpTransportResult;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** httpbin.org/ip利用者サンプルを、実ネットワークに依存しない公開境界で検査します。 */
class HttpbinIpSampleTest {
  @TempDir Path temporaryDirectory;

  @Test
  void sampleRequestsIpAndPrintsOnlyTheOriginFromAFakeResponse() throws Exception {
    var resolverCalls = new AtomicInteger();
    var transportCalls = new AtomicInteger();
    var output = new MemoryOutputSink();
    var policy =
        new ConnectionPolicy(
            "https://httpbin.org/",
            List.of("https://httpbin.org"),
            List.of("GET"),
            "none",
            Optional.empty(),
            5_000,
            10_000,
            1_048_576,
            1_048_576,
            "deny",
            "none");
    var environment =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleOutput(output::write)
            .connectionResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  assertEquals("HTTPBin", name);
                  assertEquals("resolve", operation);
                  return ConnectionResolution.resolved(name, policy);
                })
            .httpTransport(
                request -> {
                  transportCalls.incrementAndGet();
                  assertEquals("GET", request.method());
                  assertEquals("https://httpbin.org/ip", request.targetUri().toASCIIString());
                  assertEquals(
                      List.of(new HttpTransportHeader("accept", "application/json")),
                      request.headers());
                  assertTrue(request.bodyBytes().isEmpty());
                  return HttpTransportResult.response(
                      request.connectionName(),
                      request.method(),
                      200,
                      List.of(new HttpTransportHeader("content-type", "application/json")),
                      "{\"origin\":\"203.0.113.10\"}".getBytes(StandardCharsets.UTF_8));
                })
            .build();

    var result =
        new ProgramRunner()
            .run(
                Path.of("samples/21-httpbin-ip.bsb"),
                new ExecutionContext(output, () -> 0L, TraceSink.none(), environment));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertArrayEquals("203.0.113.10\n".getBytes(StandardCharsets.UTF_8), output.bytes());
    assertEquals(1, resolverCalls.get());
    assertEquals(1, transportCalls.get());
    assertEquals(1, result.httpSendCalls());
    assertEquals(0, result.httpRequestAttemptBytes());
  }

  @Test
  void jdkEmbeddingCompilesAgainstThePublicApi() {
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-encoding",
                "UTF-8",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                temporaryDirectory.toString(),
                "samples/HttpbinIpJdkEmbedding.java"));
  }
}
