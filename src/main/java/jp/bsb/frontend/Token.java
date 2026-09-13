package jp.bsb.frontend;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.RegexLiteralMetadata;

/**
 * 字句解析によって切り出された「最小の意味単位（トークン）」を表すレコードです。
 *
 * <p>【コンピュータ科学の観点：レキシーム（Lexeme）とトークン値（Value）】 コンパイラのフロントエンドでは、以下の3つの情報を区別して保持することが重要です：
 *
 * <ul>
 *   <li><b>原表記（{@code lexeme}）</b>: ソースコード中に実際に書かれていた文字列そのもの（例: {@code "「Hello\\nWorld」"} や {@code
 *       "ＡＢＣ"}）。 フォーマッタでの再構築やエラー表示で利用します。
 *   <li><b>解釈値（{@code value}）</b>: エスケープシーケンスの展開やUnicode正規化（NFC、全角英数のASCII変換）を経た値 （例: {@code
 *       "Hello\nWorld"}）。構文解析やインタープリタ実行で利用します。
 *   <li><b>ソース位置（{@code span}）</b>: 元ファイル中の開始・終了位置（行・列・バイト位置）。正確なエラー報告に必須です。
 * </ul>
 *
 * @param kind トークンの種類（{@link TokenKind}）
 * @param lexeme ソースコード上の生のテキスト（切り出された部分文字列）
 * @param value 正規化・エスケープ解決後の解釈された値
 * @param span ソースファイル上での位置範囲
 * @param regexMetadata 正規表現リテラルだけが持つflagsとパターン位置表
 */
public record Token(
    TokenKind kind,
    String lexeme,
    String value,
    SourceSpan span,
    Optional<RegexLiteralMetadata> regexMetadata) {
  /**
   * 正規表現メタデータを持たない従来トークンを作ります。
   *
   * @param kind トークンの種類
   * @param lexeme ソースコード上の生のテキスト
   * @param value 字句規則を適用した値
   * @param span ソースファイル上の位置範囲
   */
  public Token(TokenKind kind, String lexeme, String value, SourceSpan span) {
    this(kind, lexeme, value, span, Optional.empty());
  }

  /** コンパクトコンストラクタによる非null保証。 */
  public Token {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(lexeme, "lexeme");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(span, "span");
    regexMetadata = Objects.requireNonNull(regexMetadata, "regexMetadata");
    if ((kind == TokenKind.REGEX_LITERAL) != regexMetadata.isPresent()) {
      throw new IllegalArgumentException("only a regex token must carry regex metadata");
    }
  }
}
