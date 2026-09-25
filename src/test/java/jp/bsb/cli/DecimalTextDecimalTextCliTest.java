package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecimalTextDecimalTextCliTest {
  @TempDir Path temporaryDirectory;

  @Test
  void keepsVersionOneJsonAndThe124WordDictionary() throws Exception {
    Path source = temporaryDirectory.resolve("DecimalText.bsb");
    try (var input =
        DecimalTextDecimalTextCliTest.class.getResourceAsStream(
            "/conformance/decimal-text/sources/DTXT-N-values.bsb")) {
      Files.write(source, input.readAllBytes());
    }

    Invocation checked = invoke("check", "--json", source.toString());
    assertEquals(0, checked.exitCode());
    assertEquals("", checked.stderr());
    Map<String, Object> checkDocument = object(StrictJsonParser.parse(checked.stdout()));
    assertEquals(new BigDecimal("1"), checkDocument.get("schemaVersion"));
    assertEquals(Boolean.TRUE, checkDocument.get("success"));

    Invocation explained = invoke("explain", "--json", source.toString());
    assertEquals(0, explained.exitCode());
    assertEquals("", explained.stderr());
    Map<String, Object> explainDocument = object(StrictJsonParser.parse(explained.stdout()));
    assertEquals(new BigDecimal("1"), explainDocument.get("schemaVersion"));
    assertEquals(213, list(explainDocument.get("builtinWords")).size());
  }

  @Test
  void formatPreservesExponentTextInsideTheStringLiteral() throws Exception {
    Path source = temporaryDirectory.resolve("format.bsb");
    try (var input =
        DecimalTextDecimalTextCliTest.class.getResourceAsStream(
            "/conformance/decimal-text/sources/DTXT-N-format.bsb")) {
      Files.write(source, input.readAllBytes());
    }

    Invocation formatted = invoke("format", source.toString());
    assertEquals(0, formatted.exitCode());
    assertEquals("", formatted.stderr());
    assertTrue(formatted.stdout().contains("「1E+002」"));
  }

  private static Invocation invoke(String... arguments) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(arguments, stdout, stderr);
    return new Invocation(
        exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
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
