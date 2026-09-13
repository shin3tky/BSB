package jp.bsb.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticRendererTest {
  @Test
  void rendersMainLineAndDetailsInTheSpecifiedOrder() throws IOException {
    var renderer = new DiagnosticRenderer(DiagnosticTestSupport.loadMessages());
    var diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_NUMBER_LIMIT,
                Severity.ERROR,
                DiagnosticStage.LEXICAL,
                "入力.bsb",
                new SourceSpan(new SourcePosition(12, 2, 5), new SourcePosition(16, 2, 9)))
            .expected("4096桁以下")
            .actual("4097桁")
            .limit("numberDigits", 4096, 4097)
            .relatedLocation(new RelatedLocation("入力.bsb", new SourcePosition(0, 1, 1), "定義の開始"))
            .fix("桁数を減らしてください")
            .build();

    assertEquals(
        """
        入力.bsb:2:5: エラー[E_NUMBER_LIMIT]: 数値リテラルは4096桁までですが、4097桁あります。
          必要: 4096桁以下
          実際: 4097桁
          上限: numberDigits=4096
          観測値: 4097
          関連位置: 入力.bsb:1:1 定義の開始
          修正候補: 桁数を減らしてください
        """,
        renderer.render(List.of(diagnostic)));
  }

  @Test
  void omitsLineAndColumnForRawFileOffsets() throws IOException {
    var renderer = new DiagnosticRenderer(DiagnosticTestSupport.loadMessages());
    var diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_SOURCE_SIZE_LIMIT,
                Severity.ERROR,
                DiagnosticStage.UTF8,
                "入力.bsb",
                new FileOffset(33_554_432))
            .limit("sourceBytes", 33_554_432, 33_554_433)
            .build();

    assertEquals(
        """
        入力.bsb: エラー[E_SOURCE_SIZE_LIMIT]: ソースファイルが上限を超えています。
          上限: sourceBytes=33554432
          観測値: 33554433
        """,
        renderer.render(List.of(diagnostic)));
  }

  @Test
  void rendersNoOutputForNoDiagnostics() throws IOException {
    var renderer = new DiagnosticRenderer(DiagnosticTestSupport.loadMessages());

    assertEquals("", renderer.render(List.of()));
  }

  @Test
  void rendersRelatedDictionaryInformationWithoutInventingASourcePosition() throws IOException {
    var renderer = new DiagnosticRenderer(DiagnosticTestSupport.loadMessages());
    var diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_RESERVED_NAME,
                Severity.ERROR,
                DiagnosticStage.NAME,
                "入力.bsb",
                new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(6, 1, 3)))
            .relatedLocation(RelatedLocation.outsideSource("組み込み単語 足す"))
            .build();

    assertEquals(
        "入力.bsb:1:1: エラー[E_RESERVED_NAME]: この名前はBSBによって予約されています。\n" + "  関連位置: 組み込み単語 足す\n",
        renderer.render(List.of(diagnostic)));
  }
}
