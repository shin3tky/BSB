package jp.bsb.stdlib;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 組み込み単語を決定的な仕様順で公開します。 */
public final class BuiltinDictionary {
  /** コンソール出力を表す副作用能力の正規名です。 */
  public static final String CONSOLE_OUTPUT = "console.output";

  /** コンソール入力を表す能力名です。 */
  public static final String CONSOLE_INPUT = "console.input";

  /** プログラム標準エラーを表す能力名です。 */
  public static final String CONSOLE_ERROR = "console.error";

  /** プログラム指定終了を表す能力名です。 */
  public static final String PROCESS_EXIT = "process.exit";

  /** 起動引数を表す能力名です。 */
  public static final String PROCESS_ARGUMENTS = "process.arguments";

  /** プログラム識別情報を表す能力名です。 */
  public static final String PROGRAM_IDENTITY = "program.identity";

  /** 待機を表す能力名です。 */
  public static final String TIME_SLEEP = "time.sleep";

  /** 公開単調時計を表す能力名です。 */
  public static final String TIME_MONOTONIC = "time.monotonic";

  /** 現在日時を表す能力名です。 */
  public static final String TIME_WALL = "time.wall";

  /** 論理接続の解決・認可を表す能力名です。 */
  public static final String CONNECTION_RESOLVE = "connection.resolve";

  /** HTTP要求の同期送信を表す能力名です。 */
  public static final String HTTP_SEND = "http.send";

  /** 作業領域の解決・認可を表す能力名です。 */
  public static final String WORKSPACE_RESOLVE = "workspace.resolve";

  /** ファイル全内容読取を表す能力名です。 */
  public static final String FILE_READ = "file.read";

  /** ファイル全内容書込を表す能力名です。 */
  public static final String FILE_WRITE = "file.write";

