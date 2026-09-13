import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteResult;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProcessArguments;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.WorkspaceHandle;
import jp.bsb.runtime.WorkspacePolicy;
import jp.bsb.runtime.WorkspaceResolution;

/** CSVとTSVの縦断参照BSBをOSファイルなしで実行する偽能力例です。 */
public final class DelimitedTablesFakeEmbedding {
  private static final byte[] CSV =
      "\uFEFF商品,個数\r\nりんご,3\r\nみかん,\r\n".getBytes(StandardCharsets.UTF_8);
  private static final byte[] TSV =
      "\"注\n記\"\t値\r\n空\t\"\"\r\n".getBytes(StandardCharsets.UTF_8);
  private static final byte[] EXPECTED =
      "商品,個数\r\nりんご,3\r\nみかん,\r\n\"注\n記\",値\r\n".getBytes(StandardCharsets.UTF_8);

  private DelimitedTablesFakeEmbedding() {}

  public static void main(String[] arguments) throws Exception {
    var published = new AtomicReference<byte[]>();
    var resolverCalls = new AtomicInteger();
    var readCalls = new AtomicInteger();
    var writeCalls = new AtomicInteger();
    var inputHandle = WorkspaceHandle.opaque();
    var outputHandle = WorkspaceHandle.opaque();
    var policy = new WorkspacePolicy(16_777_216, 16_777_216);
    var output = new MemoryOutputSink();
    var clock = (jp.bsb.runtime.MonotonicClock) () -> 0L;
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .processArguments(
                ProcessArguments.fixed(List.of("商品.csv", "注記.tsv", "集計.csv")))
            .workspaceResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  return switch (name) {
                    case "入力" ->
                        WorkspaceResolution.resolved(name, operation, inputHandle, policy);
                    case "出力" ->
                        WorkspaceResolution.resolved(name, operation, outputHandle, policy);
                    default -> throw new AssertionError("unexpected workspace");
                  };
                })
            .fileReadCapability(
                request -> {
                  readCalls.incrementAndGet();
                  byte[] bytes =
                      switch (request.logicalName()) {
                        case "商品.csv" -> CSV;
                        case "注記.tsv" -> TSV;
                        default -> throw new AssertionError("unexpected logical input");
                      };
                  return FileReadResult.success(request, bytes);
                })
            .fileWriteCapability(
                request -> {
                  writeCalls.incrementAndGet();
                  if (!request.logicalName().equals("集計.csv")) {
                    throw new AssertionError("unexpected logical output");
                  }
                  published.set(request.bodyBytes());
                  return FileWriteResult.success(request);
                })
            .redactWorkspaceNamesInTrace()
            .build();
    var result =
        new ProgramRunner()
            .run(
                Path.of("samples/24-csv-tsv-files.bsb"),
                new ExecutionContext(output, clock, TraceSink.none(), environment));
    if (!result.successful() || !Arrays.equals(EXPECTED, published.get())) {
      throw new AssertionError("unexpected delimited-table result");
    }
    System.out.write(output.bytes());
    System.out.printf(
        "exit=%d resolverCalls=%d readCalls=%d writeCalls=%d delimitedWork=%d%n",
        result.exitCode(),
        resolverCalls.get(),
        readCalls.get(),
        writeCalls.get(),
        result.delimitedTextWorkUnits());
  }
}
