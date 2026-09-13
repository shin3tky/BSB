import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.ConnectionResolver;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.MonotonicClock;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;

/** 実ネットワークを使わずに、論理接続のホスト境界を確認する埋込み例です。 */
public final class LogicalConnectionEmbedding {
  private LogicalConnectionEmbedding() {}

  public static void main(String[] arguments) throws Exception {
    String mode = arguments.length == 0 ? "resolved" : arguments[0];
    var calls = new AtomicInteger();
    ConnectionResolver resolver = resolver(mode, calls);
    MonotonicClock clock = () -> 0L;
    var output = new MemoryOutputSink();
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .connectionResolver(resolver)
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                Path.of("samples/18-logical-connections.bsb"),
                new ExecutionContext(output, clock, TraceSink.none(), environment));
    String diagnostic =
        result.diagnostics().stream()
            .findFirst()
            .map(item -> item.code().name())
            .orElse("-");
    System.out.printf(
        "%s: exit=%d, diagnostic=%s, calls=%d%n",
        mode, result.exitCode(), diagnostic, calls.get());
  }

  private static ConnectionResolver resolver(String mode, AtomicInteger calls) {
    return (name, operation) -> {
      calls.incrementAndGet();
      return switch (mode) {
        case "resolved" -> ConnectionResolution.resolved(name, validPolicy());
        case "not-configured" -> ConnectionResolution.notConfigured(name);
        case "denied" -> ConnectionResolution.denied(name);
        case "invalid" -> ConnectionResolution.invalid(name, "BASE_URI_INVALID");
        default -> throw new IllegalArgumentException("unknown mode: " + mode);
      };
    };
  }

  private static ConnectionPolicy validPolicy() {
    return new ConnectionPolicy(
        "https://api.example.invalid/",
        List.of("https://api.example.invalid"),
        List.of("GET"),
        "none",
        Optional.empty(),
        1_000,
        2_000,
        1_024,
        2_048,
        "deny",
        "none");
  }
}
