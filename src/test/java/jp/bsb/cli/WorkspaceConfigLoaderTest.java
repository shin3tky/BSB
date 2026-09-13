package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import jp.bsb.runtime.FileReadRequest;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteRequest;
import jp.bsb.runtime.FileWriteResult;
import jp.bsb.runtime.WorkspaceResolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceConfigLoaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void buildsDirectMappingsRelativeToTheStartingDirectory() throws Exception {
    var readPath = new AtomicReference<Path>();
    var writePath = new AtomicReference<Path>();
    WorkspaceConfigLoader loader =
        new WorkspaceConfigLoader(
            new WorkspaceFileAdapter() {
              @Override
              public FileReadResult read(FileReadRequest request, Path path) {
                readPath.set(path);
                return FileReadResult.success(request, new byte[] {1});
              }

              @Override
              public FileWriteResult write(FileWriteRequest request, Path path) {
                writePath.set(path);
                return FileWriteResult.success(request);
              }
            });
    LoadedWorkspaceConfig loaded =
        loader.loadDirect(
            List.of(
                new DirectFileOption("帳票", "入力.dat", "read", Path.of("data/in.dat")),
                new DirectFileOption("帳票", "出力.dat", "write", Path.of("out/../out.dat"))),
            temporaryDirectory);
    WorkspaceResolution readResolution = loaded.resolver().resolve("帳票", "read");
    var readRequest =
        new FileReadRequest("帳票", readResolution.handle().orElseThrow(), "入力.dat", 16_777_216);
    assertTrue(loaded.reader().read(readRequest).state() == FileReadResult.State.SUCCESS);
    assertEquals(
        temporaryDirectory.resolve("data/in.dat").toAbsolutePath().normalize(), readPath.get());

    WorkspaceResolution writeResolution = loaded.resolver().resolve("帳票", "write");
    var writeRequest =
        new FileWriteRequest(
            "帳票",
            writeResolution.handle().orElseThrow(),
            "出力.dat",
            jp.bsb.runtime.ByteSequenceValue.empty(),
            16_777_216);
    assertTrue(loaded.writer().write(writeRequest).state() == FileWriteResult.State.SUCCESS);
    assertEquals(
        temporaryDirectory.resolve("out.dat").toAbsolutePath().normalize(), writePath.get());
    assertEquals(16_777_216, readResolution.policy().orElseThrow().maximumReadBytes());
  }

  @Test
  void treatsEnvironmentTildeAndGlobCharactersAsLiteralPathText() throws Exception {
    var observed = new ArrayList<Path>();
    WorkspaceConfigLoader loader =
        new WorkspaceConfigLoader(
            new WorkspaceFileAdapter() {
              @Override
              public FileReadResult read(FileReadRequest request, Path path) {
                observed.add(path);
                return FileReadResult.success(request, new byte[0]);
              }

              @Override
              public FileWriteResult write(FileWriteRequest request, Path path) {
                throw new AssertionError("write must not be called");
              }
            });
    LoadedWorkspaceConfig loaded =
        loader.loadDirect(
            List.of(
                new DirectFileOption("W", "env", "read", Path.of("$HOME/input.dat")),
                new DirectFileOption("W", "tilde", "read", Path.of("~/input.dat")),
                new DirectFileOption("W", "glob", "read", Path.of("*.dat"))),
            temporaryDirectory);
    WorkspaceResolution resolution = loaded.resolver().resolve("W", "read");
    for (String name : List.of("env", "tilde", "glob")) {
      loaded
          .reader()
          .read(new FileReadRequest("W", resolution.handle().orElseThrow(), name, 16_777_216));
    }

    assertEquals(
        List.of(
            temporaryDirectory.resolve("$HOME/input.dat").toAbsolutePath().normalize(),
            temporaryDirectory.resolve("~/input.dat").toAbsolutePath().normalize(),
            temporaryDirectory.resolve("*.dat").toAbsolutePath().normalize()),
        observed);
  }

  @Test
  void enforcesFiniteMappingAndAccessBeforeCallingTheAdapter() throws Exception {
    var calls = new ArrayList<String>();
    WorkspaceConfigLoader loader =
        new WorkspaceConfigLoader(
            new WorkspaceFileAdapter() {
              @Override
              public FileReadResult read(FileReadRequest request, Path path) {
                calls.add("read");
                return FileReadResult.success(request, new byte[0]);
              }

              @Override
              public FileWriteResult write(FileWriteRequest request, Path path) {
                calls.add("write");
                return FileWriteResult.success(request);
              }
            });
    LoadedWorkspaceConfig loaded =
        loader.loadDirect(
            List.of(new DirectFileOption("帳票", "読取専用", "read", Path.of("a"))), temporaryDirectory);
    WorkspaceResolution resolution = loaded.resolver().resolve("帳票", "read");
    var missing = new FileReadRequest("帳票", resolution.handle().orElseThrow(), "未登録", 1);
    assertEquals(FileReadResult.State.NOT_MAPPED, loaded.reader().read(missing).state());
    var denied =
        new FileWriteRequest(
            "帳票",
            resolution.handle().orElseThrow(),
            "読取専用",
            jp.bsb.runtime.ByteSequenceValue.empty(),
            1);
    assertEquals(FileWriteResult.State.ACCESS_DENIED, loaded.writer().write(denied).state());
    assertEquals(List.of(), calls);
    assertEquals(
        WorkspaceResolution.State.NOT_CONFIGURED, loaded.resolver().resolve("未設定", "read").state());
  }

  @Test
  void loadsVersionOneTomlAndResolvesPathsAgainstItsParent() throws Exception {
    Path config =
        write(
            "config/workspaces.toml",
            "schema-version = 1\n"
                + "[workspaces.\"帳票\"]\n"
                + "maximum-read-bytes = 1\n"
                + "maximum-write-bytes = 67108864\n"
                + "[workspaces.\"帳票\".files.\"入力.dat\"]\n"
                + "path = \"../data/input.dat\"\n"
                + "access = \"read-write\"\n");
    var observed = new AtomicReference<Path>();
    WorkspaceConfigLoader loader =
        new WorkspaceConfigLoader(
            new WorkspaceFileAdapter() {
              @Override
              public FileReadResult read(FileReadRequest request, Path path) {
                observed.set(path);
                return FileReadResult.success(request, new byte[0]);
              }

              @Override
              public FileWriteResult write(FileWriteRequest request, Path path) {
                observed.set(path);
                return FileWriteResult.success(request);
              }
            });

    LoadedWorkspaceConfig loaded = loader.loadToml(config);
    WorkspaceResolution resolution = loaded.resolver().resolve("帳票", "read");
    assertEquals(1, resolution.policy().orElseThrow().maximumReadBytes());
    assertEquals(67_108_864, resolution.policy().orElseThrow().maximumWriteBytes());
    loaded.reader().read(new FileReadRequest("帳票", resolution.handle().orElseThrow(), "入力.dat", 1));
    assertEquals(
        config.getParent().resolve("../data/input.dat").toAbsolutePath().normalize(),
        observed.get());
  }

  @Test
  void rejectsTomlEnvelopeEncodingSchemaKeysTypesNamesLimitsAndPaths() throws Exception {
    List<byte[]> invalid =
        List.of(
            new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'x'},
            new byte[] {(byte) 0xc3, 0x28},
            "schema-version = \"one\"\n".getBytes(StandardCharsets.UTF_8),
            "schema-version = 2\n[workspaces.A.files.x]\npath=\"a\"\naccess=\"read\"\n"
                .getBytes(StandardCharsets.UTF_8),
            "schema-version=1\nunknown=1\n[workspaces]\n".getBytes(StandardCharsets.UTF_8),
            validToml()
                .replace("[workspaces.A]", "[workspaces.\"é\"]")
                .getBytes(StandardCharsets.UTF_8),
            validToml()
                .replace("maximum-read-bytes = 1", "maximum-read-bytes = 0")
                .getBytes(StandardCharsets.UTF_8),
            validToml()
                .replace("access = \"read\"", "access = \"execute\"")
                .getBytes(StandardCharsets.UTF_8),
            validToml().replace("path = \"a\"", "path = \"\"").getBytes(StandardCharsets.UTF_8),
            (validToml() + "extra = true\n").getBytes(StandardCharsets.UTF_8),
            "schema-version=1\nschema-version=1\n[workspaces]\n".getBytes(StandardCharsets.UTF_8));
    for (int index = 0; index < invalid.size(); index++) {
      Path config = temporaryDirectory.resolve("invalid-" + index + ".toml");
      Files.write(config, invalid.get(index));
      assertThrows(
          WorkspaceConfigException.class,
          () -> WorkspaceConfigLoader.withoutPhysicalIo().loadToml(config));
    }
  }

  @Test
  void rejectsOversizedEnvelopeWorkspaceCountAndTotalFileCount() throws Exception {
    Path oversized = temporaryDirectory.resolve("oversized.toml");
    Files.write(oversized, new byte[Math.toIntExact(WorkspaceConfigLoader.MAX_CONFIG_BYTES + 1)]);
    assertThrows(
        WorkspaceConfigException.class,
        () -> WorkspaceConfigLoader.withoutPhysicalIo().loadToml(oversized));

    var manyWorkspaces = new StringBuilder("schema-version=1\n");
    for (int index = 0; index <= WorkspaceConfigLoader.MAX_WORKSPACES; index++) {
      manyWorkspaces
          .append("[workspaces.W")
          .append(index)
          .append(".files.x]\npath=\"a\"\naccess=\"read\"\n");
    }
    Path workspaces = write("many-workspaces.toml", manyWorkspaces.toString());
    assertThrows(
        WorkspaceConfigException.class,
        () -> WorkspaceConfigLoader.withoutPhysicalIo().loadToml(workspaces));

    var manyFiles = new StringBuilder("schema-version=1\n[workspaces.A]\n");
    for (int index = 0; index <= WorkspaceConfigLoader.MAX_FILES; index++) {
      manyFiles
          .append("[workspaces.A.files.f")
          .append(index)
          .append("]\npath=\"a\"\naccess=\"read\"\n");
    }
    Path files = write("many-files.toml", manyFiles.toString());
    assertThrows(
        WorkspaceConfigException.class,
        () -> WorkspaceConfigLoader.withoutPhysicalIo().loadToml(files));
  }

  @Test
  void rejectsInvalidDirectNamesDuplicatesAndCountsWithoutRevealingValues() {
    List<List<DirectFileOption>> invalid =
        List.of(
            List.of(new DirectFileOption("é", "x", "read", Path.of("secret-path"))),
            List.of(new DirectFileOption("A", "../x", "read", Path.of("secret-path"))),
            List.of(
                new DirectFileOption("A", "x", "read", Path.of("a")),
                new DirectFileOption("A", "x", "write", Path.of("b"))));
    for (List<DirectFileOption> options : invalid) {
      WorkspaceConfigException failure =
          assertThrows(
              WorkspaceConfigException.class,
              () ->
                  WorkspaceConfigLoader.withoutPhysicalIo()
                      .loadDirect(options, temporaryDirectory));
      assertFalse(failure.getMessage().contains("secret-path"));
      assertFalse(failure.getMessage().contains("../x"));
    }

    var tooMany = new ArrayList<DirectFileOption>();
    for (int index = 0; index <= WorkspaceConfigLoader.MAX_FILES; index++) {
      tooMany.add(new DirectFileOption("A", "f" + index, "read", Path.of("a")));
    }
    assertThrows(
        WorkspaceConfigException.class,
        () -> WorkspaceConfigLoader.withoutPhysicalIo().loadDirect(tooMany, temporaryDirectory));
  }

  private String validToml() {
    return "schema-version = 1\n"
        + "[workspaces.A]\n"
        + "maximum-read-bytes = 1\n"
        + "[workspaces.A.files.x]\n"
        + "path = \"a\"\n"
        + "access = \"read\"\n";
  }

  private Path write(String relative, String text) throws IOException {
    Path path = temporaryDirectory.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return path;
  }
}
