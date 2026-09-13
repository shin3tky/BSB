package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.regex.RegexLimits;
import org.junit.jupiter.api.Test;

class TextRegexRegexBudgetTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void acceptsThePerCallBoundaryAndRejectsOneInputBoundaryMoreAtomically() throws RuntimeFailure {
    var accepted = new ExecutionBudget("regex.bsb", () -> 0L);
    accepted.beforeRegexWork(1_000, 999_999, SPAN, "正規表現を含む");
    assertEquals(RegexLimits.MAX_WORK_UNITS_PER_CALL, accepted.regexWorkUnits());

    var rejected = new ExecutionBudget("regex.bsb", () -> 0L);
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () -> rejected.beforeRegexWork(1_000, 1_000_000, SPAN, "正規表現を含む"));
    assertEquals(DiagnosticCode.E_REGEX_WORK_LIMIT, failure.diagnostic().code());
    assertEquals("1000001000", failure.diagnostic().observed().orElseThrow());
    assertEquals(0, rejected.regexWorkUnits());
  }

  @Test
  void acceptsTheCumulativeBoundaryAndRejectsOneUnitMoreAtomically() throws RuntimeFailure {
    var accepted = budget(RegexLimits.MAX_WORK_UNITS_PER_EXECUTION - 1);
    accepted.beforeRegexWork(1, 0, SPAN, "正規表現に完全一致する");
    assertEquals(RegexLimits.MAX_WORK_UNITS_PER_EXECUTION, accepted.regexWorkUnits());

    var rejected = budget(RegexLimits.MAX_WORK_UNITS_PER_EXECUTION);
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> rejected.beforeRegexWork(1, 0, SPAN, "正規表現に完全一致する"));
    assertEquals(DiagnosticCode.E_REGEX_TOTAL_WORK_LIMIT, failure.diagnostic().code());
    assertEquals("10000000001", failure.diagnostic().observed().orElseThrow());
    assertEquals(RegexLimits.MAX_WORK_UNITS_PER_EXECUTION, rejected.regexWorkUnits());
  }

  @Test
  void saturatesOverflowingFactorsWithoutWrappingOrChangingTheBudget() {
    var budget = new ExecutionBudget("regex.bsb", () -> 0L);

    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                budget.beforeRegexWork(
                    RegexLimits.MAX_PROGRAM_INSTRUCTIONS, Long.MAX_VALUE, SPAN, "正規表現で分割する"));

    assertEquals(DiagnosticCode.E_REGEX_WORK_LIMIT, failure.diagnostic().code());
    assertEquals(Long.toString(Long.MAX_VALUE), failure.diagnostic().observed().orElseThrow());
    assertEquals(0, budget.regexWorkUnits());
  }

  @Test
  void rejectsInvalidFactorsAndInitialCounts() {
    var budget = new ExecutionBudget("regex.bsb", () -> 0L);

    assertThrows(
        IllegalArgumentException.class, () -> budget.beforeRegexWork(0, 0, SPAN, "正規表現を含む"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            budget.beforeRegexWork(RegexLimits.MAX_PROGRAM_INSTRUCTIONS + 1L, 0, SPAN, "正規表現を含む"));
    assertThrows(
        IllegalArgumentException.class, () -> budget.beforeRegexWork(1, -1, SPAN, "正規表現を含む"));
    assertThrows(
        IllegalArgumentException.class, () -> budget(RegexLimits.MAX_WORK_UNITS_PER_EXECUTION + 1));
  }

  private static ExecutionBudget budget(long regexWorkUnits) {
    return new ExecutionBudget("regex.bsb", () -> 0L, 0, 0, 0, 0, regexWorkUnits);
  }
}
