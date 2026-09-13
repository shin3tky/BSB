package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

class JsonJsonTraceTest {
  private static final byte[] SOURCE =
      ("メインとは （--）\n"
              + "    JSONヌル\n"
              + "    JSONを文字列に変換する 一行表示する\n"
              + "    空のJSON配列 JSONから配列に変換する 配列の長さ 一行表示する\n"
              + "こと。\n")
          .getBytes(StandardCharsets.UTF_8);

  @Test
  void tracesOnlyJsonTypeNamesWithoutChangingAnyResultOrBudget() {
    Observation normal = run(false);
    Observation traced = run(true);
    String tsv = TraceTsvFormatter.formatHostIo(traced.events());

    assertEquals(0, normal.result().exitCode());
    assertEquals(normal.result().termination(), traced.result().termination());
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(normal.result().finalGlobalValues(), traced.result().finalGlobalValues());
    assertEquals(normal.result().executedInstructions(), traced.result().executedInstructions());
    assertEquals(
        normal.result().arrayConstructionUnits(), traced.result().arrayConstructionUnits());
    assertEquals(
        normal.result().arrayElementOperationUnits(), traced.result().arrayElementOperationUnits());
    assertEquals(normal.result().jsonConstructionUnits(), traced.result().jsonConstructionUnits());
    assertEquals(normal.result().jsonWorkUnits(), traced.result().jsonWorkUnits());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes());
    assertTrue(tsv.contains("JSON:<redacted>"));
    assertTrue(tsv.contains("配列<JSON>:<redacted>"));

    var secret = new JsonRuntimeValue(new JsonString("token-secret"));
    var secretArray = new ArrayValue(ScalarType.JSON, List.of(secret));
    var event =
        new TraceEvent(
            1,
            "メイン",
            "Call:検査",
            1,
            1,
            List.of(secret, secretArray),
            List.of(secret, secretArray),
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
    String secretTsv = TraceTsvFormatter.formatHostIo(List.of(event));
    assertFalse(secretTsv.contains("secret"));
    assertFalse(secretTsv.contains("token"));
  }

  private static Observation run(boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "json-trace.bsb",
                SOURCE,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, output, List.copyOf(events));
  }

  private record Observation(
      ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
