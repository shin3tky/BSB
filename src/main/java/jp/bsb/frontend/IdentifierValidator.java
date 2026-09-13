package jp.bsb.frontend;

import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;

/**
 * ソースコードから切り出された識別子（変数名・単語名）候補文字列の検証と正規化を行うバリデータです。
 *
 * <p>【コンピュータ科学の観点：識別子の正当性検証（Identifier Validation）】
 * 識別子の検証では、単に正規表現にマッチするかどうかだけでなく、以下の段階的な検査と正確なエラー位置の特定を行います：
 *
 * <ol>
 *   <li><b>不可視文字検査</b>: ゼロ幅文字や制御文字が含まれていないか（{@link DiagnosticCode#E_INVISIBLE_CHARACTER}）。
 *       含まれている場合、問題のコードポイントが占める正確な位置（{@link jp.bsb.diagnostics.SourceSpan}）を特定して報告します。
 *   <li><b>文字種別規則検査</b>: 先頭文字が数字や記号でないか、使用禁止文字が含まれていないか（{@link
 *       DiagnosticCode#E_INVALID_IDENTIFIER}）。
 *   <li><b>長さ上限検査</b>: 正規化後の文字数（Unicodeコードポイント数）が言語仕様の上限（128文字）を超えていないか（{@link
 *       DiagnosticCode#E_IDENTIFIER_TOO_LONG}）。
 * </ol>
 */
public final class IdentifierValidator {
  /** 識別子1つあたりの最大許容Unicodeコードポイント数（128文字） */
  public static final int MAX_IDENTIFIER_CODE_POINTS = 128;

  /** Unicode 16.0の規則を使う識別子検証器を作ります。 */
  public IdentifierValidator() {}

  /**
   * ソーステキストの指定区間を識別子候補として検証・正規化します。
   *
   * @param source ソースコードテキスト情報
   * @param utf16Start 開始インデックス
   * @param utf16End 終了インデックス
   * @param diagnostics 診断収集器
   * @return 検証に合格した場合は正規化済みの識別子文字列、不合格の場合は診断を追加して {@link Optional#empty()}
   */
  public Optional<String> validate(
      SourceText source, int utf16Start, int utf16End, DiagnosticCollector diagnostics) {
    String candidate = source.text().substring(utf16Start, utf16End);
    String normalized = UnicodeRules.normalizeIdentifier(candidate);

    // 1. 不可視文字・制御文字の検査
    var forbidden = UnicodeRules.firstForbiddenIdentifierIndex(candidate);
    if (forbidden.isPresent()) {
      int index = forbidden.getAsInt();
      int codePoint = candidate.codePointAt(index);
      int sourceIndex = utf16Start + index;
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INVISIBLE_CHARACTER,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(sourceIndex, sourceIndex + Character.charCount(codePoint)))
              .field("codePoint", UnicodeRules.codePointNotation(codePoint))
              .field("unicodeName", UnicodeRules.unicodeName(codePoint))
              .expected("可視の識別子文字")
              .actual(UnicodeRules.codePointNotation(codePoint))
              .fix("この文字を削除してください")
              .build());
      return Optional.empty();
    }

    // 2. Unicode Letterで始まる識別子文法の検査
    if (!UnicodeRules.isValidIdentifier(normalized)) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INVALID_IDENTIFIER,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(utf16Start, utf16End))
              .field("candidate", candidate)
              .expected("Unicode Letterで始まる識別子")
              .actual(candidate)
              .fix("識別子に使用できる文字へ変更してください")
              .build());
      return Optional.empty();
    }

    // 3. 最大コードポイント数（128文字）の検査
    int codePointCount = normalized.codePointCount(0, normalized.length());
    if (codePointCount > MAX_IDENTIFIER_CODE_POINTS) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_IDENTIFIER_TOO_LONG,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(utf16Start, utf16End))
              .limit("identifierCodePoints", MAX_IDENTIFIER_CODE_POINTS, codePointCount)
              .expected(MAX_IDENTIFIER_CODE_POINTS + "コードポイント以下")
              .actual(codePointCount + "コードポイント")
              .fix("識別子を短くしてください")
              .build());
      return Optional.empty();
    }
    return Optional.of(normalized);
  }
}
