package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.format.SourceFormatter;
import jp.bsb.ir.Call;
import jp.bsb.ir.IrGenerator;
import org.junit.jupiter.api.Test;

/** 作業領域・区切り表の型検査、IR、formatter契約を本番経路で検証します。 */
class WorkspaceTableDelimitedStaticTest {
  private static final List<String> WORDS =
      List.of(
          "CSVを表として解析する",
          "TSVを表として解析する",
          "表をCSVに変換する",
          "表をTSVに変換する",
          "区切りテキスト解析失敗の種類を取り出す",
          "区切りテキスト解析失敗のバイト位置を取り出す",
          "区切りテキスト解析失敗の行を取り出す",
          "区切りテキスト解析失敗の列を取り出す");

  @Test
  void acceptsAllEightWordsAndLowersConcreteStackEffectsToIr() throws IOException {
    AnalysisResult result = check("sources/WST-N-delimited-types.bsb");

    assertTrue(result.successful(), result.diagnostics().toString());
    var ir = new IrGenerator().generate(result.programForIrGeneration()).programForExecution();
    List<Call> calls =
        ir.userWords().values().stream()
            .flatMap(word -> word.instructions().stream())
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> WORDS.contains(call.targetName()))
            .toList();
    assertEquals(
        List.of(
            "区切りテキスト解析失敗の種類を取り出す",
            "区切りテキスト解析失敗のバイト位置を取り出す",
            "区切りテキスト解析失敗の行を取り出す",
            "区切りテキスト解析失敗の列を取り出す",
            "CSVを表として解析する",
            "TSVを表として解析する",
            "表をCSVに変換する",
            "表をTSVに変換する"),
        calls.stream().map(Call::targetName).toList());
    assertTrue(calls.stream().allMatch(call -> call.stackEffect().isPresent()));
  }

  @Test
  void rejectsParserAndWriterInputMismatchesWithExistingTypeDiagnostic() throws IOException {
    AnalysisResult result = check("sources/WST-F-delimited-static.bsb");

    assertFalse(result.successful());
    assertEquals(
        List.of(DiagnosticCode.E_TYPE_MISMATCH, DiagnosticCode.E_TYPE_MISMATCH),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  @Test
  void formatsTheReferenceChapterIdempotently() throws IOException {
    byte[] source = resource("chapter/delimited-tables-chapter.bsb");
    byte[] canonical = resource("canonical/delimited-tables-chapter.bsb");
    var formatter = new SourceFormatter();

    var first = formatter.format("delimited-tables-chapter.bsb", source);
    var second =
        formatter.format(
            "19b-canonical.bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertEquals(new String(canonical, StandardCharsets.UTF_8), first.outputForStandardOutput());
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
  }

  private static AnalysisResult check(String path) throws IOException {
    return new SourceChecker().check(path, resource(path));
  }

  private static byte[] resource(String path) throws IOException {
    String resource = "/conformance/workspace-tables/" + path;
    try (var input = WorkspaceTableDelimitedStaticTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
