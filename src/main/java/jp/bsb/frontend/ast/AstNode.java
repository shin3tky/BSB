package jp.bsb.frontend.ast;

import jp.bsb.diagnostics.SourceSpan;

/**
 * BSBの抽象構文木（AST）を構成するすべてのノードに共通するインターフェースです。
 *
 * <p>【コンピュータ科学の観点：抽象構文木】 トークン列は「どの語がどの順番で現れたか」を表します。ASTはそこから空白や括弧の表記差を取り除き、
 * 「単語定義」「スタック効果」「本体要素」といった言語上の構造へまとめたものです。一方、診断とフォーマットには元の位置が必要なため、各ノードは対応する {@link SourceSpan}
 * を保持します。
 */
public interface AstNode {
  /**
   * このノードが元ソース中で占める半開区間を返します。
   *
   * @return 元ソース上の範囲
   */
  SourceSpan span();
}
