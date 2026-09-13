package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CliJsonRendererTest {
  @Test
  void rendersEveryMemberInNormativeOrder() {
    var fields = new TreeMap<String, String>();
    fields.put("detail", "値");
    var diagnostic =
        new DiagnosticJson(
            "E_TEST",
            "error",
            "syntax",
            "本文",
            "input.bsb",
            new JsonSpan(new JsonLineColumn(1, 2), new JsonLineColumn(2, 3), 1, 8),
            Collections.unmodifiableSortedMap(fields),
            Optional.of("期待"),
            Optional.of("実際"),
            List.of(
                new JsonRelatedLocation(
                    Optional.of("input.bsb"), Optional.of(new JsonPoint(1, 1, 0)), "先の位置"),
                new JsonRelatedLocation(Optional.empty(), Optional.empty(), "辞書")),
            List.of("修正"),
            Optional.of(new JsonResourceLimit("tokens", "100", "101")));
    CliJsonDocument document = CliJsonDocument.analysis("input.bsb", 9, List.of(diagnostic));

    CliJsonRenderer.JsonRenderResult result = new CliJsonRenderer().render(document);

    assertEquals(9, result.exitCode());
    assertEquals(
        "{\"schemaVersion\":1,\"command\":\"check\",\"source\":\"input.bsb\","
            + "\"success\":false,\"exitCode\":9,\"diagnostics\":[{\"code\":\"E_TEST\","
            + "\"severity\":\"error\",\"stage\":\"syntax\",\"message\":\"本文\","
            + "\"sourcePath\":\"input.bsb\",\"span\":{\"start\":{\"line\":1,\"column\":2},"
            + "\"endInclusive\":{\"line\":2,\"column\":3},\"utf8Start\":1,"
            + "\"utf8EndExclusive\":8},\"fields\":{\"detail\":\"値\"},\"expected\":\"期待\","
            + "\"actual\":\"実際\",\"relatedLocations\":[{\"sourcePath\":\"input.bsb\","
            + "\"point\":{\"line\":1,\"column\":1,\"utf8Offset\":0},\"description\":\"先の位置\"},"
            + "{\"description\":\"辞書\"}],\"fixes\":[\"修正\"],\"resourceLimit\":{\"name\":\"tokens\","
            + "\"limit\":\"100\",\"observed\":\"101\"}}]}\n",
        new String(result.bytes(), StandardCharsets.UTF_8));
  }

  @Test
  void escapesControlsAndPreservesUnicodeScalarsWithoutHtmlEscaping() {
    String source = "\"\\/\b\t\n\f\r\u0000\u001F日本か\u3099𠮷\u2028\u2029</script>";
    CliJsonDocument document = CliJsonDocument.analysis(source, 0, List.of());

    String rendered =
        new String(new CliJsonRenderer().render(document).bytes(), StandardCharsets.UTF_8);

    assertTrue(
        rendered.contains(
            "\"source\":\"\\\"\\\\/\\b\\t\\n\\f\\r\\u0000\\u001F日本か\u3099𠮷\u2028\u2029</script>\""));
    assertTrue(rendered.contains("</script>"));
    assertTrue(rendered.endsWith("\n"));
    assertFalse(rendered.endsWith("\n\n"));
  }

  @Test
  void producesByteIdenticalOutputAcrossRepeatedRendering() {
    CliJsonDocument document = CliJsonDocument.analysis("同じ.bsb", 0, List.of());
    CliJsonRenderer renderer = new CliJsonRenderer();

    byte[] first = renderer.render(document).bytes();
    byte[] second = renderer.render(document).bytes();
    assertArrayEquals(first, second);
    assertArrayEquals(second, renderer.render(document).bytes());
    assertEquals('{', second[0]);
  }

  @Test
  void acceptsTheConfiguredLimitAndFallsBackBeforeWritingOneByteMore() {
    CliJsonDocument empty = CliJsonDocument.analysis("", 0, List.of());
    int baseSize = new CliJsonRenderer().render(empty).bytes().length;
    int maximum = 256;
    int exactSourceBytes = maximum - baseSize;
    CliJsonRenderer renderer = new CliJsonRenderer(maximum);

    CliJsonRenderer.JsonRenderResult exact =
        renderer.render(CliJsonDocument.analysis("x".repeat(exactSourceBytes), 0, List.of()));
    CliJsonRenderer.JsonRenderResult over =
        renderer.render(CliJsonDocument.analysis("x".repeat(exactSourceBytes + 1), 0, List.of()));

    assertEquals(maximum, exact.bytes().length);
    assertEquals(0, exact.exitCode());
    assertTrue(
        new String(over.bytes(), StandardCharsets.UTF_8).contains("\"kind\":\"outputLimit\""));
    assertEquals(BsbCli.EXIT_INTERNAL, over.exitCode());
  }

  @Test
  void turnsInvalidInternalUnicodeIntoTheFixedInternalDocument() {
    CliJsonDocument invalid = CliJsonDocument.analysis("\uD800", 0, List.of());

    CliJsonRenderer.JsonRenderResult result = new CliJsonRenderer().render(invalid);

    assertEquals(BsbCli.EXIT_INTERNAL, result.exitCode());
    assertEquals(
        "{\"schemaVersion\":1,\"command\":\"check\",\"success\":false,\"exitCode\":70,"
            + "\"diagnostics\":[],\"problem\":{\"kind\":\"internal\","
            + "\"message\":\"処理系で予期しない問題が発生しました。\"}}\n",
        new String(result.bytes(), StandardCharsets.UTF_8));
  }
}
