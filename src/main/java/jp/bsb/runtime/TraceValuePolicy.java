package jp.bsb.runtime;

import jp.bsb.stdlib.ScalarType;

/** トレースへ値の内容を開示してよいかを決める内部ポリシーです。 */
@FunctionalInterface
public interface TraceValuePolicy {
  /**
   * 値の内容をトレースへ表示してよいかを返します。
   *
   * @param value 表示候補の実行時値
   * @return 内容を表示してよい場合はtrue、伏せる場合はfalse
   */
  boolean mayReveal(RuntimeValue value);

  /**
   * 束縛の公開可能な4値型だけを表示する既定ポリシーです。
   *
   * <p>後段で新しい実行時型を追加しても自動的には開示しません。公開可能と判断した型だけを、この許可リストへ明示的に追加します。
   *
   * @return 現行4値型だけを表示するポリシー
   */
  static TraceValuePolicy bindings() {
    return value ->
        switch (value) {
          case IntegerValue ignored -> true;
          case BooleanValue ignored -> true;
          case CharacterValue ignored -> true;
          case StringValue ignored -> true;
          case DecimalValue ignored -> false;
          case RoundingModeValue ignored -> false;
          case RegexValue ignored -> false;
          case ArrayValue ignored -> false;
          case InputResultValue ignored -> false;
          case DateTimeValue ignored -> false;
          case JsonRuntimeValue ignored -> false;
          case JsonParseFailureValue ignored -> false;
          case ByteSequenceValue ignored -> false;
          case Utf8DecodeFailureValue ignored -> false;
          case Base64DecodeFailureValue ignored -> false;
          case HttpRequestValue ignored -> false;
          case HttpResponseValue ignored -> false;
          case HttpSendFailureValue ignored -> false;
          case FileReadFailureValue ignored -> false;
          case FileWriteFailureValue ignored -> false;
          case DelimitedTextParseFailureValue ignored -> false;
          case JsonShapeValue ignored -> false;
          case JsonShapeFailureValue ignored -> false;
          case OptionalValue ignored -> false;
          case ResultValue ignored -> false;
        };
  }

  /**
   * 配列の配列を、既存4スカラー型とともに開示する既定ポリシーです。
   *
   * @return 配列までの公開可能な値を表示するポリシー
   */
  static TraceValuePolicy arrays() {
    return value ->
        switch (value) {
          case IntegerValue ignored -> true;
          case BooleanValue ignored -> true;
          case CharacterValue ignored -> true;
          case StringValue ignored -> true;
          case ArrayValue array ->
              array.elementType() != ScalarType.DECIMAL && array.elementType() != ScalarType.JSON;
          case DecimalValue ignored -> false;
          case RoundingModeValue ignored -> false;
          case RegexValue ignored -> false;
          case InputResultValue ignored -> false;
          case DateTimeValue ignored -> false;
          case JsonRuntimeValue ignored -> false;
          case JsonParseFailureValue ignored -> false;
          case ByteSequenceValue ignored -> false;
          case Utf8DecodeFailureValue ignored -> false;
          case Base64DecodeFailureValue ignored -> false;
          case HttpRequestValue ignored -> false;
          case HttpResponseValue ignored -> false;
          case HttpSendFailureValue ignored -> false;
          case FileReadFailureValue ignored -> false;
          case FileWriteFailureValue ignored -> false;
          case DelimitedTextParseFailureValue ignored -> false;
          case JsonShapeValue ignored -> false;
          case JsonShapeFailureValue ignored -> false;
          case OptionalValue ignored -> false;
          case ResultValue ignored -> false;
        };
  }

