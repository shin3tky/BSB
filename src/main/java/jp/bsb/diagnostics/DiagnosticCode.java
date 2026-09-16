package jp.bsb.diagnostics;

/**
 * BSB言語処理系のホスト入出力までに定義済みの診断（エラーおよび警告）を一意に識別するコード体系の列挙型です。
 *
 * <p>【コンピュータ科学の観点：一意なエラーコード（Error Codes）】 コンパイラがエラー文字列（メッセージ本文）だけを出力すると、言語処理系のバージョンアップや
 * 国際化（多言語対応）によって文面が変わった際に、自動テストやIDE（統合開発環境）のツール連携が 壊れてしまいます。 そのため、各エラーに {@code E_INVALID_UTF8}
 * のような機械可読な一意識別子（コード）を割り当て、 メッセージカタログと紐付ける設計が広く採用されています。
 */
public enum DiagnosticCode {
  // --- UTF-8 / ファイル読取り段階のエラー ---
  /** ソースファイルサイズが上限（32MiB）を超過している */
  E_SOURCE_SIZE_LIMIT,
  /** 不正なUTF-8バイト列が含まれている */
  E_INVALID_UTF8,

  // --- 字句解析（Lexical）段階のエラー ---
  /** 許可されていないUnicode空白文字（ノーブレークスペース等）が使用されている */
  E_DISALLOWED_WHITESPACE,
  /** 不可視文字（ゼロ幅文字、双方向テキスト制御文字など）が含まれている */
  E_INVISIBLE_CHARACTER,
  /** 識別子の文字数（コードポイント数）が上限（128）を超過している */
  E_IDENTIFIER_TOO_LONG,
  /** 1ファイル内のトークン総数が上限（250,000）を超過している */
  E_TOKEN_LIMIT,
  /** 識別子として無効な文字が含まれている（数字で始まっているなど） */
  E_INVALID_IDENTIFIER,
  /** BSBの文法上予期しない不正な文字が現れた */
  E_UNEXPECTED_CHARACTER,
  /** 数値リテラルの直後に区切り空白がない（例: 123abc） */
  E_MISSING_SEPARATOR,
  /** 数値リテラルの形式が不正（先頭の余分な0、不完全な小数など） */
  E_INVALID_NUMBER_LITERAL,
  /** 数値リテラルの桁数が上限（4,096桁）を超過している */
  E_NUMBER_LIMIT,
  /** 文字列または文字リテラルの閉じ引用符がない */
  E_UNTERMINATED_STRING,
  /** raw正規表現リテラルが閉じられていない */
  E_UNTERMINATED_REGEX_LITERAL,
  /** raw正規表現リテラルに未エスケープの改行がある */
  E_NEWLINE_IN_REGEX_LITERAL,
  /** 正規表現フラグが不明または重複している */
  E_REGEX_FLAG,
  /** 無効なエスケープシーケンス（\z など）が指定されている */
  E_INVALID_ESCAPE,
  /** <code>\\u{...}</code> エスケープで指定されたUnicodeスカラー値が無効（サロゲート等） */
  E_INVALID_UNICODE_SCALAR,
  /** 文字リテラル（'...'）に1文字（1書記素クラスタ）以外が指定されている */
  E_CHARACTER_LENGTH,
  /** 文字列リテラルまたは実行時に生成された文字列のバイトサイズが上限（16MiB）を超過している */
  E_STRING_LIMIT,

  // --- 構文解析（Syntax）段階のエラー ---
  /** スタック効果の括弧の開きと閉じが一致していない（全角と半角の混在） */
  E_MIXED_STACK_PARENTHESES,
  /** スタック効果の入出力区切り記号（--）が欠落している */
  E_EXPECTED_STACK_SEPARATOR,
  /** 定義ヘッダー中など、コメントの記述が禁止されている位置にコメントがある */
  E_COMMENT_NOT_ALLOWED,

