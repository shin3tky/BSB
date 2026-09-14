package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ControlFlowConditionalAnalyzerTest {
  @ParameterizedTest
  @ValueSource(strings = {"はい または\n        いいえ\n    つぎに", "いいえ かつ\n        はい\n    つぎに"})
  void acceptsShortCircuitBlocksThatProduceOneBoolean(String expression) {
    String source = "メインとは （--）\n    " + expression + "\n    一行表示する\nこと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @ParameterizedTest
  @MethodSource("shortCircuitFailureCases")
  void rejectsInvalidShortCircuitStacks(String body, DiagnosticCode expectedCode) {
    AnalysisResult result = AnalyzerTestSupport.checkText("メインとは （--）\n    " + body + "\nこと。\n");

    assertFalse(result.successful());
    assertEquals(List.of(expectedCode), result.diagnostics().stream().map(d -> d.code()).toList());
  }

  private static Stream<Arguments> shortCircuitFailureCases() {
    return Stream.of(
        Arguments.of("または\n        はい\n    つぎに", DiagnosticCode.E_SHORT_CIRCUIT_LEFT_UNDERFLOW),
        Arguments.of(
            "1 かつ\n        はい\n    つぎに", DiagnosticCode.E_SHORT_CIRCUIT_LEFT_TYPE_MISMATCH),
        Arguments.of(
            "はい または\n        1\n    つぎに 一行表示する", DiagnosticCode.E_SHORT_CIRCUIT_RIGHT_MISMATCH));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "FLOW-N001.bsb",
        "FLOW-N002.bsb",
        "FLOW-N003.bsb",
        "FLOW-N004.bsb",
        "FLOW-N005.bsb",
        "FLOW-N016.bsb"
      })
  void acceptsNormalAndNestedConditionalCases(String sourceName) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkControlFlowResource(sourceName);

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName);
  }

  @ParameterizedTest
  @MethodSource("conditionalFailureCases")
  void matchesSpecifiedConditionalFailure(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      List<String> relatedLocations)
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkControlFlowResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(8, result.exitCode(), sourceName);
    assertEquals(1, result.diagnostics().size(), sourceName);
    var diagnostic = result.diagnostics().getFirst();
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(Severity.ERROR, diagnostic.severity(), sourceName);
    assertEquals(DiagnosticStage.TYPE_AND_STACK, diagnostic.stage(), sourceName);
    assertEquals(line, position.line(), sourceName);
    assertEquals(column, position.column(), sourceName);
    assertEquals(fields, diagnostic.fields(), sourceName);
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(fix == null ? List.of() : List.of(fix), diagnostic.fixes(), sourceName);
    assertEquals(
        relatedLocations,
        diagnostic.relatedLocations().stream()
            .map(
                location ->
                    location.position().line()
                        + ":"
                        + location.position().column()
                        + " "
                        + location.description())
            .toList(),
        sourceName);
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  @Test
  void checksFollowingCallsAgainstTheJoinedStack() {
    String source =
        "選ぶとは （真偽 -- 整数）\n"
            + "    ならば\n"
            + "        1\n"
            + "    さもなければ\n"
            + "        2\n"
            + "    つぎに\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    はい を 選ぶ\n"
            + "    一行表示する\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @Test
  void joinsAFallthroughWithAnEarlyReturnWithoutComparingTheirStacks() {
    String source =
        "選ぶとは （真偽 -- 整数）\n"
            + "    ならば\n"
            + "        1\n"
            + "        戻る\n"
            + "    さもなければ\n"
            + "        2\n"
            + "    つぎに\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    はい を 選ぶ\n"
            + "    一行表示する\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @Test
  void makesTheConditionalExitUnreachableWhenBothBranchesReturn() {
    String source =
        "選ぶとは （真偽 -- 整数）\n"
            + "    ならば\n"
            + "        1 戻る\n"
            + "    さもなければ\n"
            + "        2 戻る\n"
            + "    つぎに\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @Test
  void suppressesBranchJoinDiagnosticsWhenTheConditionCannotBeChecked() {
    String source =
        "メインとは （--）\n"
            + "    ならば\n"
            + "        1\n"
            + "    さもなければ\n"
            + "        「別型」\n"
            + "    つぎに\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertEquals(
        List.of(DiagnosticCode.E_CONDITION_STACK_UNDERFLOW),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  @Test
  void resolvesNamesInsideNestedBranches() {
    String source =
        "メインとは （--）\n"
            + "    はい ならば\n"
            + "        はい ならば\n"
            + "            未定義\n"
            + "        つぎに\n"
            + "    つぎに\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertFalse(result.successful());
    assertEquals(DiagnosticCode.E_UNDEFINED_WORD, result.diagnostics().getFirst().code());
    var position = result.diagnostics().getFirst().location().displayPosition().orElseThrow();
    assertEquals(4, position.line());
    assertEquals(13, position.column());
  }

  @Test
  void analyzesExactly256NestedConditionals() {
    var source = new StringBuilder("メインとは （--）\n");
    source.append("    はい ならば\n".repeat(256));
    source.append("    つぎに\n".repeat(256));
    source.append("こと。\n");

    AnalysisResult result = AnalyzerTestSupport.checkText(source.toString());

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  private static Stream<Arguments> conditionalFailureCases() {
    return Stream.of(
        Arguments.of(
            "FLOW-F010.bsb",
            DiagnosticCode.E_CONDITION_STACK_UNDERFLOW,
            2,
            5,
            Map.of(),
            "[真偽]",
            "[]",
            "条件となる真偽値を置いてください",
            List.of()),
        Arguments.of(
            "FLOW-F011.bsb",
            DiagnosticCode.E_CONDITION_TYPE_MISMATCH,
            2,
            11,
            Map.of("actualType", "文字列"),
            "真偽",
            "文字列",
            "条件を真偽にしてください",
            List.of()),
        Arguments.of(
            "FLOW-F012.bsb",
            DiagnosticCode.E_BRANCH_STACK_MISMATCH,
            5,
            5,
            Map.of("base", "[]", "trueStack", "[整数]", "falseStack", "[]"),
            "両側で同じ型列",
            "真側[整数]、偽側[]",
            null,
            List.of("3:9 真側", "4:5 偽側")),
        Arguments.of(
            "FLOW-F013.bsb",
            DiagnosticCode.E_BRANCH_STACK_MISMATCH,
            6,
            5,
            Map.of("base", "[]", "trueStack", "[整数]", "falseStack", "[文字列]"),
            "両側で同じ型列",
            "真側[整数]、偽側[文字列]",
            null,
            List.of("3:9 真側", "5:9 偽側")),
        Arguments.of(
            "FLOW-F014.bsb",
            DiagnosticCode.E_BRANCH_STACK_MISMATCH,
            4,
            5,
            Map.of("base", "[]", "trueStack", "[整数]", "falseStack", "[]"),
            "真側[]",
            "真側[整数]",
            null,
            List.of("2:8 ならば", "4:5 つぎに")));
  }
}
