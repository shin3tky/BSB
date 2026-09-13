package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostIoProcessEnvironmentRuntimeTest {
  private static final byte[] ARGUMENT_LENGTH_SOURCE =
      ("メインとは （--）\n" + "    起動引数を得る\n" + "    配列の長さ\n" + "    一行表示する\n" + "こと。\n")
          .getBytes(StandardCharsets.UTF_8);

  @Test
  void acceptsTheExactArgumentCountBoundaryWithinTheDistributionHeap() {
    Run run = run(ARGUMENT_LENGTH_SOURCE, Collections.nCopies(65_536, ""), false);

    assertEquals(0, run.result().exitCode());
    assertEquals("65536\n", run.output().utf8Text());
    assertEquals(65_536, run.result().arrayConstructionUnits());
  }

  @Test
  void acceptsTheExactArgumentTotalBoundaryWithinTheDistributionHeap() {
    String maximum = "a".repeat(16_777_216);
    Run run = run(ARGUMENT_LENGTH_SOURCE, List.of(maximum, maximum, maximum, maximum, ""), false);

    assertEquals(0, run.result().exitCode());
    assertEquals("5\n", run.output().utf8Text());
    assertEquals(5, run.result().arrayConstructionUnits());
  }

  @Test
  void eachCallReturnsAnEquivalentButDistinctImmutableArrayAndOnlyCountsItsEffect() {
    byte[] source =
        ("メインとは （--）\n"
                + "    起動引数を得る\n"
                + "    配列の長さ\n"
                + "    起動引数を得る\n"
                + "    配列の長さ\n"
                + "    足す\n"
                + "    一行表示する\n"
                + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);
    Run run = run(source, List.of("秘密", ""), true);

    assertEquals(0, run.result().exitCode());
    List<ArrayValue> arrays =
        run.events().stream()
            .filter(event -> event.opcode().equals("Call:起動引数を得る"))
            .map(event -> (ArrayValue) event.dataAfter().getLast())
            .toList();
    ArrayValue first = arrays.get(0);
    ArrayValue second = arrays.get(1);
    assertEquals(first, second);
    assertNotSame(first, second);
    List<String> effects =
        run.events().stream()
            .filter(event -> event.opcode().equals("Call:起動引数を得る"))
            .map(TraceEvent::effect)
            .toList();
    assertEquals(List.of("process.arguments:2", "process.arguments:2"), effects);
  }

  @Test
  void distinguishesAnAbsentLocationFromInvalidLocationMetadata() {
    byte[] source =
        ("メインとは （--）\n" + "    プログラムの場所を得る を 一行表示する\n" + "こと。\n").getBytes(StandardCharsets.UTF_8);
    var output = new MemoryOutputSink();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .programIdentity(ProgramIdentity.fixed(ProgramMetadata.unlocated("embedded.bsb")))
            .build();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "embedded.bsb",
                source,
                new ExecutionContext(output, () -> 0L, TraceSink.none(), environment));

    assertEquals(10, result.exitCode());
    assertEquals("E_CAPABILITY_UNAVAILABLE", result.diagnostics().getFirst().code().name());
  }

  private static Run run(byte[] source, List<String> arguments, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleOutput(output::write)
            .processArguments(ProcessArguments.fixed(arguments))
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "arguments.bsb",
                source,
                new ExecutionContext(
                    output, () -> 0L, tracing ? events::add : TraceSink.none(), environment));
    return new Run(result, output, List.copyOf(events));
  }

  private record Run(ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
