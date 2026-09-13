package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 配列反復、21列トレース、安全な配列値プレビューを検証します。 */
class ArrayArrayLoopTraceTest {
  @ParameterizedTest
  @MethodSource("arrayLoopCases")
  void runsEveryNormativeArrayLoopCase(String sourceName, String expectedOutput)
      throws IOException {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(sourceName, output, TraceSink.none());

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertTrue(result.finalDataStack().isEmpty(), sourceName);
    assertEquals(expectedOutput, output.utf8Text(), sourceName);
  }

  @Test
  void chapterOutputAndTraceMatchAndTracingChangesNoExecutionResult() throws IOException {
    byte[] source = resourceBytes("chapter/arrays-chapter.bsb");
    byte[] expectedOutput = resourceBytes("chapter/arrays-chapter.stdout");
    String expectedTrace = resourceText("chapter/arrays-chapter.trace.tsv");
    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult normal =
        new ProgramRunner()
            .run(
                "arrays-chapter.bsb",
                source,
                new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    ProgramRunResult traced =
        new ProgramRunner()
            .run(
                "arrays-chapter.bsb",
                source,
                new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(normal.exitCode(), traced.exitCode());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertEquals(normal.finalGlobalValues(), traced.finalGlobalValues());
    assertEquals(normal.executedInstructions(), traced.executedInstructions());
    assertEquals(normal.arrayConstructionUnits(), traced.arrayConstructionUnits());
    assertEquals(normal.arrayElementOperationUnits(), traced.arrayElementOperationUnits());
    assertArrayEquals(expectedOutput, normalOutput.bytes());
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes());
    assertEquals(expectedTrace, TraceTsvFormatter.formatArray(events));
  }

  @Test
  void arrayN021TraceUsesEightElementPreviewExactly() throws IOException {
    var events = new ArrayList<TraceEvent>();
    var output = new MemoryOutputSink();

    ProgramRunResult result = run("sources/ARRAY-N021.bsb", output, events::add);

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        resourceText("chapter/arrays-truncation.trace.tsv"), TraceTsvFormatter.formatArray(events));
  }

  @Test
  void arrayR006BoundsElementsTextCodePointsAndRedactsTheWholeArray() {
    ArrayValue eight = integers(8);
    ArrayValue nine = integers(9);
    String sixteenCodePoints = "😀".repeat(16);
    String seventeenCodePoints = sixteenCodePoints + "終";
    ArrayValue sixteen = strings(sixteenCodePoints);
    ArrayValue seventeen = strings(seventeenCodePoints);
    ArrayValue controls = strings("行\n列\t終");

    assertEquals("配列<整数>:【0、1、2、3、4、5、6、7】", visible(eight));
    assertEquals("配列<整数>:【0、1、2、3、4、5、6、7、…(+1)】", visible(nine));
    assertEquals("配列<文字列>:【「" + sixteenCodePoints + "」】", visible(sixteen));
    assertEquals("配列<文字列>:【「" + sixteenCodePoints + "…」】", visible(seventeen));
    assertEquals("配列<文字列>:【「行\\n列\\t終」】", visible(controls));
    assertEquals("配列<整数>:<redacted>", TraceValueFormatter.format(nine, ignored -> false));
  }

  @Test
  void ordersMixedLoopStatesAndDiscardsOnlyTheRequestedSuffix() throws IOException {
    String nested =
        "メインとは （--）\n"
            + "    【1】を 各要素について\n"
            + "        一行表示する\n"
            + "        1 回だけ\n"
            + "        繰り返す\n"
            + "    繰り返す\n"
            + "こと。\n";
    var nestedEvents = new ArrayList<TraceEvent>();
    ProgramRunResult nestedResult =
        new ProgramRunner()
            .run(
                "nested-array-loop.bsb",
                nested.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, nestedEvents::add));

    assertTrue(nestedResult.successful(), nestedResult.diagnostics().toString());
    TraceEvent countedStart =
        nestedEvents.stream()
            .filter(event -> event.opcode().equals("CountedLoopStart"))
            .findFirst()
            .orElseThrow();
    assertEquals(List.of(new ArrayLoopTraceState(0, 1)), countedStart.controlBefore());
    assertEquals(
        List.of(
            new ArrayLoopTraceState(0, 1),
            new CountedLoopTraceState(BigInteger.ONE, BigInteger.ONE)),
        countedStart.controlAfter());

