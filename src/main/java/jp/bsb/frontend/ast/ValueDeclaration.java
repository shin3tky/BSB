package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.binding.BindingKind;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code 名前は 定数 初期値。} または {@code 名前は 変数 初期値。}を表します。
 *
 * @param name Unicode正規化済みの宣言名
 * @param lexeme 元ソースに書かれた宣言名
 * @param nameSpan 宣言名だけの範囲
 * @param markerSpan 宣言標識「は」の範囲
 * @param kind 定数または変数
 * @param kindSpan 宣言種別の範囲
 * @param initializer 初期値を構成する要素。コメントと助詞も元順序で保持
 * @param endSpan 宣言終端「。」の範囲
 * @param span 宣言名から終端直後までの範囲
 */
public record ValueDeclaration(
    String name,
    String lexeme,
    SourceSpan nameSpan,
    SourceSpan markerSpan,
    BindingKind kind,
    SourceSpan kindSpan,
    List<BodyElement> initializer,
    SourceSpan endSpan,
    SourceSpan span)
    implements TopLevelElement, BodyElement {
  /** 必須値を検証し、初期値を不変リストへコピーします。 */
  public ValueDeclaration {
    name = requireText(name, "name");
    lexeme = requireText(lexeme, "lexeme");
    Objects.requireNonNull(nameSpan, "nameSpan");
    Objects.requireNonNull(markerSpan, "markerSpan");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(kindSpan, "kindSpan");
    initializer = List.copyOf(initializer);
    Objects.requireNonNull(endSpan, "endSpan");
    Objects.requireNonNull(span, "span");
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
