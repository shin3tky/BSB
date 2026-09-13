package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.format.SourceFormatter;
import jp.bsb.frontend.Lexer;
import jp.bsb.frontend.SourceText;
import jp.bsb.frontend.TokenKind;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExponentLiteralOracleConformanceTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("literalRows")
  void comparesEveryIndependentLiteralRowWithProductionPipelines(
      ExponentConformanceData.LiteralSpec spec) {
    var diagnostics = new DiagnosticCollector();
    var lexed = new Lexer().lex(new SourceText(spec.id() + ".bsb", spec.input(), 0), diagnostics);

    if (!spec.tokenKind().equals("-")) {
      assertTrue(lexed.successful(), spec.input() + ": " + diagnostics.diagnostics());
      TokenKind expectedKind =
          spec.tokenKind().equals("IDENTIFIER")
              ? TokenKind.IDENTIFIER
              : TokenKind.valueOf(spec.tokenKind() + "_LITERAL");
      assertEquals(expectedKind, lexed.tokens().getFirst().kind());
      assertEquals(spec.input().split(" ", 2)[0], lexed.tokens().getFirst().lexeme());
    } else {
      var diagnostic = diagnostics.diagnostics().getFirst();
      assertEquals(DiagnosticCode.valueOf(spec.code()), diagnostic.code(), spec.input());
      if (!spec.reason().equals("-")) {
        assertEquals(spec.reason(), diagnostic.fields().get("reason"), spec.input());
      }
    }

    if (spec.outcome().equals("accepted")) {
      assertEquals("DECIMAL", spec.tokenKind());
      assertEquals("小数", spec.type());
      String source = program(spec.input());
      var formatted =
          new SourceFormatter().format(spec.id() + ".bsb", source.getBytes(StandardCharsets.UTF_8));
      assertTrue(formatted.successful(), formatted.diagnostics().toString());
      assertTrue(formatted.outputForStandardOutput().contains(spec.format()), spec.id());
      if (!spec.input().contains(" ")) {
        var output = new MemoryOutputSink();
        var result =
            new ProgramRunner()
                .run(
                    spec.id() + ".bsb",
                    source.getBytes(StandardCharsets.UTF_8),
                    new ExecutionContext(output, () -> 0L, TraceSink.none()));
        assertTrue(result.successful(), result.diagnostics().toString());
        assertEquals(spec.valueDisplay() + "\n", output.utf8Text(), spec.id());
      }
    }
  }

  static Stream<ExponentConformanceData.LiteralSpec> literalRows() throws Exception {
    return ExponentConformanceData.loadLiterals().stream();
  }

  private static String program(String expression) {
    return "メインとは （--）\n    " + expression + " を 一行表示する\nこと。\n";
  }
}
