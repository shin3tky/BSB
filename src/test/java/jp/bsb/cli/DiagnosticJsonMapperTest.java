package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.FileOffset;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.frontend.SourceText;
import org.junit.jupiter.api.Test;

class DiagnosticJsonMapperTest {
  private final DiagnosticJsonMapper mapper;

  DiagnosticJsonMapperTest() throws IOException {
    mapper = new DiagnosticJsonMapper(DiagnosticMessageCatalog.loadDefault());
  }

  @Test
  void mapsNonEmptyAndZeroWidthSpansFromTheAttachedSourceSnapshot() {
    String text = "A\tB\r\nか\u3099𠮷\n👩‍💻";
    SourceText source = new SourceText("位置.bsb", text, 0);
    Diagnostic range =
        Diagnostic.builder(
                DiagnosticCode.E_UNDEFINED_WORD,
                Severity.ERROR,
                DiagnosticStage.NAME,
                source.sourcePath(),
                source.span(1, 9))
            .field("word", "B")
            .field("𠮷", "supplementary")
            .field("\uE000", "private")
            .expected("定義済み単語")
            .actual("B")
            .relatedLocation(
                new RelatedLocation(source.sourcePath(), source.positionAt(10), "絵文字クラスタ"))
            .relatedLocation(RelatedLocation.outsideSource("組み込み辞書"))
            .fix("名前を変更してください")
            .build();
    Diagnostic point =
        Diagnostic.builder(
                DiagnosticCode.E_MISSING_MAIN,
                Severity.ERROR,
                DiagnosticStage.NAME,
                source.sourcePath(),
                source.span(15, 15))
            .expected("メインとは （--）")
            .actual("EOF")
            .fix("メインを追加してください")
            .build();
    AnalysisResult result =
        new AnalysisResult(Optional.empty(), List.of(point, range), Optional.of(source));

    List<DiagnosticJson> mapped = mapper.map(result);

    assertEquals(
        List.of("word", "\uE000", "𠮷"), mapped.getFirst().fields().keySet().stream().toList());
    JsonSpan span = assertInstanceOf(JsonSpan.class, mapped.getFirst().location());
    assertEquals(new JsonLineColumn(1, 2), span.start());
    assertEquals(new JsonLineColumn(2, 2), span.endInclusive());
    assertEquals(1, span.utf8Start());
    assertEquals(15, span.utf8EndExclusive());
    JsonPoint eof = assertInstanceOf(JsonPoint.class, mapped.get(1).location());
    assertEquals(new JsonPoint(3, 2, 27), eof);
    assertEquals("error", mapped.getFirst().severity());
    assertEquals("name", mapped.getFirst().stage());
    assertEquals(2, mapped.getFirst().relatedLocations().size());
  }

  @Test
  void mapsDecodedBomOffsetsAndAllStableStages() {
    byte[] bytes = "\uFEFFメインとは （--）\nこと。\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    AnalysisResult result = new SourceChecker().check("bom.bsb", bytes);

    assertEquals(3, result.source().orElseThrow().positionAt(0).utf8Offset());
    assertEquals(List.of(), mapper.map(result));

    assertEquals(
        List.of("utf8", "lexical", "syntax", "name", "typeAndStack", "ir", "runtime"),
        java.util.Arrays.stream(DiagnosticStage.values())
            .map(DiagnosticJsonMapperTest::mapStage)
            .toList());
  }

  @Test
  void mapsFileOffsetAndResourceLimitWithoutInventingLineColumns() {
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_SOURCE_SIZE_LIMIT,
                Severity.ERROR,
                DiagnosticStage.UTF8,
                "large.bsb",
                new FileOffset(33_554_432))
            .limit("sourceBytes", 33_554_432, 33_554_433)
            .build();
    AnalysisResult result =
        new AnalysisResult(Optional.empty(), List.of(diagnostic), Optional.empty());

    DiagnosticJson mapped = mapper.map(result).getFirst();

    assertEquals(new JsonOffset(33_554_432), mapped.location());
    assertEquals(
        Optional.of(new JsonResourceLimit("sourceBytes", "33554432", "33554433")),
        mapped.resourceLimit());
  }

  private static String mapStage(DiagnosticStage stage) {
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_MISSING_MAIN,
                Severity.ERROR,
                stage,
                "stage.bsb",
                new SourcePosition(0, 1, 1))
            .expected("メインとは （--）")
            .actual("EOF")
            .fix("メインを追加してください")
            .build();
    AnalysisResult result =
        new AnalysisResult(Optional.empty(), List.of(diagnostic), Optional.empty());
    try {
      return new DiagnosticJsonMapper(DiagnosticMessageCatalog.loadDefault())
          .map(result)
          .getFirst()
          .stage();
    } catch (IOException exception) {
      throw new AssertionError(exception);
    }
  }
}