  // --- 構文解析（Syntax）段階のエラー（続き） ---
  /** トップレベルで単語定義の「とは」の直前に空白が挟まれている */
  E_DEFINITION_ADJACENCY,
  /** 単語定義の終端（「こと。」）が期待される位置にない */
  E_EXPECTED_WORD_END,
  /** 「こと」の直後に定義終了マーク（「。」）がない */
  E_EXPECTED_DEFINITION_END_MARK,
  /** トップレベルに単語定義以外の文やリテラルが記述されている */
  E_UNEXPECTED_TOP_LEVEL,
  /** 単語定義の総数が上限（10,000）を超過している */
  E_DEFINITION_LIMIT,
  /** 対応する「ならば」がない「さもなければ」が現れた */
  E_UNEXPECTED_ELSE,
  /** 同じ条件分岐に2個目の「さもなければ」が現れた */
  E_DUPLICATE_ELSE,
  /** 条件分岐を閉じる「つぎに」が欠落している */
  E_EXPECTED_IF_END,
  /** 短絡評価ブロックを閉じる「つぎに」が欠落している */
  E_EXPECTED_SHORT_CIRCUIT_END,
  /** 対応する「ならば」がない「つぎに」が現れた */
  E_UNEXPECTED_BLOCK_END,
  /** 対応するループがない「繰り返す」が現れた */
  E_UNEXPECTED_LOOP_END,
  /** ループを閉じる「繰り返す」が欠落している */
  E_EXPECTED_LOOP_END,
  /** 条件ループの条件と本体を分ける「続く間」が欠落している */
  E_EXPECTED_LOOP_CONDITION_SEPARATOR,
  /** 条件ループ外または同じ条件ループの2個目の「続く間」が現れた */
  E_UNEXPECTED_LOOP_SEPARATOR,
  /** 制御構文の入れ子が上限（256段）を超過している */
  E_SYNTAX_DEPTH_LIMIT,
  /** 宣言名と宣言標識「は」の間に空白がある */
  E_DECLARATION_ADJACENCY,
  /** 宣言種別の「定数」または「変数」がない */
  E_EXPECTED_DECLARATION_KIND,
  /** 宣言名と宣言種別の間にコメントがある */
  E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED,
  /** 宣言終端の「。」がない */
  E_EXPECTED_DECLARATION_END,
  /** 対応する宣言がない宣言終端が現れた */
  E_UNEXPECTED_DECLARATION_END,
  /** 代入値を示す助詞「を」がない */
  E_EXPECTED_ASSIGNMENT_VALUE_PARTICLE,
  /** 代入先の名前がない */
  E_EXPECTED_ASSIGNMENT_TARGET,
  /** 代入先を示す助詞「に」がない */
  E_EXPECTED_ASSIGNMENT_TARGET_PARTICLE,
  /** 宣言の初期値に許可されない構文要素がある */
  E_INITIALIZER_ELEMENT_NOT_ALLOWED,
  /** 配列リテラルの先頭・連続・末尾区切りによって空要素ができた */
  E_EXPECTED_ARRAY_ELEMENT,
  /** 配列リテラルの終了記号「】」が欠落している */
  E_EXPECTED_ARRAY_END,
  /** 対応する開始記号がない配列終了記号「】」が現れた */
  E_UNEXPECTED_ARRAY_END,
  /** 配列型の要素型が欠落している */
  E_EXPECTED_ARRAY_ELEMENT_TYPE,
  /** 配列型の終了記号「>」が欠落している */
  E_EXPECTED_ARRAY_TYPE_END,
  /** 任意型の型引数が欠落している */
  E_EXPECTED_OPTIONAL_ELEMENT_TYPE,
  /** 任意型の終了記号「>」が欠落している */
  E_EXPECTED_OPTIONAL_TYPE_END,
  /** 結果型の成功型または失敗型が欠落している */
  E_EXPECTED_RESULT_TYPE_ARGUMENT,
  /** 結果型の成功型と失敗型のASCIIカンマが欠落している */
  E_EXPECTED_RESULT_TYPE_SEPARATOR,
  /** 結果型の型引数個数が2個でない */
  E_RESULT_TYPE_ARGUMENT_COUNT,
  /** 結果型の終了記号「>」が欠落している */
  E_EXPECTED_RESULT_TYPE_END,
  /** 配列要素式に制御構文、宣言、代入などの禁止要素が現れた */
  E_ARRAY_ELEMENT_NOT_ALLOWED,
  /** 論理接続宣言に初期値相当のトークンが指定された */
  E_LOGICAL_CONNECTION_DECLARATION_VALUE,
  /** 論理接続宣言がトップレベル以外に置かれた */
  E_LOGICAL_CONNECTION_DECLARATION_SCOPE,
  /** 接続確認語の静的引数開始記号 {@code <} が欠落した */
  E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START,
  /** 接続確認語の静的引数に論理接続名が欠落した */
  E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT,
  /** 接続確認語の静的引数個数が1個でない */
  E_LOGICAL_CONNECTION_ARGUMENT_COUNT,
  /** 接続確認語の静的引数終了記号「>」が欠落した */
  E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END,
  /** 作業領域宣言に初期値相当のトークンが指定された */
  E_WORKSPACE_DECLARATION_VALUE,
  /** 作業領域宣言がトップレベル以外に置かれた */
  E_WORKSPACE_DECLARATION_SCOPE,
  /** ファイル語の静的引数開始記号 {@code <} が欠落した */
  E_EXPECTED_WORKSPACE_ARGUMENT_START,
  /** ファイル語の静的引数に作業領域名が欠落した */
  E_EXPECTED_WORKSPACE_ARGUMENT,
  /** ファイル語の静的引数個数が1個でない */
  E_WORKSPACE_ARGUMENT_COUNT,
  /** ファイル語の静的引数終了記号 {@code >} が欠落した */
  E_EXPECTED_WORKSPACE_ARGUMENT_END,
  /** HTTP送信語の静的引数開始記号 {@code <} が欠落した */
  E_EXPECTED_HTTP_ARGUMENT_START,
  /** HTTP送信語の第1静的引数に論理接続名が欠落した */
  E_EXPECTED_HTTP_CONNECTION_ARGUMENT,
  /** HTTP送信語の第2静的引数にmethodが欠落した */
  E_EXPECTED_HTTP_METHOD_ARGUMENT,
  /** HTTP送信語の静的引数個数が2個でない */
  E_HTTP_ARGUMENT_COUNT,
  /** HTTP送信語の静的引数終了記号「>」が欠落した */
  E_EXPECTED_HTTP_ARGUMENT_END,

