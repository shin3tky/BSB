package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.regex.Re2RegexCompiler;
import jp.bsb.regex.RegexCompilationResult;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class TextRegexRegexMatchRuntimeTest {
  private static final String SOURCE_PATH = "TextRegex-regex-match.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsAllNormativeCompilerAndMatchCases() throws Exception {
    assertRun("TEXT-N012", "はい\n");
    assertRun("TEXT-N013", "はい\nはい\nいいえ\n");
    assertRun("TEXT-N014", "INV-0042\n");
    assertRun("TEXT-N015", "2026\n0042\n");
    assertRun("TEXT-N018", "はい\nはい\nはい\nはい\n");
  }

  @Test
  void reportsTheThreeNormativeMatchFailuresWithoutConsumingInputs() throws Exception {
    for (String caseId : List.of("TEXT-F025", "TEXT-F026", "TEXT-F027")) {
      var output = new MemoryOutputSink();
      ProgramRunResult result = run(caseId, output);

      assertEquals(10, result.exitCode(), caseId);
      assertEquals(expectedCode(caseId), result.diagnostics().getFirst().code(), caseId);
      assertEquals("", output.utf8Text(), caseId);
      assertEquals(caseId.equals("TEXT-F025") ? 2 : 3, result.finalDataStack().size(), caseId);
    }
  }

  @Test
  void preservesSupplementaryCombiningAndParticipatingEmptyCaptures() {
    String source =
        "メインとは （--）\n"
            + "    「前𠮷゙後」 と 正規表現「𠮷゙」 を 正規表現で最初を取り出す を 一行表示する\n"
            + "    「」 と 正規表現「(?<empty>)」 と 「empty」 を 正規表現の名前付き部分を取り出す を 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("𠮷゙\n\n", output.utf8Text());
  }

  @Test
  void checksGroupExistenceBeforeAttemptingAMatch() throws Exception {
    RegexValue regex = regex("(?<present>[0-9]+)");
    var stack =
        new ArrayList<RuntimeValue>(
            List.of(new StringValue("abc"), regex, new StringValue("missing")));

    RuntimeFailure failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeFailure.class,
            () ->
                new BuiltinExecutor(
                        SOURCE_PATH,
                        new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()),
                        new ExecutionBudget(SOURCE_PATH, () -> 0L))
                    .execute(
                        BuiltinDictionary.find("正規表現の名前付き部分を取り出す").orElseThrow(), stack, SPAN));

    assertEquals(DiagnosticCode.E_REGEX_GROUP_NOT_FOUND, failure.diagnostic().code());
    assertEquals("present", failure.diagnostic().fields().get("available"));
    assertEquals(List.of(new StringValue("abc"), regex, new StringValue("missing")), stack);
  }

  private static RegexValue regex(String pattern) {
    RegexCompilationResult.Success success =
        (RegexCompilationResult.Success) new Re2RegexCompiler().compile(pattern, "");
    return new RegexValue(pattern, "", success.program());
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
    try (var input = TextRegexRegexMatchRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static DiagnosticCode expectedCode(String caseId) {
    return switch (caseId) {
      case "TEXT-F025" -> DiagnosticCode.E_REGEX_NO_MATCH;
      case "TEXT-F026" -> DiagnosticCode.E_REGEX_GROUP_NOT_FOUND;
      case "TEXT-F027" -> DiagnosticCode.E_REGEX_GROUP_UNMATCHED;
      default -> throw new AssertionError(caseId);
    };
  }
}
