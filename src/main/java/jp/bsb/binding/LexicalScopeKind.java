package jp.bsb.binding;

/** 束縛で子スコープを作る構文上の位置です。 */
public enum LexicalScopeKind {
  /** プログラム全体の大域スコープ */
  GLOBAL,
  /** 利用者定義単語の本体 */
  WORD_BODY,
  /** 条件分岐の真側 */
  CONDITIONAL_TRUE,
  /** 条件分岐の偽側 */
  CONDITIONAL_FALSE,
  /** 回数ループの本体 */
  COUNTED_LOOP_BODY,
  /** 条件ループの条件計算部 */
  CONDITION_LOOP_CONDITION,
  /** 条件ループの本体 */
  CONDITION_LOOP_BODY,
  /** 配列反復の本体 */
  ARRAY_LOOP_BODY
}
