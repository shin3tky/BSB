package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jp.bsb.conformance.ExplainBuiltinCompatibility;
import jp.bsb.conformance.ResultConformanceData;
import jp.bsb.format.SourceFormatter;
import org.junit.jupiter.api.Test;

/** 中央カタログで公開する24ソースを公開CLIの全5経路へ接続します。 */
class ResultCentralCliConformanceTest {
  private static final Path ROOT = Path.of("tests/conformance/result-values");

  @Test
  void allOneHundredTwentyPublicCommandsMatchFixedHashesAndPreserveInputs() throws Exception {
    Map<String, Set<String>> codesBySource =
        ResultConformanceData.loadCatalog().stream()
            .filter(item -> item.commands().equals("public"))
            .filter(item -> !item.primaryCode().equals("-"))
            .collect(
                Collectors.groupingBy(
                    ResultConformanceData.CatalogSpec::evidence,
                    Collectors.mapping(
                        ResultConformanceData.CatalogSpec::primaryCode, Collectors.toSet())));
    int invocations = 0;
    for (var spec : ResultConformanceData.loadCommandHashes()) {
      Path source = ROOT.resolve(spec.source());
      byte[] original = Files.readAllBytes(source);
      Invocation actual = invoke(arguments(spec.command(), source));
      byte[] comparableStdout =
          spec.command().equals("explain")
              ? ExplainBuiltinCompatibility.withoutByteSequenceWords(actual.stdout())
              : actual.stdout();
      String key = spec.source() + '/' + spec.command();
      assertEquals(spec.exitCode(), actual.exitCode(), key);
      assertEquals(spec.stdoutBytes(), comparableStdout.length, key + "/stdout bytes");
      assertEquals(spec.stderrBytes(), actual.stderr().length, key + "/stderr bytes");
      assertEquals(
          spec.stdoutSha256(), ResultConformanceData.sha256(comparableStdout), key + "/stdout");
      assertEquals(
          spec.stderrSha256(), ResultConformanceData.sha256(actual.stderr()), key + "/stderr");
      assertArrayEquals(original, Files.readAllBytes(source), key + "/input");
      assertNoImplementationLeak(actual, key);
      String combined =
          new String(actual.stdout(), StandardCharsets.UTF_8)
              + new String(actual.stderr(), StandardCharsets.UTF_8);
      for (String code : codesBySource.getOrDefault(spec.source(), Set.of())) {
        if (!spec.command().equals("format") && (spec.exitCode() != 0 || code.startsWith("W_"))) {
          assertTrue(combined.contains(code), key + '/' + code);
        }
      }
      if (spec.command().equals("checkJson") || spec.command().equals("explain")) {
        String json = new String(actual.stdout(), StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{"), key);
        assertTrue(json.endsWith("}\n"), key);
      }
      invocations++;
    }
    assertEquals(120, invocations);

    Set<String> publicSources =
        ResultConformanceData.loadCommandHashes().stream()
            .map(ResultConformanceData.CommandSpec::source)
            .collect(Collectors.toSet());
    for (String source : publicSources) {
      byte[] first = invoke("format", ROOT.resolve(source).toString()).stdout();
      var second = new SourceFormatter().format(source, first);
      assertTrue(second.successful(), source);
      assertArrayEquals(
          first, second.outputForStandardOutput().getBytes(StandardCharsets.UTF_8), source);
    }
  }

  private static void assertNoImplementationLeak(Invocation actual, String key) {
    String combined =
        new String(actual.stdout(), StandardCharsets.UTF_8)
            + new String(actual.stderr(), StandardCharsets.UTF_8);
    assertFalse(combined.contains("Exception"), key);
    assertFalse(combined.contains("java."), key);
    assertFalse(combined.contains("credential-secret"), key);
    assertFalse(combined.contains("payload-secret"), key);
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
