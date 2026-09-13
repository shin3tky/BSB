package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.frontend.ast.TypeReference;
import org.junit.jupiter.api.Test;

class OptionalOptionalTypeParserTest {
  @Test
  void parsesScalarArrayAndNestedOptionalTypeArguments() {
    var parsed =
        ParserTestSupport.parseText(
            "optional.bsb",
            "保持とは （任意 < 配列 < JSON > > 任意<任意<文字列>> -- 任意<整数>）\n" + "こと。\n\nメインとは （--）\nこと。\n");

    assertTrue(parsed.parseResult().successful(), parsed.diagnostics().toString());
    List<TypeReference> inputs =
        parsed
            .parseResult()
            .programForAnalysis()
            .definitions()
            .getFirst()
            .stackEffect()
            .inputTypes();
    assertEquals("任意<配列<JSON>>", inputs.get(0).name());
    assertTrue(inputs.get(0).isOptional());
    assertTrue(inputs.get(0).typeArgument().orElseThrow().isArray());
    assertEquals("任意<任意<文字列>>", inputs.get(1).name());
    assertTrue(inputs.get(1).typeArgument().orElseThrow().isOptional());
  }

  @Test
  void reportsOptionalSpecificEmptyAndMissingEndDiagnostics() {
    var empty = ParserTestSupport.parseText("empty.bsb", "メインとは （任意<> --）\nこと。\n");
    var missing = ParserTestSupport.parseText("missing.bsb", "メインとは （任意<整数 --）\nこと。\n");

    assertEquals(
        List.of(DiagnosticCode.E_EXPECTED_OPTIONAL_ELEMENT_TYPE),
        empty.diagnostics().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(
        "任意", empty.diagnostics().diagnostics().getFirst().fields().get("typeConstructor"));
    assertEquals(">", empty.diagnostics().diagnostics().getFirst().actual().orElseThrow());
    assertEquals(
        List.of(DiagnosticCode.E_EXPECTED_OPTIONAL_TYPE_END),
        missing.diagnostics().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(1, missing.diagnostics().diagnostics().getFirst().relatedLocations().size());
  }

  @Test
  void recoversAtTheInvalidInnerConstructorWithoutDerivedOuterErrors() {
    var parsed = ParserTestSupport.parseText("recovery.bsb", "メインとは （配列<任意<>> 任意<配列<>> --）\nこと。\n");

    assertEquals(
        List.of(
            DiagnosticCode.E_EXPECTED_OPTIONAL_ELEMENT_TYPE,
            DiagnosticCode.E_EXPECTED_ARRAY_ELEMENT_TYPE),
        parsed.diagnostics().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  @Test
  void reportsEveryActuallyMissingOptionalEndFromInsideOut() {
    var parsed = ParserTestSupport.parseText("ends.bsb", "メインとは （任意<任意<整数 --）\nこと。\n");

    assertEquals(
        List.of(
            DiagnosticCode.E_EXPECTED_OPTIONAL_TYPE_END,
            DiagnosticCode.E_EXPECTED_OPTIONAL_TYPE_END),
        parsed.diagnostics().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  @Test
  void acceptsDepth256AndRejects257BeforeBuildingThatNode() {
    var accepted = ParserTestSupport.parseText("depth256.bsb", sourceWithDepth(256));
    var rejected = ParserTestSupport.parseText("depth257.bsb", sourceWithDepth(257));

    assertTrue(accepted.parseResult().successful(), accepted.diagnostics().toString());
    TypeReference type =
        accepted
            .parseResult()
            .programForAnalysis()
            .definitions()
            .getFirst()
            .stackEffect()
            .inputTypes()
            .getFirst();
    assertEquals(256, count(type.name(), "任意<"));
    assertFalse(rejected.parseResult().successful());
    assertEquals(
        List.of(DiagnosticCode.E_SYNTAX_DEPTH_LIMIT),
        rejected.diagnostics().diagnostics().stream()
            .map(diagnostic -> diagnostic.code())
            .toList());
    assertEquals("256", rejected.diagnostics().diagnostics().getFirst().limit().orElseThrow());
    assertEquals("257", rejected.diagnostics().diagnostics().getFirst().observed().orElseThrow());
  }

  private static String sourceWithDepth(int depth) {
    return "保持とは （"
        + "任意<".repeat(depth)
        + "整数"
        + ">".repeat(depth)
        + " --）\nこと。\n\nメインとは （--）\nこと。\n";
  }

  private static int count(String text, String needle) {
    int result = 0;
    for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + 1)) {
      result++;
    }
    return result;
  }
}
