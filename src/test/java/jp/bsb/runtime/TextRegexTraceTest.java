package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** 文字列・正規表現の正規表現・文字列配列表示と21列トレースを規範成果物へ固定します。 */
class TextRegexTraceTest {
  @Test
  void textN022TraceMatchesByteForByteWithoutChangingExecution() throws IOException {
    assertTrace(
        "sources/TEXT-N022.bsb",
        "42\n".getBytes(StandardCharsets.UTF_8),
        "chapter/text-regex-string.trace.tsv");
  }

  @Test
  void chapterTraceMatchesByteForByteWithoutChangingExecution() throws IOException {
    assertTrace(
        "chapter/text-regex-chapter.bsb",
        resourceBytes("chapter/text-regex-chapter.stdout"),
        "chapter/text-regex-chapter.trace.tsv");
  }

  @Test
  void tracesAVerifiedRegexConstantAndItsConcreteCallEffect() throws IOException {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "TEXT-N022.bsb",
                resourceBytes("sources/TEXT-N022.bsb"),
                new ExecutionContext(output, () -> 0L, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertTrue(
        events.stream()
            .filter(event -> event.opcode().equals("PushConst"))
            .flatMap(event -> event.dataAfter().stream())
            .anyMatch(RegexValue.class::isInstance));
    TraceEvent call =
        events.stream()
            .filter(event -> event.opcode().equals("Call:正規表現の名前付き部分を取り出す"))
            .findFirst()
            .orElseThrow();
    assertEquals(3, call.dataBefore().size());
    assertEquals(java.util.List.of(new StringValue("42")), call.dataAfter());
    assertTrue(result.regexWorkUnits() > 0);
  }

  private static void assertTrace(String sourceName, byte[] expectedOutput, String traceName)
      throws IOException {
    byte[] source = resourceBytes(sourceName);
    byte[] expectedTrace = resourceBytes(traceName);
    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();

    ProgramRunResult normal =
        runner.run(
            sourceName, source, new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    ProgramRunResult traced =
        runner.run(sourceName, source, new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(normal.exitCode(), traced.exitCode());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertEquals(normal.finalGlobalValues(), traced.finalGlobalValues());
    assertEquals(normal.executedInstructions(), traced.executedInstructions());
    assertEquals(normal.arrayConstructionUnits(), traced.arrayConstructionUnits());
    assertEquals(normal.arrayElementOperationUnits(), traced.arrayElementOperationUnits());
    assertEquals(normal.regexWorkUnits(), traced.regexWorkUnits());
    assertArrayEquals(expectedOutput, normalOutput.bytes());
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes());
    assertArrayEquals(
        expectedTrace, TraceTsvFormatter.formatTextRegex(events).getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String path = "/conformance/text-regex/" + relativePath;
    try (var input = TextRegexTraceTest.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }
}
