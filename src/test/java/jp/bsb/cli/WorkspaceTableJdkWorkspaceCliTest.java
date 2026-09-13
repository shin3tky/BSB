package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTableJdkWorkspaceCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void copiesARegisteredFileToAnotherWithRepeatedDirectOptions() throws Exception {
    Path source = write("program.bsb", program());
    Path input = temporaryDirectory.resolve("input.dat");
    Path output = temporaryDirectory.resolve("output.dat");
    byte[] content = new byte[] {0, 1, 2, (byte) 0xff};
    Files.write(input, content);

    Invocation invocation =
        invoke(
            "run",
            "--file",
            "帳票",
            "入力.dat",
            "read",
            input.toString(),
            "--file",
            "帳票",
            "出力.dat",
            "write",
            output.toString(),
            source.toString(),
            "--",
            "入力.dat",
            "出力.dat");

    assertEquals(0, invocation.exitCode(), invocation.stderr());
    assertEquals("4\n", invocation.stdout());
    assertEquals("", invocation.stderr());
    assertArrayEquals(content, Files.readAllBytes(output));
  }

  @Test
  void copiesARegisteredFileToAnotherWithToml() throws Exception {
    Path source = write("program.bsb", program());
    Path input = temporaryDirectory.resolve("data/input.dat");
    Path output = temporaryDirectory.resolve("out/output.dat");
    Files.createDirectories(input.getParent());
    Files.createDirectories(output.getParent());
    byte[] content = "TOML経路".getBytes(StandardCharsets.UTF_8);
    Files.write(input, content);
    Path config =
        write(
            "config/workspaces.toml",
            "schema-version = 1\n"
                + "[workspaces.\"帳票\"]\n"
                + "[workspaces.\"帳票\".files.\"入力.dat\"]\n"
                + "path = \"../data/input.dat\"\n"
                + "access = \"read\"\n"
                + "[workspaces.\"帳票\".files.\"出力.dat\"]\n"
                + "path = \"../out/output.dat\"\n"
                + "access = \"write\"\n");

    Invocation invocation =
        invoke(
            "run", "--workspaces", config.toString(), source.toString(), "--", "入力.dat", "出力.dat");

    assertEquals(0, invocation.exitCode(), invocation.stderr());
    assertEquals(content.length + "\n", invocation.stdout());
    assertEquals("", invocation.stderr());
    assertArrayEquals(content, Files.readAllBytes(output));
  }

  private Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(
        exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private Path write(String relative, String text) throws Exception {
    Path path = temporaryDirectory.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return path;
  }

  private static String program() {
    return "帳票は 作業領域。\n"
        + "\n"
        + "入力は 変数 空のバイト列。\n"
        + "\n"
        + "メインとは （--）\n"
        + "    起動引数を得る 0 配列から取り出す ファイルを読む<帳票> 結果から成功値を取り出す を 入力 に 入れる\n"
        + "    起動引数を得る 1 配列から取り出す 入力 ファイルへ書く<帳票> 結果から成功値を取り出す 一行表示する\n"
        + "こと。\n";
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
