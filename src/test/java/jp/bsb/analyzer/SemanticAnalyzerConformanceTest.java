package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SemanticAnalyzerConformanceTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "CORE-N001.bsb", "CORE-N002.bsb", "CORE-N003.bsb", "CORE-N004.bsb", "CORE-N005.bsb",
        "CORE-N006.bsb", "CORE-N008.bsb", "CORE-N009.bsb", "CORE-N010.bsb", "CORE-N011.bsb",
        "CORE-N012.bsb", "CORE-N013.bsb", "CORE-N014.bsb", "CORE-N015.bsb", "CORE-N016.bsb",
        "CORE-N017.bsb", "CORE-N018.bsb", "CORE-N019.bsb", "CORE-F025.bsb"
      })
  void acceptsEveryTextBackedNormalCheckCase(String sourceName) throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkResource(sourceName);

    assertTrue(result.successful(), sourceName + ": " + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName);
    assertFalse(result.programForIrGeneration().userWordSignatures().isEmpty(), sourceName);
  }

  @ParameterizedTest
  @MethodSource("staticFailureCases")
  void matchesSpecifiedStaticFailure(
      String sourceName,
      DiagnosticCode expectedCode,
      DiagnosticStage expectedStage,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix)
      throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkResource(sourceName);

    assertFalse(result.successful(), sourceName);
    assertEquals(8, result.exitCode(), sourceName);
    assertEquals(1, result.diagnostics().size(), sourceName);
    var diagnostic = result.diagnostics().getFirst();
    assertEquals(expectedCode, diagnostic.code(), sourceName);
    assertEquals(Severity.ERROR, diagnostic.severity(), sourceName);
    assertEquals(expectedStage, diagnostic.stage(), sourceName);
    var position = diagnostic.location().displayPosition().orElseThrow();
    assertEquals(line, position.line(), sourceName);
    assertEquals(column, position.column(), sourceName);
    assertEquals(fields, diagnostic.fields(), sourceName);
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(java.util.List.of(fix), diagnostic.fixes(), sourceName);
    if (sourceName.equals("CORE-F016.bsb")) {
      assertEquals("組み込み単語 足す", diagnostic.relatedLocations().getFirst().description());
      assertNull(diagnostic.relatedLocations().getFirst().position());
    }
    assertThrows(IllegalStateException.class, result::programForIrGeneration, sourceName);
  }

  @Test
  void treatsParticleWarningAsSuccess() throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkResource("CORE-F026.bsb");

    assertTrue(result.successful());
    assertEquals(0, result.exitCode());
    assertEquals(1, result.diagnostics().size());
    var warning = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.W_PARTICLE_POSITION, warning.code());
    assertEquals(Severity.WARNING, warning.severity());
    assertEquals(2, warning.location().displayPosition().orElseThrow().line());
    assertEquals(5, warning.location().displayPosition().orElseThrow().column());
    assertEquals("本体先頭以外", warning.expected().orElseThrow());
    assertEquals("を", warning.actual().orElseThrow());
    assertEquals(java.util.List.of("先頭のをを削除してください"), warning.fixes());
    result.programForIrGeneration();
  }

  @Test
  void acceptsLeadingBomThroughThePublicCheckPipeline() throws IOException {
    byte[] source = resourceBytes("CORE-N001.bsb");
    byte[] withBom = new byte[source.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(source, 0, withBom, 3, source.length);

    AnalysisResult result = new SourceChecker().check("CORE-N021.bsb", withBom);

    assertTrue(result.successful());
    assertTrue(result.diagnostics().isEmpty());
  }

  @Test
  void suppressesStaticAnalysisAfterASyntaxFailure() throws IOException {
    AnalysisResult result = AnalyzerTestSupport.checkResource("CORE-F011.bsb");

    assertFalse(result.successful());
    assertEquals(9, result.exitCode());
    assertEquals(java.util.List.of(DiagnosticCode.E_MIXED_STACK_PARENTHESES), codes(result));
    assertEquals(DiagnosticStage.SYNTAX, result.diagnostics().getFirst().stage());
  }

  private static Stream<Arguments> staticFailureCases() {
    return Stream.of(
        failure(
            "CORE-F016.bsb",
            DiagnosticCode.E_RESERVED_NAME,
            DiagnosticStage.NAME,
            1,
            1,
            Map.of(),
            "利用者定義名",
            "足す",
            "別の名前に変更してください"),
        failure(
            "CORE-F017.bsb",
            DiagnosticCode.E_DUPLICATE_NAME,
            DiagnosticStage.NAME,
            4,
            1,
            Map.of("actualName", "請求A1", "previousName", "請求Ａ１"),
            "一意な名前",
            "請求A1",
            "別の名前に変更してください"),
        failure(
            "CORE-F018.bsb",
            DiagnosticCode.E_MISSING_MAIN,
            DiagnosticStage.NAME,
            4,
            1,
            Map.of(),
            "メインとは （--）",
            "EOF",
            "メインを追加してください"),
        failure(
            "CORE-F019.bsb",
            DiagnosticCode.E_INVALID_MAIN_EFFECT,
            DiagnosticStage.NAME,
            1,
            1,
            Map.of(),
            "（--）",
            "（整数 --）",
            "メインの効果を（--）にしてください"),
        failure(
            "CORE-F020.bsb",
            DiagnosticCode.E_UNDEFINED_WORD,
            DiagnosticStage.NAME,
            2,
            5,
            Map.of("word", "表示す"),
            "定義済み単語",
            "表示す",
            "表示するへ変更してください"),
        failure(
            "CORE-F020b.bsb",
            DiagnosticCode.E_NAME_NOT_CALLABLE,
            DiagnosticStage.NAME,
            2,
            5,
            Map.of("word", "整数", "kind", "型名"),
            "呼び出し可能な単語",
            "型名 整数",
            "単語名を指定してください"),
        failure(
            "CORE-F021.bsb",
            DiagnosticCode.E_STACK_UNDERFLOW,
            DiagnosticStage.TYPE_AND_STACK,
            2,
            9,
            Map.of("word", "足す", "requiredCount", "2", "actualCount", "1"),
            "[整数, 整数]",
            "[整数]",
            "整数をもう1つ置いてください"),
        failure(
            "CORE-F022.bsb",
            DiagnosticCode.E_TYPE_MISMATCH,
            DiagnosticStage.TYPE_AND_STACK,
            2,
            17,
            Map.of("word", "足す", "inputIndex", "1", "expectedType", "整数", "actualType", "文字列"),
            "[整数, 整数]",
            "[文字列, 整数]",
            "第1入力を整数にしてください"),
        failure(
            "CORE-F023.bsb",
            DiagnosticCode.E_WORD_EFFECT_MISMATCH,
            DiagnosticStage.TYPE_AND_STACK,
            1,
            1,
            Map.of("word", "捨てる"),
            "[]",
            "[整数]",
            "宣言または本体を一致させてください"),
        failure(
            "CORE-F024.bsb",
            DiagnosticCode.E_MAIN_STACK_NOT_EMPTY,
            DiagnosticStage.TYPE_AND_STACK,
            2,
            5,
            Map.of("actualCount", "1"),
            "[]",
            "[整数]",
            "値を消費してください"),
        failure(
            "CORE-F025b.bsb",
            DiagnosticCode.E_UNKNOWN_TYPE,
            DiagnosticStage.TYPE_AND_STACK,
            1,
            9,
            Map.of("typeName", "金額"),
            "整数・真偽・文字・文字列",
            "金額",
            "言語コアの型へ変更してください"));
  }

  private static Arguments failure(
      String sourceName,
      DiagnosticCode code,
      DiagnosticStage stage,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix) {
    return Arguments.of(sourceName, code, stage, line, column, fields, expected, actual, fix);
  }

  private static java.util.List<DiagnosticCode> codes(AnalysisResult result) {
    return result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
  }

  private static byte[] resourceBytes(String name) throws IOException {
    String resource = "/conformance/language-core/sources/" + name;
    try (var input = SemanticAnalyzerConformanceTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
