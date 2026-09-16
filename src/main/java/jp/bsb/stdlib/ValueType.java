package jp.bsb.stdlib;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/**
 * 静的検査、IR、実行系が共有する具体的な値型です。
 *
 * <p>【コンピュータ科学の観点：代数的データ型】 値型は有限個の非配列型または許可された要素型を持つ配列型です。配列を単なる名前にせず、 要素型をデータとして保持することで、例えば{@code
 * 配列<整数>}と{@code 配列<文字列>}を構造的に区別できます。
 *
 * <p>この型が表す値はすべて具体型です。組み込み辞書で使う{@code T}や{@code 表示可能}は型制約であり、このモデルには含めません。
 */
public sealed interface ValueType permits ArrayType, OptionalType, ResultType, ScalarType {
  /** 型構築子を入れ子にできる最大段数です。 */
  int MAX_TYPE_CONSTRUCTOR_DEPTH = 256;

  /** 任意精度の符号付き整数です。 */
  ScalarType INTEGER = ScalarType.INTEGER;

  /** {@code はい}または{@code いいえ}の真偽値です。 */
  ScalarType BOOLEAN = ScalarType.BOOLEAN;

  /** 1拡張書記素クラスタの文字です。 */
  ScalarType CHARACTER = ScalarType.CHARACTER;

  /** Unicodeスカラー値列からなる文字列です。 */
  ScalarType STRING = ScalarType.STRING;

  /** 正確な任意精度10進小数です。 */
  ScalarType DECIMAL = ScalarType.DECIMAL;

  /** 5種類の組み込み値を持つ丸め方法型です。 */
  ScalarType ROUNDING_MODE = ScalarType.ROUNDING_MODE;

  /** Unicode 16.0適合層でコンパイル済みの正規表現型です。 */
  ScalarType REGEX = ScalarType.REGEX;

  /** 一行入力の行、終端、取消を区別する結果型です。 */
  ScalarType INPUT_RESULT = ScalarType.INPUT_RESULT;

  /** エポックミリ秒と取得時UTCオフセットを持つ日時型です。 */
  ScalarType DATE_TIME = ScalarType.DATE_TIME;

  /** nullを含む7値種別を1つの静的型で保持する第一級JSON型です。 */
  ScalarType JSON = ScalarType.JSON;

  /** 回復可能なJSON構文失敗の公開情報を保持する型です。 */
  ScalarType JSON_PARSE_FAILURE = ScalarType.JSON_PARSE_FAILURE;

  /** 任意のバイトを順序付きで保持する不変バイト列型です。 */
  ScalarType BYTE_SEQUENCE = ScalarType.BYTE_SEQUENCE;

  /** 回復可能なUTF-8復号失敗の公開情報を保持する型です。 */
  ScalarType UTF8_DECODE_FAILURE = ScalarType.UTF8_DECODE_FAILURE;

  /** 回復可能なBase64復号失敗の公開情報を保持する型です。 */
  ScalarType BASE64_DECODE_FAILURE = ScalarType.BASE64_DECODE_FAILURE;

  /** 接続・methodを含まない不変HTTP要求型です。 */
  ScalarType HTTP_REQUEST = ScalarType.HTTP_REQUEST;

  /** 完全HTTP応答型です。 */
  ScalarType HTTP_RESPONSE = ScalarType.HTTP_RESPONSE;

  /** 回復可能なHTTP通信失敗型です。 */
  ScalarType HTTP_SEND_FAILURE = ScalarType.HTTP_SEND_FAILURE;

  /** 回復可能なファイル読取失敗型です。 */
  ScalarType FILE_READ_FAILURE = ScalarType.FILE_READ_FAILURE;

  /** 回復可能なファイル書込失敗型です。 */
  ScalarType FILE_WRITE_FAILURE = ScalarType.FILE_WRITE_FAILURE;

