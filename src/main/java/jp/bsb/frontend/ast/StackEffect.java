package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 単語がデータスタックから受け取る型列と、実行後に残す型列を表します。
 *
 * <p>全角括弧とASCII括弧は構文上の表記だけが異なるため、ASTでは同じ構造へ変換します。これにより後続の型検査は括弧の種類を意識せず、入力と出力だけを扱えます。
 *
 * @param inputTypes {@code --} より前に宣言された入力型列
 * @param outputTypes {@code --} より後に宣言された出力型列
 * @param span 開き括弧から閉じ括弧の直後までの範囲
 */
public record StackEffect(
    List<TypeReference> inputTypes, List<TypeReference> outputTypes, SourceSpan span)
    implements AstNode {
  /** 入出力型列を不変リストへコピーし、ソース範囲を検証します。 */
  public StackEffect {
    inputTypes = List.copyOf(inputTypes);
    outputTypes = List.copyOf(outputTypes);
    Objects.requireNonNull(span, "span");
  }
}
