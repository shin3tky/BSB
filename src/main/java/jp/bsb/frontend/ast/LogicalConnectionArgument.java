package jp.bsb.frontend.ast;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/** 組み込み語へ渡す、値や型にはならない静的な論理接続引数です。 */
public record LogicalConnectionArgument(
    String name, String lexeme, SourceSpan span, Optional<HttpMethodArgument> httpMethod) {
  /** 論理接続のmethodを持たない接続引数を作ります。 */
  public LogicalConnectionArgument(String name, String lexeme, SourceSpan span) {
    this(name, lexeme, span, Optional.empty());
  }

  /** HTTPSの接続とmethodを持つ静的引数を作ります。 */
  public LogicalConnectionArgument(
      String name, String lexeme, SourceSpan span, HttpMethodArgument httpMethod) {
    this(name, lexeme, span, Optional.of(httpMethod));
  }

  /** 正規名、原表記、位置を検証します。 */
  public LogicalConnectionArgument {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    Objects.requireNonNull(span, "span");
    httpMethod = Objects.requireNonNull(httpMethod, "httpMethod");
  }
}