  // --- 名前解決（Name Resolution）段階のエラー ---
  /** 予約語を利用者定義の名前として使用しようとした */
  E_RESERVED_NAME,
  /** 正規化後に同一となる名前を持つ単語が複数回定義されている（二重定義） */
  E_DUPLICATE_NAME,
  /** エントリポイントとなる「メイン」単語が定義されていない */
  E_MISSING_MAIN,
  /** 「メイン」単語のスタック効果が（--）ではない */
  E_INVALID_MAIN_EFFECT,
  /** 参照された単語が定義されていない */
  E_UNDEFINED_WORD,
  /** HTTP送信語のmethodが許可された6値でない */
  E_HTTP_METHOD_INVALID,
  /** 型名や予約語など、呼び出し不可能な名前を単語本体で呼び出そうとした */
  E_NAME_NOT_CALLABLE,
  /** 現在利用できない予約機能が使われた */
  E_FEATURE_NOT_AVAILABLE,
  /** 大域値を初期化完了前に参照した */
  E_REFERENCE_BEFORE_INITIALIZATION,
  /** 局所値を宣言位置より前から参照した */
  E_REFERENCE_BEFORE_DECLARATION,
  /** 終了済みの子または兄弟スコープの名前を参照した */
  E_BINDING_OUT_OF_SCOPE,
  /** 局所宣言が可視な外側の名前を隠そうとした */
  E_NAME_SHADOWING,
  /** 定数へ代入しようとした */
  E_ASSIGN_TO_CONSTANT,
  /** 定数または単語など、変数でない名前を代入先にした */
  E_ASSIGNMENT_TARGET_NOT_VARIABLE,
  /** 宣言されていない変数名を代入先にした */
  E_UNDEFINED_ASSIGNMENT_TARGET,
  /** 大域定数・変数の総数が上限（10,000）を超過した */
  E_GLOBAL_BINDING_LIMIT,
  /** 1単語内の局所定数・変数の総数が上限（1,024）を超過した */
  E_LOCAL_BINDING_LIMIT,
  /** 1プログラム内の定数・変数の総数が上限（65,536）を超過した */
  E_BINDING_LIMIT,
  /** 論理接続宣言の総数が上限（10,000）を超過した */
  E_LOGICAL_CONNECTION_LIMIT,
  /** 静的引数に指定された論理接続が宣言されていない */
  E_UNDECLARED_LOGICAL_CONNECTION,
  /** 論理接続名を静的引数以外の値・語位置で参照した */
  E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED,
  /** 作業領域宣言の総数が上限（10,000）を超過した */
  E_WORKSPACE_DECLARATION_LIMIT,
  /** 静的引数に指定された作業領域が宣言されていない */
  E_UNDECLARED_WORKSPACE,
  /** 作業領域名を静的引数以外の値・語位置で参照した */
  E_WORKSPACE_REFERENCE_NOT_ALLOWED,

