package jp.bsb.analyzer;

import java.util.List;
import jp.bsb.stdlib.ValueType;

/**
 * 利用者定義単語が消費する入力型列と、呼出し後に積む出力型列です。
 *
 * @param inputTypes スタックの下側から上側へ並べた入力型
 * @param outputTypes スタックの下側から上側へ並べた出力型
 */
public record WordSignature(List<ValueType> inputTypes, List<ValueType> outputTypes) {
  /** 入出力型列を不変リストへコピーします。 */
  public WordSignature {
    inputTypes = List.copyOf(inputTypes);
    outputTypes = List.copyOf(outputTypes);
  }
}