  /** 回復可能なCSV/TSV解析失敗の公開情報を保持する型です。 */
  ScalarType DELIMITED_TEXT_PARSE_FAILURE = ScalarType.DELIMITED_TEXT_PARSE_FAILURE;

  /** JSON値へ適用する不変な形状定義型です。 */
  ScalarType JSON_SHAPE = ScalarType.JSON_SHAPE;

  /** JSON形状との不一致を表す閉じた失敗型です。 */
  ScalarType JSON_SHAPE_FAILURE = ScalarType.JSON_SHAPE_FAILURE;

  /**
   * スタック効果や診断へ出力する正式な型名を返します。
   *
   * @return BSBソース上の正規型名
   */
  String sourceName();

  /**
   * 型制約や未確定型ではなく、実行時の値へ割り当てられる具体型であることを返します。
   *
   * @return 常にtrue
   */
  boolean isConcrete();

  /**
   * 配列型であるかを返します。
   *
   * @return 配列型ならtrue
   */
  boolean isArray();

  /**
   * 任意型であるかを返します。
   *
   * @return 任意型ならtrue
   */
  boolean isOptional();

  /** 結果型であるかを返します。 */
  boolean isResult();

  /**
   * 現在の最大2次元配列で要素型にできるかを返します。
   *
   * @return 配列要素型にできる具体型ならtrue
   */
  boolean isArrayElementType();

  /**
   * 配列型の要素型を返します。
   *
   * @return 配列なら要素型、その他の型なら空
   */
  Optional<ValueType> arrayElementType();

  /**
   * 任意型の内包型を返します。
   *
   * @return 任意型なら内包型、その他なら空
   */
  Optional<ValueType> optionalElementType();

  /** 結果型の成功型を返します。 */
  Optional<ValueType> resultSuccessType();

  /** 結果型の失敗型を返します。 */
  Optional<ValueType> resultFailureType();

  /**
   * 検査済みの要素型から最大2次元の配列型を作ります。
   *
   * @param elementType 要素型
   * @return 要素型を保持する配列型
   * @throws NullPointerException 要素型がnullの場合
   * @throws IllegalArgumentException 禁止要素型または3次元以上になる場合
   */
  static ArrayType arrayOf(ValueType elementType) {
    return new ArrayType(elementType);
  }

  /**
   * 検査済みの具体型から任意型を作ります。
   *
   * @param elementType 内包型
   * @return 内包型を保持する任意型
   */
  static OptionalType optionalOf(ValueType elementType) {
    return new OptionalType(elementType);
  }

  /** 検査済みの成功型・失敗型から結果型を作ります。 */
  static ResultType resultOf(ValueType successType, ValueType failureType) {
    return new ResultType(successType, failureType);
  }

  /** 根から葉までに現れる型構築子の最大数を反復的に数えます。 */
  static int constructorDepth(ValueType root) {
    if (root == null) {
      throw new NullPointerException("root");
    }
    int maximum = 0;
    var work = new ArrayDeque<TypeDepth>();
    work.push(new TypeDepth(root, 0));
    while (!work.isEmpty()) {
      TypeDepth item = work.pop();
      maximum = Math.max(maximum, item.depth());
      if (item.type() instanceof OptionalType optional) {
        work.push(new TypeDepth(optional.elementType(), item.depth() + 1));
      } else if (item.type() instanceof ResultType result) {
        work.push(new TypeDepth(result.successType(), item.depth() + 1));
        work.push(new TypeDepth(result.failureType(), item.depth() + 1));
      } else if (item.type() instanceof ArrayType array) {
        work.push(new TypeDepth(array.elementType(), item.depth() + 1));
      }
    }
    return maximum;
  }

