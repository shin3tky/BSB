package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class OptionalOptionalTraceTest {
  @Test
  void formatsRevealableStatesAndTransitivelyRedactsSensitiveTypes() {
    OptionalValue integer = OptionalValue.present(new IntegerValue(BigInteger.valueOf(42)));
    OptionalValue nested = OptionalValue.present(integer);
    OptionalValue stringPresent = OptionalValue.present(new StringValue("secret"));
    OptionalValue stringAbsent = OptionalValue.absent(ValueType.STRING);
    OptionalValue jsonPresent =
        OptionalValue.present(new JsonRuntimeValue(new JsonString("token")));
    OptionalValue jsonAbsent = OptionalValue.absent(ValueType.JSON);

    assertEquals("任意<整数>:ある(42)", TraceValueFormatter.format(integer, TraceValuePolicy.optional()));
    assertEquals(
        "任意<任意<整数>>:ある(ある(42))", TraceValueFormatter.format(nested, TraceValuePolicy.optional()));
    for (OptionalValue sensitive : List.of(stringPresent, stringAbsent, jsonPresent, jsonAbsent)) {
      assertEquals(
          sensitive.type().sourceName() + ":<redacted>",
          TraceValueFormatter.format(sensitive, TraceValuePolicy.optional()));
    }
    assertEquals("任意<整数>:<redacted>", TraceValueFormatter.format(integer, ignored -> false));
  }

  @Test
  void OptionalTsvNeverLeaksOptionalJsonStateOrContents() {
    OptionalValue secret =
        OptionalValue.present(new JsonRuntimeValue(new JsonString("credential-secret")));
    var event =
        new TraceEvent(
            1,
            "メイン",
            "Call:検査",
            1,
            1,
            List.of(secret),
            List.of(OptionalValue.absent(ValueType.JSON)),
            1,
            1,
            new byte[0],
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            "");

    String trace = TraceTsvFormatter.formatOptional(List.of(event));
    assertTrue(trace.contains("任意<JSON>:<redacted>"));
    assertFalse(trace.contains("credential"));
    assertFalse(trace.contains("secret"));
    assertFalse(trace.contains(":ある"));
    assertFalse(trace.contains(":ない"));
  }

  @Test
  void nestedArrayUsesTheExistingEightElementPreview() {
    var elements =
        java.util.stream.IntStream.range(0, 10)
            .mapToObj(index -> new IntegerValue(BigInteger.valueOf(index)))
            .map(RuntimeValue.class::cast)
            .toList();
    OptionalValue value = OptionalValue.present(new ArrayValue(ScalarType.INTEGER, elements));

    assertEquals(
        "任意<配列<整数>>:ある(【0、1、2、3、4、5、6、7、…(+2)】)",
        TraceValueFormatter.format(value, TraceValuePolicy.optional()));
  }

  @Test
  void tracingChangesNeitherStoredOptionalValuesOutputsNorBudgets() {
    byte[] source =
        ("設定は 変数 空のJSONオブジェクト 「x」を JSONオブジェクトから任意値を取り出す。\n\n"
                + "メインとは （--）\n"
                + "    設定 任意に値がある\n"
                + "    ならば\n"
                + "        任意から値を取り出す 一行表示する\n"
                + "    さもなければ\n"
                + "        任意を捨てる 「default」を 一行表示する\n"
                + "    つぎに\n"
                + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);
    Observation normal = run(source, false);
    Observation traced = run(source, true);

    assertEquals(normal.result().termination(), traced.result().termination());
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(normal.result().finalGlobalValues(), traced.result().finalGlobalValues());
    assertEquals(normal.result().executedInstructions(), traced.result().executedInstructions());
    assertEquals(normal.result().jsonWorkUnits(), traced.result().jsonWorkUnits());
    assertEquals(normal.output().utf8Text(), traced.output().utf8Text());
    assertEquals("default\n", traced.output().utf8Text());
    String trace = TraceTsvFormatter.formatOptional(traced.events());
    assertTrue(trace.contains("任意<JSON>:<redacted>"));
    assertFalse(trace.contains(":ない"));
  }

  private static Observation run(byte[] source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "optional-trace.bsb",
                source,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, output, List.copyOf(events));
  }

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