  // --- 静的型・スタック効果検査段階のエラー ---
  /** ユーザー定義単語のスタック効果で内部専用の型制約（Tや表示可能）が使われた */
  E_TYPE_CONSTRAINT_NOT_ALLOWED,
  /** 存在しない型名がスタック効果に指定された */
  E_UNKNOWN_TYPE,
  /** 単語の実行に必要な引数がスタックに不足している（スタックアンダーフロー） */
  E_STACK_UNDERFLOW,
  /** スタック上の値の型と、単語が要求する入力型が一致しない */
  E_TYPE_MISMATCH,
  /** 単語本体の実行結果スタックと、宣言されたスタック効果が一致しない */
  E_WORD_EFFECT_MISMATCH,
  /** 「メイン」単語終了時にデータスタックが空になっていない（値が残っている） */
  E_MAIN_STACK_NOT_EMPTY,
  /** 「ならば」が消費する条件値がスタックにない */
  E_CONDITION_STACK_UNDERFLOW,
  /** 「ならば」が消費する値の型が真偽ではない */
  E_CONDITION_TYPE_MISMATCH,
  /** 条件分岐の到達可能な出口でスタックの個数または型が一致しない */
  E_BRANCH_STACK_MISMATCH,
  /** 「または」「かつ」が消費する左辺値がスタックにない */
  E_SHORT_CIRCUIT_LEFT_UNDERFLOW,
  /** 「または」「かつ」が消費する左辺値の型が真偽ではない */
  E_SHORT_CIRCUIT_LEFT_TYPE_MISMATCH,
  /** 短絡評価ブロックの右辺が基準スタックへ真偽値1個を追加していない */
  E_SHORT_CIRCUIT_RIGHT_MISMATCH,
  /** 「回だけ」が消費する反復回数がスタックにない */
  E_REPEAT_COUNT_UNDERFLOW,
  /** 「回だけ」が消費する値の型が整数ではない */
  E_REPEAT_COUNT_TYPE_MISMATCH,
  /** 条件ループの条件計算部が基準スタックと真偽1個を残していない */
  E_LOOP_CONDITION_MISMATCH,
  /** ループの通常終端、脱出、継続で基準スタックと一致しない */
  E_LOOP_STACK_MISMATCH,
  /** ループの外で「打ち切る」が使われた */
  E_BREAK_OUTSIDE_LOOP,
  /** ループの外で「続ける」が使われた */
  E_CONTINUE_OUTSIDE_LOOP,
  /** 「戻る」地点のスタックが単語の宣言出力と一致しない */
  E_RETURN_EFFECT_MISMATCH,
  /** 任意の局所伝播構文が利用者単語または任意出力以外で使われた */
  E_OPTIONAL_PROPAGATION_CONTEXT,
  /** 任意の局所伝播地点のprefixが単語の宣言出力と一致しない */
  E_OPTIONAL_PROPAGATION_EFFECT_MISMATCH,
  /** 結果の局所伝播構文が利用者単語または結果出力以外で使われた */
  E_RESULT_PROPAGATION_CONTEXT,
  /** 結果の局所伝播地点のprefixまたは失敗型が宣言出力と一致しない */
  E_RESULT_PROPAGATION_EFFECT_MISMATCH,
  /** 宣言の初期値が値を作らない */
  E_INITIALIZER_VALUE_MISSING,
  /** 宣言の初期値が複数の値を残す */
  E_INITIALIZER_VALUE_COUNT,
  /** 宣言の初期値から許可されない単語を呼び出した */
  E_INITIALIZER_CALL_NOT_ALLOWED,
  /** 変数へ代入する値がスタックにない */
  E_ASSIGNMENT_STACK_UNDERFLOW,
  /** 変数の宣言型と代入値の型が一致しない */
  E_ASSIGNMENT_TYPE_MISMATCH,
  /** bare「配列」が具体型として使われた */
  E_ARRAY_ELEMENT_TYPE_REQUIRED,
  /** bare「任意」が具体型として使われた */
  E_OPTIONAL_ELEMENT_TYPE_REQUIRED,
  /** bare結果型または型引数なしの結果構築が使われた */
  E_RESULT_TYPE_ARGUMENTS_REQUIRED,
  /** 推論による型構築子深さが256段を超えた */
  E_TYPE_DEPTH_LIMIT,
  /** 値なし任意値から内包値を取り出そうとした */
  E_OPTIONAL_VALUE_ABSENT,
  /** 結果値の状態と成功／失敗の取出し側が一致しない */
  E_RESULT_STATE_MISMATCH,
  /** 空配列リテラルの要素型を決定できない */
  E_EMPTY_ARRAY_TYPE_REQUIRED,
  /** 配列要素式が値を残さない */
  E_ARRAY_ELEMENT_VALUE_MISSING,
  /** 配列要素式が複数の値を残す */
  E_ARRAY_ELEMENT_VALUE_COUNT,
  /** 配列リテラル内の要素型が一致しない */
  E_ARRAY_ELEMENT_TYPE_MISMATCH,
  /** 配列要素式から許可されない単語を呼び出した */
  E_ARRAY_ELEMENT_CALL_NOT_ALLOWED,
  /** 配列で未対応の入れ子配列が使われた */
  E_NESTED_ARRAY_NOT_AVAILABLE,
  /** 具体型だが数値演算の配列要素型として許可されていない */
  E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
  /** 配列反復へ渡す値がスタックにない */
  E_ARRAY_LOOP_INPUT_UNDERFLOW,
  /** 配列反復へ渡した値が配列型でない */
  E_ARRAY_LOOP_INPUT_TYPE_MISMATCH,
  /** 正規表現の規範部分集合として構文が不正 */
  E_REGEX_SYNTAX,
  /** 正規表現に後方参照や先読みなどの未対応構文がある */
  E_REGEX_UNSUPPORTED_CONSTRUCT,
  /** raw正規表現パターンが65,536 UTF-8バイトを超えた */
  E_REGEX_PATTERN_LIMIT,
  /** 正規表現のキャプチャが64個を超えた */
  E_REGEX_CAPTURE_LIMIT,
  /** 正規表現のコンパイル命令が65,536個を超えた */
  E_REGEX_PROGRAM_LIMIT,

