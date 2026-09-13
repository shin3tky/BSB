package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code 名前とは スタック効果 本体 こと。} という1個の単語定義を表します。
 *
 * @param name Unicode正規化後の単語名
 * @param lexeme 元ソースに書かれた正規化前の単語名
 * @param nameSpan 単語名だけのソース範囲
 * @param stackEffect 宣言された入力型列と出力型列
 * @param body 本体要素を実行順に並べた不変リスト。空本体では空リスト
 * @param endSpan 終端の {@code こと。} を覆うソース範囲
 * @param span 単語名の先頭から終端記号の直後までの範囲
 */
public record WordDefinition(
    String name,
    String lexeme,
    SourceSpan nameSpan,
    StackEffect stackEffect,
    List<BodyElement> body,
    SourceSpan endSpan,
    SourceSpan span)
    implements TopLevelElement {
  /** 必須要素を検証し、本体要素を不変リストへコピーします。 */
  public WordDefinition {
    name = requireText(name, "name");
    lexeme = requireText(lexeme, "lexeme");
    Objects.requireNonNull(nameSpan, "nameSpan");
    Objects.requireNonNull(stackEffect, "stackEffect");
    body = List.copyOf(body);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
