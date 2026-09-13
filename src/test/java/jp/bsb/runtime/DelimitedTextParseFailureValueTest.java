package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

/** 区切りテキスト解析失敗値の閉じた契約と取出し語を検証します。 */
class DelimitedTextParseFailureValueTest {
  private static final String SOURCE_PATH = "table.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void holdsOnlyFourPublicFieldsAndAlwaysRedactsTraceContent() {
    var value = new DelimitedTextParseFailureValue("unexpectedQuote", 7, 2, 3);

    assertEquals(ValueType.DELIMITED_TEXT_PARSE_FAILURE, value.type());
    assertEquals("<redacted>", value.displayText());
    assertEquals(
        "区切りテキスト解析失敗:<redacted>",
        TraceValueFormatter.format(value, TraceValuePolicy.byteSequence()));
    assertFalse(TraceValuePolicy.byteSequence().mayReveal(value));
    var resultType =
        ValueType.resultOf(
            ValueType.arrayOf(ValueType.arrayOf(ValueType.STRING)),
            ValueType.DELIMITED_TEXT_PARSE_FAILURE);
    var wrapped = ResultValue.failure(resultType, value);
    assertEquals(
        "結果<配列<配列<文字列>>,区切りテキスト解析失敗>:<redacted>",
        TraceValueFormatter.format(wrapped, TraceValuePolicy.byteSequence()));
    assertFalse(TraceValuePolicy.byteSequence().mayReveal(wrapped));
    assertEquals(
        List.of("kind", "utf8Offset", "line", "column"),
        java.util.Arrays.stream(DelimitedTextParseFailureValue.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList());
  }

  @Test
  void acceptsExactlyTheClosedKindSetAndValidPositions() {
    assertEquals(5, DelimitedTextParseFailureValue.KINDS.size());
    for (String kind : DelimitedTextParseFailureValue.KINDS) {
      assertEquals(kind, new DelimitedTextParseFailureValue(kind, 0, 1, 1).kind());
    }

    assertThrows(
        IllegalArgumentException.class, () -> new DelimitedTextParseFailureValue("other", 0, 1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DelimitedTextParseFailureValue("unexpectedQuote", -1, 1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DelimitedTextParseFailureValue("unexpectedQuote", 0, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DelimitedTextParseFailureValue("unexpectedQuote", 0, 1, 0));
  }

  @Test
  void accessorsReplaceTheFailureWithOnePublicScalar() throws Exception {
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var failure = new DelimitedTextParseFailureValue("columnCountMismatch", 11, 4, 5);

    assertAccessor("区切りテキスト解析失敗の種類を取り出す", failure, new StringValue("columnCountMismatch"), budget);
    assertAccessor(
        "区切りテキスト解析失敗のバイト位置を取り出す", failure, new IntegerValue(BigInteger.valueOf(11)), budget);
    assertAccessor("区切りテキスト解析失敗の行を取り出す", failure, new IntegerValue(BigInteger.valueOf(4)), budget);
    assertAccessor("区切りテキスト解析失敗の列を取り出す", failure, new IntegerValue(BigInteger.valueOf(5)), budget);
  }

  private static void assertAccessor(
      String name,
      DelimitedTextParseFailureValue failure,
      RuntimeValue expected,
      ExecutionBudget budget)
      throws Exception {
    var stack = new ArrayList<RuntimeValue>(List.of(failure));
    new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()), budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
    assertEquals(List.of(expected), stack);
  }
}
