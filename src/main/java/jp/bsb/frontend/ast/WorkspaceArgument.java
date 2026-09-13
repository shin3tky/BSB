package jp.bsb.frontend.ast;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/** ファイル組み込み語へ渡す、値や型にはならない静的な作業領域引数です。 */
public record WorkspaceArgument(String name, String lexeme, SourceSpan span) {
  public WorkspaceArgument {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    Objects.requireNonNull(span, "span");
  }
}
