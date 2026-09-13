package jp.bsb.binding;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/** 値束縛や保存スロットを持たない、大域の論理接続宣言名です。 */
public record LogicalConnectionName(String name, String spelling, SourceSpan nameSpan)
    implements DeclaredName {
  /** 宣言名を検証します。 */
  public LogicalConnectionName {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (spelling == null || spelling.isBlank()) {
      throw new IllegalArgumentException("spelling must not be blank");
    }
    Objects.requireNonNull(nameSpan, "nameSpan");
  }

  @Override
  public DeclaredNameKind declarationKind() {
    return DeclaredNameKind.LOGICAL_CONNECTION;
  }
}
