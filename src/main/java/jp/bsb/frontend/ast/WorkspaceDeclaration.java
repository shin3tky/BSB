package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/** トップレベルで宣言された、実行時の保存領域を持たない作業領域名です。 */
public record WorkspaceDeclaration(
    String name,
    String lexeme,
    SourceSpan nameSpan,
    SourceSpan markerSpan,
    SourceSpan kindSpan,
    List<Comment> comments,
    SourceSpan endSpan,
    SourceSpan span)
    implements TopLevelElement {
  public WorkspaceDeclaration {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    Objects.requireNonNull(nameSpan, "nameSpan");
    Objects.requireNonNull(markerSpan, "markerSpan");
    Objects.requireNonNull(kindSpan, "kindSpan");
    comments = List.copyOf(comments);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }
}
