package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProgramRunnerConformanceTest {
  @ParameterizedTest
  @MethodSource("normalRunCases")
  void matchesNormalRunOutput(String sourceName, String expected) throws IOException {
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(sourceName, resourceBytes("sources/" + sourceName), deterministic(output));

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertEquals(0, result.exitCode(), sourceName);
    assertEquals(java.util.List.of(), result.finalDataStack(), sourceName);
    assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), output.bytes(), sourceName);
  }

  @Test
  void chapterTraceMatchesTheNormativeTsvExactly() throws IOException {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var context = new ExecutionContext(output, () -> 0L, events::add);

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "language-core-chapter.bsb",
                resourceBytes("chapter/language-core-chapter.bsb"),
                context);

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("42\n", output.utf8Text());
    assertEquals(
        resourceText("chapter/language-core-chapter.trace.tsv"), TraceTsvFormatter.format(events));
  }

  @Test
  void tracingDoesNotChangeOutputOrFinalState() throws IOException {
    byte[] source = resourceBytes("sources/CORE-N001.bsb");
    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult normal =
        new ProgramRunner().run("CORE-N001.bsb", source, deterministic(normalOutput));
    ProgramRunResult traced =
        new ProgramRunner()
            .run(
                "CORE-N001.bsb", source, new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertEquals(normal.exitCode(), traced.exitCode());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes());
    assertEquals(8, events.size());
  }

  private static ExecutionContext deterministic(OutputSink output) {
    return new ExecutionContext(output, () -> 0L, TraceSink.none());
  }

  private static Stream<Arguments> normalRunCases() {
    return Stream.of(
        Arguments.of("CORE-N001.bsb", "42\n"),
        Arguments.of("CORE-N002.bsb", "前方参照\n"),
        Arguments.of("CORE-N005.bsb", "42\n"),
        Arguments.of("CORE-N006.bsb", "42\n"),
        Arguments.of("CORE-N008.bsb", "全角空白\n"),
        Arguments.of("CORE-N009.bsb", "42\n"),
        Arguments.of("CORE-N010.bsb", "コメント\n"),
        Arguments.of("CORE-N011.bsb", "#はコメントではなく、。も終端ではありません\n"),
        Arguments.of("CORE-N012.bsb", "ASCII引用符\n"),
        Arguments.of("CORE-N013.bsb", "一行目\n二行目\t\\\"'「内」\n"),
        Arguments.of("CORE-N014.bsb", "か\u3099\n"),
        Arguments.of("CORE-N015.bsb", "𠮷\n"),
        Arguments.of("CORE-N016.bsb", "-42\n"),
        Arguments.of("CORE-N017.bsb", "いいえ\n"),
        Arguments.of("CORE-N018.bsb", "はい\n"),
        Arguments.of("CORE-N019.bsb", "42\n"));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = "/conformance/language-core/" + relativePath;
    try (var input = ProgramRunnerConformanceTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }
}
