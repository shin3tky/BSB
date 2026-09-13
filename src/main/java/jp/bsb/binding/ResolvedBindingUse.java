package jp.bsb.binding;

import java.util.Objects;
import jp.bsb.frontend.ast.AstNode;

/**
 * AST上の値参照または代入先と、静的に選ばれた宣言の対応です。
 *
 * @param node 参照または代入を表すASTノード
 * @param spelling 元ソースに書かれた名前
 * @param kind 読み出しまたは書き込み
 * @param bindingId 解決先の束縛ID
 */
public record ResolvedBindingUse(
    AstNode node, String spelling, BindingUseKind kind, BindingId bindingId) {
  /** 必須値を検証します。 */
  public ResolvedBindingUse {
    Objects.requireNonNull(node, "node");
    if (spelling == null || spelling.isBlank()) {
      throw new IllegalArgumentException("spelling must not be blank");
    }
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(bindingId, "bindingId");
  }
}
