package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 結果値のN/Fフィクスチャを公開CLIへ全件接続します。 */
class ResultFixtureCliConformanceTest {
  private static final Path ROOT = Path.of("tests/conformance/result-values");
  private static final List<String> COLUMNS =
      List.of(
          "case_id",
          "variant",
          "kind",
          "source",
          "canonical",
          "stdout",
          "stderr",
          "commands",
          "check_exit",
          "run_exit",
          "format_exit",
          "explain_exit",
          "diagnostic_rows",
          "state",
          "instructions",
          "output_bytes",
          "error_output_bytes",
          "array_construction",
          "array_work",
          "json_construction",
          "json_work",
          "target_words");

  @Test
  void executesAllSixHundredSeventyOnePlannedCommandsByteForByte() throws Exception {
    List<Map<String, String>> cases = loadCases();
    int invocations = 0;
    for (Map<String, String> spec : cases) {
      Path source = ROOT.resolve(spec.get("source"));
      byte[] original = Files.readAllBytes(source);
      Map<String, String> stdout = stringMap(ROOT.resolve(spec.get("stdout")));
      Map<String, String> stderr = stringMap(ROOT.resolve(spec.get("stderr")));
      for (String command : spec.get("commands").split("\\|")) {
        Invocation actual = invoke(arguments(command, source));
        assertEquals(exit(spec, command), actual.exitCode(), key(spec, command));
        assertArrayEquals(
            stdout.get(command).getBytes(StandardCharsets.UTF_8),
            actual.stdout(),
            key(spec, command));
        assertArrayEquals(
            stderr.get(command).getBytes(StandardCharsets.UTF_8),
            actual.stderr(),
            key(spec, command));
        invocations++;
      }
      assertArrayEquals(original, Files.readAllBytes(source), key(spec, "input"));
      if (!spec.get("canonical").equals("-")) {
        Invocation formatted = invoke("format", source.toString());
        assertArrayEquals(
            Files.readAllBytes(ROOT.resolve(spec.get("canonical"))),
            formatted.stdout(),
            key(spec, "canonical"));
      }
    }
    assertEquals(136, cases.size());
    assertEquals(671, invocations);
  }

  private static List<Map<String, String>> loadCases() throws Exception {
    List<String> lines = Files.readAllLines(ROOT.resolve("cases.tsv"), StandardCharsets.UTF_8);
    assertEquals(COLUMNS, List.of(lines.getFirst().split("\\t", -1)));
    var result = new ArrayList<Map<String, String>>();
    var keys = new LinkedHashSet<String>();
    for (String line : lines.subList(1, lines.size())) {
      String[] values = line.split("\\t", -1);
      assertEquals(COLUMNS.size(), values.length, line);
      var row = new LinkedHashMap<String, String>();
      for (int index = 0; index < COLUMNS.size(); index++) {
        row.put(COLUMNS.get(index), values[index]);
      }
      String key = row.get("case_id") + '/' + row.get("variant");
      assertEquals(true, keys.add(key), "duplicate case " + key);
      result.add(Map.copyOf(row));
    }
    return List.copyOf(result);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> stringMap(Path path) throws Exception {
    Object parsed = StrictJsonParser.parse(Files.readAllBytes(path));
    var result = new LinkedHashMap<String, String>();
    ((Map<String, Object>) parsed)
        .forEach(
            (key, value) -> {
              if (!(value instanceof String text)) {
                throw new IllegalArgumentException(path + ": output value is not a string");
              }
              result.put(key, text);
            });
    return Map.copyOf(result);
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

  private static int exit(Map<String, String> spec, String command) {
    String column =
        switch (command) {
          case "run" -> "run_exit";
          case "format" -> "format_exit";
          case "explain" -> "explain_exit";
          default -> "check_exit";
        };
    return Integer.parseInt(spec.get(column));
  }

  private static String key(Map<String, String> spec, String command) {
    return spec.get("case_id") + '/' + spec.get("variant") + '/' + command;
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(exitCode, stdout.toByteArray(), stderr.toByteArray());
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
