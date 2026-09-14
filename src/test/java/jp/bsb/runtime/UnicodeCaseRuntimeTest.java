package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class UnicodeCaseRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsFullMappingsAndFoldedScalarComparison() throws Exception {
    assertRun(
        "メインとは （--）\n"
            + "  「Straße」を 大文字に変換する を 一行表示する\n"
            + "  「ΟΣ」を 小文字に変換する を 一行表示する\n"
            + "  「İ」を 小文字に変換する を 一行表示する\n"
            + "  「Straße」と「STRASSE」を 大小文字を無視して比較する を 一行表示する\n"
            + "  「Σ」と「ς」を 大小文字を無視して比較する を 一行表示する\n"
            + "  「İ」と「i」を 大小文字を無視して比較する を 一行表示する\n"
            + "  「が」と「が」を 大小文字を無視して比較する を 一行表示する\n"
            + "こと。\n",
        "STRASSE\nος\ni\u0307\n0\n0\n1\n1\n");
  }

  @Test
  void acceptsAMappingResultExactlyAtTheStringLimit() throws Exception {
    var executor =
        new BuiltinExecutor(
            "case-limit.bsb",
            new BoundedOutput("case-limit.bsb", new MemoryOutputSink()),
            new ExecutionBudget("case-limit.bsb", () -> 0L));
    String input = "İ".repeat(5_592_405) + "a";
    var stack = new ArrayList<RuntimeValue>(List.of(new StringValue(input)));

    executor.execute(BuiltinDictionary.find("小文字に変換する").orElseThrow(), stack, SPAN);

    assertEquals(
        StringLimits.MAX_UTF8_BYTES,
        Utf8Length.measureUpTo(((StringValue) stack.getFirst()).value(), Long.MAX_VALUE).bytes());
  }

  @Test
  void rejectsExpandedMappingOverTheStringLimitWithoutConsumingInput() throws Exception {
    var executor =
        new BuiltinExecutor(
            "case-limit.bsb",
            new BoundedOutput("case-limit.bsb", new MemoryOutputSink()),
            new ExecutionBudget("case-limit.bsb", () -> 0L));
    String input = "İ".repeat(5_592_405) + "aa";
    var stack = new ArrayList<RuntimeValue>(List.of(new StringValue(input)));

    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () -> executor.execute(BuiltinDictionary.find("小文字に変換する").orElseThrow(), stack, SPAN));

    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, failure.diagnostic().code());
    assertEquals("16777217", failure.diagnostic().observed().orElseThrow());
    assertEquals(List.of(new StringValue(input)), stack);
  }

  private static void assertRun(String source, String expectedOutput) throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "unicode-case.bsb",
                source.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(expectedOutput, output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
  }
}