  /**
   * 数値演算の小数と丸め方法を、既存値および小数配列とともに開示する既定ポリシーです。
   *
   * @return 数値演算までの公開可能な値を表示するポリシー
   */
  static TraceValuePolicy numerics() {
    return value ->
        switch (value) {
          case IntegerValue ignored -> true;
          case DecimalValue ignored -> true;
          case RoundingModeValue ignored -> true;
          case BooleanValue ignored -> true;
          case CharacterValue ignored -> true;
          case StringValue ignored -> true;
          case RegexValue ignored -> false;
          case ArrayValue array -> array.elementType() != ScalarType.JSON;
          case InputResultValue ignored -> false;
          case DateTimeValue ignored -> false;
          case JsonRuntimeValue ignored -> false;
          case JsonParseFailureValue ignored -> false;
          case ByteSequenceValue ignored -> false;
          case Utf8DecodeFailureValue ignored -> false;
          case Base64DecodeFailureValue ignored -> false;
          case HttpRequestValue ignored -> false;
          case HttpResponseValue ignored -> false;
          case HttpSendFailureValue ignored -> false;
          case FileReadFailureValue ignored -> false;
          case FileWriteFailureValue ignored -> false;
          case DelimitedTextParseFailureValue ignored -> false;
          case JsonShapeValue ignored -> false;
          case JsonShapeFailureValue ignored -> false;
          case OptionalValue ignored -> false;
          case ResultValue ignored -> false;
        };
  }

  /**
   * 文字列・正規表現の文字列・正規表現と文字列配列を、既存値とともに開示する既定ポリシーです。
   *
   * @return 文字列・正規表現までの公開可能な値を表示するポリシー
   */
  static TraceValuePolicy textRegex() {
    return value ->
        switch (value) {
          case IntegerValue ignored -> true;
          case DecimalValue ignored -> true;
          case RoundingModeValue ignored -> true;
          case BooleanValue ignored -> true;
          case CharacterValue ignored -> true;
          case StringValue ignored -> true;
          case RegexValue ignored -> true;
          case ArrayValue array -> array.elementType() != ScalarType.JSON;
          case InputResultValue ignored -> false;
          case DateTimeValue ignored -> false;
          case JsonRuntimeValue ignored -> false;
          case JsonParseFailureValue ignored -> false;
          case ByteSequenceValue ignored -> false;
          case Utf8DecodeFailureValue ignored -> false;
          case Base64DecodeFailureValue ignored -> false;
          case HttpRequestValue ignored -> false;
          case HttpResponseValue ignored -> false;
          case HttpSendFailureValue ignored -> false;
          case FileReadFailureValue ignored -> false;
          case FileWriteFailureValue ignored -> false;
          case DelimitedTextParseFailureValue ignored -> false;
          case JsonShapeValue ignored -> false;
          case JsonShapeFailureValue ignored -> false;
          case OptionalValue ignored -> false;
          case ResultValue ignored -> false;
        };
  }

  /** ホスト入出力の入力行本文を伏せ、終端・取消状態だけを開示する既定ポリシーです。 */
  static TraceValuePolicy hostIo() {
    return value ->
        switch (value) {
          case InputResultValue input -> input.state() != InputResultValue.State.LINE;
          case DateTimeValue ignored -> true;
          case JsonRuntimeValue ignored -> false;
          case JsonParseFailureValue ignored -> false;
          case ArrayValue array when array.elementType() == ScalarType.JSON -> false;
          case OptionalValue ignored -> false;
          case ResultValue ignored -> false;
          default -> textRegex().mayReveal(value);
        };
  }

  /** JSONのJSON処理で、JSON入力になり得る文字列・文字列配列も含めて既定非開示にします。 */
  static TraceValuePolicy json() {
    return value ->
        switch (value) {
          case StringValue ignored -> false;
          case JsonRuntimeValue ignored -> false;
          case JsonParseFailureValue ignored -> false;
          case ArrayValue array
              when array.elementType() == ScalarType.STRING
                  || array.elementType() == ScalarType.JSON ->
              false;
          default -> hostIo().mayReveal(value);
        };
  }

  /** 任意値の任意値を、内包型のJSON非開示規則へ推移的に従わせます。 */
  static TraceValuePolicy optional() {
    return value ->
        switch (value) {
          case OptionalValue optional -> mayRevealOptionalType(optional.type().elementType());
          case ResultValue ignored -> false;
          default -> json().mayReveal(value);
        };
  }

  /** 結果値の結果値と任意値を、非選択側を含む全型引数の非開示規則へ従わせます。 */
  static TraceValuePolicy result() {
    return value ->
        switch (value) {
          case OptionalValue optional -> mayRevealResultType(optional.type());
          case ResultValue result -> mayRevealResultType(result.type());
          case ArrayValue array -> mayRevealResultType(array.type());
          case JsonParseFailureValue ignored -> false;
          default -> json().mayReveal(value);
        };
  }

