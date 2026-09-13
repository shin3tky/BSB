package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.format.FormatResult;
import jp.bsb.runtime.ByteSequenceValue;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.FileReadRequest;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteRequest;
import jp.bsb.runtime.FileWriteResult;
import jp.bsb.runtime.ProgramRunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTableWorkspaceCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void injectsRepeatedDirectFilesAndCoexistsWithConnections() throws Exception {
    Path connections = write("connections.toml", connectionToml());
    Path input = temporaryDirectory.resolve("input.dat");
    Path output = temporaryDirectory.resolve("output.dat");
    var backend = new RecordingBackend();
    var paths = new ArrayList<Path>();
    BsbCli cli = cli(backend, recordingAdapter(paths));

    Invocation invocation =
        invoke(
            cli,
            "run",
            "--file",
            "帳票",
            "入力.dat",
            "read",
            input.toString(),
            "--connections",
            connections.toString(),
            "--file",
            "帳票",
            "出力.dat",
            "write",
            output.toString(),
            temporaryDirectory.resolve("main.bsb").toString());

    assertEquals(0, invocation.exitCode());
    assertEquals("", invocation.stdout());
    assertEquals("", invocation.stderr());
    assertFalse(invocation.stdout().contains("CONNECTION_API_KEY_SECRET"));
    assertFalse(invocation.stderr().contains("CONNECTION_API_KEY_SECRET"));
    var environment = backend.context.get().environment();
    assertTrue(environment.connectionResolver().isPresent());
    assertTrue(environment.httpTransport().isPresent());
    assertTrue(environment.redactWorkspaceNamesInTrace());
    var resolution = environment.workspaceResolver().orElseThrow().resolve("帳票", "read");
    var handle = resolution.handle().orElseThrow();
    assertEquals(
        FileReadResult.State.SUCCESS,
        environment
            .fileReadCapability()
            .orElseThrow()
            .read(new FileReadRequest("帳票", handle, "入力.dat", 16_777_216))
            .state());
    assertEquals(
        FileWriteResult.State.SUCCESS,
        environment
            .fileWriteCapability()
            .orElseThrow()
            .write(
                new FileWriteRequest("帳票", handle, "出力.dat", ByteSequenceValue.empty(), 16_777_216))
            .state());
    assertEquals(List.of(input.toAbsolutePath(), output.toAbsolutePath()), paths);
  }

  @Test
  void injectsTomlFilesUsingTheConfigurationParent() throws Exception {
    Path config =
        write(
            "config/workspaces.toml",
            "schema-version = 1\n"
                + "[workspaces.\"帳票\"]\n"
                + "[workspaces.\"帳票\".files.\"入力\"]\n"
                + "path = \"../data/input.dat\"\n"
                + "access = \"read\"\n"
                + "[workspaces.\"帳票\".files.\"出力\"]\n"
                + "path = \"../data/output.dat\"\n"
                + "access = \"write\"\n");
    var backend = new RecordingBackend();
    var paths = new ArrayList<Path>();
    BsbCli cli = cli(backend, recordingAdapter(paths));

    Invocation invocation =
        invoke(
            cli,
            "run",
            "--workspaces",
            config.toString(),
            temporaryDirectory.resolve("main.bsb").toString());

    assertEquals(0, invocation.exitCode());
    var environment = backend.context.get().environment();
    var resolution = environment.workspaceResolver().orElseThrow().resolve("帳票", "read");
    var handle = resolution.handle().orElseThrow();
    environment
        .fileReadCapability()
        .orElseThrow()
        .read(new FileReadRequest("帳票", handle, "入力", 16_777_216));
    environment
        .fileWriteCapability()
        .orElseThrow()
        .write(new FileWriteRequest("帳票", handle, "出力", ByteSequenceValue.empty(), 16_777_216));
    assertEquals(
        List.of(
            config.getParent().resolve("../data/input.dat").toAbsolutePath().normalize(),
            config.getParent().resolve("../data/output.dat").toAbsolutePath().normalize()),
        paths);
  }

  @Test
  void rejectsInvalidWorkspaceConfigurationBeforeTheBackendWithoutDisclosure() throws Exception {
    String logicalSecret = "../LOGICAL_SECRET";
    String pathSecret = "TARGET_PATH_SECRET";
    Path source = temporaryDirectory.resolve("main.bsb");
    var directBackend = new RecordingBackend();

    Invocation direct =
        invoke(
            cli(directBackend, recordingAdapter(new ArrayList<>())),
            "run",
            "--connections",
            write("connections-secret.toml", connectionToml()).toString(),
            "--file",
            "帳票",
            logicalSecret,
            "read",
            pathSecret,
            source.toString());

    assertConfigurationFailure(direct, logicalSecret, pathSecret, "CONNECTION_API_KEY_SECRET");
    assertEquals(0, directBackend.calls.get());

    String documentSecret = "TOML_CONTENT_SECRET";
    Path malformed = write("malformed.toml", "schema-version = 1\nsecret = \"" + documentSecret);
    var tomlBackend = new RecordingBackend();
    Invocation toml =
        invoke(
            cli(tomlBackend, recordingAdapter(new ArrayList<>())),
            "run",
            "--workspaces",
            malformed.toString(),
            source.toString());

    assertConfigurationFailure(toml, documentSecret);
    assertTrue(toml.stderr().contains(malformed.toString()));
    assertEquals(0, tomlBackend.calls.get());
  }

  private BsbCli cli(RecordingBackend backend, WorkspaceFileAdapter adapter) throws Exception {
    return new BsbCli(
        backend,
        DiagnosticMessageCatalog.loadDefault(),
        new CliJsonRenderer(),
        new ExplainJsonRenderer(),
        new ConnectionConfigLoader(name -> null),
        new WorkspaceConfigLoader(adapter));
  }

  private static WorkspaceFileAdapter recordingAdapter(List<Path> paths) {
    return new WorkspaceFileAdapter() {
      @Override
      public FileReadResult read(FileReadRequest request, Path path) {
        paths.add(path);
        return FileReadResult.success(request, new byte[0]);
      }

      @Override
      public FileWriteResult write(FileWriteRequest request, Path path) {
        paths.add(path);
        return FileWriteResult.success(request);
      }
    };
  }

  private Path write(String relative, String text) throws Exception {
    Path path = temporaryDirectory.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return path;
  }

  private static String connectionToml() {
    return "schema-version = 1\n"
        + "[connections.API]\n"
        + "base-uri = \"https://api.example.test/\"\n"
        + "allowed-methods = [\"GET\"]\n"
        + "[connections.API.authentication]\n"
        + "kind = \"api-key\"\n"
        + "value = \"CONNECTION_API_KEY_SECRET\"\n";
  }

  private static Invocation invoke(BsbCli cli, String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = cli.run(arguments, stdout, stderr);
    return new Invocation(
        exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private static void assertConfigurationFailure(Invocation invocation, String... secrets) {
    assertEquals(BsbCli.EXIT_CONFIG, invocation.exitCode());
    assertEquals("", invocation.stdout());
    assertTrue(invocation.stderr().startsWith("作業領域設定エラー:"));
    for (String secret : secrets) assertFalse(invocation.stderr().contains(secret));
  }

  private static final class RecordingBackend implements CliBackend {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<ExecutionContext> context = new AtomicReference<>();

    @Override
    public AnalysisResult check(Path path) {
      throw new AssertionError("check must not be called");
    }

    @Override
    public ProgramRunResult run(Path path, ExecutionContext executionContext) {
      calls.incrementAndGet();
      context.set(executionContext);
      return new ProgramRunResult(0, List.of(), List.of(), 0, 0);
    }

    @Override
    public FormatResult format(Path path) {
      throw new AssertionError("format must not be called");
    }
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
