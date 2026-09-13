package jp.bsb.binding;

import jp.bsb.diagnostics.SourceSpan;

/** 単語、定数、変数が共有する名前空間へ登録される宣言の共通面です。 */
public sealed interface DeclaredName
    permits Binding, LogicalConnectionName, WordName, WorkspaceName {
  /**
   * Unicode正規化済みの名前を返します。
   *
   * @return Unicode正規化済みの名前
   */
  String name();

  /**
   * 元ソースに書かれた表記を返します。
   *
   * @return 元ソースに書かれた表記
   */
  String spelling();

  /**
   * 宣言の種類を返します。
   *
   * @return 宣言の種類
   */
  DeclaredNameKind declarationKind();

  /**
   * 宣言名だけを覆うソース範囲を返します。
   *
   * @return 宣言名だけを覆うソース範囲
   */
  SourceSpan nameSpan();
}
