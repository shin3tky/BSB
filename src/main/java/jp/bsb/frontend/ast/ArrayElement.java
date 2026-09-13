package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 配列リテラルの1要素を作る式です。
 *
 * <p>配列の要素式は単一の式ノードではなく、空のスタックから実行する本体要素列です。これにより{@code 20 と 2 を 足す}のような計算と、その途中の助詞・コメントを
 * 元の順序で保持します。
 *
 * @param body 要素値を作る本体要素列
 * @param span 最初の要素から最後の要素までの範囲
 */
public record ArrayElement(List<BodyElement> body, SourceSpan span) implements AstNode {
  /** 要素列を不変コピーし、空要素がASTへ入らないことを検証します。 */
  public ArrayElement {
    body = List.copyOf(body);
    if (body.isEmpty()) {
      throw new IllegalArgumentException("an array element body must not be empty");
    }
    Objects.requireNonNull(span, "span");
  }
}
