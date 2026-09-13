package jp.bsb.analyzer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WorkspaceDeclaration;

/** 作業領域宣言と、ファイル語の静的引数から宣言への解決結果を保持します。 */
public final class WorkspaceResolution {
  private static final WorkspaceResolution EMPTY = new WorkspaceResolution(List.of(), List.of());
  private final List<WorkspaceDeclaration> declarations;
  private final List<Use> uses;
  private final Map<WordCall, Use> usesByCall;

  public WorkspaceResolution(List<WorkspaceDeclaration> declarations, List<Use> uses) {
    this.declarations = List.copyOf(declarations);
    this.uses = List.copyOf(uses);
    var known = Collections.newSetFromMap(new IdentityHashMap<WorkspaceDeclaration, Boolean>());
    known.addAll(this.declarations);
    long previousOffset = -1;
    for (WorkspaceDeclaration declaration : this.declarations) {
      Objects.requireNonNull(declaration, "declaration");
      if (declaration.nameSpan().start().utf8Offset() < previousOffset) {
        throw new IllegalArgumentException("workspace declarations must be in source order");
      }
      previousOffset = declaration.nameSpan().start().utf8Offset();
    }
    var byCall = new IdentityHashMap<WordCall, Use>();
    previousOffset = -1;
    for (Use use : this.uses) {
      Objects.requireNonNull(use, "use");
      if (!known.contains(use.declaration())) {
        throw new IllegalArgumentException("workspace use refers to an unknown declaration");
      }
      if (use.call().span().start().utf8Offset() < previousOffset) {
        throw new IllegalArgumentException("workspace uses must be in source order");
      }
      previousOffset = use.call().span().start().utf8Offset();
      if (byCall.put(use.call(), use) != null) {
        throw new IllegalArgumentException("a call may have only one workspace use");
      }
    }
    usesByCall = Collections.unmodifiableMap(byCall);
  }

  public static WorkspaceResolution empty() {
    return EMPTY;
  }

  public List<WorkspaceDeclaration> declarations() {
    return declarations;
  }

  public List<Use> uses() {
    return uses;
  }

  public Optional<Use> resolve(WordCall call) {
    return Optional.ofNullable(usesByCall.get(Objects.requireNonNull(call, "call")));
  }

  /** 1個のファイル呼出しから宣言への解決済み参照です。 */
  public record Use(
      WordCall call, WorkspaceDeclaration declaration, String ownerWord, String operation) {
    public Use {
      Objects.requireNonNull(call, "call");
      Objects.requireNonNull(declaration, "declaration");
      if (ownerWord == null || ownerWord.isBlank()) {
        throw new IllegalArgumentException("ownerWord must not be blank");
      }
      if (!operation.equals("read") && !operation.equals("write")) {
        throw new IllegalArgumentException("workspace operation must be read or write");
      }
    }
  }
}
