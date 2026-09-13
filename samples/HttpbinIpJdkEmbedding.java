import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.JdkHttpsTransport;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.MonotonicClock;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;

/** httpbin.org/ipから呼出し元のIPアドレス表現を取得する実HTTPS例です。 */
public final class HttpbinIpJdkEmbedding {
  private HttpbinIpJdkEmbedding() {}

  public static void main(String[] arguments) throws Exception {
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
    MonotonicClock clock = MonotonicClock.system();
    var output = new MemoryOutputSink();
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .connectionResolver(
                (name, operation) -> ConnectionResolution.resolved(name, policy))
            .httpTransport(JdkHttpsTransport.builder().build())
            .redactLogicalConnectionNamesInTrace()
            .build();
    var result =
        new ProgramRunner()
            .run(
                Path.of("samples/21-httpbin-ip.bsb"),
                new ExecutionContext(output, clock, TraceSink.none(), environment));
    System.out.write(output.bytes());
    if (result.exitCode() != 0) {
      System.err.println(result.diagnostics().getFirst().code().name());
    }
    System.exit(result.exitCode());
  }
}
