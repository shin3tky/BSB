package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumericDecimalLiteralRuntimeTest {
  @Test
  void runsNumericN001AndNumericN018WithCanonicalValues() throws Exception {
    assertRun("NUM-N001.bsb", "0.0\n1.23\n");
    assertRun("NUM-N018.bsb", "0.0\n1.0\n0.001\n");
  }

  @Test
  void runsRoundingModeValuesThroughDisplayAndEquality() throws Exception {
    assertRun("NUM-N013.bsb", "最近接偶数丸め\nいいえ\n");
  }

  @Test
  void acceptsThe4096DigitBoundaryBeforeConstructingTheDecimalValue() {
    String literal = "1." + "0".repeat(4_095);
    String source = "メインとは （--）\n    " + literal + " を 一行表示する\nこと。\n";
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "decimal-limit.bsb",
                source.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("1.0\n", output.utf8Text());
  }

  private static void assertRun(String sourceName, String expected) throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                sourceName,
                numericsBytes("sources/" + sourceName),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertEquals(expected, output.utf8Text(), sourceName);
  }

  private static byte[] numericsBytes(String relativePath) throws Exception {
    String resource = "/conformance/numerics/" + relativePath;
    try (var input = NumericDecimalLiteralRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
