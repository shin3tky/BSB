package jp.bsb.binding;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 親をたどれる字句スコープです。
 *
 * @param id スコープID
 * @param kind 構文上のスコープ種別
 * @param parentId 親スコープ。大域スコープだけ空
 * @param ownerWord 所有する正規化済み単語名。大域スコープだけ空
 * @param span このスコープが覆うソース範囲
 */
public record LexicalScope(
    ScopeId id,
    LexicalScopeKind kind,
    Optional<ScopeId> parentId,
    Optional<String> ownerWord,
    SourceSpan span) {
  /** 大域と局所の必須関係を検証します。 */
  public LexicalScope {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(kind, "kind");
    parentId = Objects.requireNonNull(parentId, "parentId");
    ownerWord = Objects.requireNonNull(ownerWord, "ownerWord");
    Objects.requireNonNull(span, "span");
    ownerWord.ifPresent(
        value -> {
          if (value.isBlank()) {
            throw new IllegalArgumentException("owner word must not be blank");
          }
        });
    boolean global = kind == LexicalScopeKind.GLOBAL;
    if (global == parentId.isPresent() || global == ownerWord.isPresent()) {
      throw new IllegalArgumentException(
          "only the global scope may omit its parent and owner word");
    }
  }

  /**
   * 大域スコープを作ります。
   *
   * @param id スコープID
   * @param span プログラム全体の範囲
   * @return 大域スコープ
   */
  public static LexicalScope global(ScopeId id, SourceSpan span) {
    return new LexicalScope(id, LexicalScopeKind.GLOBAL, Optional.empty(), Optional.empty(), span);
  }

  /**
   * 大域スコープかを返します。
   *
   * @return 大域スコープならtrue
   */
  public boolean isGlobal() {
    return kind == LexicalScopeKind.GLOBAL;
  }
}
