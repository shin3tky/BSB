package jp.bsb.frontend;

/**
 * 字句解析（Lexical Analysis）によって分類されるトークンの種類（種別）を表す列挙型です。
 *
 * <p>【コンピュータ科学の観点：トークン種別（Token Kind / Token Type）】
 * 字句解析器（Lexer）は、生のテキストを読み込み、意味を持つ最小単位である「トークン」へと分割・分類します。
 * 本列挙型は、BSB言語のホスト入出力までに定義されているすべてのトークンカテゴリを網羅しています。
 * 構文解析器（Parser）は、このトークン種別の並び順（文法規則）を検証することで構文木を構築します。
 */
public enum TokenKind {
  /** 識別子（変数名、単語名など。例: {@code メイン}, {@code カウント}） */
  IDENTIFIER,

  /** 整数リテラル（任意精度の符号付き整数。例: {@code 42}, {@code -100}） */
  INTEGER_LITERAL,

  /** 10進小数リテラル（例: {@code 3.14}。制御フローでも字句解析・構文解析・formatのみ受理） */
  DECIMAL_LITERAL,

  /** 真偽値リテラル（真偽値。{@code はい} または {@code いいえ}） */
  BOOLEAN_LITERAL,

  /** 文字リテラル（単一の文字／1書記素クラスタ。例: {@code 'A'}, {@code 'あ'}） */
  CHARACTER_LITERAL,

  /** 文字列リテラル（日本語鉤括弧または二重引用符で囲まれた文字列。例: {@code 「こんにちは」}, {@code "hello"}） */
  STRING_LITERAL,

  /** rawパターンと直後のflagsを保持する正規表現リテラル */
  REGEX_LITERAL,

  /** 助詞（読みやすさのためのメタデータ。例: {@code を}, {@code に}, {@code と}, {@code から}, {@code で}） */
  PARTICLE,

  /** 予約構文語（言語仕様で固定された文法キーワード。例: {@code とは}, {@code こと}） */
  RESERVED_SYNTAX,

  /** 条件分岐の真側を開始する {@code ならば} */
  CONDITIONAL_START,

  /** 条件分岐の偽側を開始する {@code さもなければ} */
  CONDITIONAL_ELSE,

  /** 条件分岐を終了する {@code つぎに} */
  CONDITIONAL_END,

  /** 短絡論理和の右辺評価ブロックを開始する {@code または} */
  SHORT_CIRCUIT_OR,

  /** 短絡論理積の右辺評価ブロックを開始する {@code かつ} */
  SHORT_CIRCUIT_AND,

  /** 回数ループを開始する {@code 回だけ} */
  COUNTED_LOOP_START,

  /** 条件ループの条件計算部を開始する {@code ここから} */
  CONDITION_LOOP_START,

  /** 条件ループの条件計算部と本体を分ける {@code 続く間} */
  LOOP_CONDITION_SEPARATOR,

  /** 最も内側のループを終了する {@code 繰り返す} */
  LOOP_END,

  /** 最も内側のループから脱出する {@code 打ち切る} */
  BREAK,

  /** 最も内側のループの次反復へ進む {@code 続ける} */
  CONTINUE,

  /** 現在の利用者定義単語から復帰する {@code 戻る} */
  RETURN,

  /** 値なしなら現在の利用者定義単語から復帰する局所伝播構文 */
  OPTIONAL_PROPAGATE,

  /** 失敗なら現在の利用者定義単語から復帰する局所伝播構文 */
  RESULT_PROPAGATE,

  /** 宣言名に隣接する宣言標識 {@code は} */
  DECLARATION_MARKER,

  /** 更新できない名前付き値を宣言する {@code 定数} */
  CONSTANT_DECLARATION,

  /** 更新できる名前付き値を宣言する {@code 変数} */
  VARIABLE_DECLARATION,

  /** ホスト側の接続定義を静的に参照する {@code 論理接続} */
  LOGICAL_CONNECTION_DECLARATION,

  /** ホスト側の有限ファイル登録を静的に参照する {@code 作業領域} */
  WORKSPACE_DECLARATION,

  /** 変数へ値を保存する {@code 入れる} */
  ASSIGNMENT,

  /** 配列リテラルの開始記号（{@code 【}） */
  ARRAY_OPEN,

  /** 配列リテラル内の要素区切り（{@code 、}または{@code ，}） */
  ARRAY_SEPARATOR,

  /** 配列リテラルの終了記号（{@code 】}） */
  ARRAY_CLOSE,

  /** 配列型の型引数開始記号（{@code <}） */
  ARRAY_TYPE_OPEN,

  /** 配列型の型引数終了記号（{@code >}） */
  ARRAY_TYPE_CLOSE,

  /** 結果型・結果構築の2個の型引数を区切るASCIIカンマ */
  RESULT_TYPE_SEPARATOR,

  /** 配列の各要素を順に処理する反復開始語（{@code 各要素について}） */
  ARRAY_LOOP_START,

  /** 全角のスタック効果開き括弧（{@code （}） */
  STACK_OPEN_FULLWIDTH,

  /** 全角のスタック効果閉じ括弧（{@code ）}） */
  STACK_CLOSE_FULLWIDTH,

  /** 半角ASCIIのスタック効果開き括弧（{@code (}） */
  STACK_OPEN_ASCII,

  /** 半角ASCIIのスタック効果閉じ括弧（{@code )}） */
  STACK_CLOSE_ASCII,

  /** スタック効果の入力と出力の区切り記号（{@code --}） */
  STACK_SEPARATOR,

  /** 単語定義の終了記号（{@code 。}） */
  DEFINITION_END,

  /** 定数・変数宣言の終了記号（{@code 。}） */
  DECLARATION_END,

  /** コメント（{@code #} から行末まで。プログラムの実行には影響しない非実行要素） */
  COMMENT;

  /**
   * このトークンがソースファイルあたりのトークン総数制限（250,000トークン）のカウント対象に含まれるかを返します。
   *
   * <p>仕様に基づき、コメント（{@link #COMMENT}）は制限カウントから除外されます。
   *
   * @return カウント対象であれば true、コメント等の非実行トークンであれば false
   */
  public boolean countsTowardLimit() {
    return this != COMMENT;
  }
}