    var continueEvents = new ArrayList<TraceEvent>();
    ProgramRunResult continued =
        run("sources/ARRAY-N015.bsb", new MemoryOutputSink(), continueEvents::add);
    assertTrue(continued.successful(), continued.diagnostics().toString());
    TraceEvent continueJump =
        continueEvents.stream()
            .filter(event -> event.opcode().equals("Jump"))
            .findFirst()
            .orElseThrow();
    assertEquals(continueJump.controlBefore(), continueJump.controlAfter());

    var breakEvents = new ArrayList<TraceEvent>();
    ProgramRunResult broken =
        run("sources/ARRAY-N016.bsb", new MemoryOutputSink(), breakEvents::add);
    assertTrue(broken.successful(), broken.diagnostics().toString());
    TraceEvent breakJump =
        breakEvents.stream()
            .filter(event -> event.opcode().equals("Jump"))
            .findFirst()
            .orElseThrow();
    assertFalse(breakJump.controlBefore().isEmpty());
    assertTrue(breakJump.controlAfter().isEmpty());
  }

  @Test
  void iterationChargesOneOperationOnlyForEachElementPassedToTheBody() throws IOException {
    ProgramRunResult nonEmpty =
        run("sources/ARRAY-N013.bsb", new MemoryOutputSink(), TraceSink.none());
    ProgramRunResult empty =
        run("sources/ARRAY-N014.bsb", new MemoryOutputSink(), TraceSink.none());

    assertTrue(nonEmpty.successful(), nonEmpty.diagnostics().toString());
    assertTrue(empty.successful(), empty.diagnostics().toString());
    assertEquals(3, nonEmpty.arrayConstructionUnits());
    assertEquals(6, nonEmpty.arrayElementOperationUnits());
    assertEquals(0, empty.arrayConstructionUnits());
    assertEquals(0, empty.arrayElementOperationUnits());
  }

  @Test
  void returnDiscardsTheArrayStateOwnedByTheReturningFrame() {
    String source =
        "早く戻るとは （--）\n"
            + "    【1】を 各要素について\n"
            + "        一行表示する\n"
            + "        戻る\n"
            + "    繰り返す\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    早く戻る\n"
            + "こと。\n";
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "return-array-loop.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    TraceEvent returned =
        events.stream()
            .filter(event -> event.word().equals("早く戻る") && event.opcode().equals("Return"))
            .findFirst()
            .orElseThrow();
    assertEquals(List.of(new ArrayLoopTraceState(0, 1)), returned.controlBefore());
    assertTrue(returned.controlAfter().isEmpty());
  }

  private static String visible(RuntimeValue value) {
    return TraceValueFormatter.format(value, TraceValuePolicy.arrays());
  }

  private static ArrayValue integers(int count) {
    var elements = new ArrayList<RuntimeValue>(count);
    for (int index = 0; index < count; index++) {
      elements.add(new IntegerValue(BigInteger.valueOf(index)));
    }
    return new ArrayValue(ScalarType.INTEGER, elements);
  }

  private static ArrayValue strings(String value) {
    return new ArrayValue(ScalarType.STRING, List.of(new StringValue(value)));
  }

  private static ProgramRunResult run(String resource, MemoryOutputSink output, TraceSink trace)
      throws IOException {
    String sourcePath = resource.substring(resource.lastIndexOf('/') + 1);
    return new ProgramRunner()
        .run(sourcePath, resourceBytes(resource), new ExecutionContext(output, () -> 0L, trace));
  }

  private static Stream<Arguments> arrayLoopCases() {
    return Stream.of(
        Arguments.of("sources/ARRAY-N013.bsb", "1\n2\n3\n"),
        Arguments.of("sources/ARRAY-N014.bsb", "完了\n"),
        Arguments.of("sources/ARRAY-N015.bsb", "1\n3\n"),
        Arguments.of("sources/ARRAY-N016.bsb", "1\n2\n"),
        Arguments.of("sources/ARRAY-N017.bsb", "一\n二\n"));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String path = "/conformance/arrays/" + relativePath;
    try (var input = ArrayArrayLoopTraceTest.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }

  private static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }
}
