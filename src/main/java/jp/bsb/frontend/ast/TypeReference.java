package jp.bsb.frontend.ast;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * スタック効果に書かれた型名を表します。この段階では名前の存在確認を行わず、後続の静的検査が利用可能な具体型、型制約、後段機能を判定します。
 *
 * @param name Unicode正規化後の型名候補
 * @param lexeme 元ソース上の表記
 * @param span 型名のソース範囲
 * @param typeArgument 配列型・任意型・結果型の第1型引数、単純な型名なら空
 * @param secondTypeArgument 結果型の第2型引数、それ以外なら空
 */
public record TypeReference(
    String name,
    String lexeme,
    SourceSpan span,
    Optional<TypeReference> typeArgument,
    Optional<TypeReference> secondTypeArgument)
    implements AstNode {
  /**
   * 束縛までの単純な型名を作る互換コンストラクタです。
   *
   * @param name Unicode正規化後の型名候補
   * @param lexeme 元ソース上の表記
   * @param span 型名のソース範囲
   */
  public TypeReference(String name, String lexeme, SourceSpan span) {
    this(name, lexeme, span, Optional.empty(), Optional.empty());
  }

  /** 1引数型構築子を作る互換コンストラクタです。 */
  public TypeReference(
      String name, String lexeme, SourceSpan span, Optional<TypeReference> typeArgument) {
    this(name, lexeme, span, typeArgument, Optional.empty());
  }

  /** 型名候補、原表記、ソース範囲を検証します。 */
  public TypeReference {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    if (span == null) {
      throw new NullPointerException("span");
    }
    typeArgument = Objects.requireNonNull(typeArgument, "typeArgument");
    secondTypeArgument = Objects.requireNonNull(secondTypeArgument, "secondTypeArgument");
    boolean unary = name.startsWith("配列<") || name.startsWith("任意<");
    boolean result = name.startsWith("結果<");
    if (typeArgument.isPresent() != (unary || result) || secondTypeArgument.isPresent() != result) {
      throw new IllegalArgumentException(
          "constructed type names must contain the required type arguments");
    }
  }

  /**
   * 要素型を持つ配列型構文かを返します。
   *
   * @return 配列型構文ならtrue
   */
  public boolean isArray() {
    return typeArgument.isPresent() && name.startsWith("配列<");
  }

  /**
   * 型引数を持つ任意型構文かを返します。
   *
   * @return 任意型構文ならtrue
   */
  public boolean isOptional() {
    return typeArgument.isPresent() && name.startsWith("任意<");
  }

  /** 2個の型引数を持つ結果型構文かを返します。 */
  public boolean isResult() {
    return secondTypeArgument.isPresent() && name.startsWith("結果<");
  }

  /**
   * 配列までの配列専用呼出し元向けに、型引数を要素型名で返します。
   *
   * @return 型構築子の型引数。単純型なら空
   */
  public Optional<TypeReference> elementType() {
    return typeArgument;
  }
}
