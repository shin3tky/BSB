package jp.bsb.analyzer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.frontend.ast.LogicalConnectionDeclaration;
import jp.bsb.frontend.ast.WordCall;

/** 論理接続宣言と、静的引数から宣言への解決結果を保持します。 */
public final class LogicalConnectionResolution {
  private static final LogicalConnectionResolution EMPTY =
      new LogicalConnectionResolution(List.of(), List.of());

  private final List<LogicalConnectionDeclaration> declarations;
  private final List<Use> uses;
  private final Map<WordCall, Use> usesByCall;

  /** 宣言と利用をソース順で検証し、AST同一性による参照表を作ります。 */
  public LogicalConnectionResolution(
      List<LogicalConnectionDeclaration> declarations, List<Use> uses) {
    this.declarations = List.copyOf(declarations);
    this.uses = List.copyOf(uses);
    var known =
        Collections.newSetFromMap(new IdentityHashMap<LogicalConnectionDeclaration, Boolean>());
    known.addAll(this.declarations);
    long previousOffset = -1;
    for (LogicalConnectionDeclaration declaration : this.declarations) {
      Objects.requireNonNull(declaration, "declaration");
      if (declaration.nameSpan().start().utf8Offset() < previousOffset) {
        throw new IllegalArgumentException(
            "logical connection declarations must be in source order");
      }
      previousOffset = declaration.nameSpan().start().utf8Offset();
    }
    var byCall = new IdentityHashMap<WordCall, Use>();
    previousOffset = -1;
    for (Use use : this.uses) {
      Objects.requireNonNull(use, "use");
      if (!known.contains(use.declaration())) {
        throw new IllegalArgumentException(
            "logical connection use refers to an unknown declaration");
      }
      if (use.call().span().start().utf8Offset() < previousOffset) {
        throw new IllegalArgumentException("logical connection uses must be in source order");
      }
      previousOffset = use.call().span().start().utf8Offset();
      if (byCall.put(use.call(), use) != null) {
        throw new IllegalArgumentException("a call may have only one logical connection use");
      }
    }
    usesByCall = Collections.unmodifiableMap(byCall);
  }

  /** 空の解決結果を返します。 */
  public static LogicalConnectionResolution empty() {
    return EMPTY;
  }

  public List<LogicalConnectionDeclaration> declarations() {
    return declarations;
  }

  public List<Use> uses() {
    return uses;
  }

  public Optional<Use> resolve(WordCall call) {
    return Optional.ofNullable(usesByCall.get(Objects.requireNonNull(call, "call")));
  }

  /** 1個の確認呼出しから宣言への解決済み参照です。 */
  public record Use(
      WordCall call,
      LogicalConnectionDeclaration declaration,
      String ownerWord,
      String operation,
      Optional<String> httpMethod) {
    /** 論理接続のmethodを持たない接続確認利用を作ります。 */
    public Use(
        WordCall call,
        LogicalConnectionDeclaration declaration,
        String ownerWord,
        String operation) {
      this(call, declaration, ownerWord, operation, Optional.empty());
    }

    public Use {
      Objects.requireNonNull(call, "call");
      Objects.requireNonNull(declaration, "declaration");
      if (ownerWord == null || ownerWord.isBlank()) {
        throw new IllegalArgumentException("ownerWord must not be blank");
      }
      if (!operation.equals("resolve")) {
        throw new IllegalArgumentException("the logical connections operation must be resolve");
      }
      httpMethod = Objects.requireNonNull(httpMethod, "httpMethod");
    }
  }
}
