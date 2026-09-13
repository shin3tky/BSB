package jp.bsb.stdlib;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 言語で使う具体的な非配列型です。 */
public enum ScalarType implements ValueType {
  /** 任意精度の符号付き整数です。 */
  INTEGER("整数", true),

  /** {@code はい}または{@code いいえ}の真偽値です。 */
  BOOLEAN("真偽", true),

  /** 1拡張書記素クラスタの文字です。 */
  CHARACTER("文字", true),

  /** Unicodeスカラー値列からなる文字列です。 */
  STRING("文字列", true),

  /** 正確な任意精度10進小数です。 */
  DECIMAL("小数", true),

  /** 小数除算と丸めで使用する5種類の丸め方法です。 */
  ROUNDING_MODE("丸め方法", false),

  /** Unicode 16.0適合層でコンパイル済みの正規表現です。 */
  REGEX("正規表現", false),

  /** 一行入力の行、終端、取消を区別する結果です。 */
  INPUT_RESULT("入力結果", false),

  /** エポックミリ秒と取得時UTCオフセットを持つ日時です。 */
  DATE_TIME("日時", false),

  /** 7値種別を内部に保持する第一級JSON値です。 */
  JSON("JSON", true),

  /** 回復可能なJSON構文失敗の公開情報だけを保持する値です。 */
  JSON_PARSE_FAILURE("JSON解析失敗", false),

  /** 0から255の値を順序付きで保持する不変バイト列です。 */
  BYTE_SEQUENCE("バイト列", false),

  /** 回復可能なUTF-8復号失敗の種類とバイト位置を保持する値です。 */
  UTF8_DECODE_FAILURE("UTF8復号失敗", false),

  /** 回復可能なBase64復号失敗の種類と文字位置を保持する値です。 */
  BASE64_DECODE_FAILURE("Base64復号失敗", false),

  /** 接続・methodを含まない不変なHTTP要求ビルダーです。 */
  HTTP_REQUEST("HTTP要求", false),

  /** 完全に受信・検証されたHTTP応答です。 */
  HTTP_RESPONSE("HTTP応答", false),

  /** 回復可能な通信失敗の閉じた種類を保持する値です。 */
  HTTP_SEND_FAILURE("HTTP送信失敗", false),

  /** 回復可能なファイル読取失敗の閉じた種類を保持する値です。 */
  FILE_READ_FAILURE("ファイル読取失敗", false),

  /** 回復可能なファイル書込失敗の閉じた種類を保持する値です。 */
  FILE_WRITE_FAILURE("ファイル書込失敗", false),

  /** 回復可能なCSV/TSV解析失敗の公開情報だけを保持する値です。 */
  DELIMITED_TEXT_PARSE_FAILURE("区切りテキスト解析失敗", false);

  private static final List<ScalarType> ALL = List.of(values());
  private static final List<ScalarType> ARRAY_ELEMENTS =
      ALL.stream().filter(ScalarType::isArrayElementType).toList();

  private final String sourceName;
  private final boolean arrayElementType;

  ScalarType(String sourceName, boolean arrayElementType) {
    this.sourceName = sourceName;
    this.arrayElementType = arrayElementType;
  }

  @Override
  public String sourceName() {
    return sourceName;
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public boolean isOptional() {
    return false;
  }

  @Override
  public boolean isResult() {
    return false;
  }

  @Override
  public boolean isArrayElementType() {
    return arrayElementType;
  }

  @Override
  public Optional<ValueType> arrayElementType() {
    return Optional.empty();
  }

  @Override
  public Optional<ValueType> optionalElementType() {
    return Optional.empty();
  }

  @Override
  public Optional<ValueType> resultSuccessType() {
    return Optional.empty();
  }

  @Override
  public Optional<ValueType> resultFailureType() {
    return Optional.empty();
  }

  /**
   * 正規名からスカラー型を検索します。
   *
   * @param name 正規型名
   * @return 対応するスカラー型。不明なら空
   */
  public static Optional<ScalarType> fromSourceName(String name) {
    return Arrays.stream(values()).filter(type -> type.sourceName.equals(name)).findFirst();
  }

  static List<ScalarType> all() {
    return ALL;
  }

  static List<ScalarType> arrayElements() {
    return ARRAY_ELEMENTS;
  }

  @Override
  public String toString() {
    return sourceName;
  }
}
