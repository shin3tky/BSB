package jp.bsb.ir;

import java.util.List;
import java.util.Objects;
import jp.bsb.stdlib.ValueType;

/**
 * 名前や型変数を残さず、IR呼出しまたはIR単語へ埋め込む具体的なスタック効果です。
 *
 * @param inputTypes スタックの底から頂上へ並べた具体的な入力型
 * @param outputTypes スタックの底から頂上へ並べた具体的な出力型
 */
public record IrStackEffect(List<ValueType> inputTypes, List<ValueType> outputTypes) {
  /** 具体型列を不変コピーとして保持します。 */
  public IrStackEffect {
    inputTypes = List.copyOf(inputTypes);
    outputTypes = List.copyOf(outputTypes);
    if (inputTypes.stream().anyMatch(Objects::isNull)
        || outputTypes.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("IR stack effect types");
    }
  }
}
