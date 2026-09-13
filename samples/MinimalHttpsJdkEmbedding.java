import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.CredentialReference;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.JdkHttpsTransport;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.MonotonicClock;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.StreamConsoleInput;
import jp.bsb.runtime.TraceSink;

/** 利用者が明示した実endpointで同じ参照BSBを実行する任意smoke例です。 */
public final class MinimalHttpsJdkEmbedding {
  private MinimalHttpsJdkEmbedding() {}

  public static void main(String[] arguments) throws Exception {
    String baseUri = requiredEnvironment("BSB_HTTPS_BASE_URI");
    String apiKey = requiredEnvironment("BSB_HTTPS_API_KEY");
    String apiKeyHeader = System.getenv().getOrDefault("BSB_HTTPS_API_KEY_HEADER", "x-api-key");
    URI parsed = URI.create(baseUri);
    String origin = parsed.getScheme() + "://" + parsed.getRawAuthority();
    var credential = CredentialReference.opaque();
    var policy =
        new ConnectionPolicy(
            baseUri,
            List.of(origin),
            List.of("POST"),
            "apiKey",
            Optional.of(credential),
            5_000,
            10_000,
            1_048_576,
            1_048_576,
            "deny",
            "none");
    var transport = JdkHttpsTransport.builder().apiKey(credential, apiKeyHeader, apiKey).build();
    MonotonicClock clock = MonotonicClock.system();
    var output = new MemoryOutputSink();
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleInput(new StreamConsoleInput(System.in))
            .consoleOutput(output::write)
            .connectionResolver((name, operation) -> ConnectionResolution.resolved(name, policy))
            .httpTransport(transport)
            .redactLogicalConnectionNamesInTrace()
            .build();
    var result =
        new ProgramRunner()
            .run(
                Path.of("samples/20-minimal-https.bsb"),
                new ExecutionContext(output, clock, TraceSink.none(), environment));
    System.out.write(output.bytes());
    if (result.exitCode() != 0) {
      System.err.println(result.diagnostics().getFirst().code().name());
    }
    System.exit(result.exitCode());
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) throw new IllegalArgumentException(name + " is required");
    return value;
  }
}
