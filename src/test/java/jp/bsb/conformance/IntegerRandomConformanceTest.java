package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.MonotonicClock;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.BuiltinOperation;
import jp.bsb.stdlib.BuiltinTypeRule;
import org.junit.jupiter.api.Test;

class IntegerRandomConformanceTest {
  @Test
  void publishesTheFixedIntegerSignatureAndRandomnessEffect() {
    var word = BuiltinDictionary.find("整数乱数を得る").orElseThrow();
    assertEquals(List.of("整数", "整数"), word.inputTypeNames());
    assertEquals(List.of("整数"), word.outputTypeNames());
    assertEquals(BuiltinTypeRule.FIXED, word.typeRule());
    assertEquals(BuiltinOperation.RANDOM_INTEGER, word.operation());
    assertEquals(Set.of("random.bytes"), word.capabilities());
    assertEquals(word.capabilities(), word.sideEffects());
    assertEquals("RNG", word.featureGroup());
    assertTrue(word.returnsNormally());
  }

  @Test
  void normalSourcesAcceptSingletonsNegativeBoundsAndValuesBeyondLong() throws Exception {
    for (var expected :
        List.of(
            new Expected("RNG-N001", "1\n"),
            new Expected("RNG-N002", "-42\n"),
            new Expected("RNG-N003", "-9223372036854775809\n"))) {
      var output = new MemoryOutputSink();
      ProgramRunResult result = run(expected.id(), output);
      assertEquals(0, result.exitCode(), result.diagnostics().toString());
      assertEquals(expected.output(), output.utf8Text());
    }
  }

  @Test
  void failureSourcesReportTheExistingRangeTypeAndStackDiagnostics() throws Exception {
    var ids = List.of("RNG-F001", "RNG-F002", "RNG-F003");
    var codes =
        List.of(
            DiagnosticCode.E_NUMERIC_RANGE_INVALID,
            DiagnosticCode.E_TYPE_MISMATCH,
            DiagnosticCode.E_STACK_UNDERFLOW);
    for (int index = 0; index < ids.size(); index++) {
      var output = new MemoryOutputSink();
      ProgramRunResult result = run(ids.get(index), output);
      assertTrue(result.exitCode() != 0);
      assertEquals(codes.get(index), result.diagnostics().getFirst().code());
      assertEquals("", output.utf8Text());
    }
  }

  private static ProgramRunResult run(String id, MemoryOutputSink output) throws Exception {
    String path = "/conformance/integer-random/sources/" + id + ".bsb";
    byte[] bytes;
    try (var stream = IntegerRandomConformanceTest.class.getResourceAsStream(path)) {
      assertNotNull(stream, path);
      bytes = stream.readAllBytes();
    }
    MonotonicClock clock = () -> 0L;
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .randomSource(candidate -> Arrays.fill(candidate, (byte) 0))
            .build();
    return new ProgramRunner()
        .run(
            id + ".bsb", bytes, new ExecutionContext(output, clock, TraceSink.none(), environment));
  }

  private record Expected(String id, String output) {}
}