  /** バイト列の3型と、それらを型引数に持つラッパーを常に伏せます。 */
  static TraceValuePolicy byteSequence() {
    return value ->
        switch (value) {
          case ByteSequenceValue ignored -> false;
          case Utf8DecodeFailureValue ignored -> false;
          case Base64DecodeFailureValue ignored -> false;
          case HttpRequestValue ignored -> false;
          case HttpResponseValue ignored -> false;
          case HttpSendFailureValue ignored -> false;
          case FileReadFailureValue ignored -> false;
          case FileWriteFailureValue ignored -> false;
          case DelimitedTextParseFailureValue ignored -> false;
          case OptionalValue optional -> mayRevealByteSequenceType(optional.type());
          case ResultValue result -> mayRevealByteSequenceType(result.type());
          default -> result().mayReveal(value);
        };
  }

  private static boolean mayRevealOptionalType(jp.bsb.stdlib.ValueType type) {
    jp.bsb.stdlib.ValueType current = type;
    while (current.isOptional()) {
      current = current.optionalElementType().orElseThrow();
    }
    if (current.equals(jp.bsb.stdlib.ValueType.STRING)
        || current.equals(jp.bsb.stdlib.ValueType.JSON)
        || current.equals(jp.bsb.stdlib.ValueType.INPUT_RESULT)
        || current.equals(jp.bsb.stdlib.ValueType.JSON_PARSE_FAILURE)) {
      return false;
    }
    return current.arrayElementType().stream()
        .noneMatch(element -> element == ScalarType.STRING || element == ScalarType.JSON);
  }

  private static boolean mayRevealResultType(jp.bsb.stdlib.ValueType root) {
    var work = new java.util.ArrayDeque<jp.bsb.stdlib.ValueType>();
    work.push(root);
    while (!work.isEmpty()) {
      jp.bsb.stdlib.ValueType type = work.pop();
      if (type.equals(jp.bsb.stdlib.ValueType.STRING)
          || type.equals(jp.bsb.stdlib.ValueType.JSON)
          || type.equals(jp.bsb.stdlib.ValueType.INPUT_RESULT)
          || type.equals(jp.bsb.stdlib.ValueType.JSON_PARSE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.JSON_SHAPE)
          || type.equals(jp.bsb.stdlib.ValueType.JSON_SHAPE_FAILURE)) {
        return false;
      }
      if (type instanceof jp.bsb.stdlib.OptionalType optional) {
        work.push(optional.elementType());
      } else if (type instanceof jp.bsb.stdlib.ResultType result) {
        work.push(result.successType());
        work.push(result.failureType());
      } else if (type instanceof jp.bsb.stdlib.ArrayType array) {
        work.push(array.elementType());
      }
    }
    return true;
  }

  private static boolean mayRevealByteSequenceType(jp.bsb.stdlib.ValueType root) {
    var work = new java.util.ArrayDeque<jp.bsb.stdlib.ValueType>();
    work.push(root);
    while (!work.isEmpty()) {
      jp.bsb.stdlib.ValueType type = work.pop();
      if (type.equals(jp.bsb.stdlib.ValueType.BYTE_SEQUENCE)
          || type.equals(jp.bsb.stdlib.ValueType.UTF8_DECODE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.BASE64_DECODE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.HTTP_REQUEST)
          || type.equals(jp.bsb.stdlib.ValueType.HTTP_RESPONSE)
          || type.equals(jp.bsb.stdlib.ValueType.HTTP_SEND_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.FILE_READ_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.FILE_WRITE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.DELIMITED_TEXT_PARSE_FAILURE)) {
        return false;
      }
      if (type instanceof jp.bsb.stdlib.OptionalType optional) {
        work.push(optional.elementType());
      } else if (type instanceof jp.bsb.stdlib.ResultType result) {
        work.push(result.successType());
        work.push(result.failureType());
      }
    }
    return mayRevealResultType(root);
  }
}
