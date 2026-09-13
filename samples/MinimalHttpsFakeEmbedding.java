import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.CredentialReference;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.HttpTransportHeader;
import jp.bsb.runtime.HttpTransportResult;
import jp.bsb.runtime.InputEvent;
import jp.bsb.runtime.MemoryConsoleInput;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;

/** 同期HTTPSの参照BSBをネットワークなしで実行する偽能力例です。 */
public final class MinimalHttpsFakeEmbedding {
  private MinimalHttpsFakeEmbedding() {}

  public static void main(String[] arguments) throws Exception {
    byte[] input =
        (arguments.length == 0 ? "{\"name\":\"山田 太郎\"}" : arguments[0])
            .getBytes(StandardCharsets.UTF_8);
    var credential = CredentialReference.opaque();
    var resolverCalls = new AtomicInteger();
    var transportCalls = new AtomicInteger();
    var output = new MemoryOutputSink();
    var clock = (jp.bsb.runtime.MonotonicClock) () -> 0L;
    var policy =
        new ConnectionPolicy(
            "https://api.example.invalid/",
            List.of("https://api.example.invalid"),
            List.of("POST"),
            "apiKey",
            Optional.of(credential),
            1_000,
            2_000,
            65_536,
            65_536,
            "deny",
            "none");
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleInput(new MemoryConsoleInput(List.of(new InputEvent.Line(input, input.length))))
            .consoleOutput(output::write)
            .connectionResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  return ConnectionResolution.resolved(name, policy);
                })
            .httpTransport(
                request -> {
                  transportCalls.incrementAndGet();
                  if (!request.policy().credentialReference().orElseThrow().equals(credential)
                      || !request.method().equals("POST")
                      || !request.targetUri().toASCIIString().endsWith("/v1/customers")
                      || !request.headers().contains(new HttpTransportHeader("content-type", "application/json"))) {
                    throw new AssertionError("unexpected HTTP request");
                  }
                  return HttpTransportResult.response(
                      request.connectionName(),
                      request.method(),
                      201,
                      List.of(new HttpTransportHeader("content-type", "application/json")),
                      "{\"accepted\":true,\"id\":\"C-100\"}"
                          .getBytes(StandardCharsets.UTF_8));
                })
            .build();
    var result =
        new ProgramRunner()
            .run(
                Path.of("samples/20-minimal-https.bsb"),
                new ExecutionContext(output, clock, TraceSink.none(), environment));
    System.out.write(output.bytes());
    System.out.printf(
        "exit=%d resolverCalls=%d transportCalls=%d%n",
        result.exitCode(), resolverCalls.get(), transportCalls.get());
  }
}
