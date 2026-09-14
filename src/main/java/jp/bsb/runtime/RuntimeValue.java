package jp.bsb.runtime;

import jp.bsb.stdlib.ValueType;

/**
 * 実行時データスタックへ積める値の共通型です。
 *
 * <p>【コンピュータ科学の観点：タグ付き直和】Javaの {@code Object} を直接積まず、取り得る型を {@code sealed}
 * で閉じています。そのため、新しい値型を追加したとき、処理漏れをコンパイラが知らせられます。
 */
public sealed interface RuntimeValue
    permits ArrayValue,
        IntegerValue,
        DecimalValue,
        RoundingModeValue,
        BooleanValue,
        CharacterValue,
        StringValue,
        RegexValue,
        InputResultValue,
        DateTimeValue,
        JsonRuntimeValue,
        JsonParseFailureValue,
        ByteSequenceValue,
        Utf8DecodeFailureValue,
        Base64DecodeFailureValue,
        HttpRequestValue,
        HttpResponseValue,
        HttpSendFailureValue,
        FileReadFailureValue,
        FileWriteFailureValue,
        DelimitedTextParseFailureValue,
        JsonShapeValue,
        JsonShapeFailureValue,
        OptionalValue,
        ResultValue {
  /**
   * 値のBSB型を返します。
   *
   * @return 値が持つ具体型
   */
  ValueType type();

  /**
   * {@code 表示する} が出力する正規文字列を返します。
   *
   * @return 表示用文字列
   */
  String displayText();

  /**
   * トレースで値型と内容を区別できる表現を返します。
   *
   * @return {@code 型:値} 形式の文字列
   */
  default String traceText() {
    return type().sourceName() + ":" + displayText();
  }
}
