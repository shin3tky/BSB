package jp.bsb.binding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.frontend.ast.AstNode;

/**
 * 成功した名前解決の完全な結果です。
 *
 * <p>宣言一覧は元ソース順、束縛IDは到達可能な定数・変数だけを数えた連番、利用箇所はASTノードの同一性で保持します。ASTレコードが偶然同じ内容でも別位置の利用を 同一視しません。
 */
public final class NameResolution {
  private static final NameResolution EMPTY = new NameResolution(List.of(), List.of(), List.of());

  private final List<DeclaredName> declarations;
  private final Map<String, List<DeclaredName>> declarationsByName;
  private final List<Binding> bindings;
  private final Map<BindingId, Binding> bindingsById;
  private final List<LexicalScope> scopes;
  private final Map<ScopeId, LexicalScope> scopesById;
  private final List<ResolvedBindingUse> uses;
  private final Map<AstNode, ResolvedBindingUse> usesByNode;
  private final Map<BindingId, List<ResolvedBindingUse>> usesByBinding;

  /**
   * 元ソース順の宣言、親より後に並べたスコープ、元ソース順の利用箇所から解決結果を作ります。
   *
   * @param declarations 単語、定数、変数を同じ名前空間へ並べた一覧
   * @param scopes 字句スコープの親先行一覧
   * @param uses 値参照と代入先の元ソース順一覧
   */
  public NameResolution(
      List<? extends DeclaredName> declarations,
      List<LexicalScope> scopes,
      List<ResolvedBindingUse> uses) {
    Objects.requireNonNull(declarations, "declarations");
    Objects.requireNonNull(scopes, "scopes");
    Objects.requireNonNull(uses, "uses");

    this.declarations = List.copyOf(declarations);
    var names = new LinkedHashMap<String, List<DeclaredName>>();
    var bindingList = new ArrayList<Binding>();
    long previousDeclarationOffset = -1;
    for (DeclaredName declaration : this.declarations) {
      Objects.requireNonNull(declaration, "declaration");
      if (declaration.nameSpan().start().utf8Offset() < previousDeclarationOffset) {
        throw new IllegalArgumentException("declarations must be in source order");
      }
      previousDeclarationOffset = declaration.nameSpan().start().utf8Offset();
      names.computeIfAbsent(declaration.name(), ignored -> new ArrayList<>()).add(declaration);
      if (declaration instanceof Binding binding) {
        bindingList.add(binding);
      }
    }
    var immutableNames = new LinkedHashMap<String, List<DeclaredName>>();
    names.forEach(
        (name, namedDeclarations) -> immutableNames.put(name, List.copyOf(namedDeclarations)));
    declarationsByName = Collections.unmodifiableMap(immutableNames);
    bindings = List.copyOf(bindingList);

    var byId = new LinkedHashMap<BindingId, Binding>();
    for (int index = 0; index < bindings.size(); index++) {
      Binding binding = bindings.get(index);
      int expectedId = index + 1;
      if (binding.id().value() != expectedId) {
        throw new IllegalArgumentException("binding ids must be contiguous in source order");
      }
      byId.put(binding.id(), binding);
    }
    bindingsById = Collections.unmodifiableMap(byId);

    this.scopes = List.copyOf(scopes);
    scopesById = validateScopes(this.scopes);
    validateBindings(this.bindings, scopesById);
    validateNamespace(this.declarations, scopesById);

    this.uses = List.copyOf(uses);
    var identityUses = new IdentityHashMap<AstNode, ResolvedBindingUse>();
    var reverseUses = new LinkedHashMap<BindingId, List<ResolvedBindingUse>>();
    long previousUseOffset = -1;
    for (ResolvedBindingUse use : this.uses) {
      Objects.requireNonNull(use, "use");
      if (!bindingsById.containsKey(use.bindingId())) {
        throw new IllegalArgumentException("use refers to an unknown binding: " + use.bindingId());
      }
      if (use.node().span().start().utf8Offset() < previousUseOffset) {
        throw new IllegalArgumentException("binding uses must be in source order");
      }
      previousUseOffset = use.node().span().start().utf8Offset();
      if (identityUses.put(use.node(), use) != null) {
        throw new IllegalArgumentException("an AST node may have only one binding use");
      }
      reverseUses.computeIfAbsent(use.bindingId(), ignored -> new ArrayList<>()).add(use);
    }
    usesByNode = Collections.unmodifiableMap(identityUses);
    var immutableReverseUses = new LinkedHashMap<BindingId, List<ResolvedBindingUse>>();
    reverseUses.forEach(
        (id, bindingUses) -> immutableReverseUses.put(id, List.copyOf(bindingUses)));
    usesByBinding = Collections.unmodifiableMap(immutableReverseUses);
  }

