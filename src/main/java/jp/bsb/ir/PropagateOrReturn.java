package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.Propagation;
import jp.bsb.stdlib.ValueType;

/** 正常ペイロードを取り出して続行し、値なし・失敗なら指定ラッパー型で復帰します。 */
public record PropagateOrReturn(Propagation.Kind kind, ValueType returnType, SourceSpan span)
    implements IrInstruction {
  public PropagateOrReturn {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(returnType, "returnType");
    Objects.requireNonNull(span, "span");
    boolean matches =
        kind == Propagation.Kind.OPTIONAL ? returnType.isOptional() : returnType.isResult();
    if (!matches) {
      throw new IllegalArgumentException("propagation return type differs from its kind");
    }
  }

  @Override
  public String opcode() {
    return kind == Propagation.Kind.OPTIONAL
        ? "PropagateOptionalOrReturn"
        : "PropagateResultOrReturn";
  }
}
