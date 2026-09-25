package jp.bsb.stdlib;

/**
 * 組み込み単語の実行処理を表す、安定した内部識別子です。
 *
 * <p>【コンピュータ科学の観点：データと動作の対応】実行器が日本語の単語名を比較して処理を選ぶと、改名や別名追加で壊れやすくなります。
 * 辞書項目にこの列挙値を持たせることで、型情報・副作用情報・実装の対応を1か所で確定します。
 */
public enum BuiltinOperation {
  /** 同型数値の加算。 */
  ADD,
  /** 同型数値の減算。 */
  SUBTRACT,
  /** 同型数値の乗算。 */
  MULTIPLY,
  /** 同型値の等値比較。 */
  EQUALS,
  /** 第1数値が第2数値より小さいかを判定。 */
  LESS_THAN,
  /** 整数の床除算による商。 */
  INTEGER_QUOTIENT,
  /** 整数の床除算による剰余。 */
  INTEGER_REMAINDER,
  /** 整数の床除算による商と剰余。 */
  INTEGER_QUOTIENT_AND_REMAINDER,
  /** 既定精度の小数除算。 */
  DECIMAL_DIVIDE,
  /** 明示精度と丸め方法を使う小数除算。 */
  PRECISION_DECIMAL_DIVIDE,
  /** 同型数値の絶対値。 */
  ABSOLUTE,
  /** 同型数値の最小値。 */
  MINIMUM,
  /** 同型数値の最大値。 */
  MAXIMUM,
  /** 数値の符号を整数で取得。 */
  SIGN,
  /** 数値を同型の上下限へ収める。 */
  CLAMP,
  /** 2整数の非負の最大公約数。 */
  GREATEST_COMMON_DIVISOR,
  /** 2整数の非負の最小公倍数。 */
  LEAST_COMMON_MULTIPLE,
  /** 数値の非負整数乗。 */
  INTEGER_POWER,
  /** 整数が偶数かの判定。 */
  IS_EVEN,
  /** 整数が奇数かの判定。 */
  IS_ODD,
  /** 小数を指定した小数桁数へ丸める。 */
  ROUND_TO_DECIMAL_PLACES,
  /** 小数を指定した有効桁数へ丸める。 */
  ROUND_TO_SIGNIFICANT_DIGITS,
  /** 整数から小数への正確な変換。 */
  INTEGER_TO_DECIMAL,
  /** 小数から整数への正確な変換。 */
  DECIMAL_TO_INTEGER,
  /** 小数から整数への明示丸め。 */
  ROUND_TO_INTEGER,
  /** 型付きの丸め方法組み込み値。 */
  ROUNDING_MODE_VALUE,
  /** 2文字列の連結。 */
  STRING_CONCAT,
  /** Unicodeスカラー値順の文字列比較。 */
  STRING_COMPARE,
  /** Unicode既定完全大文字写像。 */
  STRING_TO_UPPER_CASE,
  /** Unicode既定完全小文字写像。 */
  STRING_TO_LOWER_CASE,
  /** Unicode既定完全ケースフォールド後のスカラー値順比較。 */
  STRING_CASE_INSENSITIVE_COMPARE,
  /** Unicode White_Spaceに基づく前後空白除去。 */
  STRING_TRIM,
  /** 書記素クラスタ数。 */
  GRAPHEME_LENGTH,
  /** 書記素クラスタ位置の文字取得。 */
  GRAPHEME_GET,
  /** 書記素クラスタ半開区間の文字列取得。 */
  GRAPHEME_SLICE,
  /** 書記素クラスタ位置を返す文字列検索。 */
  GRAPHEME_FIND,
  /** 書記素境界に一致する文字列の全置換。 */
  STRING_REPLACE,
  /** 書記素境界に一致する文字列分割。 */
  STRING_SPLIT,
  /** Unicodeコードポイント数。 */
  CODE_POINT_LENGTH,
  /** コードポイント位置の文字取得。 */
  CODE_POINT_GET,
  /** コードポイント半開区間の文字列取得。 */
  CODE_POINT_SLICE,
  /** コードポイント位置を返す文字列検索。 */
  CODE_POINT_FIND,
  /** 整数から正規文字列への変換。 */
  INTEGER_TO_STRING,
  /** 小数から正規文字列への変換。 */
  DECIMAL_TO_STRING,
  /** ASCII文法の文字列から整数への変換。 */
  STRING_TO_INTEGER,
  /** ASCII文法の文字列から小数への変換。 */
  STRING_TO_DECIMAL,
  /** 正規表現の完全一致。 */
  REGEX_FULL_MATCH,
  /** 正規表現による部分一致。 */
  REGEX_CONTAINS,
  /** 正規表現の最初の一致取得。 */
  REGEX_FIRST,
  /** 正規表現の名前付きキャプチャ取得。 */
  REGEX_NAMED_GROUP,
  /** 正規表現による全置換。 */
  REGEX_REPLACE,
  /** 正規表現による文字列分割。 */
  REGEX_SPLIT,
  /** 要素型が辞書出力型で確定した空配列値。 */
  EMPTY_ARRAY,
  /** 配列の要素数。 */
  ARRAY_LENGTH,
  /** 配列の添字参照。 */
  ARRAY_GET,
  /** 配列の半開区間取得。 */
  ARRAY_SLICE,
  /** 配列の要素置換。 */
  ARRAY_REPLACE,
  /** 配列の末尾追加。 */
  ARRAY_APPEND,
  /** 2配列の連結。 */
  ARRAY_CONCAT,
  /** 配列の先頭追加。 */
  ARRAY_PREPEND,
  /** 配列の逆順化。 */
  ARRAY_REVERSE,
  /** 配列の要素包含判定。 */
  ARRAY_CONTAINS,
  /** 配列内の最初の要素位置検索。 */
  ARRAY_FIND,
  /** 配列の空判定。 */
  ARRAY_IS_EMPTY,
  /** 配列先頭の安全な取得。 */
  ARRAY_FIRST_OPTIONAL,
  /** 配列末尾の安全な取得。 */
  ARRAY_LAST_OPTIONAL,
  /** 配列先頭の削除。 */
  ARRAY_DELETE_FIRST,
  /** 配列末尾の削除。 */
  ARRAY_DELETE_LAST,
  /** 配列の半開区間削除。 */
  ARRAY_DELETE_RANGE,
  /** 配列内の等しい要素数。 */
  ARRAY_COUNT,
  /** 配列の指定位置への挿入。 */
  ARRAY_INSERT,
  /** 配列の指定位置の削除。 */
  ARRAY_DELETE_AT,
  /** 配列の指定位置以降の検索。 */
  ARRAY_FIND_FROM,
  /** 配列の順序を保つ重複除去。 */
  ARRAY_UNIQUE,
  /** 同じ値を指定数並べた配列の構築。 */
  ARRAY_REPEAT_VALUE,
  /** 配列の指定位置の安全な取得。 */
  ARRAY_GET_OPTIONAL,
  /** 二次元配列の指定列の安全な取得。 */
  ARRAY_COLUMN_OPTIONAL,
  /** 改行なし表示。 */
  DISPLAY,
  /** 表示後にLFを出力。 */
  DISPLAY_LINE,
  /** LFだけを出力。 */
  NEWLINE,
  /** 一行入力。 */
  READ_LINE,
  /** 入力結果が行かを判定。 */
  INPUT_IS_LINE,
  /** 入力結果が終端かを判定。 */
  INPUT_IS_END,
  /** 入力結果が取消かを判定。 */
  INPUT_IS_CANCEL,
  /** 行の入力結果から文字列を取得。 */
  INPUT_TAKE_LINE,
  /** 入力結果を破棄。 */
  INPUT_DROP,
  /** 改行なし標準エラー表示。 */
  ERROR_DISPLAY,
  /** 標準エラー表示後にLFを出力。 */
  ERROR_DISPLAY_LINE,
  /** 標準エラーへLFだけを出力。 */
  ERROR_NEWLINE,
  /** プログラム指定終了。 */
  PROGRAM_EXIT,
  /** 起動引数配列の取得。 */
  PROCESS_ARGUMENTS,
  /** プログラム論理名の取得。 */
  PROGRAM_NAME,
  /** プログラム場所URIの取得。 */
  PROGRAM_LOCATION,
  /** 指定ミリ秒の待機。 */
  SLEEP,
  /** 実行開始からの単調ミリ秒取得。 */
  MONOTONIC_MILLISECONDS,
  /** 現在日時の取得。 */
  WALL_TIME,
  /** 日時の固定ASCII文字列化。 */
  DATE_TIME_TO_STRING,
  /** JSON null値の構築。 */
  JSON_NULL,
  /** 空JSON配列の構築。 */
  JSON_EMPTY_ARRAY,
  /** 空JSONオブジェクトの構築。 */
  JSON_EMPTY_OBJECT,
  /** JSON文字列の解析。 */
  JSON_PARSE,
  /** JSON値の直列化。 */
  JSON_SERIALIZE,
  /** BSB真偽からJSON真偽への変換。 */
  BOOLEAN_TO_JSON,
  /** BSB整数からJSON整数への変換。 */
  INTEGER_TO_JSON,
  /** BSB小数からJSON小数への変換。 */
  DECIMAL_TO_JSON,
  /** BSB文字列からJSON文字列への変換。 */
  STRING_TO_JSON,
  /** JSON真偽からBSB真偽への変換。 */
  JSON_TO_BOOLEAN,
  /** JSON整数からBSB整数への変換。 */
  JSON_TO_INTEGER,
  /** JSON小数からBSB小数への変換。 */
  JSON_TO_DECIMAL,
  /** JSON文字列からBSB文字列への変換。 */
  JSON_TO_STRING,
  /** JSON null判定。 */
  JSON_IS_NULL,
  /** JSON真偽判定。 */
  JSON_IS_BOOLEAN,
  /** JSON整数判定。 */
  JSON_IS_INTEGER,
  /** JSON小数判定。 */
  JSON_IS_DECIMAL,
  /** JSON文字列判定。 */
  JSON_IS_STRING,
  /** JSON配列判定。 */
  JSON_IS_ARRAY,
  /** JSONオブジェクト判定。 */
  JSON_IS_OBJECT,
  /** JSON配列からBSB JSON配列への変換。 */
  JSON_TO_ARRAY,
  /** BSB JSON配列からJSON配列への変換。 */
  ARRAY_TO_JSON,
  /** JSON配列の長さ取得。 */
  JSON_ARRAY_LENGTH,
  /** JSON配列の添字参照。 */
  JSON_ARRAY_GET,
  /** JSON配列の半開区間取得。 */
  JSON_ARRAY_SLICE,
  /** JSON配列の要素置換。 */
  JSON_ARRAY_REPLACE,
  /** JSON配列の末尾追加。 */
  JSON_ARRAY_APPEND,
  /** JSONオブジェクトのメンバー数取得。 */
  JSON_OBJECT_SIZE,
  /** JSONオブジェクトのキー一覧取得。 */
  JSON_OBJECT_KEYS,
  /** JSONオブジェクトのキー存在判定。 */
  JSON_OBJECT_CONTAINS_KEY,
  /** JSONオブジェクトの必須値取得。 */
  JSON_OBJECT_GET_REQUIRED,
  /** JSONオブジェクトへのキー設定。 */
  JSON_OBJECT_SET,
  /** JSONオブジェクトからのキー削除。 */
  JSON_OBJECT_DELETE,
  /** RFC 6901 JSON Pointerによる任意参照。 */
  JSON_POINTER_GET_OPTIONAL,
  /** キー配列と値配列からのJSONオブジェクト一括構築。 */
  JSON_OBJECT_BUILD,
  /** 値を値あり任意値へ包む。 */
  OPTIONAL_WRAP,
  /** 任意値の値あり状態を判定する。 */
  OPTIONAL_PREDICATE,
  /** 値あり任意値から内包値を取り出す。 */
  OPTIONAL_UNWRAP,
  /** 任意値を状態にかかわらず破棄する。 */
  OPTIONAL_DROP,
  /** JSONオブジェクトから任意値を取得する。 */
  JSON_OBJECT_GET_OPTIONAL,
  /** 成功結果の構築。 */
  RESULT_SUCCESS_WRAP,
  /** 失敗結果の構築。 */
  RESULT_FAILURE_WRAP,
  /** 成功状態の判定。 */
  RESULT_IS_SUCCESS,
  /** 失敗状態の判定。 */
  RESULT_IS_FAILURE,
  /** 成功値の取出し。 */
  RESULT_SUCCESS_UNWRAP,
  /** 失敗値の取出し。 */
  RESULT_FAILURE_UNWRAP,
  /** 結果値の破棄。 */
  RESULT_DROP,
  /** JSON文字列の回復可能な解析。 */
  JSON_PARSE_RESULT,
  /** JSON解析失敗種別の取出し。 */
  JSON_PARSE_FAILURE_KIND,
  /** JSON解析失敗バイト位置の取出し。 */
  JSON_PARSE_FAILURE_OFFSET,
  /** JSON解析失敗行の取出し。 */
  JSON_PARSE_FAILURE_LINE,
  /** JSON解析失敗列の取出し。 */
  JSON_PARSE_FAILURE_COLUMN,
  /** 静的に指定された論理接続の設定と利用可否を確認する。 */
  LOGICAL_CONNECTION_CHECK,
  /** 空の不変バイト列を作る。 */
  EMPTY_BYTE_SEQUENCE,
  /** バイト列の論理バイト数を返す。 */
  BYTE_SEQUENCE_LENGTH,
  /** バイト列の半開区間を返す。 */
  BYTE_SEQUENCE_SLICE,
  /** 文字列をBOMなしUTF-8へ符号化する。 */
  STRING_TO_UTF8_BYTES,
  /** バイト列を厳密UTF-8復号し結果で返す。 */
  UTF8_BYTES_TO_STRING_RESULT,
  /** UTF-8復号失敗の種類を返す。 */
  UTF8_DECODE_FAILURE_KIND,
  /** UTF-8復号失敗のバイト位置を返す。 */
  UTF8_DECODE_FAILURE_OFFSET,
  /** バイト列を規範Base64文字列へ符号化する。 */
  BYTE_SEQUENCE_TO_BASE64,
  /** Base64文字列を厳密復号し結果で返す。 */
  BASE64_TO_BYTE_SEQUENCE_RESULT,
  /** Base64復号失敗の種類を返す。 */
  BASE64_DECODE_FAILURE_KIND,
  /** Base64復号失敗の文字位置を返す。 */
  BASE64_DECODE_FAILURE_OFFSET,
  /** 空の不変HTTP要求を作る。 */
  EMPTY_HTTP_REQUEST,
  /** HTTP要求の相対経路を置換する。 */
  HTTP_REQUEST_SET_PATH,
  /** HTTP要求の問い合わせ項目を末尾へ追加する。 */
  HTTP_REQUEST_ADD_QUERY,
  /** HTTP要求の同名ヘッダーを置換する。 */
  HTTP_REQUEST_SET_HEADER,
  /** HTTP要求の本文を規範JSONのUTF-8へ置換する。 */
  HTTP_REQUEST_SET_JSON_BODY,
  /** HTTP要求の本文を文字列のUTF-8へ置換する。 */
  HTTP_REQUEST_SET_STRING_BODY,
  /** HTTP要求の本文を不変バイト列へ置換する。 */
  HTTP_REQUEST_SET_BYTE_BODY,
  /** 静的接続・methodでHTTP要求を同期送信する。 */
  HTTP_SEND,
  /** HTTP応答の最終状態コードを返す。 */
  HTTP_RESPONSE_STATUS,
  /** HTTP応答の同名ヘッダー値一覧を返す。 */
  HTTP_RESPONSE_HEADER_VALUES,
  /** HTTP応答の完全本文を返す。 */
  HTTP_RESPONSE_BODY,
  /** HTTP送信失敗の安定種類を返す。 */
  HTTP_SEND_FAILURE_KIND,
  /** HTTP要求を残して本文の有無を返す。 */
  HTTP_REQUEST_HAS_BODY,
  /** 静的作業領域から論理ファイル全体を読む。 */
  FILE_READ,
  /** 静的作業領域の論理ファイル全体を原子的に置換する。 */
  FILE_WRITE,
  /** ファイル読取失敗の安定種類を返す。 */
  FILE_READ_FAILURE_KIND,
  /** ファイル書込失敗の安定種類を返す。 */
  FILE_WRITE_FAILURE_KIND,
  /** CSV文字列を文字列表へ回復可能に解析する。 */
  CSV_PARSE_TABLE,
  /** TSV文字列を文字列表へ回復可能に解析する。 */
  TSV_PARSE_TABLE,
  /** 文字列表を規範CSV文字列へ変換する。 */
  TABLE_TO_CSV,
  /** 文字列表を規範TSV文字列へ変換する。 */
  TABLE_TO_TSV,
  /** 区切りテキスト解析失敗種別の取出し。 */
  DELIMITED_TEXT_PARSE_FAILURE_KIND,
  /** 区切りテキスト解析失敗バイト位置の取出し。 */
  DELIMITED_TEXT_PARSE_FAILURE_OFFSET,
  /** 区切りテキスト解析失敗行の取出し。 */
  DELIMITED_TEXT_PARSE_FAILURE_LINE,
  /** 区切りテキスト解析失敗列の取出し。 */
  DELIMITED_TEXT_PARSE_FAILURE_COLUMN,
  /** JSON null葉形状の構築。 */
  JSON_SHAPE_NULL,
  /** JSON真偽葉形状の構築。 */
  JSON_SHAPE_BOOLEAN,
  /** JSON整数葉形状の構築。 */
  JSON_SHAPE_INTEGER,
  /** JSON小数葉形状の構築。 */
  JSON_SHAPE_DECIMAL,
  /** JSON文字列葉形状の構築。 */
  JSON_SHAPE_STRING,
  /** JSON配列形状の構築。 */
  JSON_SHAPE_ARRAY,
  /** 空JSONオブジェクト形状の構築。 */
  JSON_SHAPE_EMPTY_OBJECT,
  /** JSONオブジェクト形状への必須キー設定。 */
  JSON_SHAPE_SET_REQUIRED,
  /** JSONオブジェクト形状への任意キー設定。 */
  JSON_SHAPE_SET_OPTIONAL,
  /** JSON形状のnull許容化。 */
  JSON_SHAPE_NULLABLE,
  /** JSON値の形状検証。 */
  JSON_SHAPE_VALIDATE,
  /** JSON形状失敗種類の取出し。 */
  JSON_SHAPE_FAILURE_KIND,
  /** JSON形状失敗pathの取出し。 */
  JSON_SHAPE_FAILURE_PATH,
  /** JSON形状失敗の期待種類の取出し。 */
  JSON_SHAPE_FAILURE_EXPECTED_KIND,
  /** JSON形状失敗の実際種類の取出し。 */
  JSON_SHAPE_FAILURE_ACTUAL_KIND,
  /** 文字列二次元配列をapplication/x-www-form-urlencodedへ符号化する。 */
  HTTP_FORM_URL_ENCODE,
  /** HTTP応答を2xx成功とそれ以外へ分類する。 */
  HTTP_RESPONSE_REQUIRE_SUCCESS,
  /** 文字列の空判定。 */
  STRING_IS_EMPTY,
  /** Unicode White_Spaceだけからなる文字列の判定。 */
  STRING_IS_BLANK,
  /** 書記素境界に一致する部分文字列の包含判定。 */
  STRING_CONTAINS,
  /** 書記素境界に一致する接頭辞判定。 */
  STRING_STARTS_WITH,
  /** 書記素境界に一致する接尾辞判定。 */
  STRING_ENDS_WITH,
  /** 文字列の指定回数反復。 */
  STRING_REPEAT,
  /** 文字列配列の区切り付き連結。 */
  STRING_JOIN,
  /** 指定書記素位置以降の文字列検索。 */
  GRAPHEME_FIND_FROM,
  /** 最後に一致する書記素位置の文字列検索。 */
  GRAPHEME_FIND_LAST
}