  /**
   * 空の解決結果を返します。
   *
   * @return 宣言もスコープも利用箇所もない互換用の結果
   */
  public static NameResolution empty() {
    return EMPTY;
  }

  /**
   * 制御フローまでの単語だけを同じ名前空間へ登録した結果を作ります。
   *
   * @param words 元ソース順の利用者定義単語
   * @return 単語だけを持つ解決結果
   */
  public static NameResolution wordsOnly(List<WordName> words) {
    return new NameResolution(words, List.of(), List.of());
  }

  /**
   * 全宣言を返します。
   *
   * @return 元ソース順の単語、定数、変数
   */
  public List<DeclaredName> declarations() {
    return declarations;
  }

  /**
   * 値束縛を返します。
   *
   * @return 到達可能な定数・変数の束縛ID順一覧
   */
  public List<Binding> bindings() {
    return bindings;
  }

  /**
   * 字句スコープを返します。
   *
   * @return 親先行の字句スコープ一覧
   */
  public List<LexicalScope> scopes() {
    return scopes;
  }

  /**
   * 全利用箇所を返します。
   *
   * @return 元ソース位置順の読み出し・書き込み一覧
   */
  public List<ResolvedBindingUse> uses() {
    return uses;
  }

  /**
   * 文脈なしで一意な宣言を検索します。
   *
   * @param normalizedName 正規化済みの利用者名
   * @return 単語、定数、変数のいずれか。未知なら空
   */
  public Optional<DeclaredName> findDeclaration(String normalizedName) {
    List<DeclaredName> matches = findDeclarations(normalizedName);
    return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
  }

  /**
   * 同名の宣言を元ソース順で返します。兄弟スコープでは同じ局所名を使えるため、複数になる場合があります。
   *
   * @param normalizedName 正規化済みの利用者名
   * @return 同名宣言の不変一覧
   */
  public List<DeclaredName> findDeclarations(String normalizedName) {
    return declarationsByName.getOrDefault(
        Objects.requireNonNull(normalizedName, "normalizedName"), List.of());
  }

  /**
   * 大域スコープの単語、定数、変数を検索します。
   *
   * @param normalizedName 正規化済みの利用者名
   * @return 一意な大域宣言。未知なら空
   */
  public Optional<DeclaredName> findGlobalDeclaration(String normalizedName) {
    return findDeclarations(normalizedName).stream().filter(NameResolution::isGlobal).findFirst();
  }

  /**
   * 束縛IDから値宣言を検索します。
   *
   * @param id 束縛ID
   * @return 対応する定数または変数。未知なら空
   */
  public Optional<Binding> findBinding(BindingId id) {
    return Optional.ofNullable(bindingsById.get(Objects.requireNonNull(id, "id")));
  }

  /**
   * スコープIDから字句スコープを検索します。
   *
   * @param id スコープID
   * @return 対応する字句スコープ。未知なら空
   */
  public Optional<LexicalScope> findScope(ScopeId id) {
    return Optional.ofNullable(scopesById.get(Objects.requireNonNull(id, "id")));
  }

  /**
   * AST要素からその利用方法と束縛IDを引きます。同値ではなく同じASTインスタンスだけが一致します。
   *
   * @param node 値参照または代入先AST
   * @return 解決済み利用。値利用でなければ空
   */
  public Optional<ResolvedBindingUse> findUse(AstNode node) {
    return Optional.ofNullable(usesByNode.get(Objects.requireNonNull(node, "node")));
  }

  /**
   * AST要素から宣言を直接引きます。
   *
   * @param node 値参照または代入先AST
   * @return 解決先。値利用でなければ空
   */
  public Optional<Binding> resolve(AstNode node) {
    return findUse(node).flatMap(use -> findBinding(use.bindingId()));
  }

  /**
   * 宣言から全利用箇所を元ソース順で逆引きします。
   *
   * @param id 束縛ID
   * @return 読み出し・書き込み一覧。利用がなければ空リスト
   */
  public List<ResolvedBindingUse> usesOf(BindingId id) {
    Objects.requireNonNull(id, "id");
    return usesByBinding.getOrDefault(id, List.of());
  }

