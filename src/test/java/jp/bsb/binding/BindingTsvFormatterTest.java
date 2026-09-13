package jp.bsb.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jp.bsb.analyzer.SourceChecker;
import org.junit.jupiter.api.Test;

/** 束縛の内部名前解決説明が静的かつ決定的であることを検証します。 */
class BindingTsvFormatterTest {
  @Test
  void formatsTheChapterBindingReportByteForByte() throws IOException {
    byte[] source = resourceBytes("chapter/bindings-chapter.bsb");
    String expected = resourceText("chapter/bindings-chapter.bindings.tsv");
    var analysis = new SourceChecker().check("bindings-chapter.bsb", source);

    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    String first = BindingTsvFormatter.format(analysis.programForIrGeneration());
    String second = BindingTsvFormatter.format(analysis.programForIrGeneration());

    assertEquals(expected, first);
    assertEquals(first, second);
  }

  @Test
  void reportsBlockLocalsByOwnerWordAndWritesAtTheTargetName() {
    String source =
        "メインとは （--）\n"
            + "    はい ならば\n"
            + "        値は 変数 1。\n"
            + "        値 を 一行表示する\n"
            + "        2 を 値 に 入れる\n"
            + "        値 を 一行表示する\n"
            + "    つぎに\n"
            + "こと。\n";
    var analysis = new SourceChecker().check("block.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    BindingUseReport report = BindingUseReport.from(analysis.programForIrGeneration());

    assertEquals(3, report.entries().size());
    assertEquals(
        java.util.List.of(BindingUseKind.READ, BindingUseKind.WRITE, BindingUseKind.READ),
        report.entries().stream().map(BindingUseReport.Entry::useKind).toList());
    assertEquals(13, report.entries().get(1).usePosition().column());
    assertTrue(report.entries().stream().allMatch(entry -> entry.scope().equals("local:メイン")));
    assertTrue(report.entries().stream().allMatch(entry -> entry.type().sourceName().equals("整数")));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String path = "/conformance/bindings/" + relativePath;
    try (var input = BindingTsvFormatterTest.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }

  private static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }
}
