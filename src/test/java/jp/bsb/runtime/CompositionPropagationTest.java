package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class CompositionPropagationTest {
  @Test
  void propagatesOptionalAbsenceAndResultFailureWhilePreservingPrefix() {
    String source =
        "任意を流すとは （任意<JSON> -- 任意<JSON>）\n"
            + "    任意から値を取り出すか戻る\n"
            + "    任意にする\n"
            + "こと。\n\n"
            + "結果を流すとは （文字列 結果<整数,文字列> -- 文字列 結果<整数,文字列>）\n"
            + "    結果から成功値を取り出すか戻る\n"
            + "    1 足す\n"
            + "    成功にする<整数,文字列>\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    「{\\\"id\\\":1}」 JSONを解析する 「id」 を JSONオブジェクトから任意値を取り出す\n"
            + "    任意を流す 一行表示する\n"
            + "    空のJSONオブジェクト 「id」 を JSONオブジェクトから任意値を取り出す\n"
            + "    任意を流す 一行表示する\n"
            + "    「ok」を 41 を 成功にする<整数,文字列> 結果を流す\n"
            + "    一行表示する 一行表示する\n"
            + "    「kept」を 「bad」を 失敗にする<整数,文字列> 結果を流す\n"
            + "    一行表示する 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "composition.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("ある（1）\nない\n成功（42）\nok\n失敗（bad）\nkept\n", output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
    assertTrue(
        events.stream()
            .anyMatch(
                event ->
                    event.opcode().equals("PropagateOptionalOrReturn")
                        && event.branchTaken().orElse(false)));
    assertTrue(
        events.stream()
            .anyMatch(
                event ->
                    event.opcode().equals("PropagateResultOrReturn")
                        && event.branchTaken().orElse(false)));
  }

  @Test
  void diagnosesContextPrefixFailureTypeAndInputErrors() {
    assertCode(
        "メインとは （--）\n    1 任意にする 任意から値を取り出すか戻る\nこと。\n",
        DiagnosticCode.E_OPTIONAL_PROPAGATION_CONTEXT);
    assertCode(
        "不一致とは （文字列 任意<整数> -- 任意<整数>）\n"
            + "    任意から値を取り出すか戻る\n"
            + "    任意にする\n"
            + "こと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_OPTIONAL_PROPAGATION_EFFECT_MISMATCH);
    assertCode(
        "不一致とは （結果<整数,文字列> -- 結果<整数,真偽>）\n"
            + "    結果から成功値を取り出すか戻る\n"
            + "    成功にする<整数,真偽>\n"
            + "こと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_RESULT_PROPAGATION_EFFECT_MISMATCH);
    assertCode(
        "不正とは （結果<整数,文字列> -- 整数）\n" + "    結果から成功値を取り出すか戻る\n" + "こと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_RESULT_PROPAGATION_CONTEXT);
    assertCode(
        "不足とは （-- 任意<整数>）\n" + "    任意から値を取り出すか戻る\n" + "こと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_STACK_UNDERFLOW);
    assertCode(
        "型違いとは （整数 -- 任意<整数>）\n"
            + "    任意から値を取り出すか戻る\n"
            + "    任意にする\n"
            + "こと。\n\nメインとは （--）\nこと。\n",
        DiagnosticCode.E_TYPE_MISMATCH);
  }

  private static void assertCode(String source, DiagnosticCode expected) {
    var result =
        new SourceChecker()
            .check("propagation-failure.bsb", source.getBytes(StandardCharsets.UTF_8));
    assertFalse(result.successful(), result.diagnostics().toString());
    assertEquals(expected, result.diagnostics().getFirst().code(), result.diagnostics().toString());
  }
}
