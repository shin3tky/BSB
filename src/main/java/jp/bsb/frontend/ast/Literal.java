package jp.bsb.frontend.ast;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 単語本体に現れる値のリテラルです。原表記と字句解析後の値を分けて保持するため、後続のフォーマッタと静的検査が同じASTを利用できます。
 *
 * @param kind リテラルの種類
 * @param lexeme 元ソース上の引用符や表記を含む文字列
 * @param value エスケープ展開済みの値。小数は値生成前の正規字句を保持
 * @param span リテラルのソース範囲
 * @param regexMetadata 正規表現リテラルだけが持つflagsとパターン位置表
 */
public record Literal(
    LiteralKind kind,
    String lexeme,
    String value,
    SourceSpan span,
    Optional<RegexLiteralMetadata> regexMetadata)
    implements BodyElement {
  /**
   * 正規表現メタデータを持たない従来リテラルを作ります。
   *
   * @param kind リテラルの種類
   * @param lexeme 元ソース上の表記
   * @param value 字句規則を適用した値
   * @param span リテラルのソース範囲
   */
  public Literal(LiteralKind kind, String lexeme, String value, SourceSpan span) {
    this(kind, lexeme, value, span, Optional.empty());
  }

  /** リテラルの各構成要素がnullでないことを検証します。 */
  public Literal {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(lexeme, "lexeme");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(span, "span");
    regexMetadata = Objects.requireNonNull(regexMetadata, "regexMetadata");
    if ((kind == LiteralKind.REGEX) != regexMetadata.isPresent()) {
      throw new IllegalArgumentException("only a regex literal must carry regex metadata");
    }
    regexMetadata.ifPresent(
        metadata -> {
          int boundaries = value.codePointCount(0, value.length()) + 1;
          if (metadata.patternPositions().size() != boundaries) {
            throw new IllegalArgumentException(
                "regex pattern position count differs from its value");
          }
        });
  }
}
