package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExponentExponentCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void checkRunAndFormatUseTheirNormativeExponentRepresentations() throws Exception {
    Path source = copyExponent("sources/EXP-N-values.bsb");

    Invocation checked = invoke("check", source.toString());
    Invocation run = invoke("run", source.toString());
    Invocation formatted = invoke("format", source.toString());

    assertEquals(new Invocation(0, "", ""), checked);
    assertEquals(0, run.exitCode());
    assertEquals(ExponentText("sources/EXP-N-values.stdout"), run.stdout());
    assertEquals("", run.stderr());
    assertEquals(0, formatted.exitCode());
    assertEquals(ExponentText("sources/EXP-N-values.bsb"), formatted.stdout());
    assertEquals("", formatted.stderr());
  }

  @Test
  void scaleFailureHasTheSameHumanAndVersionOneJsonContractForCheckAndRun() throws Exception {
    Path source = copyExponent("sources/EXP-F-source-scale.bsb");

    Invocation checked = invoke("check", source.toString());
    Invocation run = invoke("run", source.toString());
    Invocation formatted = invoke("format", source.toString());
    Invocation json = invoke("check", "--json", source.toString());

    assertEquals(8, checked.exitCode());
    assertEquals("", checked.stdout());
    assertTrue(checked.stderr().contains("E_DECIMAL_SCALE_LIMIT"));
    assertEquals(checked, run);
    assertEquals(new Invocation(0, ExponentText("sources/EXP-F-source-scale.bsb"), ""), formatted);
    assertEquals(8, json.exitCode());
    assertEquals("", json.stderr());

    Map<String, Object> document = object(StrictJsonParser.parse(json.stdout()));
    assertEquals(new BigDecimal("1"), document.get("schemaVersion"));
    assertEquals(Boolean.FALSE, document.get("success"));
    assertEquals(new BigDecimal("8"), document.get("exitCode"));
    Map<String, Object> diagnostic = object(list(document.get("diagnostics")).getFirst());
    assertEquals("E_DECIMAL_SCALE_LIMIT", diagnostic.get("code"));
    assertEquals("typeAndStack", diagnostic.get("stage"));
    assertEquals(Map.of("metric", "rawScale", "word", "小数リテラル"), diagnostic.get("fields"));
    Map<String, Object> span = object(diagnostic.get("span"));
    assertEquals(
        Map.of("line", new BigDecimal("2"), "column", new BigDecimal("5")), span.get("start"));
    assertEquals(
        Map.of(
            "name", "decimalScale",
            "limit", "65536",
            "observed", "65537"),
        diagnostic.get("resourceLimit"));
  }

  @Test
  void lexicalReasonAndExplainDictionaryRemainPublicVersionOneContracts() throws Exception {
    Path invalid = temporaryDirectory.resolve("invalid-exponent.bsb");
    Files.writeString(invalid, "メインとは （--）\n    1e+ を 一行表示する\nこと。\n", StandardCharsets.UTF_8);
    Invocation human = invoke("check", invalid.toString());
    Invocation json = invoke("check", "--json", invalid.toString());

    assertEquals(9, human.exitCode());
    assertEquals("", human.stdout());
    assertTrue(human.stderr().contains("E_INVALID_NUMBER_LITERAL"));
    assertEquals(9, json.exitCode());
    assertEquals("", json.stderr());
    Map<String, Object> checkDocument = object(StrictJsonParser.parse(json.stdout()));
    Map<String, Object> diagnostic = object(list(checkDocument.get("diagnostics")).getFirst());
    assertEquals("lexical", diagnostic.get("stage"));
    assertEquals(Map.of("reason", "missingExponentDigits"), diagnostic.get("fields"));

    Path accepted = copyExponent("sources/EXP-N-values.bsb");
    Invocation explained = invoke("explain", "--json", accepted.toString());
    assertEquals(0, explained.exitCode());
    assertEquals("", explained.stderr());
    Map<String, Object> explainDocument = object(StrictJsonParser.parse(explained.stdout()));
    assertEquals(new BigDecimal("1"), explainDocument.get("schemaVersion"));
    assertEquals(167, list(explainDocument.get("builtinWords")).size());
    Map<String, Object> summary = object(explainDocument.get("summary"));
    assertEquals(List.of("console.output"), summary.get("capabilities"));
    assertFalse(list(summary.get("effects")).isEmpty());
  }

  private Path copyExponent(String relativePath) throws IOException {
    Path target = temporaryDirectory.resolve(Path.of(relativePath).getFileName());
    Files.write(target, ExponentBytes(relativePath));
    return target;
  }

  private static byte[] ExponentBytes(String relativePath) throws IOException {
    String resource = "/conformance/exponent-literals/" + relativePath;
    try (var input = ExponentExponentCliTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static String ExponentText(String relativePath) throws IOException {
    return new String(ExponentBytes(relativePath), StandardCharsets.UTF_8);
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exitCode = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(
        exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value) {
    return (List<Object>) value;
  }

  private record Invocation(int exitCode, String stdout, String stderr) {}
}
