package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonNull;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonString;
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

class DecimalRandomConformanceTest {
  @Test
  void publishesTheFixedDecimalSignatureAndSharesTheRandomnessEffect() {
    var word = BuiltinDictionary.find("小数乱数を得る").orElseThrow();
    assertEquals(List.of("小数", "小数", "整数"), word.inputTypeNames());
    assertEquals(List.of("小数"), word.outputTypeNames());
    assertEquals(BuiltinTypeRule.FIXED, word.typeRule());
    assertEquals(BuiltinOperation.RANDOM_DECIMAL, word.operation());
    assertEquals(Set.of("random.bytes"), word.capabilities());
    assertEquals(word.capabilities(), word.sideEffects());
    assertEquals("RNG", word.featureGroup());
    assertTrue(word.returnsNormally());
  }

  @Test
  void runsEveryDeclaredNormalSourceIncludingDecimalArraysAndMaximumGridDigits() throws Exception {
    for (String[] row : rows("normal.tsv")) {
      var output = new MemoryOutputSink();
      var calls = new AtomicInteger();
      var result = run(row[1], output, calls);
      assertEquals(0, result.exitCode(), row[0] + result.diagnostics());
      assertEquals(((JsonString) JsonCodec.parse(row[2])).value(), output.utf8Text(), row[0]);
      assertEquals(Integer.parseInt(row[3]), calls.get(), row[0]);
    }
  }

  @Test
  void everyDeclaredFailureMatchesItsExitCodeStructuredFieldsAndJapaneseMessage() throws Exception {
    var messages = DiagnosticMessageCatalog.loadDefault();
    for (String[] row : rows("diagnostics.tsv")) {
      var output = new MemoryOutputSink();
      var calls = new AtomicInteger();
      var result = run(row[1], output, calls);
      assertEquals(Integer.parseInt(row[2]), result.exitCode(), row[0]);
      var diagnostic = result.diagnostics().getFirst();
      assertEquals(row[3], diagnostic.code().name(), row[0]);
      for (var member : ((JsonObject) JsonCodec.parse(row[4])).members()) {
        assertEquals(
            ((JsonString) member.value()).value(), diagnostic.fields().get(member.key()), row[0]);
      }
      var message = JsonCodec.parse(row[5]);
      if (!(message instanceof JsonNull))
        assertEquals(((JsonString) message).value(), messages.format(diagnostic), row[0]);
      assertEquals("", output.utf8Text(), row[0]);
      assertEquals(0, calls.get(), row[0]);
    }
  }

  private static List<String[]> rows(String name) throws Exception {
    return new String(resource(name), StandardCharsets.UTF_8)
        .lines()
        .skip(1)
        .map(line -> line.split("\t", -1))
        .toList();
  }

  private static byte[] resource(String path) throws Exception {
    try (var stream =
        DecimalRandomConformanceTest.class.getResourceAsStream(
            "/conformance/decimal-random/" + path)) {
      assertNotNull(stream, path);
      return stream.readAllBytes();
    }
  }

  private static ProgramRunResult run(String source, MemoryOutputSink output, AtomicInteger calls)
      throws Exception {
    MonotonicClock clock = () -> 0L;
    var environment =
        ExecutionEnvironment.builder(clock)
            .consoleOutput(output::write)
            .randomSource(
                bytes -> {
                  calls.incrementAndGet();
                  Arrays.fill(bytes, (byte) 0);
                })
            .build();
    return new ProgramRunner()
        .run(
            source,
            resource("sources/" + source),
            new ExecutionContext(output, clock, TraceSink.none(), environment));
  }
}
