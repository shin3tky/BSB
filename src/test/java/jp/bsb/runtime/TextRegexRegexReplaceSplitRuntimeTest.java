package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.regex.Re2RegexCompiler;
import jp.bsb.regex.RegexCompilationResult;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

class TextRegexRegexReplaceSplitRuntimeTest {
  private static final String SOURCE_PATH = "TextRegex-regex-replace-split.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsTheNormativeReplacementAndSplitCases() throws Exception {
    assertRun("TEXT-N016", "0042/2026 $ 2026-0042\n");
    assertRun("TEXT-N017", "【「a」、「b」、「」】\n【「」、「a」、「b」、「」】\n");
  }

  @Test
  void reportsTheNormativeTemplateFailureBeforeMatchingWithoutChangingInputs() throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run("TEXT-F028", output);

    assertEquals(10, result.exitCode());
    assertEquals("", output.utf8Text());
    assertEquals(0, result.regexWorkUnits());
    assertEquals(3, result.finalDataStack().size());
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_REGEX_REPLACEMENT_TEMPLATE, diagnostic.code());
    assertEquals(2, diagnostic.location().displayPosition().orElseThrow().line());
    assertEquals(48, diagnostic.location().displayPosition().orElseThrow().column());
    var fields = new LinkedHashMap<String, String>();
    fields.put("word", "正規表現で置き換える");
    fields.put("templateOffset", "0");
    fields.put("reference", "missing");
    fields.put("available", "n");
    fields.put("groupCount", "1");
    assertEquals(
        List.copyOf(fields.keySet()),
        diagnostic.fields().keySet().stream().limit(fields.size()).toList());
    fields.forEach((key, value) -> assertEquals(value, diagnostic.fields().get(key), key));
    assertEquals("存在するキャプチャ参照", diagnostic.expected().orElseThrow());
    assertEquals("${missing}", diagnostic.actual().orElseThrow());
    assertEquals(List.of("${n}を使用してください"), diagnostic.fixes());
  }

  @Test
  void preservesPriorOutputAndTheFailingCallInputs() throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run("TEXT-F029", output);

    assertEquals(10, result.exitCode());
    assertEquals(DiagnosticCode.E_REGEX_NO_MATCH, result.diagnostics().getFirst().code());
    assertEquals("前\n", output.utf8Text());
    assertEquals(2, result.finalDataStack().size());
    assertTrue(result.regexWorkUnits() > 0);
  }

  @Test
  void expandsEveryTemplateFormAndTurnsUnmatchedCapturesIntoEmptyText() throws Exception {
    RegexValue regex = regex("(?<n>a)(b)?");
    var stack = stack(string("ab a"), regex, string("$$/$0/$1/$2/${n}"));

    execute("正規表現で置き換える", stack, budget());

    assertEquals(List.of(string("$/ab/a/b/a $/a/a//a")), stack);
    for (String invalid : List.of("$", "$3", "${missing}", "${n")) {
      var invalidStack = stack(string("a"), regex, string(invalid));
      var invalidBudget = budget();
      RuntimeFailure failure =
          assertThrows(
              RuntimeFailure.class, () -> execute("正規表現で置き換える", invalidStack, invalidBudget));
      assertEquals(DiagnosticCode.E_REGEX_REPLACEMENT_TEMPLATE, failure.diagnostic().code());
      assertEquals(0, invalidBudget.regexWorkUnits());
      assertEquals(List.of(string("a"), regex, string(invalid)), invalidStack);
    }
  }

  @Test
  void advancesEmptyMatchesByOneUnicodeCodePointAndKeepsBothEnds() throws Exception {
    RegexValue empty = regex("");
    var replacement = stack(string("𠮷a"), empty, string("-"));
    execute("正規表現で置き換える", replacement, budget());
    assertEquals(List.of(string("-𠮷-a-")), replacement);

    var split = stack(string("𠮷a"), empty);
    execute("正規表現で分割する", split, budget());
    assertEquals("【「」、「𠮷」、「a」、「」】", split.getFirst().displayText());
  }

  @Test
  void chargesExactlyOneMatchCallForEveryRegexOperation() throws Exception {
    RegexValue regex = regex("(?<n>a)");
    long expected = (long) regex.program().instructionCount() * 2;

    assertRegexCharge("正規表現に完全一致する", stack(string("a"), regex), expected);
    assertRegexCharge("正規表現を含む", stack(string("a"), regex), expected);
    assertRegexCharge("正規表現で最初を取り出す", stack(string("a"), regex), expected);
    assertRegexCharge("正規表現の名前付き部分を取り出す", stack(string("a"), regex, string("n")), expected);
    assertRegexCharge("正規表現で置き換える", stack(string("a"), regex, string("$0")), expected);
    assertRegexCharge("正規表現で分割する", stack(string("a"), regex), expected);
  }

  @Test
  void completesNestedRepetitionWithoutDependingOnTheRuntimeTimeout() throws Exception {
    RegexValue regex = regex("(a+)+b");
    var stack = stack(string("a".repeat(100_000)), regex);

    execute("正規表現を含む", stack, budget());

    assertEquals(List.of(new BooleanValue(false)), stack);
  }

  @Test
  void preflightsRegexReplacementUtf8LengthAndConsumesOnlyRegexWorkOnFailure() throws Exception {
    RegexValue regex = regex("a");
    String replacement = "x".repeat(StringLimits.MAX_UTF8_BYTES);
    var accepted = stack(string("a"), regex, string(replacement));
    execute("正規表現で置き換える", accepted, budget());
    assertEquals(replacement, ((StringValue) accepted.getFirst()).value());

    var rejected = stack(string("ab"), regex, string(replacement));
    List<RuntimeValue> original = List.copyOf(rejected);
    var rejectedBudget = budget();
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("正規表現で置き換える", rejected, rejectedBudget));
    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, failure.diagnostic().code());
    assertEquals("16777217", failure.diagnostic().observed().orElseThrow());
    assertTrue(rejectedBudget.regexWorkUnits() > 0);
    assertEquals(0, rejectedBudget.arrayConstructionUnits());
    assertEquals(0, rejectedBudget.arrayElementOperationUnits());
    assertEquals(original, rejected);
  }

  @Test
  void enforcesRegexSplitLengthAndArrayBudgetsAfterChargingOneMatchCall() throws Exception {
    RegexValue comma = regex(",");
    var accepted = stack(string(",".repeat(ArrayLimits.MAX_LENGTH - 1)), comma);
    var acceptedBudget = budget();
    execute("正規表現で分割する", accepted, acceptedBudget);
    assertEquals(ArrayLimits.MAX_LENGTH, ((ArrayValue) accepted.getFirst()).size());
    assertEquals(ArrayLimits.MAX_LENGTH, acceptedBudget.arrayConstructionUnits());
    assertEquals(ArrayLimits.MAX_LENGTH, acceptedBudget.arrayElementOperationUnits());
    assertTrue(acceptedBudget.regexWorkUnits() > 0);

    var rejected = stack(string(",".repeat(ArrayLimits.MAX_LENGTH)), comma);
    List<RuntimeValue> original = List.copyOf(rejected);
    var rejectedBudget = budget();
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("正規表現で分割する", rejected, rejectedBudget));
    assertEquals(DiagnosticCode.E_ARRAY_LENGTH_LIMIT, failure.diagnostic().code());
    assertEquals("65537", failure.diagnostic().observed().orElseThrow());
    assertTrue(rejectedBudget.regexWorkUnits() > 0);
    assertEquals(0, rejectedBudget.arrayConstructionUnits());
    assertEquals(0, rejectedBudget.arrayElementOperationUnits());
    assertEquals(original, rejected);
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
    assertTrue(result.regexWorkUnits() > 0, caseId);
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
    try (var input = TextRegexRegexReplaceSplitRuntimeTest.class.getResourceAsStream(resource)) {
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

  private static void assertRegexCharge(
      String builtinName, ArrayList<RuntimeValue> stack, long expected) throws RuntimeFailure {
    var executionBudget = budget();
    execute(builtinName, stack, executionBudget);
    assertEquals(expected, executionBudget.regexWorkUnits(), builtinName);
  }

  private static ExecutionBudget budget() {
    return new ExecutionBudget(SOURCE_PATH, () -> 0L);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static StringValue string(String value) {
    return new StringValue(value);
  }
}