  private static Map<ScopeId, LexicalScope> validateScopes(List<LexicalScope> scopes) {
    var byId = new LinkedHashMap<ScopeId, LexicalScope>();
    for (int index = 0; index < scopes.size(); index++) {
      LexicalScope scope = Objects.requireNonNull(scopes.get(index), "scope");
      if (scope.id().value() != index + 1) {
        throw new IllegalArgumentException("scope ids must be contiguous in parent-first order");
      }
      if (index == 0 && !scope.isGlobal()) {
        throw new IllegalArgumentException("the first scope must be global");
      }
      if (index > 0 && scope.isGlobal()) {
        throw new IllegalArgumentException("there may be only one global scope");
      }
      if (scope.parentId().isPresent()) {
        LexicalScope parent = byId.get(scope.parentId().orElseThrow());
        if (parent == null) {
          throw new IllegalArgumentException("a scope parent must precede its child");
        }
        if (!Objects.equals(parent.ownerWord(), scope.ownerWord())
            && scope.kind() != LexicalScopeKind.WORD_BODY) {
          throw new IllegalArgumentException("nested scopes must keep the same owner word");
        }
        if (scope.kind() == LexicalScopeKind.WORD_BODY && !parent.isGlobal()) {
          throw new IllegalArgumentException("a word body scope must be a child of global scope");
        }
      }
      byId.put(scope.id(), scope);
    }
    return Collections.unmodifiableMap(byId);
  }

  private static void validateBindings(
      List<Binding> bindings, Map<ScopeId, LexicalScope> scopesById) {
    int expectedGlobalInitializationOrder = 1;
    for (Binding binding : bindings) {
      LexicalScope scope = scopesById.get(binding.scopeId());
      if (scope == null) {
        throw new IllegalArgumentException(
            "binding refers to an unknown scope: " + binding.scopeId());
      }
      boolean global = binding.storage() == BindingStorage.GLOBAL;
      if (global != scope.isGlobal()) {
        throw new IllegalArgumentException("binding storage must match its lexical scope");
      }
      if (global) {
        if (binding.globalInitializationOrder().orElseThrow()
            != expectedGlobalInitializationOrder++) {
          throw new IllegalArgumentException(
              "global initialization orders must be contiguous in source order");
        }
      }
    }
  }

  private static void validateNamespace(
      List<DeclaredName> declarations, Map<ScopeId, LexicalScope> scopesById) {
    var globalNames = new LinkedHashMap<String, DeclaredName>();
    var localNamesByScope = new LinkedHashMap<ScopeId, Map<String, Binding>>();
    for (DeclaredName declaration : declarations) {
      if (isGlobal(declaration)) {
        if (globalNames.putIfAbsent(declaration.name(), declaration) != null) {
          throw new IllegalArgumentException(
              "duplicate resolved global name: " + declaration.name());
        }
      } else {
        Binding binding = (Binding) declaration;
        Map<String, Binding> localNames =
            localNamesByScope.computeIfAbsent(binding.scopeId(), ignored -> new LinkedHashMap<>());
        if (localNames.putIfAbsent(binding.name(), binding) != null) {
          throw new IllegalArgumentException(
              "duplicate resolved local name in one scope: " + binding.name());
        }
      }
    }

    for (DeclaredName declaration : declarations) {
      if (!(declaration instanceof Binding binding) || binding.storage() != BindingStorage.LOCAL) {
        continue;
      }
      if (globalNames.containsKey(binding.name())) {
        throw new IllegalArgumentException("a local binding may not shadow a global name");
      }
      LexicalScope scope = scopesById.get(binding.scopeId());
      while (scope != null && scope.parentId().isPresent()) {
        scope = scopesById.get(scope.parentId().orElseThrow());
        Map<String, Binding> parentNames = localNamesByScope.getOrDefault(scope.id(), Map.of());
        Binding outer = parentNames.get(binding.name());
        if (outer != null
            && outer.declarationSpan().end().utf8Offset()
                <= binding.declarationSpan().start().utf8Offset()) {
          throw new IllegalArgumentException("a local binding may not shadow a visible outer name");
        }
      }
    }
  }

  private static boolean isGlobal(DeclaredName declaration) {
    return declaration instanceof WordName
        || declaration instanceof LogicalConnectionName
        || declaration instanceof WorkspaceName
        || declaration instanceof Binding binding && binding.storage() == BindingStorage.GLOBAL;
  }
}
