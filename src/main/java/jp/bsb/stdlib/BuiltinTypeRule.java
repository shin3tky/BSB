package jp.bsb.stdlib;

/** 組み込み単語の入力型へ適用する型規則です。 */
public enum BuiltinTypeRule {
  /** 宣言された具体型列と完全に一致させます。 */
  FIXED,

  /** 2入力が同じ具体型であることを要求します。辞書上の {@code T T} に対応します。 */
  SAME_TYPE_PAIR,

  /** {@code N}を整数または小数の同じ具体型へ具体化します。 */
  SAME_NUMERIC_TYPE,

  /** 各{@code 数値}入力を、互いに独立した整数または小数へ具体化します。 */
  INDEPENDENT_NUMERIC_INPUTS,

  /** 表示可能な具体型を1値受理します。辞書上の {@code 表示可能} に対応します。 */
  DISPLAYABLE,

  /** {@code 配列<T> -- 整数}です。 */
  ARRAY_LENGTH,

  /** {@code 配列<T> 整数 -- T}です。 */
  ARRAY_GET,

  /** {@code 配列<T> 整数 整数 -- 配列<T>}です。 */
  ARRAY_SLICE,

  /** {@code 配列<T> 整数 T -- 配列<T>}です。 */
  ARRAY_REPLACE,

  /** {@code 配列<T> T -- 配列<T>}です。 */
  ARRAY_APPEND,

  /** {@code 配列<T> 配列<T> -- 配列<T>}です。 */
  ARRAY_CONCAT,

  /** {@code 配列<T> T -- 配列<T>}です。 */
  ARRAY_PREPEND,

  /** {@code 配列<T> -- 配列<T>}です。 */
  ARRAY_REVERSE,

  /** 等値比較可能な{@code T}について、{@code 配列<T> T -- 真偽}です。 */
  ARRAY_CONTAINS,

  /** 等値比較可能な{@code T}について、{@code 配列<T> T -- 整数}です。 */
  ARRAY_FIND,

  /** {@code 配列<T> -- 真偽}です。 */
  ARRAY_IS_EMPTY,

  /** {@code 配列<T> -- 任意<T>}です。 */
  ARRAY_EDGE_OPTIONAL,

  /** {@code 配列<T> -- 配列<T>}です。 */
  ARRAY_DELETE_EDGE,

  /** {@code 配列<T> 整数 整数 -- 配列<T>}です。 */
  ARRAY_DELETE_RANGE,

  /** {@code T -- 任意<T>}です。 */
  OPTIONAL_WRAP,

  /** {@code 任意<T> -- 任意<T> 真偽}です。 */
  OPTIONAL_PREDICATE,

  /** {@code 任意<T> -- T}です。 */
  OPTIONAL_UNWRAP,

  /** {@code 任意<T> --}です。 */
  OPTIONAL_DROP,

  /** 明示した{@code <T,E>}で{@code T -- 結果<T,E>}を具体化します。 */
  RESULT_SUCCESS_WRAP,
  /** 明示した{@code <T,E>}で{@code E -- 結果<T,E>}を具体化します。 */
  RESULT_FAILURE_WRAP,
  /** {@code 結果<T,E> -- 結果<T,E> 真偽}です。 */
  RESULT_PREDICATE,
  /** {@code 結果<T,E> -- T}です。 */
  RESULT_SUCCESS_UNWRAP,
  /** {@code 結果<T,E> -- E}です。 */
  RESULT_FAILURE_UNWRAP,
  /** {@code 結果<T,E> --}です。 */
  RESULT_DROP
}
