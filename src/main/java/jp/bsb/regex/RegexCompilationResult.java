package jp.bsb.regex;

import java.util.Map;
import java.util.Objects;
import jp.bsb.diagnostics.DiagnosticCode;

/** RE2/J固有の例外と位置を外へ漏らさず、コンパイル成功またはBSB診断情報を返す結果です。 */
public sealed interface RegexCompilationResult
    permits RegexCompilationResult.Failure, RegexCompilationResult.Success {
  /**
   * 正常にコンパイルした結果です。
   *
   * @param program 検査済みプログラム
   */
  record Success(RegexProgram program) implements RegexCompilationResult {
    /** nullを拒否します。 */
    public Success {
      Objects.requireNonNull(program, "program");
    }
  }

  /**
   * BSBの静的診断へ変換済みの失敗です。
   *
   * @param code 正規表現コンパイルに属するBSB診断コード
   * @param patternOffset rawパターン内の0始まりUnicodeコードポイント位置
   * @param fields 構造化診断へ引き渡す不変フィールド
   */
  record Failure(DiagnosticCode code, int patternOffset, Map<String, String> fields)
      implements RegexCompilationResult {
    /** コード、位置、フィールドを検証して不変化します。 */
    public Failure {
      Objects.requireNonNull(code, "code");
      if (code != DiagnosticCode.E_REGEX_SYNTAX
          && code != DiagnosticCode.E_REGEX_UNSUPPORTED_CONSTRUCT
          && code != DiagnosticCode.E_REGEX_PATTERN_LIMIT
          && code != DiagnosticCode.E_REGEX_CAPTURE_LIMIT
          && code != DiagnosticCode.E_REGEX_PROGRAM_LIMIT) {
        throw new IllegalArgumentException("a regex compiler failure requires a regex code");
      }
      if (patternOffset < 0) {
        throw new IllegalArgumentException("patternOffset must not be negative");
      }
      fields = Map.copyOf(fields);
    }
  }
}
