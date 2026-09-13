package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.format.SourceFormatter;
import org.junit.jupiter.api.Test;

/** 回復可能JSONの全公開物理sourceを公開CLIの5経路へ接続します。 */
class RecoverableJsonCentralCliConformanceTest {
  private static final Path ROOT = Path.of("tests/conformance/recoverable-json");

  @Test
  void allOneHundredPublishedCommandInvocationsAreDeterministicAndKeepTheirInputs()
      throws Exception {
    List<Path> sources = publicSources();
    assertEquals(20, sources.size());
    int invocations = 0;
    for (Path source : sources) {
      byte[] original = Files.readAllBytes(source);
      for (String command : List.of("check", "checkJson", "run", "format", "explain")) {
        Invocation first = invoke(arguments(command, source));
        Invocation second = invoke(arguments(command, source));
        String key = ROOT.relativize(source) + "/" + command;
        assertEquals(first.exitCode(), second.exitCode(), key);
        assertArrayEquals(first.stdout(), second.stdout(), key + "/stdout");
        assertArrayEquals(first.stderr(), second.stderr(), key + "/stderr");
        assertArrayEquals(original, Files.readAllBytes(source), key + "/input");
        assertNoImplementationLeak(first, key);
        assertExpectedExit(source, command, first.exitCode());
        if (command.equals("checkJson") || command.equals("explain")) {
          String json = new String(first.stdout(), StandardCharsets.UTF_8);
          assertTrue(json.startsWith("{\"schemaVersion\":1,"), key);
          assertTrue(json.endsWith("}\n"), key);
          assertEquals(0, first.stderr().length, key);
        }
        if (command.equals("format")) {
          assertEquals(0, first.exitCode(), key);
          assertEquals(0, first.stderr().length, key);
          var secondFormat = new SourceFormatter().format(source.toString(), first.stdout());
          assertTrue(secondFormat.successful(), key);
          assertArrayEquals(
              first.stdout(),
              secondFormat.outputForStandardOutput().getBytes(StandardCharsets.UTF_8),
              key);
        }
        invocations++;
      }
    }
    assertEquals(100, invocations);
  }

  private static List<Path> publicSources() throws Exception {
    var result = new ArrayList<Path>();
    try (var paths = Files.list(ROOT.resolve("sources"))) {
      paths
          .filter(path -> path.getFileName().toString().endsWith(".bsb"))
          .sorted()
          .forEach(result::add);
    }
    result.add(ROOT.resolve("chapter/recoverable-json-chapter.bsb"));
    return List.copyOf(result);
  }

  private static void assertExpectedExit(Path source, String command, int actual) {
    String name = source.getFileName().toString();
    int expected;
    if (command.equals("format")) {
      expected = 0;
    } else if (name.startsWith("RJSON-N") || name.equals("recoverable-json-chapter.bsb")) {
      expected = 0;
    } else if (name.equals("RJSON-F-unreachable.bsb")) {
      expected = 0;
    } else if (name.equals("RJSON-F-existing-parser.bsb") && command.equals("run")) {
      expected = 10;
    } else if (name.equals("RJSON-F-existing-parser.bsb")) {
      expected = 0;
    } else {
      expected = 8;
    }
    assertEquals(expected, actual, name + '/' + command);
  }

  private static void assertNoImplementationLeak(Invocation invocation, String key) {
    String combined =
        new String(invocation.stdout(), StandardCharsets.UTF_8)
            + new String(invocation.stderr(), StandardCharsets.UTF_8);
    assertFalse(combined.contains("Exception"), key);
    assertFalse(combined.contains("java."), key);
    assertFalse(combined.contains("secret-marker"), key);
  }

  private static String[] arguments(String command, Path source) {
    return switch (command) {
      case "check" -> new String[] {"check", source.toString()};
      case "checkJson" -> new String[] {"check", "--json", source.toString()};
      case "run" -> new String[] {"run", source.toString()};
      case "format" -> new String[] {"format", source.toString()};
      case "explain" -> new String[] {"explain", "--json", source.toString()};
      default -> throw new IllegalArgumentException("unknown command " + command);
    };
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
