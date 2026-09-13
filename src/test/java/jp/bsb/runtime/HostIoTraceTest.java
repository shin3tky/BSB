package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.BuiltinEnvironmentTsvFormatter;
import org.junit.jupiter.api.Test;

class HostIoTraceTest {
  private static final HexFormat HEX = HexFormat.of().withUpperCase();
  private static final byte[] SOURCE =
      ("メインとは （--）\n"
              + "    「O」を 一行表示する\n"
              + "    「E」を エラー一行表示する\n"
              + "    一行を入力する\n"
              + "    入力結果を捨てる\n"
              + "    9 を 終了する\n"
              + "こと。\n")
          .getBytes(StandardCharsets.UTF_8);

  @Test
  void formats22ColumnsWithRedactedInputAndDistinctCapabilityEffects() {
    Observation traced = run(true);

    assertEquals(9, traced.result().exitCode());
    String tsv = TraceTsvFormatter.formatHostIo(traced.events());
    String[] rows = tsv.split("\n");
    assertEquals(22, rows[0].split("\t", -1).length);
    for (String row : rows) {
      assertEquals(22, row.split("\t", -1).length, row);
    }
    assertTrue(tsv.contains("console.output:4F0A"));
    assertTrue(tsv.contains("console.error:450A"));
    assertTrue(tsv.contains("console.input:line"));
    assertTrue(tsv.contains("process.exit:9"));
    assertTrue(tsv.contains("入力結果:<redacted>"));
    assertFalse(tsv.contains("秘密"));

    TraceEvent error = event(traced.events(), "Call:エラー一行表示する");
    assertEquals("", HEX.formatHex(error.output()));
    TraceEvent exit = event(traced.events(), "Call:終了する");
    assertEquals(0, exit.callDepthAfter());
  }

  @Test
  void tracingDoesNotAddCapabilityCallsOrChangeAnyResultOrBudget() {
    Observation normal = run(false);
    Observation traced = run(true);

    assertEquals(normal.calls(), traced.calls());
    assertEquals(normal.result().termination(), traced.result().termination());
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(normal.result().finalGlobalValues(), traced.result().finalGlobalValues());
    assertEquals(normal.result().executedInstructions(), traced.result().executedInstructions());
    assertEquals(normal.result().outputBytes(), traced.result().outputBytes());
    assertEquals(normal.result().errorOutputBytes(), traced.result().errorOutputBytes());
    assertArrayEquals(normal.stdout().bytes(), traced.stdout().bytes());
    assertArrayEquals(normal.stderr().bytes(), traced.stderr().bytes());
  }

  @Test
  void environmentDictionaryTsvIsGeneratedInNormativeOrder() throws Exception {
    String actual =
        BuiltinEnvironmentTsvFormatter.format(
            BuiltinDictionary.words().stream()
                .filter(
                    word ->
                        Set.of("CORE", "FLOW", "BIND", "ARRAY", "NUM", "TEXT", "IO")
                            .contains(word.featureGroup()))
                .toList());
    String complete = resourceText("chapter/host-io-builtins.tsv");
    String expectedImplemented =
        complete.lines().limit(21).collect(java.util.stream.Collectors.joining("\n")) + "\n";

    assertEquals(expectedImplemented, actual);
  }

  @Test
  void completeChapterOutputAndTraceMatchByteForByte() throws Exception {
    byte[] source = resourceText("chapter/host-io-chapter.bsb").getBytes(StandardCharsets.UTF_8);
    var stdout = new MemoryOutputSink();
    var stderr = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    long epoch =
        java.time.LocalDateTime.of(2026, 8, 29, 14, 5, 6, 123_000_000)
            .toInstant(java.time.ZoneOffset.ofHours(9))
            .toEpochMilli();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleInput(
                new MemoryConsoleInput(
                    List.of(
                        MemoryConsoleInput.rawLine("山田 太郎".getBytes(StandardCharsets.UTF_8), 1))))
            .consoleOutput(stdout::write)
            .consoleError(stderr::write)
            .programIdentity(
                ProgramIdentity.fixed(
                    ProgramMetadata.located(
                        "host-io-chapter.bsb", "file:///work/host-io-chapter.bsb")))
            .wallTime(() -> new WallTimeReading(epoch, 540))
            .programControl(ProgramControl.accepting())
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "host-io-chapter.bsb",
                source,
                new ExecutionContext(stdout, () -> 0L, events::add, environment));

    assertEquals(0, result.exitCode());
    assertEquals(ExecutionTermination.Kind.PROGRAM_EXIT, result.termination().kind());
    assertEquals(resourceText("chapter/host-io-chapter.stdout"), stdout.utf8Text());
    assertEquals(resourceText("chapter/host-io-chapter.stderr"), stderr.utf8Text());
    assertEquals(
        resourceText("chapter/host-io-chapter.trace.tsv"), TraceTsvFormatter.formatHostIo(events));
  }

  @Test
  void endAndCancelExposeOnlyTheirState() {
    var end = inputEvent(InputResultValue.end());
    var cancel = inputEvent(InputResultValue.cancel());
    String formatted = TraceTsvFormatter.formatHostIo(List.of(end, cancel));

    assertTrue(formatted.contains("入力結果:<入力終端>"));
    assertTrue(formatted.contains("入力結果:<入力取消>"));
  }

  private static TraceEvent inputEvent(InputResultValue value) {
    return new TraceEvent(
        1,
        "メイン",
        "Call:一行を入力する",
        1,
        1,
        List.of(),
        List.of(value),
        1,
        1,
        new byte[0],
        List.of(),
        List.of(),
        List.of(),
        java.util.Optional.empty(),
        java.util.Optional.empty(),
        java.util.Optional.empty(),
        value.state() == InputResultValue.State.END ? "console.input:end" : "console.input:cancel");
  }

  private static TraceEvent event(List<TraceEvent> events, String opcode) {
    return events.stream().filter(event -> event.opcode().equals(opcode)).findFirst().orElseThrow();
  }

  private static Observation run(boolean tracing) {
    var stdout = new MemoryOutputSink();
    var stderr = new MemoryOutputSink();
    var calls = new ArrayList<String>();
    var events = new ArrayList<TraceEvent>();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleInput(
                () -> {
                  calls.add("input");
                  return MemoryConsoleInput.rawLine("秘密".getBytes(StandardCharsets.UTF_8), 1);
                })
            .consoleOutput(
                bytes -> {
                  calls.add("stdout:" + HEX.formatHex(bytes));
                  stdout.write(bytes);
                })
            .consoleError(
                bytes -> {
                  calls.add("stderr:" + HEX.formatHex(bytes));
                  stderr.write(bytes);
                })
            .programControl(
                code -> {
                  calls.add("exit:" + code);
                })
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "trace.bsb",
                SOURCE,
                new ExecutionContext(
                    stdout, () -> 0L, tracing ? events::add : TraceSink.none(), environment));
    return new Observation(result, stdout, stderr, List.copyOf(calls), List.copyOf(events));
  }

  private static String resourceText(String relative) throws Exception {
    try (var input =
        HostIoTraceTest.class.getResourceAsStream("/conformance/host-io/" + relative)) {
      if (input == null) {
        throw new IllegalArgumentException("missing resource: " + relative);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private record Observation(
      ProgramRunResult result,
      MemoryOutputSink stdout,
      MemoryOutputSink stderr,
      List<String> calls,
      List<TraceEvent> events) {}
}
