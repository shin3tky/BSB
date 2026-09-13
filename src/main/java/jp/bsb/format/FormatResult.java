package jp.bsb.format;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;

/**
 * format処理の標準出力候補と診断を、混在させずに保持する結果型です。
 *
 * <p>【コンピュータ科学の観点：出力チャネルの分離】 正規化したソースは標準出力、診断は標準エラーへ送ります。構文エラー時に途中まで生成したソースを出力すると、
 * リダイレクト先のファイルを壊す可能性があるため、失敗結果は出力候補を持ちません。
 *
 * @param standardOutputCandidate 成功時の正規表記。空ソースの場合は空文字列を包む。失敗時は空
 * @param diagnostics 人間向け表示と構造化検査に共用する診断の不変リスト
 */
public record FormatResult(Optional<String> standardOutputCandidate, List<Diagnostic> diagnostics) {
  /** 出力候補と診断を非nullの不変値として保持します。 */
  public FormatResult {
    Objects.requireNonNull(standardOutputCandidate, "standardOutputCandidate");
    diagnostics = List.copyOf(diagnostics);
  }

  /**
   * 正規表記を出力できる成功結果かを返します。
   *
   * @return 出力候補が存在する場合はtrue
   */
  public boolean successful() {
    return standardOutputCandidate.isPresent();
  }

  /**
   * 標準出力へ書ける正規表記を返します。
   *
   * @return 正規表記。空ソースでは空文字列
   * @throws IllegalStateException UTF-8・字句・構文のいずれかで失敗した場合
   */
  public String outputForStandardOutput() {
    return standardOutputCandidate.orElseThrow(
        () -> new IllegalStateException("a failed format result has no standard output"));
  }

  static FormatResult success(String output, List<Diagnostic> diagnostics) {
    return new FormatResult(Optional.of(output), diagnostics);
  }

  static FormatResult failure(List<Diagnostic> diagnostics) {
    return new FormatResult(Optional.empty(), diagnostics);
  }
}