  /** 根から葉までの各経路に現れる配列構築子数の最大値を反復的に数えます。 */
  static int arrayConstructorDepth(ValueType root) {
    if (root == null) {
      throw new NullPointerException("root");
    }
    int maximum = 0;
    var work = new ArrayDeque<TypeDepth>();
    work.push(new TypeDepth(root, 0));
    while (!work.isEmpty()) {
      TypeDepth item = work.pop();
      ValueType type = item.type();
      int depth = item.depth();
      if (type instanceof ArrayType array) {
        int arrayDepth = depth + 1;
        maximum = Math.max(maximum, arrayDepth);
        work.push(new TypeDepth(array.elementType(), arrayDepth));
      } else if (type instanceof OptionalType optional) {
        work.push(new TypeDepth(optional.elementType(), depth));
      } else if (type instanceof ResultType result) {
        work.push(new TypeDepth(result.successType(), depth));
        work.push(new TypeDepth(result.failureType(), depth));
      }
    }
    return maximum;
  }

  /**
   * 正規型名を具体型へ変換します。
   *
   * <p>正規型名として、定義済みの非配列型と許可された葉型の1次元・2次元配列型を受理します。bare {@code 配列}、型引数不足、3次元以上、
   * 配列要素として許可されない型は具体型ではないため空を返し、構文解析・意味解析が文脈に応じた規定診断を選べるようにします。
   *
   * @param name 正規型名
   * @return 対応する具体型。不明または現在の言語で不正な型なら空
   */
  static Optional<ValueType> fromSourceName(String name) {
    if (name == null) {
      return Optional.empty();
    }
    TypeNameParser parser = new TypeNameParser(name);
    ValueType parsed = parser.parse(0);
    return parsed != null && parser.atEnd() ? Optional.of(parsed) : Optional.empty();
  }

  /**
   * バイト列の全非配列型を仕様順で返します。
   *
   * @return 不変のスカラー型一覧
   */
  static List<ScalarType> scalarTypes() {
    return ScalarType.all();
  }

  /**
   * バイト列で1次元配列の要素にできる型を仕様順で返します。
   *
   * @return 丸め方法と正規表現を除く不変の非配列型一覧
   */
  static List<ScalarType> arrayElementTypes() {
    return ScalarType.arrayElements();
  }

  /**
   * 既存型の順序を保ち、その後にバイト列の3型を加えた非配列型を返します。
   *
   * <p>配列型は要素型ごとに構成する値であり、この有限一覧には含めません。
   *
   * @return 仕様順の非配列型の新しい配列
   */
  static ScalarType[] values() {
    return ScalarType.values();
  }

  /** 正規型名だけを受理する、公開状態を持たない小さな再帰下降解析器です。 */
  final class TypeNameParser {
    private final String text;
    private int index;

    private TypeNameParser(String text) {
      this.text = text;
    }

    private ValueType parse(int constructorDepth) {
      for (ScalarType scalar : ScalarType.values()) {
        if (text.startsWith(scalar.sourceName(), index)) {
          int end = index + scalar.sourceName().length();
          if (end == text.length() || text.charAt(end) == '>' || text.charAt(end) == ',') {
            index = end;
            return scalar;
          }
        }
      }
      boolean array = text.startsWith("配列<", index);
      boolean optional = text.startsWith("任意<", index);
      boolean result = text.startsWith("結果<", index);
      if ((!array && !optional && !result) || constructorDepth >= MAX_TYPE_CONSTRUCTOR_DEPTH) {
        return null;
      }
      index += 3;
      ValueType argument = parse(constructorDepth + 1);
      if (argument == null) {
        return null;
      }
      ValueType second = null;
      if (result) {
        if (index >= text.length() || text.charAt(index) != ',') {
          return null;
        }
        index++;
        second = parse(constructorDepth + 1);
        if (second == null) {
          return null;
        }
      }
      if (index >= text.length() || text.charAt(index) != '>') {
        return null;
      }
      index++;
      if (array) {
        return argument.isArrayElementType() ? new ArrayType(argument) : null;
      }
      return result ? new ResultType(argument, second) : new OptionalType(argument);
    }

    private boolean atEnd() {
      return index == text.length();
    }
  }

  /** 型深さ計算中だけ使う不変作業項目です。 */
  record TypeDepth(ValueType type, int depth) {}
}
