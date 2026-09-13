package jp.bsb.binding;

import java.util.Objects;
import java.util.OptionalInt;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 静的に解決された定数または変数の宣言です。
 *
 * @param id 元ソース順の安定した束縛ID
 * @param name Unicode正規化済みの名前
 * @param spelling 元ソースに書かれた表記
 * @param kind 定数または変数
 * @param storage 大域または局所の保存領域
 * @param scopeId 宣言を直接含む字句スコープ
 * @param typeState 宣言型の解析状態
 * @param globalInitializationOrder 大域初期化順。局所束縛では空
 * @param nameSpan 宣言名だけのソース範囲
 * @param declarationSpan 宣言全体のソース範囲
 */
public record Binding(
    BindingId id,
    String name,
    String spelling,
    BindingKind kind,
    BindingStorage storage,
    ScopeId scopeId,
    BindingTypeState typeState,
    OptionalInt globalInitializationOrder,
    SourceSpan nameSpan,
    SourceSpan declarationSpan)
    implements DeclaredName {
  /** 必須値、初期化方式、ソース範囲の不変条件を検証します。 */
  public Binding {
    Objects.requireNonNull(id, "id");
    name = requireText(name, "name");
    spelling = requireText(spelling, "spelling");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(scopeId, "scopeId");
    Objects.requireNonNull(typeState, "typeState");
    Objects.requireNonNull(globalInitializationOrder, "globalInitializationOrder");
    Objects.requireNonNull(nameSpan, "nameSpan");
    Objects.requireNonNull(declarationSpan, "declarationSpan");
    if ((storage == BindingStorage.GLOBAL) != globalInitializationOrder.isPresent()) {
      throw new IllegalArgumentException(
          "only a global binding must have a global initialization order");
    }
    if (globalInitializationOrder.isPresent() && globalInitializationOrder.getAsInt() < 1) {
      throw new IllegalArgumentException("global initialization order must be at least 1");
    }
    if (nameSpan.start().utf8Offset() < declarationSpan.start().utf8Offset()
        || nameSpan.end().utf8Offset() > declarationSpan.end().utf8Offset()) {
      throw new IllegalArgumentException("name span must be contained by declaration span");
    }
  }

  @Override
  public DeclaredNameKind declarationKind() {
    return kind.declaredNameKind();
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
