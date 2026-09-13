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

/** 名前付き作業領域の参照BSBをOSファイルなしで実行する偽能力例です。 */
public final class NamedWorkspaceFakeEmbedding {
  private NamedWorkspaceFakeEmbedding() {}

  public static void main(String[] arguments) throws Exception {
    byte[] first = {1, 2, 3};
    byte[] second = {4, 5, 6};
    var published = new AtomicReference<byte[]>();
    var resolverCalls = new AtomicInteger();
    var readCalls = new AtomicInteger();
    var writeCalls = new AtomicInteger();
    var handle = WorkspaceHandle.opaque();
    var policy = new WorkspacePolicy(16_777_216, 16_777_216);
    var output = new MemoryOutputSink();
    var clock = (jp.bsb.runtime.MonotonicClock) () -> 0L;
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .processArguments(ProcessArguments.fixed(List.of("入力A.dat", "入力B.dat", "出力.dat")))
            .workspaceResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  return WorkspaceResolution.resolved(name, operation, handle, policy);
                })
            .fileReadCapability(
                request -> {
                  readCalls.incrementAndGet();
                  byte[] bytes =
                      switch (request.logicalName()) {
                        case "入力A.dat" -> first;
                        case "入力B.dat" -> second;
                        default -> throw new AssertionError("unexpected logical file");
                      };
                  return FileReadResult.success(request, bytes);
                })
            .fileWriteCapability(
                request -> {
                  writeCalls.incrementAndGet();
                  published.set(request.bodyBytes());
                  return FileWriteResult.success(request);
                })
            .redactWorkspaceNamesInTrace()
            .build();
    var result =
        new ProgramRunner()
            .run(
                Path.of("samples/23-named-workspaces-files.bsb"),
                new ExecutionContext(output, clock, TraceSink.none(), environment));
    if (!Arrays.equals(first, published.get())) {
      throw new AssertionError("unexpected published content");
    }
    System.out.write(output.bytes());
    System.out.printf(
        "exit=%d resolverCalls=%d readCalls=%d writeCalls=%d%n",
        result.exitCode(), resolverCalls.get(), readCalls.get(), writeCalls.get());
  }
}
