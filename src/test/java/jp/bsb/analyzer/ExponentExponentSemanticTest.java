package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExponentExponentSemanticTest {
  @ParameterizedTest
  @MethodSource("acceptedScaleBoundaries")
  void acceptsRawAndNormalizedScaleBoundaries(String lexeme) {
    AnalysisResult result = check(programWith(lexeme + " を 一行表示する"));

    assertTrue(result.successful(), lexeme + ": " + result.diagnostics());
  }

  @ParameterizedTest
  @MethodSource("rejectedScaleBoundaries")
  void reportsStableScaleMetricsBeforeCreatingIr(String lexeme, String metric) {
    AnalysisResult result = check(programWith(lexeme + " を 一行表示する"));

    assertFalse(result.successful(), lexeme);
    assertEquals(8, result.exitCode(), lexeme);
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_DECIMAL_SCALE_LIMIT, diagnostic.code(), lexeme);
    assertEquals(DiagnosticStage.TYPE_AND_STACK, diagnostic.stage(), lexeme);
    assertEquals(2, diagnostic.location().displayPosition().orElseThrow().line(), lexeme);
    assertEquals(5, diagnostic.location().displayPosition().orElseThrow().column(), lexeme);
    assertEquals("小数リテラル", diagnostic.fields().get("word"), lexeme);
    assertEquals(metric, diagnostic.fields().get("metric"), lexeme);
    assertEquals("decimalScale", diagnostic.limitName().orElseThrow(), lexeme);
    assertEquals("65536", diagnostic.limit().orElseThrow(), lexeme);
    assertEquals("65537", diagnostic.observed().orElseThrow(), lexeme);
    assertEquals("スケール絶対値65536以下", diagnostic.expected().orElseThrow(), lexeme);
    assertEquals("65537", diagnostic.actual().orElseThrow(), lexeme);
    assertEquals("指数の絶対値を小さくしてください", diagnostic.fixes().getFirst(), lexeme);
    assertThrows(IllegalStateException.class, result::programForIrGeneration, lexeme);
  }

  @Test
  void validatesAnUnreachableHugeExponentWithFiniteObservedValue() {
    String huge = "1e" + "9".repeat(4095);
    AnalysisResult result = check(programWith("戻る\n    " + huge));

    Diagnostic diagnostic =
        result.diagnostics().stream()
            .filter(item -> item.code() == DiagnosticCode.E_DECIMAL_SCALE_LIMIT)
            .findFirst()
            .orElseThrow();
    assertEquals("rawScale", diagnostic.fields().get("metric"));
    assertEquals("65537", diagnostic.observed().orElseThrow());
  }

  private static Stream<String> acceptedScaleBoundaries() {
    return Stream.of("1e-65536", "1e65536", "10e65535", "0e65536");
  }

  private static Stream<Arguments> rejectedScaleBoundaries() {
    return Stream.of(
        Arguments.of("1e-65537", "rawScale"),
        Arguments.of("1e65537", "rawScale"),
        Arguments.of("10e65536", "normalizedScale"),
        Arguments.of("0e65537", "rawScale"));
  }

  private static AnalysisResult check(String source) {
    return new SourceChecker().check("exponent.bsb", source.getBytes(StandardCharsets.UTF_8));
  }

  private static String programWith(String body) {
    return "メインとは （--）\n    " + body + "\nこと。\n";
  }
}
