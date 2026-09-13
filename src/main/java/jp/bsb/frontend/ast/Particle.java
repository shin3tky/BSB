package jp.bsb.frontend.ast;

import jp.bsb.diagnostics.SourceSpan;

/**
 * 単語本体に書かれた助詞を表します。助詞は実行命令にはなりませんが、配置検査、診断、フォーマットのためにASTへ残します。
 *
 * @param name 正規化後の助詞
 * @param lexeme 元ソース上の表記
 * @param span 助詞のソース範囲
 */
public record Particle(String name, String lexeme, SourceSpan span) implements BodyElement {
  /** 助詞名、原表記、ソース範囲を検証します。 */
  public Particle {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    if (span == null) {
      throw new NullPointerException("span");
    }
  }
}
