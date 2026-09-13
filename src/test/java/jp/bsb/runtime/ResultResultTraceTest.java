package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ResultResultTraceTest {
  @Test
  void formatsRevealableStatesAndRedactsEitherSensitiveTypeArgument() {
    ResultType visibleType = ValueType.resultOf(ValueType.INTEGER, ValueType.BOOLEAN);
    ResultValue success =
        ResultValue.success(visibleType, new IntegerValue(BigInteger.valueOf(42)));
    ResultValue failure = ResultValue.failure(visibleType, new BooleanValue(false));

    assertEquals(
        "結果<整数,真偽>:成功(42)", TraceValueFormatter.format(success, TraceValuePolicy.result()));
    assertEquals(
        "結果<整数,真偽>:失敗(いいえ)", TraceValueFormatter.format(failure, TraceValuePolicy.result()));

    ResultValue inactiveSecret =
        ResultValue.success(
            ValueType.resultOf(ValueType.INTEGER, ValueType.STRING),
            new IntegerValue(BigInteger.valueOf(42)));
    assertEquals(
        "結果<整数,文字列>:<redacted>",
        TraceValueFormatter.format(inactiveSecret, TraceValuePolicy.result()));
    assertEquals("結果<整数,真偽>:<redacted>", TraceValueFormatter.format(success, ignored -> false));
    assertEquals(
        "結果<整数,真偽>:<redacted>",
        TraceValueFormatter.format(success, value -> !(value instanceof IntegerValue)));
  }

  @Test
  void mixedOptionalResultUsesOneIterativePreviewAndExistingLimits() {
    ResultValue nested =
        ResultValue.success(
            ValueType.resultOf(ValueType.optionalOf(ValueType.INTEGER), ValueType.BOOLEAN),
            OptionalValue.present(new IntegerValue(BigInteger.valueOf(42))));
    assertEquals(
        "結果<任意<整数>,真偽>:成功(ある(42))", TraceValueFormatter.format(nested, TraceValuePolicy.result()));

    String terminal = "x".repeat(33);
    ResultValue longText =
        ResultValue.success(
            ValueType.resultOf(ValueType.STRING, ValueType.BOOLEAN), new StringValue(terminal));
    assertEquals(
        "結果<文字列,真偽>:成功(" + "x".repeat(32) + "…)",
        TraceValueFormatter.format(longText, ignored -> true));

    var elements =
        java.util.stream.IntStream.range(0, 9)
            .mapToObj(index -> new IntegerValue(BigInteger.valueOf(index)))
            .map(RuntimeValue.class::cast)
            .toList();
    ResultValue array =
        ResultValue.success(
            ValueType.resultOf(ValueType.arrayOf(ScalarType.INTEGER), ValueType.BOOLEAN),
            new ArrayValue(ScalarType.INTEGER, elements));
    assertEquals(
        "結果<配列<整数>,真偽>:成功(【0、1、2、3、4、5、6、7、…(+1)】)",
        TraceValueFormatter.format(array, TraceValuePolicy.result()));
  }

  @Test
  void ResultTsvNeverLeaksSensitivePayloadOrState() {
    ResultValue secret =
        ResultValue.failure(
            ValueType.resultOf(ValueType.INTEGER, ValueType.STRING),
            new StringValue("credential-secret"));
    var event =
        new TraceEvent(
            1,
            "メイン",
            "Call:結果から成功値を取り出す",
            1,
            1,
            List.of(secret),
            List.of(secret),
            1,
            1,
            new byte[0],
            List.of(),
            List.of(),
            List.of(),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            "");

    String trace = TraceTsvFormatter.formatResult(List.of(event));
    assertTrue(trace.contains("結果<整数,文字列>:<redacted>"));
    assertFalse(trace.contains("credential"));
    assertFalse(trace.contains("secret"));
    assertFalse(trace.contains(":成功("));
    assertFalse(trace.contains(":失敗("));
  }
}
