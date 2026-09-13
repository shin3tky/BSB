package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class TextRegexBasicStringRuntimeTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsTheSixNormativeBasicStringCases() throws Exception {
    assertRun("TEXT-N001", "1\n2\n1\n2\n1\n1\n");
    assertRun("TEXT-N002", "が\nか\n");
    assertRun("TEXT-N003", "が\nが\n\n");
    assertRun("TEXT-N004", "2\n3\n-1\n0\n");
    assertRun("TEXT-N005", "山田さん\n-1\n0\n");
    assertRun("TEXT-N006", "山田 太郎\n");
  }

  @Test
  void reportsAllFiveNormativeBoundaryFailuresWithoutConsumingInputs() throws Exception {
    for (String caseId : List.of("TEXT-F015", "TEXT-F016", "TEXT-F017", "TEXT-F018", "TEXT-F019")) {
      var output = new MemoryOutputSink();
      ProgramRunResult result = run(caseId, output);
      Diagnostic diagnostic = result.diagnostics().getFirst();

      assertEquals(10, result.exitCode(), caseId);
      assertEquals(expectedCode(caseId), diagnostic.code(), caseId);
      assertEquals(expectedUnit(caseId), diagnostic.fields().get("unit"), caseId);
      assertEquals("", output.utf8Text(), caseId);
      assertTrue(result.finalDataStack().size() >= 2, caseId);
    }
  }

  @Test
  void preflightsTheSixteenMebibyteConcatenationBoundaryAtomically() throws Exception {
    var executor =
        new BuiltinExecutor(
            "concat-limit.bsb",
            new BoundedOutput("concat-limit.bsb", new MemoryOutputSink()),
            new ExecutionBudget("concat-limit.bsb", () -> 0L));
    String first = "a".repeat(StringLimits.MAX_UTF8_BYTES / 2);
    String acceptedSecond = "b".repeat(StringLimits.MAX_UTF8_BYTES / 2);
    var accepted =
        new ArrayList<RuntimeValue>(
            List.of(new StringValue(first), new StringValue(acceptedSecond)));

    executor.execute(BuiltinDictionary.find("つなぐ").orElseThrow(), accepted, SPAN);
    assertEquals(1, accepted.size());
    assertEquals(StringLimits.MAX_UTF8_BYTES, ((StringValue) accepted.getFirst()).value().length());

    String rejectedSecond = acceptedSecond + "b";
    var rejected =
        new ArrayList<RuntimeValue>(
            List.of(new StringValue(first), new StringValue(rejectedSecond)));
    RuntimeFailure failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class,
            () -> executor.execute(BuiltinDictionary.find("つなぐ").orElseThrow(), rejected, SPAN));

    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, failure.diagnostic().code());
    assertEquals("16777217", failure.diagnostic().observed().orElseThrow());
    assertEquals(List.of(new StringValue(first), new StringValue(rejectedSecond)), rejected);
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
    try (var input = TextRegexBasicStringRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static DiagnosticCode expectedCode(String caseId) {
    return switch (caseId) {
      case "TEXT-F015", "TEXT-F016" -> DiagnosticCode.E_STRING_INDEX_OUT_OF_BOUNDS;
      case "TEXT-F017" -> DiagnosticCode.E_STRING_RANGE_OUT_OF_BOUNDS;
      case "TEXT-F018" -> DiagnosticCode.E_CODE_POINT_INDEX_OUT_OF_BOUNDS;
      case "TEXT-F019" -> DiagnosticCode.E_CODE_POINT_RANGE_OUT_OF_BOUNDS;
      default -> throw new AssertionError(caseId);
    };
  }

  private static String expectedUnit(String caseId) {
    return caseId.equals("TEXT-F018") || caseId.equals("TEXT-F019") ? "codePoint" : "grapheme";
  }
}