  private static final List<BuiltinWord> WORDS =
      List.of(
          numeric(
              "足す",
              BuiltinOperation.ADD,
              "同じ型の2数値を加えます。",
              List.of("N", "N"),
              List.of("N"),
              "20 22 足す"),
          numeric(
              "引く",
              BuiltinOperation.SUBTRACT,
              "同じ型の第1数値から第2数値を引きます。",
              List.of("N", "N"),
              List.of("N"),
              "50 8 引く"),
          numeric(
              "掛ける",
              BuiltinOperation.MULTIPLY,
              "同じ型の2数値を掛けます。",
              List.of("N", "N"),
              List.of("N"),
              "21 2 掛ける"),
          new BuiltinWord(
              "等しい",
              Set.of(),
              "同じ型の2値が等しいかを調べます。",
              List.of("T", "T"),
              List.of("真偽"),
              BuiltinTypeRule.SAME_TYPE_PAIR,
              BuiltinOperation.EQUALS,
              Set.of(),
              "CORE",
              "10 10 等しい"),
          display("表示する", BuiltinOperation.DISPLAY, "値を改行せず表示します。", "42 表示する"),
          display("一行表示する", BuiltinOperation.DISPLAY_LINE, "値を表示して改行します。", "42 一行表示する"),
          new BuiltinWord(
              "改行する",
              Set.of(),
              "LFを1つ出力します。",
              List.of(),
              List.of(),
              BuiltinTypeRule.FIXED,
              BuiltinOperation.NEWLINE,
              Set.of(CONSOLE_OUTPUT),
              "CORE",
              "改行する"),
          numeric(
              "比べて小さい",
              BuiltinOperation.LESS_THAN,
              "同じ型の第1数値が第2数値より小さいかを調べます。",
              List.of("N", "N"),
              List.of("真偽"),
              "3 と 5 を 比べて小さい"),
          emptyArray("空の整数配列", "配列<整数>"),
          emptyArray("空の真偽配列", "配列<真偽>"),
          emptyArray("空の文字配列", "配列<文字>"),
          emptyArray("空の文字列配列", "配列<文字列>"),
          arrayOperation(
              "配列の長さ",
              BuiltinOperation.ARRAY_LENGTH,
              BuiltinTypeRule.ARRAY_LENGTH,
              "配列の要素数を返します。",
              List.of("配列<T>"),
              List.of("整数"),
              "【1、2】 を 配列の長さ"),
          arrayOperation(
              "配列から取り出す",
              BuiltinOperation.ARRAY_GET,
              BuiltinTypeRule.ARRAY_GET,
              "指定した添字の要素を返します。",
              List.of("配列<T>", "整数"),
              List.of("T"),
              "【10、20】 と 0 を 配列から取り出す"),
          arrayOperation(
              "配列の一部を取り出す",
              BuiltinOperation.ARRAY_SLICE,
              BuiltinTypeRule.ARRAY_SLICE,
              "半開区間の要素を新しい配列として返します。",
              List.of("配列<T>", "整数", "整数"),
              List.of("配列<T>"),
              "【10、20、30】 と 0 と 2 を 配列の一部を取り出す"),
          arrayOperation(
              "配列の要素を置き換える",
              BuiltinOperation.ARRAY_REPLACE,
              BuiltinTypeRule.ARRAY_REPLACE,
              "指定した要素を置き換えた新しい配列を返します。",
              List.of("配列<T>", "整数", "T"),
              List.of("配列<T>"),
              "【10、20】 と 0 と 30 を 配列の要素を置き換える"),
          arrayOperation(
              "配列の末尾へ追加する",
              BuiltinOperation.ARRAY_APPEND,
              BuiltinTypeRule.ARRAY_APPEND,
              "末尾へ要素を追加した新しい配列を返します。",
              List.of("配列<T>", "T"),
              List.of("配列<T>"),
              "【10、20】 と 30 を 配列の末尾へ追加する"),
          fixedNumeric(
              "割った商",
              BuiltinOperation.INTEGER_QUOTIENT,
              "整数を床除算した商を返します。",
              List.of("整数", "整数"),
              List.of("整数"),
              "10 と 3 を 割った商"),
          fixedNumeric(
              "割った剰余",
              BuiltinOperation.INTEGER_REMAINDER,
              "整数を床除算した剰余を返します。",
              List.of("整数", "整数"),
              List.of("整数"),
              "10 と 3 を 割った剰余"),
          fixedNumeric(
              "割った商と剰余",
              BuiltinOperation.INTEGER_QUOTIENT_AND_REMAINDER,
              "整数を床除算した商と剰余を返します。",
              List.of("整数", "整数"),
              List.of("整数", "整数"),
              "10 と 3 を 割った商と剰余"),
          independentNumeric(
              "小数で割る",
              BuiltinOperation.DECIMAL_DIVIDE,
              "2数値を既定精度で小数除算します。",
              List.of("数値", "数値"),
              List.of("小数"),
              "10 と 3 を 小数で割る"),
          independentNumeric(
              "精度指定で割る",
              BuiltinOperation.PRECISION_DECIMAL_DIVIDE,
              "精度と丸め方法を指定して2数値を小数除算します。",
              List.of("数値", "数値", "整数", "丸め方法"),
              List.of("小数"),
              "1 と 8 と 2 と 最近接偶数丸め で 精度指定で割る"),
          numeric(
              "絶対値",
              BuiltinOperation.ABSOLUTE,
              "数値の絶対値を返します。",
              List.of("N"),
              List.of("N"),
              "-42 を 絶対値"),
          numeric(
              "最小値",
              BuiltinOperation.MINIMUM,
              "同じ型の2数値の小さい方を返します。",
              List.of("N", "N"),
              List.of("N"),
              "-2 と 3 を 最小値"),
          numeric(
              "最大値",
              BuiltinOperation.MAXIMUM,
              "同じ型の2数値の大きい方を返します。",
              List.of("N", "N"),
              List.of("N"),
              "2.5 と 2.50 を 最大値"),
          fixedNumeric(
              "小数に変換する",
              BuiltinOperation.INTEGER_TO_DECIMAL,
              "整数を正確な小数へ変換します。",
              List.of("整数"),
              List.of("小数"),
              "42 を 小数に変換する"),
          fixedNumeric(
              "整数に変換する",
              BuiltinOperation.DECIMAL_TO_INTEGER,
              "小数部が0の小数を整数へ変換します。",
              List.of("小数"),
              List.of("整数"),
              "42.0 を 整数に変換する"),
          fixedNumeric(
              "整数に丸める",
              BuiltinOperation.ROUND_TO_INTEGER,
              "指定方法で小数を整数へ丸めます。",
              List.of("小数", "丸め方法"),
              List.of("整数"),
              "2.5 と 最近接偶数丸め で 整数に丸める"),
          roundingModeValue("最近接偶数丸め"),
          roundingModeValue("四捨五入"),
          roundingModeValue("0方向へ丸め"),
          roundingModeValue("正方向へ丸め"),
          roundingModeValue("負方向へ丸め"),
          emptyArray("空の小数配列", "配列<小数>", "NUM"),
          fixedText("つなぐ", BuiltinOperation.STRING_CONCAT, List.of("文字列", "文字列"), List.of("文字列")),
          fixedText(
              "文字列を比較する", BuiltinOperation.STRING_COMPARE, List.of("文字列", "文字列"), List.of("整数")),
          fixedText("前後の空白を除く", BuiltinOperation.STRING_TRIM, List.of("文字列"), List.of("文字列")),
          fixedText("文字列の長さ", BuiltinOperation.GRAPHEME_LENGTH, List.of("文字列"), List.of("整数")),
          fixedText(
              "文字列から取り出す", BuiltinOperation.GRAPHEME_GET, List.of("文字列", "整数"), List.of("文字")),
          fixedText(
              "文字列の一部を取り出す",
              BuiltinOperation.GRAPHEME_SLICE,
              List.of("文字列", "整数", "整数"),
              List.of("文字列")),
          fixedText(
              "文字列から探す", BuiltinOperation.GRAPHEME_FIND, List.of("文字列", "文字列"), List.of("整数")),
          fixedText(
              "文字列を置き換える",
              BuiltinOperation.STRING_REPLACE,
              List.of("文字列", "文字列", "文字列"),
              List.of("文字列")),
          fixedText(
              "文字列を分割する", BuiltinOperation.STRING_SPLIT, List.of("文字列", "文字列"), List.of("配列<文字列>")),
          fixedText(
              "文字列のコードポイント数", BuiltinOperation.CODE_POINT_LENGTH, List.of("文字列"), List.of("整数")),
          fixedText(
              "文字列からコードポイントを取り出す",
              BuiltinOperation.CODE_POINT_GET,
              List.of("文字列", "整数"),
              List.of("文字")),
          fixedText(
              "コードポイント範囲で文字列の一部を取り出す",
              BuiltinOperation.CODE_POINT_SLICE,
              List.of("文字列", "整数", "整数"),
              List.of("文字列")),
          fixedText(
              "コードポイント位置で文字列から探す",
              BuiltinOperation.CODE_POINT_FIND,
              List.of("文字列", "文字列"),
              List.of("整数")),
          fixedText(
              "整数を文字列に変換する", BuiltinOperation.INTEGER_TO_STRING, List.of("整数"), List.of("文字列")),
          fixedText(
              "小数を文字列に変換する", BuiltinOperation.DECIMAL_TO_STRING, List.of("小数"), List.of("文字列")),
          fixedText(
              "文字列を整数に変換する", BuiltinOperation.STRING_TO_INTEGER, List.of("文字列"), List.of("整数")),
          fixedText(
              "文字列を小数に変換する", BuiltinOperation.STRING_TO_DECIMAL, List.of("文字列"), List.of("小数")),
          fixedText(
              "正規表現に完全一致する",
              BuiltinOperation.REGEX_FULL_MATCH,
              List.of("文字列", "正規表現"),
              List.of("真偽")),
          fixedText(
              "正規表現を含む", BuiltinOperation.REGEX_CONTAINS, List.of("文字列", "正規表現"), List.of("真偽")),
          fixedText(
              "正規表現で最初を取り出す", BuiltinOperation.REGEX_FIRST, List.of("文字列", "正規表現"), List.of("文字列")),
          fixedText(
              "正規表現の名前付き部分を取り出す",
              BuiltinOperation.REGEX_NAMED_GROUP,
              List.of("文字列", "正規表現", "文字列"),
              List.of("文字列")),
          fixedText(
              "正規表現で置き換える",
              BuiltinOperation.REGEX_REPLACE,
              List.of("文字列", "正規表現", "文字列"),
              List.of("文字列")),
          fixedText(
              "正規表現で分割する",
              BuiltinOperation.REGEX_SPLIT,
              List.of("文字列", "正規表現"),
              List.of("配列<文字列>")),
          hostIo(
              "一行を入力する",
              BuiltinOperation.READ_LINE,
              List.of(),
              List.of("入力結果"),
              CONSOLE_INPUT,
              true),
          hostIo(
              "入力行である",
              BuiltinOperation.INPUT_IS_LINE,
              List.of("入力結果"),
              List.of("入力結果", "真偽"),
              null,
              true),
          hostIo(
              "入力終端である",
              BuiltinOperation.INPUT_IS_END,
              List.of("入力結果"),
              List.of("入力結果", "真偽"),
              null,
              true),
          hostIo(
              "入力キャンセルである",
              BuiltinOperation.INPUT_IS_CANCEL,
              List.of("入力結果"),
              List.of("入力結果", "真偽"),
              null,
              true),
          hostIo(
              "入力行を取り出す",
              BuiltinOperation.INPUT_TAKE_LINE,
              List.of("入力結果"),
              List.of("文字列"),
              null,
              true),
          hostIo("入力結果を捨てる", BuiltinOperation.INPUT_DROP, List.of("入力結果"), List.of(), null, true),
          hostIoDisplay("エラー表示する", BuiltinOperation.ERROR_DISPLAY, false),
          hostIoDisplay("エラー一行表示する", BuiltinOperation.ERROR_DISPLAY_LINE, true),
          hostIo(
              "エラー改行する", BuiltinOperation.ERROR_NEWLINE, List.of(), List.of(), CONSOLE_ERROR, true),
          hostIo(
              "終了する", BuiltinOperation.PROGRAM_EXIT, List.of("整数"), List.of(), PROCESS_EXIT, false),
          hostIo(
              "起動引数を得る",
              BuiltinOperation.PROCESS_ARGUMENTS,
              List.of(),
              List.of("配列<文字列>"),
              PROCESS_ARGUMENTS,
              true),
          hostIo(
              "プログラム名を得る",
              BuiltinOperation.PROGRAM_NAME,
              List.of(),
              List.of("文字列"),
              PROGRAM_IDENTITY,
              true),
          hostIo(
              "プログラムの場所を得る",
              BuiltinOperation.PROGRAM_LOCATION,
              List.of(),
              List.of("文字列"),
              PROGRAM_IDENTITY,
              true),
          hostIo("待つ", BuiltinOperation.SLEEP, List.of("整数"), List.of(), TIME_SLEEP, true),
          hostIo(
              "単調ミリ秒を得る",
              BuiltinOperation.MONOTONIC_MILLISECONDS,
              List.of(),
              List.of("整数"),
              TIME_MONOTONIC,
              true),
          hostIo("現在日時を得る", BuiltinOperation.WALL_TIME, List.of(), List.of("日時"), TIME_WALL, true),
          hostIo(
              "日時を文字列に変換する",
              BuiltinOperation.DATE_TIME_TO_STRING,
              List.of("日時"),
              List.of("文字列"),
              null,
              true),
          jsonFeature(
              "JSONヌル",
              BuiltinOperation.JSON_NULL,
              "JSONのnull値を作ります。",
              List.of(),
              List.of("JSON"),
              "JSONヌル"),
          jsonFeature(
              "空のJSON配列",
              BuiltinOperation.JSON_EMPTY_ARRAY,
              "空の不変JSON配列を作ります。",
              List.of(),
              List.of("JSON"),
              "空のJSON配列"),
          jsonFeature(
              "空のJSONオブジェクト",
              BuiltinOperation.JSON_EMPTY_OBJECT,
              "空の不変JSONオブジェクトを作ります。",
              List.of(),
              List.of("JSON"),
              "空のJSONオブジェクト"),
          jsonFeature(
              "JSONを解析する",
              BuiltinOperation.JSON_PARSE,
              "文字列全体を厳密なJSONとして解析します。",
              List.of("文字列"),
              List.of("JSON"),
              "「{\"x\":1}」を JSONを解析する"),
          jsonFeature(
              "JSONを文字列に変換する",
              BuiltinOperation.JSON_SERIALIZE,
              "JSON値を決定的なコンパクトJSONへ変換します。",
              List.of("JSON"),
              List.of("文字列"),
              "JSONヌルを JSONを文字列に変換する"),
          jsonFeature(
              "真偽をJSONに変換する",
              BuiltinOperation.BOOLEAN_TO_JSON,
              "真偽をJSON真偽へ変換します。",
              List.of("真偽"),
              List.of("JSON"),
              "はいを 真偽をJSONに変換する"),
          jsonFeature(
              "整数をJSONに変換する",
              BuiltinOperation.INTEGER_TO_JSON,
              "整数を同じ値のJSON整数へ変換します。",
              List.of("整数"),
              List.of("JSON"),
              "42を 整数をJSONに変換する"),
          jsonFeature(
              "小数をJSONに変換する",
              BuiltinOperation.DECIMAL_TO_JSON,
              "小数を同じ値のJSON小数へ変換します。",
              List.of("小数"),
              List.of("JSON"),
              "2.5を 小数をJSONに変換する"),
          jsonFeature(
              "文字列をJSONに変換する",
              BuiltinOperation.STRING_TO_JSON,
              "文字列を同じ値のJSON文字列へ変換します。",
              List.of("文字列"),
              List.of("JSON"),
              "「値」を 文字列をJSONに変換する"),
          jsonFeature(
              "JSONから真偽を取り出す",
              BuiltinOperation.JSON_TO_BOOLEAN,
              "JSON真偽からBSB真偽を取り出します。",
              List.of("JSON"),
              List.of("真偽"),
              "「true」を JSONを解析する JSONから真偽を取り出す"),
          jsonFeature(
              "JSONから整数を取り出す",
              BuiltinOperation.JSON_TO_INTEGER,
              "JSON整数からBSB整数を取り出します。",
              List.of("JSON"),
              List.of("整数"),
              "「42」を JSONを解析する JSONから整数を取り出す"),
          jsonFeature(
              "JSONから小数を取り出す",
              BuiltinOperation.JSON_TO_DECIMAL,
              "JSON小数からBSB小数を取り出します。",
              List.of("JSON"),
              List.of("小数"),
              "「2.5」を JSONを解析する JSONから小数を取り出す"),
          jsonFeature(
              "JSONから文字列を取り出す",
              BuiltinOperation.JSON_TO_STRING,
              "JSON文字列からBSB文字列を取り出します。",
              List.of("JSON"),
              List.of("文字列"),
              "「\"値\"」を JSONを解析する JSONから文字列を取り出す"),
          jsonFeature(
              "JSONヌルである",
              BuiltinOperation.JSON_IS_NULL,
              "JSON値がnullかを調べ、元値も残します。",
              List.of("JSON"),
              List.of("JSON", "真偽"),
              "JSONヌル JSONヌルである"),
          jsonFeature(
              "JSON真偽である",
              BuiltinOperation.JSON_IS_BOOLEAN,
              "JSON値が真偽かを調べ、元値も残します。",
              List.of("JSON"),
              List.of("JSON", "真偽"),
              "はいを 真偽をJSONに変換する JSON真偽である"),
          jsonFeature(
              "JSON整数である",
              BuiltinOperation.JSON_IS_INTEGER,
              "JSON値が整数かを調べ、元値も残します。",
              List.of("JSON"),
              List.of("JSON", "真偽"),
              "1を 整数をJSONに変換する JSON整数である"),
          jsonFeature(
              "JSON小数である",
              BuiltinOperation.JSON_IS_DECIMAL,
              "JSON値が小数かを調べ、元値も残します。",
              List.of("JSON"),
              List.of("JSON", "真偽"),
              "1.0を 小数をJSONに変換する JSON小数である"),
          jsonFeature(
              "JSON文字列である",
              BuiltinOperation.JSON_IS_STRING,
              "JSON値が文字列かを調べ、元値も残します。",
              List.of("JSON"),
              List.of("JSON", "真偽"),
              "「値」を 文字列をJSONに変換する JSON文字列である"),
          jsonFeature(
              "JSON配列である",
              BuiltinOperation.JSON_IS_ARRAY,
              "JSON値が配列かを調べ、元値も残します。",
              List.of("JSON"),
              List.of("JSON", "真偽"),
              "空のJSON配列 JSON配列である"),
          jsonFeature(
              "JSONオブジェクトである",
              BuiltinOperation.JSON_IS_OBJECT,
              "JSON値がオブジェクトかを調べ、元値も残します。",
              List.of("JSON"),
              List.of("JSON", "真偽"),
              "空のJSONオブジェクト JSONオブジェクトである"),
          jsonFeature(
              "JSONから配列に変換する",
              BuiltinOperation.JSON_TO_ARRAY,
              "JSON配列をBSBのJSON値配列へ変換します。",
              List.of("JSON"),
              List.of("配列<JSON>"),
              "空のJSON配列 JSONから配列に変換する"),
          jsonFeature(
              "JSON配列に変換する",
              BuiltinOperation.ARRAY_TO_JSON,
              "BSBのJSON値配列をJSON配列へ変換します。",
              List.of("配列<JSON>"),
              List.of("JSON"),
              "空のJSON配列 JSONから配列に変換する JSON配列に変換する"),
          jsonFeature(
              "JSON配列の長さ",
              BuiltinOperation.JSON_ARRAY_LENGTH,
              "JSON配列の要素数を返します。",
              List.of("JSON"),
              List.of("整数"),
              "空のJSON配列 JSON配列の長さ"),
          jsonFeature(
              "JSON配列から取り出す",
              BuiltinOperation.JSON_ARRAY_GET,
              "JSON配列の指定位置から要素を取り出します。",
              List.of("JSON", "整数"),
              List.of("JSON"),
              "「[1]」を JSONを解析する 0を JSON配列から取り出す"),
          jsonFeature(
              "JSON配列の一部を取り出す",
              BuiltinOperation.JSON_ARRAY_SLICE,
              "JSON配列の半開区間を新しいJSON配列で返します。",
              List.of("JSON", "整数", "整数"),
              List.of("JSON"),
              "「[1,2]」を JSONを解析する 0と1を JSON配列の一部を取り出す"),
          jsonFeature(
              "JSON配列の要素を置き換える",
              BuiltinOperation.JSON_ARRAY_REPLACE,
              "指定要素を置換した新しいJSON配列を返します。",
              List.of("JSON", "整数", "JSON"),
              List.of("JSON"),
              "「[1]」を JSONを解析する 0と JSONヌルを JSON配列の要素を置き換える"),
          jsonFeature(
              "JSON配列の末尾へ追加する",
              BuiltinOperation.JSON_ARRAY_APPEND,
              "末尾へ要素を加えた新しいJSON配列を返します。",
              List.of("JSON", "JSON"),
              List.of("JSON"),
              "空のJSON配列と JSONヌルを JSON配列の末尾へ追加する"),
          jsonFeature(
              "JSONオブジェクトの要素数",
              BuiltinOperation.JSON_OBJECT_SIZE,
              "JSONオブジェクトのメンバー数を返します。",
              List.of("JSON"),
              List.of("整数"),
              "空のJSONオブジェクト JSONオブジェクトの要素数"),
          jsonFeature(
              "JSONオブジェクトのキー一覧",
              BuiltinOperation.JSON_OBJECT_KEYS,
              "JSONオブジェクトのキーを保持順で返します。",
              List.of("JSON"),
              List.of("配列<文字列>"),
              "空のJSONオブジェクト JSONオブジェクトのキー一覧"),
          jsonFeature(
              "JSONオブジェクトにキーがある",
              BuiltinOperation.JSON_OBJECT_CONTAINS_KEY,
              "キーの存在を調べ、元オブジェクトも残します。",
              List.of("JSON", "文字列"),
              List.of("JSON", "真偽"),
              "空のJSONオブジェクトと「x」を JSONオブジェクトにキーがある"),
          jsonFeature(
              "JSONオブジェクトから必須値を取り出す",
              BuiltinOperation.JSON_OBJECT_GET_REQUIRED,
              "存在するキーのJSON値を取り出します。",
              List.of("JSON", "文字列"),
              List.of("JSON"),
              "「{\"x\":1}」を JSONを解析する 「x」を JSONオブジェクトから必須値を取り出す"),
          jsonFeature(
              "JSONオブジェクトに設定する",
              BuiltinOperation.JSON_OBJECT_SET,
              "キーを設定した新しいJSONオブジェクトを返します。",
              List.of("JSON", "文字列", "JSON"),
              List.of("JSON"),
              "空のJSONオブジェクトと「x」と JSONヌルを JSONオブジェクトに設定する"),
          jsonFeature(
              "JSONオブジェクトから削除する",
              BuiltinOperation.JSON_OBJECT_DELETE,
              "キーを削除した新しいJSONオブジェクトを返します。",
              List.of("JSON", "文字列"),
              List.of("JSON"),
              "空のJSONオブジェクトと「x」を JSONオブジェクトから削除する"),
          optionalFeature(
              "任意にする",
              BuiltinOperation.OPTIONAL_WRAP,
              BuiltinTypeRule.OPTIONAL_WRAP,
              "値を値あり任意値へ包みます。",
              List.of("T"),
              List.of("任意<T>"),
              "42 を 任意にする"),
          optionalFeature(
              "任意に値がある",
              BuiltinOperation.OPTIONAL_PREDICATE,
              BuiltinTypeRule.OPTIONAL_PREDICATE,
              "任意値を残して値があるかを調べます。",
              List.of("任意<T>"),
              List.of("任意<T>", "真偽"),
              "42 を 任意にする 任意に値がある"),
          optionalFeature(
              "任意から値を取り出す",
              BuiltinOperation.OPTIONAL_UNWRAP,
              BuiltinTypeRule.OPTIONAL_UNWRAP,
              "値あり任意値から内包値を取り出します。",
              List.of("任意<T>"),
              List.of("T"),
              "42 を 任意にする 任意から値を取り出す"),
          optionalFeature(
              "任意を捨てる",
              BuiltinOperation.OPTIONAL_DROP,
              BuiltinTypeRule.OPTIONAL_DROP,
              "任意値を状態にかかわらず破棄します。",
              List.of("任意<T>"),
              List.of(),
              "42 を 任意にする 任意を捨てる"),
          optionalFeature(
              "JSONオブジェクトから任意値を取り出す",
              BuiltinOperation.JSON_OBJECT_GET_OPTIONAL,
              BuiltinTypeRule.FIXED,
              "キーがあれば値あり、なければ値なしの任意JSONを返します。",
              List.of("JSON", "文字列"),
              List.of("任意<JSON>"),
              "「{\"x\":1}」を JSONを解析する 「x」を JSONオブジェクトから任意値を取り出す"),
          resultFeature(
              "成功にする",
              BuiltinOperation.RESULT_SUCCESS_WRAP,
              BuiltinTypeRule.RESULT_SUCCESS_WRAP,
              "値を成功結果へ包みます。",
              List.of("T"),
              List.of("結果<T,E>"),
              "42 を 成功にする<整数,文字列>"),
          resultFeature(
              "失敗にする",
              BuiltinOperation.RESULT_FAILURE_WRAP,
              BuiltinTypeRule.RESULT_FAILURE_WRAP,
              "値を失敗結果へ包みます。",
              List.of("E"),
              List.of("結果<T,E>"),
              "「not-found」を 失敗にする<整数,文字列>"),
          resultFeature(
              "結果が成功である",
              BuiltinOperation.RESULT_IS_SUCCESS,
              BuiltinTypeRule.RESULT_PREDICATE,
              "結果値を残して成功状態かを調べます。",
              List.of("結果<T,E>"),
              List.of("結果<T,E>", "真偽"),
              "42 を 成功にする<整数,文字列> 結果が成功である"),
          resultFeature(
              "結果が失敗である",
              BuiltinOperation.RESULT_IS_FAILURE,
              BuiltinTypeRule.RESULT_PREDICATE,
              "結果値を残して失敗状態かを調べます。",
              List.of("結果<T,E>"),
              List.of("結果<T,E>", "真偽"),
              "「not-found」を 失敗にする<整数,文字列> 結果が失敗である"),
          resultFeature(
              "結果から成功値を取り出す",
              BuiltinOperation.RESULT_SUCCESS_UNWRAP,
              BuiltinTypeRule.RESULT_SUCCESS_UNWRAP,
              "成功結果から成功値を取り出します。",
              List.of("結果<T,E>"),
              List.of("T"),
              "42 を 成功にする<整数,文字列> 結果から成功値を取り出す"),
          resultFeature(
              "結果から失敗値を取り出す",
              BuiltinOperation.RESULT_FAILURE_UNWRAP,
              BuiltinTypeRule.RESULT_FAILURE_UNWRAP,
              "失敗結果から失敗値を取り出します。",
              List.of("結果<T,E>"),
              List.of("E"),
              "「not-found」を 失敗にする<整数,文字列> 結果から失敗値を取り出す"),
          resultFeature(
              "結果を捨てる",
              BuiltinOperation.RESULT_DROP,
              BuiltinTypeRule.RESULT_DROP,
              "結果値を状態にかかわらず破棄します。",
              List.of("結果<T,E>"),
              List.of(),
              "42 を 成功にする<整数,文字列> 結果を捨てる"),
          recoverableJsonFeature(
              "JSONを解析して結果を返す",
              BuiltinOperation.JSON_PARSE_RESULT,
              "文字列全体を厳密なJSONとして解析し、構文失敗を結果値で返します。",
              List.of("文字列"),
              List.of("結果<JSON,JSON解析失敗>"),
              "「{\"x\":1}」を JSONを解析して結果を返す"),
          recoverableJsonFeature(
              "JSON解析失敗の種類を取り出す",
              BuiltinOperation.JSON_PARSE_FAILURE_KIND,
              "JSON解析失敗から安定した失敗種別を取り出します。",
              List.of("JSON解析失敗"),
              List.of("文字列"),
              "JSON解析失敗の種類を取り出す"),
          recoverableJsonFeature(
              "JSON解析失敗のバイト位置を取り出す",
              BuiltinOperation.JSON_PARSE_FAILURE_OFFSET,
              "JSON解析失敗から0始まりのUTF-8バイト位置を取り出します。",
              List.of("JSON解析失敗"),
              List.of("整数"),
              "JSON解析失敗のバイト位置を取り出す"),
          recoverableJsonFeature(
              "JSON解析失敗の行を取り出す",
              BuiltinOperation.JSON_PARSE_FAILURE_LINE,
              "JSON解析失敗から1始まりの行番号を取り出します。",
              List.of("JSON解析失敗"),
              List.of("整数"),
              "JSON解析失敗の行を取り出す"),
          recoverableJsonFeature(
              "JSON解析失敗の列を取り出す",
              BuiltinOperation.JSON_PARSE_FAILURE_COLUMN,
              "JSON解析失敗から1始まりのUnicodeスカラー値列を取り出します。",
              List.of("JSON解析失敗"),
              List.of("整数"),
              "JSON解析失敗の列を取り出す"),
          new BuiltinWord(
              "論理接続を確認する",
              Set.of(),
              "ホスト側に論理接続の設定が存在し、利用可能かを確認します。",
              List.of(),
              List.of(),
              BuiltinTypeRule.FIXED,
              BuiltinOperation.LOGICAL_CONNECTION_CHECK,
              Set.of(CONNECTION_RESOLVE),
              Set.of(CONNECTION_RESOLVE),
              true,
              "CONN",
              "論理接続を確認する<顧客管理API>"),
          byteSequenceFeature(
              "空のバイト列",
              BuiltinOperation.EMPTY_BYTE_SEQUENCE,
              "空の不変バイト列を返します。",
              List.of(),
              List.of("バイト列"),
              "空のバイト列"),
          byteSequenceFeature(
              "バイト列の長さ",
              BuiltinOperation.BYTE_SEQUENCE_LENGTH,
              "バイト列の論理バイト数を返します。",
              List.of("バイト列"),
              List.of("整数"),
              "空のバイト列 バイト列の長さ"),
          byteSequenceFeature(
              "バイト列の一部を取り出す",
              BuiltinOperation.BYTE_SEQUENCE_SLICE,
              "バイト半開区間を元値を変えずに返します。",
              List.of("バイト列", "整数", "整数"),
              List.of("バイト列"),
              "空のバイト列 と 0 と 0 を バイト列の一部を取り出す"),
          byteSequenceFeature(
              "文字列をUTF8バイト列に変換する",
              BuiltinOperation.STRING_TO_UTF8_BYTES,
              "文字列をBOMなしの最短UTF-8へ符号化します。",
              List.of("文字列"),
              List.of("バイト列"),
              "「日本語」を 文字列をUTF8バイト列に変換する"),
          byteSequenceFeature(
              "バイト列をUTF8文字列に変換して結果を返す",
              BuiltinOperation.UTF8_BYTES_TO_STRING_RESULT,
              "バイト列を厳密なUTF-8として復号し結果値で返します。",
              List.of("バイト列"),
              List.of("結果<文字列,UTF8復号失敗>"),
              "空のバイト列 バイト列をUTF8文字列に変換して結果を返す"),
          byteSequenceFeature(
              "UTF8復号失敗の種類を取り出す",
              BuiltinOperation.UTF8_DECODE_FAILURE_KIND,
              "UTF-8復号失敗から安定した失敗種別を取り出します。",
              List.of("UTF8復号失敗"),
              List.of("文字列"),
              "UTF8復号失敗の種類を取り出す"),
          byteSequenceFeature(
              "UTF8復号失敗のバイト位置を取り出す",
              BuiltinOperation.UTF8_DECODE_FAILURE_OFFSET,
              "UTF-8復号失敗から0始まりのバイト位置を取り出します。",
              List.of("UTF8復号失敗"),
              List.of("整数"),
              "UTF8復号失敗のバイト位置を取り出す"),
          byteSequenceFeature(
              "バイト列をBase64文字列に変換する",
              BuiltinOperation.BYTE_SEQUENCE_TO_BASE64,
              "バイト列をRFC 4648の規範Base64文字列へ符号化します。",
              List.of("バイト列"),
              List.of("文字列"),
              "空のバイト列 バイト列をBase64文字列に変換する"),
          byteSequenceFeature(
              "Base64文字列をバイト列に変換して結果を返す",
              BuiltinOperation.BASE64_TO_BYTE_SEQUENCE_RESULT,
              "文字列を厳密な規範Base64として復号し結果値で返します。",
              List.of("文字列"),
              List.of("結果<バイト列,Base64復号失敗>"),
              "「」を Base64文字列をバイト列に変換して結果を返す"),
          byteSequenceFeature(
              "Base64復号失敗の種類を取り出す",
              BuiltinOperation.BASE64_DECODE_FAILURE_KIND,
              "Base64復号失敗から安定した失敗種別を取り出します。",
              List.of("Base64復号失敗"),
              List.of("文字列"),
              "Base64復号失敗の種類を取り出す"),
          byteSequenceFeature(
              "Base64復号失敗の文字位置を取り出す",
              BuiltinOperation.BASE64_DECODE_FAILURE_OFFSET,
              "Base64復号失敗から0始まりのUnicodeスカラー値位置を取り出します。",
              List.of("Base64復号失敗"),
              List.of("整数"),
              "Base64復号失敗の文字位置を取り出す"),
          httpsFeature(
              "空のHTTP要求",
              BuiltinOperation.EMPTY_HTTP_REQUEST,
              "経路・問い合わせ・ヘッダーが空で本文不在の不変HTTP要求を返します。",
              List.of(),
              List.of("HTTP要求"),
              "空のHTTP要求"),
          httpsFeature(
              "HTTP要求に経路を設定する",
              BuiltinOperation.HTTP_REQUEST_SET_PATH,
              "相対Unicode経路を設定した新しいHTTP要求を返します。",
              List.of("HTTP要求", "文字列"),
              List.of("HTTP要求"),
              "空のHTTP要求 「v1/items」を HTTP要求に経路を設定する"),
          httpsFeature(
              "HTTP要求に問い合わせ項目を追加する",
              BuiltinOperation.HTTP_REQUEST_ADD_QUERY,
              "問い合わせ名と値を順序と重複を保って追加します。",
              List.of("HTTP要求", "文字列", "文字列"),
              List.of("HTTP要求"),
              "空のHTTP要求 「q」と「東京」を HTTP要求に問い合わせ項目を追加する"),
          httpsFeature(
              "HTTP要求にヘッダーを設定する",
              BuiltinOperation.HTTP_REQUEST_SET_HEADER,
              "ASCIIヘッダーを大小文字非区別で置換します。",
              List.of("HTTP要求", "文字列", "文字列"),
              List.of("HTTP要求"),
              "空のHTTP要求 「Accept」と「application/json」を HTTP要求にヘッダーを設定する"),
          httpsFeature(
              "HTTP要求にJSON本文を設定する",
              BuiltinOperation.HTTP_REQUEST_SET_JSON_BODY,
              "規範JSONを設定時にUTF-8本文へ固定します。",
              List.of("HTTP要求", "JSON"),
              List.of("HTTP要求"),
              "空のHTTP要求 JSONのnull HTTP要求にJSON本文を設定する"),
          httpsFeature(
              "HTTP要求に文字列本文を設定する",
              BuiltinOperation.HTTP_REQUEST_SET_STRING_BODY,
              "文字列を設定時にUTF-8本文へ固定します。",
              List.of("HTTP要求", "文字列"),
              List.of("HTTP要求"),
              "空のHTTP要求 「本文」を HTTP要求に文字列本文を設定する"),
          httpsFeature(
              "HTTP要求にバイト列本文を設定する",
              BuiltinOperation.HTTP_REQUEST_SET_BYTE_BODY,
              "不変バイト列を損失なく本文へ設定します。",
              List.of("HTTP要求", "バイト列"),
              List.of("HTTP要求"),
              "空のHTTP要求 空のバイト列 HTTP要求にバイト列本文を設定する"),
          new BuiltinWord(
              "HTTP要求を送信する",
              Set.of(),
              "静的な論理接続とmethodを使ってHTTP要求を同期送信します。",
              List.of("HTTP要求"),
              List.of("結果<HTTP応答,HTTP送信失敗>"),
              BuiltinTypeRule.FIXED,
              BuiltinOperation.HTTP_SEND,
              Set.of(CONNECTION_RESOLVE, HTTP_SEND),
              Set.of(CONNECTION_RESOLVE, HTTP_SEND),
              true,
              "HTTPS",
              "空のHTTP要求 HTTP要求を送信する<顧客管理API,POST>"),
          httpsFeature(
              "HTTP応答から状態コードを取り出す",
              BuiltinOperation.HTTP_RESPONSE_STATUS,
              "完全HTTP応答から最終状態コードを取り出します。",
              List.of("HTTP応答"),
              List.of("整数"),
              "HTTP応答から状態コードを取り出す"),
          httpsFeature(
              "HTTP応答からヘッダー値一覧を取り出す",
              BuiltinOperation.HTTP_RESPONSE_HEADER_VALUES,
              "HTTP応答から同名ヘッダーの全値を受信順で取り出します。",
              List.of("HTTP応答", "文字列"),
              List.of("配列<文字列>"),
              "HTTP応答 「Content-Type」を HTTP応答からヘッダー値一覧を取り出す"),
          httpsFeature(
              "HTTP応答から本文を取り出す",
              BuiltinOperation.HTTP_RESPONSE_BODY,
              "HTTP応答から完全受信済み本文を取り出します。",
              List.of("HTTP応答"),
              List.of("バイト列"),
              "HTTP応答から本文を取り出す"),
          httpsFeature(
              "HTTP送信失敗の種類を取り出す",
              BuiltinOperation.HTTP_SEND_FAILURE_KIND,
              "HTTP送信失敗から安定した閉じた種類を取り出します。",
              List.of("HTTP送信失敗"),
              List.of("文字列"),
              "HTTP送信失敗の種類を取り出す"),
          httpsFeature(
              "HTTP要求に本文がある",
              BuiltinOperation.HTTP_REQUEST_HAS_BODY,
              "HTTP要求を残して長さ0を含む本文の有無を返します。",
              List.of("HTTP要求"),
              List.of("HTTP要求", "真偽"),
              "空のHTTP要求 HTTP要求に本文がある"),
          emptyArray("空の整数二次元配列", "配列<配列<整数>>", "NARRAY"),
          emptyArray("空の真偽二次元配列", "配列<配列<真偽>>", "NARRAY"),
          emptyArray("空の文字二次元配列", "配列<配列<文字>>", "NARRAY"),
          emptyArray("空の文字列二次元配列", "配列<配列<文字列>>", "NARRAY"),
          emptyArray("空の小数二次元配列", "配列<配列<小数>>", "NARRAY"),
          emptyArray("空のJSON二次元配列", "配列<配列<JSON>>", "NARRAY"),
          workspaceFileFeature(
              "ファイルを読む",
              BuiltinOperation.FILE_READ,
              "静的作業領域の登録から論理ファイル全体を読みます。",
              List.of("文字列"),
              List.of("結果<バイト列,ファイル読取失敗>"),
              Set.of(WORKSPACE_RESOLVE, FILE_READ),
              "「入力.dat」を ファイルを読む<帳票>"),
          workspaceFileFeature(
              "ファイルへ書く",
              BuiltinOperation.FILE_WRITE,
              "静的作業領域の登録へ全バイトを原子的に書きます。",
              List.of("文字列", "バイト列"),
              List.of("結果<整数,ファイル書込失敗>"),
              Set.of(WORKSPACE_RESOLVE, FILE_WRITE),
              "「出力.dat」と 空のバイト列を ファイルへ書く<帳票>"),
          workspaceFileFeature(
              "ファイル読取失敗の種類を取り出す",
              BuiltinOperation.FILE_READ_FAILURE_KIND,
              "ファイル読取失敗から安定した閉じた種類を取り出します。",
              List.of("ファイル読取失敗"),
              List.of("文字列"),
              Set.of(),
              "ファイル読取失敗の種類を取り出す"),
          workspaceFileFeature(
              "ファイル書込失敗の種類を取り出す",
              BuiltinOperation.FILE_WRITE_FAILURE_KIND,
              "ファイル書込失敗から安定した閉じた種類を取り出します。",
              List.of("ファイル書込失敗"),
              List.of("文字列"),
              Set.of(),
              "ファイル書込失敗の種類を取り出す"),
          delimitedTableFeature(
              "CSVを表として解析する",
              BuiltinOperation.CSV_PARSE_TABLE,
              "CSV文字列を厳密に解析し、文字列の二次元配列を結果で返します。",
              List.of("文字列"),
              List.of("結果<配列<配列<文字列>>,区切りテキスト解析失敗>"),
              "「a,b」を CSVを表として解析する"),
          delimitedTableFeature(
              "TSVを表として解析する",
              BuiltinOperation.TSV_PARSE_TABLE,
              "TSV文字列を厳密に解析し、文字列の二次元配列を結果で返します。",
              List.of("文字列"),
              List.of("結果<配列<配列<文字列>>,区切りテキスト解析失敗>"),
              "「a\tb」を TSVを表として解析する"),
          delimitedTableFeature(
              "表をCSVに変換する",
              BuiltinOperation.TABLE_TO_CSV,
              "文字列の二次元配列を規範CSV文字列へ変換します。",
              List.of("配列<配列<文字列>>"),
              List.of("文字列"),
              "【【「a」、「b」】】を 表をCSVに変換する"),
          delimitedTableFeature(
              "表をTSVに変換する",
              BuiltinOperation.TABLE_TO_TSV,
              "文字列の二次元配列を規範TSV文字列へ変換します。",
              List.of("配列<配列<文字列>>"),
              List.of("文字列"),
              "【【「a」、「b」】】を 表をTSVに変換する"),
          delimitedTableFeature(
              "区切りテキスト解析失敗の種類を取り出す",
              BuiltinOperation.DELIMITED_TEXT_PARSE_FAILURE_KIND,
              "区切りテキスト解析失敗から安定した閉じた種類を取り出します。",
              List.of("区切りテキスト解析失敗"),
              List.of("文字列"),
              "区切りテキスト解析失敗の種類を取り出す"),
          delimitedTableFeature(
              "区切りテキスト解析失敗のバイト位置を取り出す",
              BuiltinOperation.DELIMITED_TEXT_PARSE_FAILURE_OFFSET,
              "区切りテキスト解析失敗から0始まりのUTF-8バイト位置を取り出します。",
              List.of("区切りテキスト解析失敗"),
              List.of("整数"),
              "区切りテキスト解析失敗のバイト位置を取り出す"),
          delimitedTableFeature(
              "区切りテキスト解析失敗の行を取り出す",
              BuiltinOperation.DELIMITED_TEXT_PARSE_FAILURE_LINE,
              "区切りテキスト解析失敗から1始まりの行を取り出します。",
              List.of("区切りテキスト解析失敗"),
              List.of("整数"),
              "区切りテキスト解析失敗の行を取り出す"),
          delimitedTableFeature(
              "区切りテキスト解析失敗の列を取り出す",
              BuiltinOperation.DELIMITED_TEXT_PARSE_FAILURE_COLUMN,
              "区切りテキスト解析失敗から1始まりの列を取り出します。",
              List.of("区切りテキスト解析失敗"),
              List.of("整数"),
              "区切りテキスト解析失敗の列を取り出す"),
          jsonShapeFeature(
              "JSONヌルの形状",
              BuiltinOperation.JSON_SHAPE_NULL,
              "JSON nullだけを受理する形状を作ります。",
              List.of(),
              List.of("JSON形状"),
              "JSONヌルの形状"),
          jsonShapeFeature(
              "JSON真偽の形状",
              BuiltinOperation.JSON_SHAPE_BOOLEAN,
              "JSON真偽だけを受理する形状を作ります。",
              List.of(),
              List.of("JSON形状"),
              "JSON真偽の形状"),
          jsonShapeFeature(
              "JSON整数の形状",
              BuiltinOperation.JSON_SHAPE_INTEGER,
              "JSON整数だけを受理する形状を作ります。",
              List.of(),
              List.of("JSON形状"),
              "JSON整数の形状"),
          jsonShapeFeature(
              "JSON小数の形状",
              BuiltinOperation.JSON_SHAPE_DECIMAL,
              "JSON小数だけを受理する形状を作ります。",
              List.of(),
              List.of("JSON形状"),
              "JSON小数の形状"),
          jsonShapeFeature(
              "JSON文字列の形状",
              BuiltinOperation.JSON_SHAPE_STRING,
              "JSON文字列だけを受理する形状を作ります。",
              List.of(),
              List.of("JSON形状"),
              "JSON文字列の形状"),
          jsonShapeFeature(
              "JSON配列の形状にする",
              BuiltinOperation.JSON_SHAPE_ARRAY,
              "形状を全要素へ適用するJSON配列形状を作ります。",
              List.of("JSON形状"),
              List.of("JSON形状"),
              "JSON整数の形状 JSON配列の形状にする"),
          jsonShapeFeature(
              "空のJSONオブジェクト形状",
              BuiltinOperation.JSON_SHAPE_EMPTY_OBJECT,
              "メンバー制約のないJSONオブジェクト形状を作ります。",
              List.of(),
              List.of("JSON形状"),
              "空のJSONオブジェクト形状"),
          jsonShapeFeature(
              "JSON形状に必須キーを設定する",
              BuiltinOperation.JSON_SHAPE_SET_REQUIRED,
              "JSONオブジェクト形状へ必須キーを設定します。",
              List.of("JSON形状", "文字列", "JSON形状"),
              List.of("JSON形状"),
              "空のJSONオブジェクト形状と「id」と JSON整数の形状を JSON形状に必須キーを設定する"),
          jsonShapeFeature(
              "JSON形状に任意キーを設定する",
              BuiltinOperation.JSON_SHAPE_SET_OPTIONAL,
              "JSONオブジェクト形状へ任意キーを設定します。",
              List.of("JSON形状", "文字列", "JSON形状"),
              List.of("JSON形状"),
              "空のJSONオブジェクト形状と「name」と JSON文字列の形状を JSON形状に任意キーを設定する"),
          jsonShapeFeature(
              "JSON形状をヌル許容にする",
              BuiltinOperation.JSON_SHAPE_NULLABLE,
              "JSON nullまたは元の形状を受理する形状を作ります。",
              List.of("JSON形状"),
              List.of("JSON形状"),
              "JSON文字列の形状 JSON形状をヌル許容にする"),
          jsonShapeFeature(
              "JSONの形状を検証する",
              BuiltinOperation.JSON_SHAPE_VALIDATE,
              "JSON値を形状で検証し、原値または閉じた失敗配列を返します。",
              List.of("JSON", "JSON形状"),
              List.of("結果<JSON,配列<JSON形状失敗>>"),
              "JSONヌルと JSONヌルの形状を JSONの形状を検証する"),
          jsonShapeFeature(
              "JSON形状失敗の種類を取り出す",
              BuiltinOperation.JSON_SHAPE_FAILURE_KIND,
              "JSON形状失敗から閉じた種類を取り出します。",
              List.of("JSON形状失敗"),
              List.of("文字列"),
              "JSON形状失敗の種類を取り出す"),
          jsonShapeFeature(
              "JSON形状失敗のパスを取り出す",
              BuiltinOperation.JSON_SHAPE_FAILURE_PATH,
              "JSON形状失敗からJSON Pointer形式のパスを取り出します。",
              List.of("JSON形状失敗"),
              List.of("文字列"),
              "JSON形状失敗のパスを取り出す"),
          jsonShapeFeature(
              "JSON形状失敗の期待種類を取り出す",
              BuiltinOperation.JSON_SHAPE_FAILURE_EXPECTED_KIND,
              "JSON形状失敗から期待したJSON種類を取り出します。",
              List.of("JSON形状失敗"),
              List.of("文字列"),
              "JSON形状失敗の期待種類を取り出す"),
          jsonShapeFeature(
              "JSON形状失敗の実際種類を取り出す",
              BuiltinOperation.JSON_SHAPE_FAILURE_ACTUAL_KIND,
              "JSON形状失敗から実際のJSON種類を取り出します。",
              List.of("JSON形状失敗"),
              List.of("文字列"),
              "JSON形状失敗の実際種類を取り出す"),
          jsonErgonomicsFeature(
              "JSONをポインターで任意参照する",
              BuiltinOperation.JSON_POINTER_GET_OPTIONAL,
              "RFC 6901 JSON Pointerで参照し、対象がなければ値なしを返します。",
              List.of("JSON", "文字列"),
              List.of("任意<JSON>"),
              "「{\"items\":[1]}」を JSONを解析する 「/items/0」を JSONをポインターで任意参照する"),
          jsonErgonomicsFeature(
              "JSONオブジェクトを構築する",
              BuiltinOperation.JSON_OBJECT_BUILD,
              "同じ長さのキー配列とJSON値配列から不変オブジェクトを構築します。",
              List.of("配列<文字列>", "配列<JSON>"),
              List.of("JSON"),
              "【「id」】と 【1を 整数をJSONに変換する】を JSONオブジェクトを構築する"),
          fixedText(
              "大文字に変換する", BuiltinOperation.STRING_TO_UPPER_CASE, List.of("文字列"), List.of("文字列")),
          fixedText(
              "小文字に変換する", BuiltinOperation.STRING_TO_LOWER_CASE, List.of("文字列"), List.of("文字列")),
          fixedText(
              "大小文字を無視して比較する",
              BuiltinOperation.STRING_CASE_INSENSITIVE_COMPARE,
              List.of("文字列", "文字列"),
              List.of("整数")));

