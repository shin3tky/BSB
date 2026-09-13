package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ExponentExponentLiteralRuntimeTest {
  @Test
  void runsNormativeValuesArithmeticAndTypedContextsExactly() throws Exception {
    assertRun("sources/EXP-N-values.bsb", "sources/EXP-N-values.stdout");
    assertRun("sources/EXP-N-arithmetic.bsb", "sources/EXP-N-arithmetic.stdout");
    assertRun("sources/EXP-N-contexts.bsb", "sources/EXP-N-contexts.stdout");
    assertRun("sources/EXP-N-compatibility.bsb", "sources/EXP-N-compatibility.stdout");
  }

  @Test
  void rendersThePositiveScaleBoundaryWithoutExponentNotation() {
    String source = "メインとは （--）\n    1e65536 を 一行表示する\nこと。\n";
    MemoryOutputSink output = new MemoryOutputSink();
    ProgramRunResult result = run(source.getBytes(StandardCharsets.UTF_8), output);

    assertTrue(result.successful(), result.diagnostics().toString());
    String text = output.utf8Text();
    assertEquals(65_540, text.length());
    assertTrue(text.startsWith("1"));
    assertTrue(text.endsWith(".0\n"));
    assertTrue(text.chars().noneMatch(character -> character == 'e' || character == 'E'));
  }

  @Test
  void tracesTheCanonicalDecimalValueInsteadOfTheSourceLexeme() {
    String source = "メインとは （--）\n    1e2 を 一行表示する\nこと。\n";
    var events = new ArrayList<TraceEvent>();
    MemoryOutputSink output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "exponent-trace.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    String trace = TraceTsvFormatter.formatNumeric(events);
    assertTrue(trace.contains("小数:100.0"));
    assertFalse(trace.contains("小数:1e2"));
  }

  private static void assertRun(String sourcePath, String outputPath) throws Exception {
    byte[] source = resourceBytes(sourcePath);
    String expected = new String(resourceBytes(outputPath), StandardCharsets.UTF_8);
    MemoryOutputSink output = new MemoryOutputSink();
    ProgramRunResult result = run(source, output);

    assertTrue(result.successful(), sourcePath + ": " + result.diagnostics());
    assertEquals(expected, output.utf8Text(), sourcePath);
  }

  private static ProgramRunResult run(byte[] source, MemoryOutputSink output) {
    return new ProgramRunner()
        .run(
            "Exponent-exponent.bsb",
            source,
            new ExecutionContext(output, () -> 0L, TraceSink.none()));
  }

  private static byte[] resourceBytes(String relativePath) throws Exception {
    String resource = "/conformance/exponent-literals/" + relativePath;
    try (var input = ExponentExponentLiteralRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
