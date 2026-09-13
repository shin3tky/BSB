package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class TextRegexReplaceSplitRuntimeTest {
  private static final String SOURCE_PATH = "TextRegex-replace-split.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsTheThreeNormativeReplaceAndSplitCases() throws Exception {
    assertRun("TEXT-N007", "XX\nがが\n");
    assertRun("TEXT-N008", "【「」、「a」、「」、「」】\n【「」】\n");
    assertRun("TEXT-N011", "3\n【「赤」、「青」、「緑」】\n");
  }

  @Test
  void reportsEmptyOperandsWithoutChangingInputsOrBudget() throws Exception {
    for (String caseId : List.of("TEXT-F020", "TEXT-F021")) {
      var output = new MemoryOutputSink();
      ProgramRunResult result = run(caseId, output);

      assertEquals(10, result.exitCode(), caseId);
      assertEquals(
          caseId.equals("TEXT-F020")
              ? DiagnosticCode.E_EMPTY_SEARCH_TEXT
              : DiagnosticCode.E_EMPTY_DELIMITER,
          result.diagnostics().getFirst().code(),
          caseId);
      assertEquals("abc", result.diagnostics().getFirst().fields().get("targetPreview"), caseId);
      assertEquals("", output.utf8Text(), caseId);
      assertEquals(0, result.arrayConstructionUnits(), caseId);
      assertEquals(0, result.arrayElementOperationUnits(), caseId);
      assertTrue(result.finalDataStack().size() >= 2, caseId);
    }
  }

  @Test
  void preflightsReplacementUtf8LengthBeforeChangingTheStack() throws Exception {
    String replacement = "x".repeat(StringLimits.MAX_UTF8_BYTES);
    var accepted = stack(string("a"), string("a"), string(replacement));
    execute("文字列を置き換える", accepted, budget(0, 0));
    assertEquals(replacement, ((StringValue) accepted.getFirst()).value());

    var rejected = stack(string("ab"), string("a"), string(replacement));
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("文字列を置き換える", rejected, budget(0, 0)));
    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, failure.diagnostic().code());
    assertEquals("16777217", failure.diagnostic().observed().orElseThrow());
    assertEquals(List.of(string("ab"), string("a"), string(replacement)), rejected);
  }

  @Test
  void enforcesSplitLengthAndBothArrayBudgetsAtomically() throws Exception {
    String acceptedTarget = "a,".repeat(ArrayLimits.MAX_LENGTH - 1) + "a";
    var acceptedBudget = budget(0, 0);
    var accepted = stack(string(acceptedTarget), string(","));
    execute("文字列を分割する", accepted, acceptedBudget);
    assertEquals(ArrayLimits.MAX_LENGTH, ((ArrayValue) accepted.getFirst()).size());
    assertEquals(ArrayLimits.MAX_LENGTH, acceptedBudget.arrayConstructionUnits());
    assertEquals(ArrayLimits.MAX_LENGTH, acceptedBudget.arrayElementOperationUnits());

    String tooManyTarget = ",".repeat(ArrayLimits.MAX_LENGTH);
    assertSplitFailure(tooManyTarget, budget(0, 0), DiagnosticCode.E_ARRAY_LENGTH_LIMIT, 0, 0);
    assertSplitFailure(
        acceptedTarget,
        budget(ArrayLimits.MAX_CONSTRUCTION_UNITS - ArrayLimits.MAX_LENGTH + 1, 0),
        DiagnosticCode.E_ARRAY_CONSTRUCTION_LIMIT,
        ArrayLimits.MAX_CONSTRUCTION_UNITS - ArrayLimits.MAX_LENGTH + 1,
        0);
    assertSplitFailure(
        acceptedTarget,
        budget(0, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - ArrayLimits.MAX_LENGTH + 1),
        DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT,
        0,
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - ArrayLimits.MAX_LENGTH + 1);
  }

  private static void assertSplitFailure(
      String target,
      ExecutionBudget budget,
      DiagnosticCode code,
      long expectedConstruction,
      long expectedOperations) {
    var stack = stack(string(target), string(","));
    List<RuntimeValue> original = List.copyOf(stack);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("文字列を分割する", stack, budget));
    assertEquals(code, failure.diagnostic().code());
    assertEquals(original, stack);
    assertEquals(expectedConstruction, budget.arrayConstructionUnits());
    assertEquals(expectedOperations, budget.arrayElementOperationUnits());
  }

  private static void assertRun(String caseId, String expectedOutput) throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(caseId, output);
    assertTrue(result.successful(), caseId + ": " + result.diagnostics());
    assertEquals(expectedOutput, output.utf8Text(), caseId);
    assertTrue(result.finalDataStack().isEmpty(), caseId);
  }

  private static ProgramRunResult run(String caseId, MemoryOutputSink output) throws Exception {
    return new ProgramRunner()
        .run(
            caseId + ".bsb",
            source(caseId),
            new ExecutionContext(output, () -> 0L, TraceSink.none()));
  }

  private static byte[] source(String caseId) throws Exception {
    String resource = "/conformance/text-regex/sources/" + caseId + ".bsb";
    try (var input = TextRegexReplaceSplitRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static void execute(
      String builtinName, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()), budget)
        .execute(BuiltinDictionary.find(builtinName).orElseThrow(), stack, SPAN);
  }

  private static ExecutionBudget budget(long construction, long operations) {
    return new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, construction, operations);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static StringValue string(String value) {
    return new StringValue(value);
  }
}