  private static final Map<String, BuiltinWord> BY_NAME = indexByName();
  private static final Set<String> CANONICAL_NAMES = canonicalNameSet();

  private BuiltinDictionary() {}

  /**
   * 仕様順に並んだ全辞書項目を返します。
   *
   * @return 不変の辞書項目リスト
   */
  public static List<BuiltinWord> words() {
    return WORDS;
  }

  /**
   * 正規名または別名から辞書項目を検索します。
   *
   * @param name 正規化済みの名前
   * @return 一致した辞書項目
   */
  public static Optional<BuiltinWord> find(String name) {
    return Optional.ofNullable(BY_NAME.get(name));
  }

  /**
   * 全組み込み単語の正規名を返します。
   *
   * @return 不変の名前集合
   */
  public static Set<String> canonicalNames() {
    return CANONICAL_NAMES;
  }

  private static BuiltinWord fixed(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "CORE",
        example);
  }

  private static BuiltinWord display(
      String name, BuiltinOperation operation, String description, String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        List.of("表示可能"),
        List.of(),
        BuiltinTypeRule.DISPLAYABLE,
        operation,
        Set.of(CONSOLE_OUTPUT),
        "CORE",
        example);
  }

  private static BuiltinWord numeric(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    String featureGroup =
        switch (name) {
          case "足す", "引く", "掛ける" -> "CORE";
          case "比べて小さい" -> "FLOW";
          default -> "NUM";
        };
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.SAME_NUMERIC_TYPE,
        operation,
        Set.of(),
        featureGroup,
        example);
  }

  private static BuiltinWord independentNumeric(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.INDEPENDENT_NUMERIC_INPUTS,
        operation,
        Set.of(),
        "NUM",
        example);
  }

  private static BuiltinWord fixedNumeric(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "NUM",
        example);
  }

  private static BuiltinWord roundingModeValue(String name) {
    return new BuiltinWord(
        name,
        Set.of(),
        "丸め方法を表す組み込み値です。",
        List.of(),
        List.of("丸め方法"),
        BuiltinTypeRule.FIXED,
        BuiltinOperation.ROUNDING_MODE_VALUE,
        Set.of(),
        "NUM",
        name);
  }

  private static BuiltinWord fixedText(
      String name, BuiltinOperation operation, List<String> inputs, List<String> outputs) {
    return new BuiltinWord(
        name,
        Set.of(),
        textFeatureDescription(operation),
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "TEXT",
        textFeatureExample(operation));
  }

  private static BuiltinWord jsonFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "JSON",
        example);
  }

  private static BuiltinWord optionalFeature(
      String name,
      BuiltinOperation operation,
      BuiltinTypeRule typeRule,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        typeRule,
        operation,
        Set.of(),
        "OPT",
        example);
  }

  private static BuiltinWord resultFeature(
      String name,
      BuiltinOperation operation,
      BuiltinTypeRule typeRule,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        typeRule,
        operation,
        Set.of(),
        "RESULT",
        example);
  }

  private static BuiltinWord recoverableJsonFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "RJSON",
        example);
  }

  private static BuiltinWord byteSequenceFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "BYTES",
        example);
  }

  private static BuiltinWord httpsFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "HTTPS",
        example);
  }

  private static BuiltinWord workspaceFileFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      Set<String> capabilities,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        capabilities,
        capabilities,
        true,
        "WST",
        example);
  }

  private static BuiltinWord delimitedTableFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "WST",
        example);
  }

  private static BuiltinWord jsonShapeFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "JSHAPE",
        example);
  }

  private static BuiltinWord jsonErgonomicsFeature(
      String name,
      BuiltinOperation operation,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        Set.of(),
        "JERG",
        example);
  }

  private static BuiltinWord hostIo(
      String name,
      BuiltinOperation operation,
      List<String> inputs,
      List<String> outputs,
      String capability,
      boolean returnsNormally) {
    Set<String> capabilities = capability == null ? Set.of() : Set.of(capability);
    return new BuiltinWord(
        name,
        Set.of(),
        hostIoDescription(operation),
        inputs,
        outputs,
        BuiltinTypeRule.FIXED,
        operation,
        capabilities,
        capabilities,
        returnsNormally,
        "IO",
        hostIoExample(operation));
  }

  private static String textFeatureDescription(BuiltinOperation operation) {
    return switch (operation) {
      case STRING_CONCAT -> "2文字列を順に連結します。";
      case STRING_COMPARE -> "2文字列をUnicodeスカラー値順で比較します。";
      case STRING_TO_UPPER_CASE -> "Unicode 16.0の既定完全大文字写像を適用します。";
      case STRING_TO_LOWER_CASE -> "Unicode 16.0の既定完全小文字写像を適用します。";
      case STRING_CASE_INSENSITIVE_COMPARE -> "Unicode 16.0の既定完全ケースフォールド後に比較します。";
      case STRING_TRIM -> "文字列の前後から規定の空白を除きます。";
      case GRAPHEME_LENGTH -> "文字列の拡張書記素クラスタ数を返します。";
      case GRAPHEME_GET -> "書記素位置で文字を1つ取り出します。";
      case GRAPHEME_SLICE -> "書記素の半開区間を新しい文字列として返します。";
      case GRAPHEME_FIND -> "書記素位置で部分文字列を探します。";
      case STRING_REPLACE -> "一致する通常文字列をすべて置き換えます。";
      case STRING_SPLIT -> "通常文字列の区切りで文字列を分割します。";
      case CODE_POINT_LENGTH -> "文字列のUnicodeコードポイント数を返します。";
      case CODE_POINT_GET -> "コードポイント位置で文字を1つ取り出します。";
      case CODE_POINT_SLICE -> "コードポイントの半開区間を新しい文字列として返します。";
      case CODE_POINT_FIND -> "コードポイント位置で部分文字列を探します。";
      case INTEGER_TO_STRING -> "整数を正規10進表記の文字列へ変換します。";
      case DECIMAL_TO_STRING -> "小数を正規固定小数点表記の文字列へ変換します。";
      case STRING_TO_INTEGER -> "正規10進表記の文字列を整数へ変換します。";
      case STRING_TO_DECIMAL -> "正規小数表記の文字列を小数へ変換します。";
      case REGEX_FULL_MATCH -> "文字列全体が正規表現に一致するかを返します。";
      case REGEX_CONTAINS -> "正規表現に一致する部分があるかを返します。";
      case REGEX_FIRST -> "正規表現に最初に一致した部分文字列を返します。";
      case REGEX_NAMED_GROUP -> "最初の一致から指定した名前付き部分を返します。";
      case REGEX_REPLACE -> "正規表現に一致する部分をテンプレートで置き換えます。";
      case REGEX_SPLIT -> "正規表現の一致を区切りとして文字列を分割します。";
      default -> throw new IllegalArgumentException("not a text/regex operation: " + operation);
    };
  }

  private static String textFeatureExample(BuiltinOperation operation) {
    return switch (operation) {
      case STRING_CONCAT -> "「こん」と「にちは」を つなぐ";
      case STRING_COMPARE -> "「あ」と「い」を 文字列を比較する";
      case STRING_TO_UPPER_CASE -> "「Straße」を 大文字に変換する";
      case STRING_TO_LOWER_CASE -> "「ΟΣ」を 小文字に変換する";
      case STRING_CASE_INSENSITIVE_COMPARE -> "「Straße」と「STRASSE」を 大小文字を無視して比較する";
      case STRING_TRIM -> "「 前後 」を 前後の空白を除く";
      case GRAPHEME_LENGTH -> "「が𠮷」を 文字列の長さ";
      case GRAPHEME_GET -> "「赤青」と1を 文字列から取り出す";
      case GRAPHEME_SLICE -> "「赤青緑」と0と2を 文字列の一部を取り出す";
      case GRAPHEME_FIND -> "「赤青緑」と「青」を 文字列から探す";
      case STRING_REPLACE -> "「赤青赤」と「赤」と「白」を 文字列を置き換える";
      case STRING_SPLIT -> "「赤,青」と「,」を 文字列を分割する";
      case CODE_POINT_LENGTH -> "「が」を 文字列のコードポイント数";
      case CODE_POINT_GET -> "「が」と1を 文字列からコードポイントを取り出す";
      case CODE_POINT_SLICE -> "「が𠮷」と0と2を コードポイント範囲で文字列の一部を取り出す";
      case CODE_POINT_FIND -> "「が𠮷」と「𠮷」を コードポイント位置で文字列から探す";
      case INTEGER_TO_STRING -> "42 を 整数を文字列に変換する";
      case DECIMAL_TO_STRING -> "2.50 を 小数を文字列に変換する";
      case STRING_TO_INTEGER -> "「42」を 文字列を整数に変換する";
      case STRING_TO_DECIMAL -> "「2.50」を 文字列を小数に変換する";
      case REGEX_FULL_MATCH -> "「42」と 正規表現「[0-9]+」を 正規表現に完全一致する";
      case REGEX_CONTAINS -> "「番号42」と 正規表現「[0-9]+」を 正規表現を含む";
      case REGEX_FIRST -> "「番号42」と 正規表現「[0-9]+」を 正規表現で最初を取り出す";
      case REGEX_NAMED_GROUP -> "「番号42」と 正規表現「(?<number>[0-9]+)」と「number」を 正規表現の名前付き部分を取り出す";
      case REGEX_REPLACE -> "「番号42」と 正規表現「[0-9]+」と「番号」を 正規表現で置き換える";
      case REGEX_SPLIT -> "「赤,青」と 正規表現「,」を 正規表現で分割する";
      default -> throw new IllegalArgumentException("not a text/regex operation: " + operation);
    };
  }

  private static String hostIoDescription(BuiltinOperation operation) {
    return switch (operation) {
      case READ_LINE -> "標準入力から一行、終端、取消のいずれかを取得します。";
      case INPUT_IS_LINE -> "入力結果が完成した行かを調べ、元の入力結果も残します。";
      case INPUT_IS_END -> "入力結果が入力終端かを調べ、元の入力結果も残します。";
      case INPUT_IS_CANCEL -> "入力結果が取消かを調べ、元の入力結果も残します。";
      case INPUT_TAKE_LINE -> "行状態の入力結果から文字列を取り出します。";
      case INPUT_DROP -> "入力結果を状態にかかわらず破棄します。";
      case ERROR_NEWLINE -> "標準エラーへLFを1つ出力します。";
      case PROGRAM_EXIT -> "指定した0から255の終了コードでプログラムを終了します。";
      case PROCESS_ARGUMENTS -> "プログラムへ渡された起動引数を文字列配列で返します。";
      case PROGRAM_NAME -> "実行中プログラムの論理名を返します。";
      case PROGRAM_LOCATION -> "実行中プログラムの場所をURI文字列で返します。";
      case SLEEP -> "指定したミリ秒だけ実行環境へ待機を要求します。";
      case MONOTONIC_MILLISECONDS -> "実行開始からの非減少な経過ミリ秒を返します。";
      case WALL_TIME -> "実行環境の現在日時とUTCオフセットを取得します。";
      case DATE_TIME_TO_STRING -> "日時をロケール非依存の固定形式へ変換します。";
      default -> throw new IllegalArgumentException("not a host I/O operation: " + operation);
    };
  }

  private static String hostIoExample(BuiltinOperation operation) {
    return switch (operation) {
      case READ_LINE -> "一行を入力する";
      case INPUT_IS_LINE -> "一行を入力する 入力行である";
      case INPUT_IS_END -> "一行を入力する 入力終端である";
      case INPUT_IS_CANCEL -> "一行を入力する 入力キャンセルである";
      case INPUT_TAKE_LINE -> "一行を入力する 入力行を取り出す";
      case INPUT_DROP -> "一行を入力する 入力結果を捨てる";
      case ERROR_NEWLINE -> "エラー改行する";
      case PROGRAM_EXIT -> "0 を 終了する";
      case PROCESS_ARGUMENTS -> "起動引数を得る";
      case PROGRAM_NAME -> "プログラム名を得る";
      case PROGRAM_LOCATION -> "プログラムの場所を得る";
      case SLEEP -> "1000 を 待つ";
      case MONOTONIC_MILLISECONDS -> "単調ミリ秒を得る";
      case WALL_TIME -> "現在日時を得る";
      case DATE_TIME_TO_STRING -> "現在日時を得る 日時を文字列に変換する";
      default -> throw new IllegalArgumentException("not a host I/O operation: " + operation);
    };
  }

  private static BuiltinWord hostIoDisplay(String name, BuiltinOperation operation, boolean line) {
    return new BuiltinWord(
        name,
        Set.of(),
        line ? "値を標準エラーへ表示してLFを出力します。" : "値を標準エラーへ表示します。",
        List.of("表示可能"),
        List.of(),
        BuiltinTypeRule.DISPLAYABLE,
        operation,
        Set.of(CONSOLE_ERROR),
        Set.of(CONSOLE_ERROR),
        true,
        "IO",
        line ? "「記録」を エラー一行表示する" : "「記録」を エラー表示する");
  }

  private static BuiltinWord emptyArray(String name, String outputType) {
    return emptyArray(name, outputType, "ARRAY");
  }

  private static BuiltinWord emptyArray(String name, String outputType, String featureGroup) {
    return new BuiltinWord(
        name,
        Set.of(),
        "要素型が確定した空の不変配列を作ります。",
        List.of(),
        List.of(outputType),
        BuiltinTypeRule.FIXED,
        BuiltinOperation.EMPTY_ARRAY,
        Set.of(),
        featureGroup,
        name);
  }

  private static BuiltinWord arrayOperation(
      String name,
      BuiltinOperation operation,
      BuiltinTypeRule typeRule,
      String description,
      List<String> inputs,
      List<String> outputs,
      String example) {
    return new BuiltinWord(
        name,
        Set.of(),
        description,
        inputs,
        outputs,
        typeRule,
        operation,
        Set.of(),
        "ARRAY",
        example);
  }

  private static Map<String, BuiltinWord> indexByName() {
    var result = new LinkedHashMap<String, BuiltinWord>();
    for (BuiltinWord word : WORDS) {
      putUnique(result, word.canonicalName(), word);
      for (String alias : word.aliases()) {
        putUnique(result, alias, word);
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static Set<String> canonicalNameSet() {
    var result = new LinkedHashSet<String>();
    WORDS.forEach(word -> result.add(word.canonicalName()));
    return Collections.unmodifiableSet(result);
  }

  private static void putUnique(
      Map<String, BuiltinWord> dictionary, String name, BuiltinWord word) {
    if (dictionary.putIfAbsent(name, word) != null) {
      throw new ExceptionInInitializerError("duplicate builtin name: " + name);
    }
  }
}
