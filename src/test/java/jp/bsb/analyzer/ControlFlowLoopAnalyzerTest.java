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

class ControlFlowLoopAnalyzerTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "FLOW-N007.bsb",
        "FLOW-N008.bsb",
        "FLOW-N009.bsb",
        "FLOW-N010.bsb",
        "FLOW-N011.bsb",
        "FLOW-N012.bsb",
        "FLOW-N013.bsb",
        "FLOW-N014.bsb",
        "FLOW-N015.bsb",
        "FLOW-N016.bsb",
        "FLOW-N019.bsb"
      })
  void acceptsNormalLoopAndTransferCases(String sourceName) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkControlFlowResource(sourceName);

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName);
  }

  @ParameterizedTest
  @MethodSource("loopFailureCases")
  void matchesSpecifiedLoopAndTransferFailure(
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
    assertEquals(relatedLocations, relatedLocations(diagnostic), sourceName);
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  @Test
  void suppressesUnreachableNameErrorsAndReportsOneWarning() throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkControlFlowResource("FLOW-F026.bsb");

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(1, result.diagnostics().size());
    var diagnostic = result.diagnostics().getFirst();
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(DiagnosticCode.W_UNREACHABLE_CODE, diagnostic.code());
    assertEquals(Severity.WARNING, diagnostic.severity());
    assertEquals(3, position.line());
    assertEquals(5, position.column());
    assertEquals(Map.of("cause", "戻る", "causeLine", "2", "causeColumn", "5"), diagnostic.fields());
    assertEquals("到達可能な処理", diagnostic.expected().orElseThrow());
    assertEquals("未定義", diagnostic.actual().orElseThrow());
    assertEquals(List.of("この処理を削除するか戻る前へ移動してください"), diagnostic.fixes());
    assertEquals(List.of("2:5 戻る"), relatedLocations(diagnostic));
    var unreachable =
        result.programForIrGeneration().syntax().definitions().getFirst().body().get(1);
    assertFalse(result.programForIrGeneration().isReachable(unreachable));
  }

  @ParameterizedTest
  @ValueSource(strings = {"打ち切る", "続ける"})
  void checksTheStackAtEachLoopTransfer(String transfer) {
    String source =
        "メインとは （--）\n"
            + "    1 回だけ\n"
            + "        99\n"
            + "        "
            + transfer
            + "\n"
            + "    繰り返す\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertFalse(result.successful());
    var diagnostic = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_LOOP_STACK_MISMATCH, diagnostic.code());
    assertEquals("[整数]", diagnostic.fields().get("actualStack"));
    assertEquals(transfer.equals("打ち切る") ? "脱出" : "継続", diagnostic.fields().get("pathKind"));
  }

  @Test
  void consumesTransfersAtTheInnermostLoopOnly() {
    String source =
        "メインとは （--）\n"
            + "    2 回だけ\n"
            + "        3 回だけ\n"
            + "            はい ならば\n"
            + "                打ち切る\n"
            + "            さもなければ\n"
            + "                続ける\n"
            + "            つぎに\n"
            + "        繰り返す\n"
            + "    繰り返す\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @Test
  void makesAConditionLoopWithoutAnExitPathUnreachableAfterTheLoop() {
    String source =
        "メインとは （--）\n"
            + "    ここから\n"
            + "        続ける\n"
            + "    続く間\n"
            + "    繰り返す\n"
            + "    未定義\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(List.of(DiagnosticCode.W_UNREACHABLE_CODE), codes(result));
  }

  @Test
  void preservesANonEmptyBaseStackAcrossAConditionLoop() {
    String source =
        "保つとは （整数 -- 整数）\n"
            + "    ここから\n"
            + "        いいえ\n"
            + "    続く間\n"
            + "    繰り返す\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    1 を 保つ\n"
            + "    一行表示する\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @Test
  void keepsTheZeroIterationPathWhenTheCountedLoopBodyReturns() {
    String source =
        "選ぶとは （-- 整数）\n"
            + "    0 回だけ\n"
            + "        1 戻る\n"
            + "    繰り返す\n"
            + "    2\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    選ぶ 一行表示する\n"
            + "こと。\n";

    AnalysisResult result = AnalyzerTestSupport.checkText(source);

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  @Test
  void acceptsExactly256NestedCountedLoopsWithoutFixedPointIteration() {
    var source = new StringBuilder("メインとは （--）\n");
    source.append("    1 回だけ\n".repeat(256));
    source.append("    繰り返す\n".repeat(256));
    source.append("こと。\n");

    AnalysisResult result = AnalyzerTestSupport.checkText(source.toString());

    assertTrue(result.successful(), result.diagnostics().toString());
  }

  private static Stream<Arguments> loopFailureCases() {
    return Stream.of(
        failure(
            "FLOW-F015.bsb",
            DiagnosticCode.E_REPEAT_COUNT_UNDERFLOW,
            2,
            5,
            Map.of(),
            "[整数]",
            "[]",
            "反復回数を置いてください",
            List.of()),
        failure(
            "FLOW-F016.bsb",
            DiagnosticCode.E_REPEAT_COUNT_TYPE_MISMATCH,
            2,
            8,
            Map.of("actualType", "真偽"),
            "整数",
            "真偽",
            "反復回数を整数にしてください",
            List.of()),
        failure(
            "FLOW-F017.bsb",
            DiagnosticCode.E_LOOP_STACK_MISMATCH,
            4,
            5,
            Map.of("pathKind", "通常終端", "base", "[]", "actualStack", "[整数]"),
            "[]",
            "[整数]",
            null,
            List.of("2:7 回だけ", "4:5 繰り返す")),
        failure(
            "FLOW-F018.bsb",
            DiagnosticCode.E_LOOP_CONDITION_MISMATCH,
            3,
            5,
            Map.of("base", "[]", "expectedStack", "[真偽]", "actualStack", "[]"),
            "[真偽]",
            "[]",
            "条件となる真偽値を置いてください",
            List.of("2:5 ここから")),
        failure(
            "FLOW-F019.bsb",
            DiagnosticCode.E_LOOP_CONDITION_MISMATCH,
            5,
            5,
            Map.of("base", "[]", "expectedStack", "[真偽]", "actualStack", "[整数,真偽]"),
            "[真偽]",
            "[整数, 真偽]",
            "余分な値を条件計算部に残さないでください",
            List.of("2:5 ここから")),
        failure(
            "FLOW-F020.bsb",
            DiagnosticCode.E_BREAK_OUTSIDE_LOOP,
            2,
            5,
            Map.of(),
            "ループ内",
            "打ち切る",
            "ループ内へ移動してください",
            List.of()),
        failure(
            "FLOW-F021.bsb",
            DiagnosticCode.E_CONTINUE_OUTSIDE_LOOP,
            2,
            5,
            Map.of(),
            "ループ内",
            "続ける",
            "ループ内へ移動してください",
            List.of()),
        failure(
            "FLOW-F022.bsb",
            DiagnosticCode.E_RETURN_EFFECT_MISMATCH,
            3,
            5,
            Map.of("word", "誤る"),
            "[]",
            "[整数]",
            "戻る前に宣言出力へ合わせてください",
            List.of("1:1 誤る")));
  }

  private static Arguments failure(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix,
      List<String> relatedLocations) {
    return Arguments.of(
        sourceName, code, line, column, fields, expected, actual, fix, relatedLocations);
  }

  private static List<String> relatedLocations(jp.bsb.diagnostics.Diagnostic diagnostic) {
    return diagnostic.relatedLocations().stream()
        .map(
            location ->
                location.position().line()
                    + ":"
                    + location.position().column()
                    + " "
                    + location.description())
        .toList();
  }

  private static List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
  }
}
