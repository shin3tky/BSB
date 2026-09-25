package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

class StringConvenienceRuntimeTest {
  private static final String SOURCE_PATH = "string-convenience.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsAllNineConvenienceWordsWithGraphemeBoundarySemantics() {
    String source =
        "メインとは （--）\n"
            + "    「」を 文字列が空である 一行表示する\n"
            + "    「　\\t」を 文字列が空白だけである 一行表示する\n"
            + "    「が」と「か」を 文字列を含む 一行表示する\n"
            + "    「が山」と「が」を 指定文字列で始まる 一行表示する\n"
            + "    「山が」と「が」を 指定文字列で終わる 一行表示する\n"
            + "    「赤」と 3 を 文字列を繰り返す 一行表示する\n"
            + "    【「赤」、「青」、「」】と「,」を 文字列配列を区切ってつなぐ 一行表示する\n"
            + "    「赤青赤」と「赤」と 1 を 開始位置から文字列を探す 一行表示する\n"
            + "    「赤青赤」と「赤」を 後ろから文字列を探す 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(
        result.successful(),
        result.diagnostics().stream()
            .map(diagnostic -> diagnostic.code() + ":" + diagnostic.fields())
            .toList()
            .toString());
    assertEquals("はい\nはい\nいいえ\nはい\nはい\n赤赤赤\n赤,青,\n2\n2\n", output.utf8Text());
  }

  @Test
  void definesEmptyNeedleAndEmptyCollectionResults() throws Exception {
    assertEquals(2, integerResult("開始位置から文字列を探す", string("赤青"), string(""), integer(2)));
    assertEquals(2, integerResult("後ろから文字列を探す", string("赤青"), string("")));

    var emptyJoin = stack(new ArrayValue(ScalarType.STRING, List.of()), string("--"));
    execute("文字列配列を区切ってつなぐ", emptyJoin, budget());
    assertEquals("", ((StringValue) emptyJoin.getFirst()).value());

    var hugeEmptyRepeat = stack(string(""), integer(BigInteger.TEN.pow(10_000)));
    execute("文字列を繰り返す", hugeEmptyRepeat, budget());
    assertEquals("", ((StringValue) hugeEmptyRepeat.getFirst()).value());
  }

  @Test
  void rejectsInvalidSearchStartWithoutConsumingInputs() {
    var inputs = stack(string("赤青"), string("赤"), integer(3));
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("開始位置から文字列を探す", inputs, budget()));

    assertEquals(DiagnosticCode.E_STRING_SEARCH_START_OUT_OF_BOUNDS, failure.diagnostic().code());
    assertEquals("[0,2]", failure.diagnostic().fields().get("validRange"));
    assertEquals(List.of(string("赤青"), string("赤"), integer(3)), inputs);
  }

  @Test
  void preflightsRepeatAndJoinLimitsAtomically() throws Exception {
    String half = "a".repeat(StringLimits.MAX_UTF8_BYTES / 2);
    var acceptedRepeat = stack(string(half), integer(2));
    execute("文字列を繰り返す", acceptedRepeat, budget());
    assertEquals(
        StringLimits.MAX_UTF8_BYTES, ((StringValue) acceptedRepeat.getFirst()).value().length());

    var rejectedRepeat = stack(string(half + "a"), integer(2));
    assertLimitFailure("文字列を繰り返す", rejectedRepeat);

    ArrayValue parts = new ArrayValue(ScalarType.STRING, List.of(string(half), string(half)));
    var acceptedJoin = stack(parts, string(""));
    execute("文字列配列を区切ってつなぐ", acceptedJoin, budget());
    assertEquals(
        StringLimits.MAX_UTF8_BYTES, ((StringValue) acceptedJoin.getFirst()).value().length());

    var rejectedJoin = stack(parts, string("x"));
    assertLimitFailure("文字列配列を区切ってつなぐ", rejectedJoin);
  }

  @Test
  void rejectsNegativeRepeatCountWithoutChangingTheStack() {
    var inputs = stack(string("赤"), integer(-1));
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("文字列を繰り返す", inputs, budget()));

    assertEquals(DiagnosticCode.E_NEGATIVE_REPEAT_COUNT, failure.diagnostic().code());
    assertEquals(List.of(string("赤"), integer(-1)), inputs);
  }

  @Test
  void recognizesUnicodeWhitespaceButNotZeroWidthSpace() throws Exception {
    var blank = stack(string("\u3000\t\n"));
    execute("文字列が空白だけである", blank, budget());
    assertTrue(((BooleanValue) blank.getFirst()).value());

    var zeroWidth = stack(string("\u200B"));
    execute("文字列が空白だけである", zeroWidth, budget());
    assertFalse(((BooleanValue) zeroWidth.getFirst()).value());
  }

  @Test
  void chargesJoinElementWorkAndRejectsTheWholeCallAtomically() throws Exception {
    ArrayValue parts =
        new ArrayValue(ScalarType.STRING, List.of(string("赤"), string("青"), string("緑")));
    var acceptedBudget = budget();
    execute("文字列配列を区切ってつなぐ", stack(parts, string(",")), acceptedBudget);
    assertEquals(3, acceptedBudget.arrayElementOperationUnits());

    var rejectedBudget =
        new ExecutionBudget(
            SOURCE_PATH, () -> 0L, 0, 0, 0, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 2);
    var inputs = stack(parts, string(","));
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("文字列配列を区切ってつなぐ", inputs, rejectedBudget));
    assertEquals(DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT, failure.diagnostic().code());
    assertEquals(List.of(parts, string(",")), inputs);
    assertEquals(
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 2, rejectedBudget.arrayElementOperationUnits());
  }

  private static int integerResult(String word, RuntimeValue... inputs) throws Exception {
    var stack = stack(inputs);
    execute(word, stack, budget());
    return ((IntegerValue) stack.getFirst()).value().intValueExact();
  }

  private static void assertLimitFailure(String word, ArrayList<RuntimeValue> inputs) {
    List<RuntimeValue> original = List.copyOf(inputs);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute(word, inputs, budget()));
    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, failure.diagnostic().code());
    assertEquals("16777217", failure.diagnostic().observed().orElseThrow());
    assertEquals(original, inputs);
  }

  private static void execute(
      String builtinName, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()), budget)
        .execute(BuiltinDictionary.find(builtinName).orElseThrow(), stack, SPAN);
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

  private static IntegerValue integer(long value) {
    return integer(BigInteger.valueOf(value));
  }

  private static IntegerValue integer(BigInteger value) {
    return new IntegerValue(value);
  }
}
