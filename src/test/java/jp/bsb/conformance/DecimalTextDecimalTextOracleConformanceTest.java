package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DecimalTextDecimalTextOracleConformanceTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("decimalTexts")
  void matchesEveryIndependentGrammarAndValueRow(DecimalTextConformanceData.DecimalTextSpec spec) {
    String word = spec.id().equals("DTXT-F009") ? "文字列を整数に変換する" : "文字列を小数に変換する";
    String source = source(spec.input(), word);
    var output = new MemoryOutputSink();
    var result =
        new ProgramRunner()
            .run(
                spec.id() + '-' + spec.variant() + ".bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    if (spec.outcome().equals("accepted")) {
      assertTrue(result.successful(), spec + ": " + result.diagnostics());
      assertEquals(spec.valueDisplay() + "\n", output.utf8Text(), spec.toString());
    } else {
      assertEquals(10, result.exitCode(), spec.toString());
      assertEquals(spec.code(), result.diagnostics().getFirst().code().name(), spec.toString());
      assertEquals("", output.utf8Text(), spec.toString());
    }
  }

  static java.util.stream.Stream<DecimalTextConformanceData.DecimalTextSpec> decimalTexts()
      throws Exception {
    return DecimalTextConformanceData.loadDecimalTexts().stream();
  }

  private static String source(String input, String word) {
    return "メインとは （--）\n    「" + input + "」 を " + word + " を 一行表示する\nこと。\n";
  }
}
