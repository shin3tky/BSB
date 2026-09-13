package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ExplainJsonRendererTest {
  private static final JsonSpan SPAN =
      new JsonSpan(new JsonLineColumn(1, 1), new JsonLineColumn(1, 2), 0, 6);

  @Test
  void rendersEveryExplanationMemberInNormativeOrder() {
    var explanation =
        new ExplainJson(
            new ExplainSummaryJson(
                "メイン",
                List.of("メイン"),
                List.of("一行表示する"),
                List.of("console.output"),
                List.of("console.output")),
            List.of(
                new ExplainBuiltinWordJson(
                    "一行表示する",
                    List.of(),
                    "説明",
                    new ExplainStackEffectJson(List.of("表示可能"), List.of(), true),
                    "displayable",
                    List.of("console.output"),
                    List.of("console.output"),
                    "2",
                    "42 を 一行表示する")),
            List.of(
                new ExplainUserWordJson(
                    "メイン",
                    "メイン",
                    SPAN,
                    new ExplainStackEffectJson(List.of(), List.of(), true),
                    true,
                    List.of("console.output"),
                    List.of("console.output"),
                    List.of("console.output"),
                    List.of("console.output"))),
            List.of(new ExplainScopeJson("S1", "global", Optional.empty(), Optional.empty(), SPAN)),
            List.of(
                new ExplainBindingJson(
                    "B1",
                    "値",
                    "値",
                    "variable",
                    "global",
                    "S1",
                    "整数",
                    OptionalInt.of(1),
                    SPAN,
                    List.of(new ExplainBindingUseJson("値", "read", SPAN)))));
    var document = ExplainJsonDocument.success("input.bsb", List.of(), explanation);

    var result = new ExplainJsonRenderer().render(document);
    String json = new String(result.bytes(), StandardCharsets.UTF_8);

    assertEquals(0, result.exitCode());
    assertTrue(json.startsWith("{\"schemaVersion\":1,\"command\":\"explain\""));
    assertTrue(json.contains("\"summary\":{\"entryPoint\":\"メイン\""));
    assertTrue(json.contains("\"builtinWords\":[{\"name\":\"一行表示する\""));
    assertTrue(json.contains("\"userWords\":[{\"name\":\"メイン\""));
    assertTrue(json.contains("\"scopes\":[{\"id\":\"S1\",\"kind\":\"global\""));
    assertTrue(json.contains("\"bindings\":[{\"id\":\"B1\""));
    assertTrue(
        json.contains("\"parameterizedCapabilities\":{\"schemaVersion\":1,\"declarations\":[]"));
    assertTrue(json.endsWith("\n"));
  }

  @Test
  void rendersProblemsWithEmptyExplanationArrays() {
    var document =
        ExplainJsonDocument.problem(
            Optional.empty(), 2, CliJsonProblemKind.USAGE, "ソースファイルを1つ指定してください。");

    String json =
        new String(new ExplainJsonRenderer().render(document).bytes(), StandardCharsets.UTF_8);

    assertEquals(
        "{\"schemaVersion\":1,\"command\":\"explain\",\"success\":false,\"exitCode\":2,"
            + "\"diagnostics\":[],\"builtinWords\":[],\"userWords\":[],\"scopes\":[],"
            + "\"bindings\":[],\"problem\":{\"kind\":\"usage\","
            + "\"message\":\"ソースファイルを1つ指定してください。\"}}\n",
        json);
  }

  @Test
  void usesCommandSpecificLimitAndInternalFallbackDocuments() {
    int maximum = 300;
    var renderer = new ExplainJsonRenderer(maximum);
    var over =
        renderer.render(
            ExplainJsonDocument.problem(
                Optional.of("x".repeat(300)), 2, CliJsonProblemKind.USAGE, "問題"));
    var invalid =
        renderer.render(
            ExplainJsonDocument.problem(Optional.of("\uD800"), 2, CliJsonProblemKind.USAGE, "問題"));

    assertEquals(BsbCli.EXIT_INTERNAL, over.exitCode());
    assertTrue(
        new String(over.bytes(), StandardCharsets.UTF_8).contains("\"kind\":\"outputLimit\""));
    assertEquals(BsbCli.EXIT_INTERNAL, invalid.exitCode());
    assertTrue(
        new String(invalid.bytes(), StandardCharsets.UTF_8).contains("\"kind\":\"internal\""));
  }

  @Test
  void acceptsTheConfiguredLimitAndFallsBackAtOneByteOver() {
    var empty =
        ExplainJsonDocument.problem(Optional.of(""), 2, CliJsonProblemKind.USAGE, "引数を確認してください。");
    int baseSize = new ExplainJsonRenderer().render(empty).bytes().length;
    int maximum = 300;
    int exactSourceBytes = maximum - baseSize;
    var renderer = new ExplainJsonRenderer(maximum);

    var exact =
        renderer.render(
            ExplainJsonDocument.problem(
                Optional.of("x".repeat(exactSourceBytes)),
                2,
                CliJsonProblemKind.USAGE,
                "引数を確認してください。"));
    var over =
        renderer.render(
            ExplainJsonDocument.problem(
                Optional.of("x".repeat(exactSourceBytes + 1)),
                2,
                CliJsonProblemKind.USAGE,
                "引数を確認してください。"));

    assertEquals(maximum, exact.bytes().length);
    assertEquals(2, exact.exitCode());
    assertEquals(BsbCli.EXIT_INTERNAL, over.exitCode());
    assertTrue(
        new String(over.bytes(), StandardCharsets.UTF_8).contains("\"kind\":\"outputLimit\""));
  }

  @Test
  void escapesControlsPreservesUnicodeScalarsAndRendersDeterministically() {
    String source = "\"\\/\b\t\n\f\r\u0000\u001F日本か\u3099𠮷👩‍💻\u2028\u2029";
    var document =
        ExplainJsonDocument.problem(
            Optional.of(source), 2, CliJsonProblemKind.USAGE, "引数を確認してください。");
    var renderer = new ExplainJsonRenderer();

    byte[] first = renderer.render(document).bytes();
    byte[] second = renderer.render(document).bytes();
    String json = new String(first, StandardCharsets.UTF_8);

    assertArrayEquals(first, second);
    assertTrue(
        json.contains(
            "\"source\":\"\\\"\\\\/\\b\\t\\n\\f\\r\\u0000\\u001F日本か\u3099𠮷👩‍💻\u2028\u2029\""));
    assertTrue(json.endsWith("\n"));
    assertFalse(json.endsWith("\n\n"));
  }
}
