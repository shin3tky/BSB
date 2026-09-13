package jp.bsb.format;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.frontend.Lexer;
import jp.bsb.frontend.Parser;
import jp.bsb.frontend.SourceText;
import jp.bsb.frontend.Utf8SourceReader;

/**
 * 元のUTF-8入力から正規表記または構文診断を生成する、format用の処理パイプラインです。
 *
 * <p>処理はUTF-8検証、字句解析、構文解析で止めます。名前解決や型検査を呼ばないため、未定義単語、重複定義、小数などを含む構文的に有効な入力も整形できます。
 */
public final class SourceFormatter {
  private final Utf8SourceReader sourceReader;
  private final Lexer lexer;
  private final Parser parser;
  private final CanonicalFormatter formatter;

  /** 標準の読取り器、字句解析器、構文解析器、正規フォーマッタを組み合わせます。 */
  public SourceFormatter() {
    sourceReader = new Utf8SourceReader();
    lexer = new Lexer();
    parser = new Parser();
    formatter = new CanonicalFormatter();
  }

  /**
   * ファイルを読み込んでformat処理を行います。
   *
   * @param path 入力ファイル
   * @return 出力候補と診断を分離した結果
   * @throws IOException ファイルの物理的なI/Oエラーが発生した場合
   */
  public FormatResult format(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    var diagnostics = new DiagnosticCollector();
    var source = sourceReader.read(path, diagnostics);
    return source
        .map(value -> formatDecoded(value, diagnostics))
        .orElseGet(() -> FormatResult.failure(diagnostics.diagnostics()));
  }

  /**
   * 生のバイト配列を入力としてformat処理を行います。テストや標準入力との統合でも同じ厳密UTF-8経路を利用できます。
   *
   * @param sourcePath 診断表示に使うソース識別パス
   * @param bytes BOMを含み得る元バイト列
   * @return 出力候補と診断を分離した結果
   */
  public FormatResult format(String sourcePath, byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    var diagnostics = new DiagnosticCollector();
    var source = sourceReader.read(sourcePath, bytes, diagnostics);
    return source
        .map(value -> formatDecoded(value, diagnostics))
        .orElseGet(() -> FormatResult.failure(diagnostics.diagnostics()));
  }

  private FormatResult formatDecoded(SourceText source, DiagnosticCollector diagnostics) {
    var lexResult = lexer.lex(source, diagnostics);
    if (!lexResult.successful()) {
      return FormatResult.failure(diagnostics.diagnostics());
    }

    var parseResult = parser.parse(source, lexResult, diagnostics);
    if (!parseResult.successful()) {
      return FormatResult.failure(diagnostics.diagnostics());
    }

    String output = formatter.format(parseResult.programForAnalysis());
    return FormatResult.success(output, diagnostics.diagnostics());
  }
}