  // --- 中間表現（IR）生成段階のエラー ---
  /** 生成されたIR命令の総数が上限（250,000命令）を超過している */
  E_IR_LIMIT,

  // --- 実行時（Runtime）段階のエラー・資源制限超過 ---
  /** 整数演算の結果の桁数が上限（65,536桁）を超過した */
  E_INTEGER_RESULT_LIMIT,
  /** データスタック上の要素数が上限（65,536）を超過した（スタックオーバーフロー） */
  E_DATA_STACK_LIMIT,
  /** 単語の呼出スタックの深さが上限（1,024フレーム）を超過した（無限再帰等） */
  E_CALL_STACK_LIMIT,
  /** プログラムの実行命令数が上限（10,000,000命令）を超過した（無限再帰等） */
  E_INSTRUCTION_LIMIT,
  /** プログラムの実行時間が上限（30秒）を超過した */
  E_EXECUTION_TIMEOUT,
  /** 標準出力への累積出力バイト数が上限（64MiB）を超過した */
  E_OUTPUT_LIMIT,
  /** 回数ループへ負の反復回数が渡された */
  E_NEGATIVE_REPEAT_COUNT,
  /** 配列の添字が有効範囲外にある */
  E_ARRAY_INDEX_OUT_OF_BOUNDS,
  /** 配列の半開区間が有効条件を満たさない */
  E_ARRAY_RANGE_OUT_OF_BOUNDS,
  /** 配列長が65,536要素の上限を超えた */
  E_ARRAY_LENGTH_LIMIT,
  /** 二次元配列の論理葉要素数が1,000,000要素の上限を超えた */
  E_ARRAY_NESTED_ELEMENT_LIMIT,
  /** 1実行の配列要素構築単位が上限を超えた */
  E_ARRAY_CONSTRUCTION_LIMIT,
  /** 1実行の配列要素処理単位が上限を超えた */
  E_ARRAY_ELEMENT_OPERATION_LIMIT,
  /** 整数または小数の除算で除数が0だった */
  E_DIVISION_BY_ZERO,
  /** 明示した小数除算の有効桁数が1から4,096の範囲外だった */
  E_DIVISION_PRECISION_OUT_OF_RANGE,
  /** 小数部を持つ値を正確に整数へ変換しようとした */
  E_DECIMAL_NOT_INTEGER,
  /** 小数演算結果の有効桁数が65,536桁を超えた */
  E_DECIMAL_RESULT_PRECISION_LIMIT,
  /** 小数リテラルまたは小数演算結果のスケール絶対値が65,536を超えた */
  E_DECIMAL_SCALE_LIMIT,
  /** 書記素単位の文字列位置が範囲外 */
  E_STRING_INDEX_OUT_OF_BOUNDS,
  /** 書記素単位の文字列半開区間が不正 */
  E_STRING_RANGE_OUT_OF_BOUNDS,
  /** コードポイント単位の文字列位置が範囲外 */
  E_CODE_POINT_INDEX_OUT_OF_BOUNDS,
  /** コードポイント単位の文字列半開区間が不正 */
  E_CODE_POINT_RANGE_OUT_OF_BOUNDS,
  /** 通常置換の検索文字列が空 */
  E_EMPTY_SEARCH_TEXT,
  /** 通常分割の区切り文字列が空 */
  E_EMPTY_DELIMITER,
  /** 文字列が整数の正規入力文法に一致しない */
  E_INTEGER_TEXT_INVALID,
  /** 文字列が小数の正規入力文法に一致しない */
  E_DECIMAL_TEXT_INVALID,
  /** 数値変換元文字列の数字が4,096桁を超えた */
  E_NUMERIC_TEXT_DIGIT_LIMIT,
  /** 生成文字列が16 MiBを超えた */
  E_STRING_UTF8_LIMIT,
  /** 抽出対象に正規表現の一致がない */
  E_REGEX_NO_MATCH,
  /** 指定した名前付きキャプチャが存在しない */
  E_REGEX_GROUP_NOT_FOUND,
  /** 名前付きキャプチャが今回の一致に参加しなかった */
  E_REGEX_GROUP_UNMATCHED,
  /** 正規表現置換テンプレートが不正 */
  E_REGEX_REPLACEMENT_TEMPLATE,
  /** 1回の正規表現呼出しが照合量上限を超えた */
  E_REGEX_WORK_LIMIT,
  /** 1回のrunの正規表現累積照合量が上限を超えた */
  E_REGEX_TOTAL_WORK_LIMIT,
  /** 到達した組み込み語に必要な実行環境能力がない */
  E_CAPABILITY_UNAVAILABLE,
  /** 実行環境能力が処理を完了できなかった */
  E_CAPABILITY_FAILURE,
  /** 入力行が厳密なUTF-8でない */
  E_INPUT_UTF8,
  /** 1入力行が16 MiBを超えた */
  E_INPUT_LINE_LIMIT,
  /** 1実行の生入力累積が64 MiBを超えた */
  E_INPUT_TOTAL_LIMIT,
  /** 行でない入力結果から行を取り出そうとした */
  E_INPUT_RESULT_NOT_LINE,
  /** プログラム標準エラーが64 MiBを超えた */
  E_ERROR_OUTPUT_LIMIT,
  /** プログラム指定終了コードが0から255の範囲外だった */
  E_EXIT_CODE_RANGE,
  /** 起動引数が65,536個を超えた */
  E_ARGUMENT_COUNT_LIMIT,
  /** 起動引数のUTF-8累積長が64 MiBを超えた */
  E_ARGUMENT_TOTAL_LIMIT,
  /** 埋込みホストが渡したプログラム識別情報が不正だった */
  E_PROGRAM_METADATA_INVALID,
  /** 1回の待機時間が0から1日の範囲外だった */
  E_WAIT_DURATION_RANGE,
  /** 1実行の待機要求累積が7日を超えた */
  E_WAIT_TOTAL_LIMIT,
  /** 待機要求が実行環境に取り消された */
  E_WAIT_CANCELLED,
  /** 日時またはUTCオフセットが表現範囲外だった */
  E_DATETIME_RANGE,
  /** JSON文字列が規範文法に一致しなかった */
  E_JSON_SYNTAX,
  /** JSONオブジェクト内で同じ展開後キーが重複した */
  E_JSON_DUPLICATE_KEY,
  /** JSON数値が桁数・精度・スケール上限を超えた */
  E_JSON_NUMBER_LIMIT,
  /** JSON容器の入れ子が256段を超えた */
  E_JSON_DEPTH_LIMIT,
  /** 1 JSON値の値ノード数が250,000を超えた */
  E_JSON_NODE_LIMIT,
  /** 1 JSON配列が65,536要素を超えた */
  E_JSON_ARRAY_LENGTH_LIMIT,
  /** 1 JSONオブジェクトが65,536メンバーを超えた */
  E_JSON_OBJECT_MEMBER_LIMIT,
  /** JSON値の実行時種別が操作の要求と一致しなかった */
  E_JSON_KIND_MISMATCH,
  /** JSONオブジェクトの必須キーが存在しなかった */
  E_JSON_KEY_NOT_FOUND,
  /** JSON Pointerの文字列表現がRFC 6901に一致しなかった */
  E_JSON_POINTER_SYNTAX,
  /** JSONオブジェクト一括構築のキー数と値数が一致しなかった */
  E_JSON_OBJECT_BUILD_LENGTH_MISMATCH,
  /** JSON配列の添字が有効範囲外だった */
  E_JSON_INDEX_OUT_OF_BOUNDS,
  /** JSON配列の半開区間が不正だった */
  E_JSON_RANGE_OUT_OF_BOUNDS,
  /** JSON直列化結果が16 MiBを超えた */
  E_JSON_OUTPUT_LIMIT,
  /** 1実行のJSON構築単位が上限を超えた */
  E_JSON_CONSTRUCTION_LIMIT,
  /** 1実行のJSON作業単位が上限を超えた */
  E_JSON_WORK_LIMIT,
  /** 対象の論理接続がホストに設定されていない */
  E_LOGICAL_CONNECTION_NOT_CONFIGURED,
  /** 対象の論理接続の利用をホストが拒否した */
  E_LOGICAL_CONNECTION_ACCESS_DENIED,
  /** 対象の論理接続設定が規範を満たさない */
  E_LOGICAL_CONNECTION_CONFIGURATION_INVALID,
  /** 論理ファイル名が可搬名規則を満たさない */
  E_LOGICAL_FILE_NAME_INVALID,
  /** 対象の作業領域がホストに設定されていない */
  E_WORKSPACE_NOT_CONFIGURED,
  /** 対象の作業領域操作をホストが拒否した */
  E_WORKSPACE_ACCESS_DENIED,
  /** 対象の作業領域設定が規範を満たさない */
  E_WORKSPACE_CONFIGURATION_INVALID,
  /** 論理ファイルが作業領域へ登録されていない */
  E_WORKSPACE_FILE_NOT_MAPPED,
  /** 論理ファイルの操作が登録で許可されていない */
  E_WORKSPACE_FILE_ACCESS_DENIED,
  /** 1実行のファイル操作回数が上限を超えた */
  E_FILE_OPERATION_LIMIT,
  /** 1実行のファイル読取成功累積が上限を超えた */
  E_FILE_READ_TOTAL_LIMIT,
  /** 1実行のファイル書込試行累積が上限を超えた */
  E_FILE_WRITE_TOTAL_LIMIT,
  /** ファイル操作中に全実行が取り消された */
  E_FILE_CANCELLED,
  /** 非空の直列化入力表に0セル行がある */
  E_DELIMITED_TEXT_EMPTY_ROW,
  /** 直列化入力表の行ごとの列数が一致しない */
  E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH,
  /** 直列化入力表のセルが区切りテキスト規則に適合しない */
  E_DELIMITED_TEXT_CELL_INVALID,
  /** 区切りテキストの候補出力が16 MiBを超える */
  E_DELIMITED_TEXT_OUTPUT_LIMIT,
  /** 1実行の区切りテキスト作業累積が上限を超える */
  E_DELIMITED_TEXT_WORK_LIMIT,
  /** JSON形状setterの入力がオブジェクト形状でなかった */
  E_JSON_SHAPE_OBJECT_REQUIRED,
  /** 1個のJSON形状が65,536ノードを超えた */
  E_JSON_SHAPE_NODE_LIMIT,
  /** JSON形状の入れ子が256段を超えた */
  E_JSON_SHAPE_DEPTH_LIMIT,
  /** 1実行のJSON形状検証作業累積が上限を超えた */
  E_JSON_SHAPE_WORK_LIMIT,
  /** JSON形状失敗の公開パスが65,536 UTF-8バイトを超えた */
  E_JSON_SHAPE_PATH_LIMIT,
  /** バイト列の半開区間が有効条件を満たさない */
  E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS,
  /** 1個のバイト列が64 MiBを超える */
  E_BYTE_SEQUENCE_SIZE_LIMIT,
  /** 1実行のバイト列構築累積が128 MiBを超える */
  E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT,
  /** 1実行のバイト列作業累積が256 MiBを超える */
  E_BYTE_SEQUENCE_WORK_LIMIT,
  /** HTTP要求の相対経路が規範を満たさない */
  E_HTTP_PATH_INVALID,
  /** HTTP要求の相対経路が上限を超える */
  E_HTTP_PATH_LIMIT,
  /** HTTP要求の問い合わせ項目数または符号化長が上限を超える */
  E_HTTP_QUERY_LIMIT,
  /** HTTP要求のヘッダー名が規範を満たさない */
  E_HTTP_HEADER_NAME_INVALID,
  /** HTTP要求のヘッダー値が規範を満たさない */
  E_HTTP_HEADER_VALUE_INVALID,
  /** HTTP要求へ予約ヘッダーを設定しようとした */
  E_HTTP_HEADER_RESERVED,
  /** HTTP要求のヘッダー数または総量が上限を超える */
  E_HTTP_HEADER_LIMIT,
  /** HTTP metadata構築の累積上限を超える */
  E_HTTP_METADATA_CONSTRUCTION_LIMIT,
  /** 最終対象URIが上限を超える */
  E_HTTP_TARGET_URI_LIMIT,
  /** 解決済み接続方針が静的methodを許可しない */
  E_HTTP_METHOD_NOT_ALLOWED,
  /** GETまたはHEADへ本文を設定した */
  E_HTTP_BODY_NOT_ALLOWED,
  /** 要求本文が接続方針上限を超える */
  E_HTTP_REQUEST_SIZE_LIMIT,
  /** HTTP送信回数上限を超える */
  E_HTTP_SEND_LIMIT,
  /** HTTP要求本文試行累積を超える */
  E_HTTP_REQUEST_TOTAL_LIMIT,
  /** HTTP応答本文受信累積を超える */
  E_HTTP_RESPONSE_TOTAL_LIMIT,
  /** 接続の認証方式がHTTPSで未対応 */
  E_HTTP_AUTHENTICATION_UNSUPPORTED,
  /** APIキー資格情報が設定されていない */
  E_HTTP_CREDENTIAL_NOT_CONFIGURED,
  /** APIキー資格情報へのアクセスが拒否された */
  E_HTTP_CREDENTIAL_ACCESS_DENIED,
  /** APIキー資格情報が規範を満たさない */
  E_HTTP_CREDENTIAL_INVALID,
  /** HTTP送信中に実行が取り消された */
  E_HTTP_CANCELLED,
  /** HTTP再試行方針が不正だった */
  E_HTTP_RETRY_POLICY_INVALID,
  /** HTTP信頼性待機の累積上限を超えた */
  E_HTTP_RETRY_WAIT_LIMIT,
  /** HTTP再試行に必要な待機能力がなかった */
  E_HTTP_RETRY_WAIT_UNAVAILABLE,
  /** HTTP再試行の待機能力が失敗した */
  E_HTTP_RETRY_WAIT_FAILURE,
  /** HTTP再試行の待機が取り消された */
  E_HTTP_RETRY_WAIT_CANCELLED,
  /** Retry-After HTTP-dateに必要な壁時計能力がなかった */
  E_HTTP_RETRY_CLOCK_UNAVAILABLE,
  /** Retry-After HTTP-dateの壁時計能力が失敗した */
  E_HTTP_RETRY_CLOCK_FAILURE,
  /** 必須の最終失敗記録能力がなかった */
  E_HTTP_FINAL_FAILURE_UNAVAILABLE,
  /** 最終失敗記録能力が失敗した */
  E_HTTP_FINAL_FAILURE_FAILURE,
  /** 最終失敗記録が取り消された */
  E_HTTP_FINAL_FAILURE_CANCELLED,
  /** 最終失敗記録数が上限を超えた */
  E_HTTP_FINAL_FAILURE_LIMIT,
  /** form URL encode入力の行が名前・値の2要素でなかった */
  E_HTTP_FORM_ROW_WIDTH,
  /** form URL encode入力の項目数が上限を超えた */
  E_HTTP_FORM_ITEM_LIMIT,

  // --- 診断システム自体の制限 ---
  /** 1回の解析で検出された診断数が上限（100件）に到達したため以降を抑止 */
  E_DIAGNOSTIC_LIMIT,

  // --- 警告（Warning） ---
  /** 本体の先頭・末尾、連続助詞、実行要素と隣接しない位置などに助詞が配置されている */
  W_PARTICLE_POSITION,
  /** 別の既存の識別子と見た目が非常に紛らわしい（ホモグリフ・混同可能文字） */
  W_CONFUSABLE_IDENTIFIER,
  /** 無条件の制御移行より後ろに、実行されない処理がある */
  W_UNREACHABLE_CODE
}
