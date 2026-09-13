package jp.bsb.analyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.frontend.Lexer;
import jp.bsb.frontend.Parser;
import jp.bsb.frontend.SourceText;
import jp.bsb.frontend.Utf8SourceReader;

/** UTF-8入力から構文解析と静的検査までを接続する、{@code check} 用の処理パイプラインです。 */
public final class SourceChecker {
  private final Utf8SourceReader sourceReader;
  private final Lexer lexer;
  private final Parser parser;
  private final SemanticAnalyzer analyzer;

  /** 標準の読取り器、字句解析器、構文解析器、静的解析器を組み合わせます。 */
  public SourceChecker() {
    sourceReader = new Utf8SourceReader();
    lexer = new Lexer();
    parser = new Parser();
    analyzer = new SemanticAnalyzer();
  }

  /**
   * ファイルを読み込み、静的検査します。
   *
   * @param path 入力ファイル
   * @return 派生診断の抑止を適用した解析結果
   * @throws IOException ファイルの物理的なI/Oエラーが発生した場合
   */
  public AnalysisResult check(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    var diagnostics = new DiagnosticCollector();
    var source = sourceReader.read(path, diagnostics);
    return source
        .map(value -> checkDecoded(value, diagnostics))
        .orElseGet(() -> AnalysisResult.failure(diagnostics.diagnostics()));
  }

  /**
   * 生のバイト列を厳密なUTF-8として読み、静的検査します。
   *
   * @param sourcePath 診断表示に使用するソース識別パス
   * @param bytes BOMを含み得る元バイト列
   * @return 派生診断の抑止を適用した解析結果
   */
  public AnalysisResult check(String sourcePath, byte[] bytes) {
    Objects.requireNonNull(sourcePath, "sourcePath");
    Objects.requireNonNull(bytes, "bytes");
    var diagnostics = new DiagnosticCollector();
    var source = sourceReader.read(sourcePath, bytes, diagnostics);
    return source
        .map(value -> checkDecoded(value, diagnostics))
        .orElseGet(() -> AnalysisResult.failure(diagnostics.diagnostics()));
  }

  private AnalysisResult checkDecoded(SourceText source, DiagnosticCollector diagnostics) {
    var lexResult = lexer.lex(source, diagnostics);
    if (!lexResult.successful()) {
      return AnalysisResult.failure(diagnostics.diagnostics()).withSource(source);
    }

    var parseResult = parser.parse(source, lexResult, diagnostics);
    if (!parseResult.successful()) {
      return AnalysisResult.failure(diagnostics.diagnostics()).withSource(source);
    }

    return analyzer.analyze(parseResult.programForAnalysis()).withSource(source);
  }
}
