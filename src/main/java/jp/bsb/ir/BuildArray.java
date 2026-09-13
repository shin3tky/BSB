package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ValueType;

/**
 * 直前に評価した同型要素をソース順の配列へまとめます。
 *
 * @param elementType 配列へ格納する具体要素型
 * @param elementCount データスタックから取り出す要素数
 * @param span 配列リテラルまたは型付き空配列値のソース範囲
 */
public record BuildArray(ValueType elementType, int elementCount, SourceSpan span)
    implements IrInstruction {
  /** 許可された具体要素型、非負の要素数、ソース位置を検証します。 */
  public BuildArray {
    Objects.requireNonNull(elementType, "elementType");
    if (!elementType.isArrayElementType()) {
      throw new IllegalArgumentException("BuildArray requires an available array element type");
    }
    if (elementCount < 0 || elementCount > ArrayLimits.MAX_LENGTH) {
      throw new IllegalArgumentException("array element count is outside the supported range");
    }
    Objects.requireNonNull(span, "span");
  }

  @Override
  public String opcode() {
    return "BuildArray";
  }
}
