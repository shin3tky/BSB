package jp.bsb.binding;

import jp.bsb.diagnostics.SourceSpan;

/** 値束縛や保存スロットを持たない、大域の作業領域宣言名です。 */
public record WorkspaceName(String name, String spelling, SourceSpan nameSpan)
    implements DeclaredName {
  public WorkspaceName {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("name must not be blank");
    if (spelling == null || spelling.isBlank()) {
      throw new IllegalArgumentException("spelling must not be blank");
    }
    if (nameSpan == null) throw new NullPointerException("nameSpan");
  }

  @Override
  public DeclaredNameKind declarationKind() {
    return DeclaredNameKind.WORKSPACE;
  }
}
