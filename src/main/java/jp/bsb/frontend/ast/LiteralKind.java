package jp.bsb.frontend.ast;

/** AST上で区別するホスト入出力までのリテラル種別です。 */
public enum LiteralKind {
  /** 符号付き整数リテラル。 */
  INTEGER,

  /** 正確な10進小数の字句。言語コアでは値として実行しません。 */
  DECIMAL,

  /** {@code はい} または {@code いいえ}。 */
  BOOLEAN,

  /** 1拡張書記素クラスタの文字リテラル。 */
  CHARACTER,

  /** Unicodeスカラー値列を保持する文字列リテラル。 */
  STRING,

  /** rawパターンとflagsを保持する正規表現リテラル。 */
  REGEX
}
