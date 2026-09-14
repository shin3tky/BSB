package jp.bsb.analyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import jp.bsb.binding.Binding;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingStorage;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.binding.BindingUseKind;
import jp.bsb.binding.DeclaredName;
import jp.bsb.binding.LexicalScope;
import jp.bsb.binding.LexicalScopeKind;
import jp.bsb.binding.LogicalConnectionName;
import jp.bsb.binding.NameResolution;
import jp.bsb.binding.ResolvedBindingUse;
import jp.bsb.binding.ScopeId;
import jp.bsb.binding.WordName;
import jp.bsb.binding.WorkspaceName;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.ArrayLoop;
import jp.bsb.frontend.ast.Assignment;
import jp.bsb.frontend.ast.BodyElement;
import jp.bsb.frontend.ast.ConditionLoop;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.CountedLoop;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LogicalConnectionDeclaration;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.ShortCircuitEvaluation;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.frontend.ast.WorkspaceDeclaration;
import jp.bsb.stdlib.BuiltinDictionary;

/** 配列の配列要素を含む値宣言へ字句スコープを割り当て、全読み出し・書き込みを静的な束縛IDへ解決します。 */
final class BindingResolver {
  static final int MAX_GLOBAL_BINDINGS = 10_000;
  static final int MAX_LOCAL_BINDINGS_PER_WORD = 1_024;
  static final int MAX_PROGRAM_BINDINGS = 65_536;

  private final Program program;
  private final Map<String, WordDefinition> words;
  private final Map<String, ValueDeclaration> globalDeclarations;
  private final Map<String, LogicalConnectionDeclaration> logicalConnections;
  private final Map<String, WorkspaceDeclaration> workspaces;
  private final Set<BodyElement> unreachableElements;
  private final DiagnosticCollector diagnostics;
  private final List<LexicalScope> scopes = new ArrayList<>();
  private final List<DeclarationInfo> declarationInfos = new ArrayList<>();
  private final Map<ValueDeclaration, DeclarationInfo> infoByDeclaration = new IdentityHashMap<>();
  private final Map<ValueDeclaration, Binding> bindingByDeclaration = new IdentityHashMap<>();
  private final Map<WordDefinition, ScopeFrame> wordScopes = new IdentityHashMap<>();
  private final Map<Conditional, ScopeFrame> trueScopes = new IdentityHashMap<>();
  private final Map<Conditional, ScopeFrame> falseScopes = new IdentityHashMap<>();
  private final Map<ShortCircuitEvaluation, ScopeFrame> shortCircuitScopes =
      new IdentityHashMap<>();
  private final Map<CountedLoop, ScopeFrame> countedScopes = new IdentityHashMap<>();
  private final Map<ConditionLoop, ScopeFrame> conditionScopes = new IdentityHashMap<>();
  private final Map<ConditionLoop, ScopeFrame> conditionBodyScopes = new IdentityHashMap<>();
  private final Map<ArrayLoop, ScopeFrame> arrayLoopScopes = new IdentityHashMap<>();
  private final List<ResolvedBindingUse> uses = new ArrayList<>();
  private final ScopeFrame globalScope;
  private boolean valid = true;
  private int acceptedBindingCount;
  private int acceptedGlobalCount;

  private BindingResolver(
      Program program,
      Map<String, WordDefinition> words,
      Map<String, ValueDeclaration> globalDeclarations,
      Map<String, LogicalConnectionDeclaration> logicalConnections,
      Map<String, WorkspaceDeclaration> workspaces,
      Set<BodyElement> unreachableElements,
      DiagnosticCollector diagnostics) {
    this.program = program;
    this.words = words;
    this.globalDeclarations = globalDeclarations;
    this.logicalConnections = logicalConnections;
    this.workspaces = workspaces;
    this.unreachableElements = unreachableElements;
    this.diagnostics = diagnostics;
    LexicalScope scope = LexicalScope.global(nextScopeId(), program.span());
    scopes.add(scope);
    globalScope = new ScopeFrame(scope, null);
  }

  /** 有効な大域名を前提に局所登録、ID割当て、利用解決を行います。 */
  static Result resolve(
      Program program,
      Map<String, WordDefinition> words,
      Map<String, ValueDeclaration> globalDeclarations,
      Map<String, LogicalConnectionDeclaration> logicalConnections,
      Map<String, WorkspaceDeclaration> workspaces,
      Set<BodyElement> unreachableElements,
      DiagnosticCollector diagnostics) {
    var resolver =
        new BindingResolver(
            program,
            words,
            globalDeclarations,
            logicalConnections,
            workspaces,
            unreachableElements,
            diagnostics);
    return resolver.resolve();
  }

  private Result resolve() {
    registerGlobalDeclarations();
    registerWordScopesAndLocals();
    assignStableBindingIds();
    resolveGlobalInitializers();
    resolveWordBodies();

    List<DeclaredName> declarations = new ArrayList<>();
    words.values().stream()
        .map(word -> new WordName(word.name(), word.lexeme(), word.nameSpan()))
        .forEach(declarations::add);
    logicalConnections.values().stream()
        .map(
            connection ->
                new LogicalConnectionName(
                    connection.name(), connection.lexeme(), connection.nameSpan()))
        .forEach(declarations::add);
    workspaces.values().stream()
        .map(
            workspace ->
                new WorkspaceName(workspace.name(), workspace.lexeme(), workspace.nameSpan()))
        .forEach(declarations::add);
    bindingByDeclaration.values().forEach(declarations::add);
    declarations.sort(Comparator.comparingLong(name -> name.nameSpan().start().utf8Offset()));
    uses.sort(Comparator.comparingLong(use -> use.node().span().start().utf8Offset()));
    NameResolution resolution = new NameResolution(declarations, scopes, uses);
    var bindingIdsByDeclaration = new IdentityHashMap<ValueDeclaration, BindingId>();
    bindingByDeclaration.forEach(
        (declaration, binding) -> bindingIdsByDeclaration.put(declaration, binding.id()));
    return new Result(resolution, Collections.unmodifiableMap(bindingIdsByDeclaration), valid);
  }

  private void registerGlobalDeclarations() {
    int initializationOrder = 0;
    for (ValueDeclaration declaration : globalDeclarations.values()) {
      initializationOrder++;
      if (++acceptedGlobalCount > MAX_GLOBAL_BINDINGS) {
        reportLimit(
            DiagnosticCode.E_GLOBAL_BINDING_LIMIT,
            declaration,
            "globalBindings",
            MAX_GLOBAL_BINDINGS,
            acceptedGlobalCount);
        continue;
      }
      if (++acceptedBindingCount > MAX_PROGRAM_BINDINGS) {
        reportLimit(
            DiagnosticCode.E_BINDING_LIMIT,
            declaration,
            "programBindings",
            MAX_PROGRAM_BINDINGS,
            acceptedBindingCount);
        continue;
      }
      var info =
          new DeclarationInfo(
              declaration, globalScope, BindingStorage.GLOBAL, OptionalInt.of(initializationOrder));
      declarationInfos.add(info);
      infoByDeclaration.put(declaration, info);
      globalScope.directDeclarations.put(declaration.name(), info);
    }
  }

  private void registerWordScopesAndLocals() {
    for (WordDefinition word : words.values()) {
      ScopeFrame scope =
          createScope(LexicalScopeKind.WORD_BODY, globalScope, word.name(), word.span());
      wordScopes.put(word, scope);
      var counters = new WordCounters();
      registerBody(word.body(), scope, Map.of(), word.name(), counters);
    }
  }

  private void registerBody(
      List<BodyElement> body,
      ScopeFrame scope,
      Map<String, DeclarationInfo> inheritedVisible,
      String ownerWord,
      WordCounters counters) {
    var visible = new LinkedHashMap<>(inheritedVisible);
    for (BodyElement element : body) {
      if (unreachableElements.contains(element)) {
        continue;
      }
      if (element instanceof ValueDeclaration declaration) {
        registerLocalDeclaration(declaration, scope, inheritedVisible, visible, counters);
      } else if (element instanceof Conditional conditional) {
        ScopeFrame trueScope =
            createScope(LexicalScopeKind.CONDITIONAL_TRUE, scope, ownerWord, conditional.span());
        trueScopes.put(conditional, trueScope);
        registerBody(conditional.trueBody(), trueScope, visible, ownerWord, counters);
        if (conditional.hasElse()) {
          ScopeFrame falseScope =
              createScope(LexicalScopeKind.CONDITIONAL_FALSE, scope, ownerWord, conditional.span());
          falseScopes.put(conditional, falseScope);
          registerBody(conditional.falseBody(), falseScope, visible, ownerWord, counters);
        }
      } else if (element instanceof ShortCircuitEvaluation evaluation) {
        ScopeFrame rightScope =
            createScope(
                LexicalScopeKind.SHORT_CIRCUIT_RIGHT,
                scope,
                ownerWord,
                evaluation.span());
        shortCircuitScopes.put(evaluation, rightScope);
        registerBody(evaluation.rightBody(), rightScope, visible, ownerWord, counters);
      } else if (element instanceof CountedLoop loop) {
        ScopeFrame loopScope =
            createScope(LexicalScopeKind.COUNTED_LOOP_BODY, scope, ownerWord, loop.span());
        countedScopes.put(loop, loopScope);
        registerBody(loop.body(), loopScope, visible, ownerWord, counters);
      } else if (element instanceof ConditionLoop loop) {
        ScopeFrame conditionScope =
            createScope(LexicalScopeKind.CONDITION_LOOP_CONDITION, scope, ownerWord, loop.span());
        conditionScopes.put(loop, conditionScope);
        registerBody(loop.conditionBody(), conditionScope, visible, ownerWord, counters);
        ScopeFrame bodyScope =
            createScope(LexicalScopeKind.CONDITION_LOOP_BODY, scope, ownerWord, loop.span());
        conditionBodyScopes.put(loop, bodyScope);
        registerBody(loop.body(), bodyScope, visible, ownerWord, counters);
      } else if (element instanceof ArrayLoop loop) {
        ScopeFrame loopScope =
            createScope(LexicalScopeKind.ARRAY_LOOP_BODY, scope, ownerWord, loop.span());
        arrayLoopScopes.put(loop, loopScope);
        registerBody(loop.body(), loopScope, visible, ownerWord, counters);
      }
    }
  }

  private void registerLocalDeclaration(
      ValueDeclaration declaration,
      ScopeFrame scope,
      Map<String, DeclarationInfo> inheritedVisible,
      Map<String, DeclarationInfo> visible,
      WordCounters counters) {
    if (LanguageNames.reservedForValue(declaration.name())) {
      reportReservedName(declaration);
      return;
    }
    DeclarationInfo previous = scope.directDeclarations.get(declaration.name());
    if (previous != null) {
      reportDuplicateName(declaration, previous.declaration);
      return;
    }

    DeclaredName outer = globalDeclaredName(declaration.name()).orElse(null);
    if (outer == null) {
      DeclarationInfo inherited = inheritedVisible.get(declaration.name());
      if (inherited != null) {
        outer = temporaryBinding(inherited);
      }
    }
    if (outer != null) {
      reportShadowing(declaration, outer);
      return;
    }

    if (++counters.localBindings > MAX_LOCAL_BINDINGS_PER_WORD) {
      reportLimit(
          DiagnosticCode.E_LOCAL_BINDING_LIMIT,
          declaration,
          "localBindingsPerWord",
          MAX_LOCAL_BINDINGS_PER_WORD,
          counters.localBindings);
      return;
    }
    if (++acceptedBindingCount > MAX_PROGRAM_BINDINGS) {
      reportLimit(
          DiagnosticCode.E_BINDING_LIMIT,
          declaration,
          "programBindings",
          MAX_PROGRAM_BINDINGS,
          acceptedBindingCount);
      return;
    }

    var info = new DeclarationInfo(declaration, scope, BindingStorage.LOCAL, OptionalInt.empty());
    scope.directDeclarations.put(declaration.name(), info);
    declarationInfos.add(info);
    infoByDeclaration.put(declaration, info);
    visible.put(declaration.name(), info);
  }

  private void assignStableBindingIds() {
    declarationInfos.sort(
        Comparator.comparingLong(info -> info.declaration.nameSpan().start().utf8Offset()));
    for (int index = 0; index < declarationInfos.size(); index++) {
      DeclarationInfo info = declarationInfos.get(index);
      ValueDeclaration declaration = info.declaration;
      Binding binding =
          new Binding(
              new BindingId(index + 1),
              declaration.name(),
              declaration.lexeme(),
              declaration.kind(),
              info.storage,
              info.scope.scope.id(),
              BindingTypeState.uninferred(),
              info.globalInitializationOrder,
              declaration.nameSpan(),
              declaration.span());
      bindingByDeclaration.put(declaration, binding);
      info.binding = binding;
    }
  }

  private void resolveGlobalInitializers() {
    declarationInfos.stream()
        .filter(info -> info.storage == BindingStorage.GLOBAL)
        .sorted(Comparator.comparingInt(info -> info.globalInitializationOrder.orElseThrow()))
        .forEach(
            info ->
                resolveInitializer(
                    info.declaration,
                    globalScope,
                    Map.of(),
                    info.globalInitializationOrder.orElseThrow()));
  }

  private void resolveWordBodies() {
    for (WordDefinition word : words.values()) {
      resolveBody(word.body(), wordScopes.get(word), Map.of());
    }
  }

  private void resolveBody(
      List<BodyElement> body, ScopeFrame scope, Map<String, Binding> inheritedVisible) {
    var visible = new LinkedHashMap<>(inheritedVisible);
    for (BodyElement element : body) {
      if (unreachableElements.contains(element)) {
        continue;
      }
      if (element instanceof ValueDeclaration declaration) {
        resolveInitializer(declaration, scope, visible, 0);
        Binding binding = bindingByDeclaration.get(declaration);
        if (binding != null) {
          visible.put(binding.name(), binding);
        }
      } else if (element instanceof ValueReference reference) {
        resolveRead(reference, scope, visible, 0);
      } else if (element instanceof ArrayLiteral array) {
        resolveArrayReferences(array, null, scope, visible, 0);
      } else if (element instanceof Assignment assignment) {
        resolveWrite(assignment, scope, visible);
      } else if (element instanceof Conditional conditional) {
        resolveBody(conditional.trueBody(), trueScopes.get(conditional), visible);
        if (conditional.hasElse()) {
          resolveBody(conditional.falseBody(), falseScopes.get(conditional), visible);
        }
      } else if (element instanceof ShortCircuitEvaluation evaluation) {
        resolveBody(evaluation.rightBody(), shortCircuitScopes.get(evaluation), visible);
      } else if (element instanceof CountedLoop loop) {
        resolveBody(loop.body(), countedScopes.get(loop), visible);
      } else if (element instanceof ConditionLoop loop) {
        resolveBody(loop.conditionBody(), conditionScopes.get(loop), visible);
        resolveBody(loop.body(), conditionBodyScopes.get(loop), visible);
      } else if (element instanceof ArrayLoop loop) {
        resolveBody(loop.body(), arrayLoopScopes.get(loop), visible);
      }
    }
  }

  private void resolveInitializer(
      ValueDeclaration declaration,
      ScopeFrame scope,
      Map<String, Binding> visible,
      int globalInitializationOrder) {
    for (BodyElement element : declaration.initializer()) {
      if (element instanceof ValueReference reference) {
        resolveInitializerReference(
            reference, declaration, scope, visible, globalInitializationOrder);
      } else if (element instanceof ArrayLiteral array) {
        resolveArrayReferences(array, declaration, scope, visible, globalInitializationOrder);
      }
    }
  }

  /** 配列要素内の値参照も、配列外と同じ字句スコープ・初期化順序で解決します。 */
  private void resolveArrayReferences(
      ArrayLiteral array,
      ValueDeclaration declaration,
      ScopeFrame scope,
      Map<String, Binding> visible,
      int globalInitializationOrder) {
    for (var arrayElement : array.elements()) {
      for (BodyElement element : arrayElement.body()) {
        if (element instanceof ValueReference reference) {
          if (declaration == null) {
            resolveRead(reference, scope, visible, globalInitializationOrder);
          } else {
            resolveInitializerReference(
                reference, declaration, scope, visible, globalInitializationOrder);
          }
        } else if (element instanceof ArrayLiteral nested) {
          resolveArrayReferences(nested, declaration, scope, visible, globalInitializationOrder);
        }
      }
    }
  }

  private void resolveInitializerReference(
      ValueReference reference,
      ValueDeclaration declaration,
      ScopeFrame scope,
      Map<String, Binding> visible,
      int globalInitializationOrder) {
    Binding declaredBinding = bindingByDeclaration.get(declaration);
    if (globalInitializationOrder == 0
        && declaredBinding != null
        && reference.name().equals(declaredBinding.name())) {
      reportBeforeDeclaration(reference.span(), reference.name(), "read", declaredBinding, false);
      return;
    }
    resolveRead(reference, scope, visible, globalInitializationOrder);
  }

  private void resolveRead(
      ValueReference reference,
      ScopeFrame scope,
      Map<String, Binding> visible,
      int globalInitializationOrder) {
    Binding binding = visible.get(reference.name());
    if (binding == null) {
      binding = globalBinding(reference.name()).orElse(null);
    }
    if (binding != null) {
      if (globalInitializationOrder > 0
          && binding.storage() == BindingStorage.GLOBAL
          && binding.globalInitializationOrder().orElseThrow() >= globalInitializationOrder) {
        reportBeforeInitialization(reference, binding);
        return;
      }
      uses.add(
          new ResolvedBindingUse(reference, reference.lexeme(), BindingUseKind.READ, binding.id()));
      return;
    }

    Binding later = laterInSameScope(reference.name(), reference.span(), scope).orElse(null);
    if (later != null) {
      reportBeforeDeclaration(reference.span(), reference.name(), "read", later, false);
      return;
    }
    Binding outOfScope =
        outOfScopeCandidate(reference.name(), reference.span(), scope).orElse(null);
    if (outOfScope != null) {
      reportOutOfScope(reference.span(), reference.name(), outOfScope);
    }
  }

  private void resolveWrite(Assignment assignment, ScopeFrame scope, Map<String, Binding> visible) {
    Binding binding = visible.get(assignment.targetName());
    if (binding == null) {
      binding = globalBinding(assignment.targetName()).orElse(null);
    }
    if (binding != null) {
      if (!binding.kind().isMutable()) {
        reportAssignToConstant(assignment, binding);
        return;
      }
      uses.add(
          new ResolvedBindingUse(
              assignment, assignment.targetLexeme(), BindingUseKind.WRITE, binding.id()));
      return;
    }

    Binding later =
        laterInSameScope(assignment.targetName(), assignment.targetSpan(), scope).orElse(null);
    if (later != null) {
      reportBeforeDeclaration(
          assignment.targetSpan(), assignment.targetName(), "write", later, true);
      return;
    }
    Binding outOfScope =
        outOfScopeCandidate(assignment.targetName(), assignment.targetSpan(), scope).orElse(null);
    if (outOfScope != null) {
      reportOutOfScope(assignment.targetSpan(), assignment.targetName(), outOfScope);
      return;
    }
    WordDefinition word = words.get(assignment.targetName());
    if (word != null) {
      reportTargetNotVariable(assignment, "利用者定義単語", word.nameSpan(), word.lexeme());
      return;
    }
    LogicalConnectionDeclaration connection = logicalConnections.get(assignment.targetName());
    if (connection != null) {
      reportTargetNotVariable(assignment, "論理接続", connection.nameSpan(), connection.lexeme());
      return;
    }
    WorkspaceDeclaration workspace = workspaces.get(assignment.targetName());
    if (workspace != null) {
      reportTargetNotVariable(assignment, "作業領域", workspace.nameSpan(), workspace.lexeme());
      return;
    }
    if (BuiltinDictionary.find(assignment.targetName()).isPresent()) {
      reportTargetNotVariable(assignment, "組み込み単語", null, assignment.targetLexeme());
      return;
    }
    reportUndefinedAssignmentTarget(assignment);
  }

  private Optional<Binding> globalBinding(String name) {
    DeclarationInfo info = globalScope.directDeclarations.get(name);
    return info == null ? Optional.empty() : Optional.ofNullable(info.binding);
  }

  private Optional<DeclaredName> globalDeclaredName(String name) {
    WordDefinition word = words.get(name);
    if (word != null) {
      return Optional.of(new WordName(word.name(), word.lexeme(), word.nameSpan()));
    }
    LogicalConnectionDeclaration connection = logicalConnections.get(name);
    if (connection != null) {
      return Optional.of(
          new LogicalConnectionName(connection.name(), connection.lexeme(), connection.nameSpan()));
    }
    WorkspaceDeclaration workspace = workspaces.get(name);
    if (workspace != null) {
      return Optional.of(
          new WorkspaceName(workspace.name(), workspace.lexeme(), workspace.nameSpan()));
    }
    DeclarationInfo info = globalScope.directDeclarations.get(name);
    return info == null ? Optional.empty() : Optional.of(temporaryBinding(info));
  }

  private Optional<Binding> laterInSameScope(String name, SourceSpan useSpan, ScopeFrame scope) {
    DeclarationInfo info = scope.directDeclarations.get(name);
    if (info == null || info.binding == null) {
      return Optional.empty();
    }
    return info.declaration.nameSpan().start().utf8Offset() > useSpan.start().utf8Offset()
        ? Optional.of(info.binding)
        : Optional.empty();
  }

  private Optional<Binding> outOfScopeCandidate(
      String name, SourceSpan useSpan, ScopeFrame useScope) {
    if (useScope.scope.ownerWord().isEmpty()) {
      return Optional.empty();
    }
    return declarationInfos.stream()
        .filter(info -> info.storage == BindingStorage.LOCAL)
        .filter(info -> info.declaration.name().equals(name))
        .filter(
            info ->
                info.scope.scope.ownerWord().equals(useScope.scope.ownerWord())
                    && !isAncestorOrSelf(info.scope, useScope))
        .filter(
            info -> info.declaration.nameSpan().start().utf8Offset() < useSpan.start().utf8Offset())
        .map(info -> info.binding)
        .filter(java.util.Objects::nonNull)
        .max(Comparator.comparingLong(binding -> binding.nameSpan().start().utf8Offset()));
  }

  private static boolean isAncestorOrSelf(ScopeFrame candidate, ScopeFrame scope) {
    for (ScopeFrame current = scope; current != null; current = current.parent) {
      if (current == candidate) {
        return true;
      }
    }
    return false;
  }

  private ScopeFrame createScope(
      LexicalScopeKind kind, ScopeFrame parent, String ownerWord, SourceSpan span) {
    LexicalScope scope =
        new LexicalScope(
            nextScopeId(), kind, Optional.of(parent.scope.id()), Optional.of(ownerWord), span);
    scopes.add(scope);
    return new ScopeFrame(scope, parent);
  }

  private ScopeId nextScopeId() {
    return new ScopeId(scopes.size() + 1);
  }

  private Binding temporaryBinding(DeclarationInfo info) {
    ValueDeclaration declaration = info.declaration;
    return new Binding(
        new BindingId(1),
        declaration.name(),
        declaration.lexeme(),
        declaration.kind(),
        info.storage,
        info.scope.scope.id(),
        BindingTypeState.uninferred(),
        info.storage == BindingStorage.GLOBAL
            ? info.globalInitializationOrder
            : OptionalInt.empty(),
        declaration.nameSpan(),
        declaration.span());
  }

  private void reportReservedName(ValueDeclaration declaration) {
    var builder =
        Diagnostic.builder(
                DiagnosticCode.E_RESERVED_NAME,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                declaration.nameSpan())
            .expected("利用者定義名")
            .actual(declaration.lexeme())
            .fix("別の名前に変更してください");
    BuiltinDictionary.find(declaration.name())
        .ifPresent(
            word ->
                builder.relatedLocation(
                    RelatedLocation.outsideSource("組み込み単語 " + word.canonicalName())));
    diagnostics.add(builder.build());
    valid = false;
  }

  private void reportDuplicateName(ValueDeclaration declaration, ValueDeclaration previous) {
    diagnostics.add(
        Diagnostic.builder(
                DiagnosticCode.E_DUPLICATE_NAME,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                declaration.nameSpan())
            .field("actualName", declaration.lexeme())
            .field("previousName", previous.lexeme())
            .expected("一意な名前")
            .actual(declaration.lexeme())
            .fix("別の名前に変更してください")
            .relatedLocation(
                new RelatedLocation(
                    program.sourcePath(), previous.nameSpan().start(), previous.lexeme()))
            .build());
    valid = false;
  }

  private void reportShadowing(ValueDeclaration declaration, DeclaredName outer) {
    String outerKind =
        outer instanceof Binding binding
            ? (binding.storage() == BindingStorage.GLOBAL ? "大域" : "局所")
                + binding.declarationKind().sourceName()
            : outer instanceof LogicalConnectionName
                ? "大域論理接続"
                : outer instanceof WorkspaceName ? "大域作業領域" : "大域単語";
    diagnostics.add(
        Diagnostic.builder(
                DiagnosticCode.E_NAME_SHADOWING,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                declaration.nameSpan())
            .field("name", declaration.name())
            .field("outerKind", outerKind)
            .expected("外側と異なる局所名")
            .actual(declaration.lexeme())
            .fix("別の局所名に変更してください")
            .relatedLocation(
                new RelatedLocation(
                    program.sourcePath(), outer.nameSpan().start(), outer.spelling()))
            .build());
    valid = false;
  }

  private void reportBeforeInitialization(ValueReference reference, Binding binding) {
    int order = binding.globalInitializationOrder().orElseThrow();
    boolean self =
        reference.name().equals(binding.name())
            && reference.span().start().utf8Offset()
                >= binding.declarationSpan().start().utf8Offset();
    diagnostics.add(
        Diagnostic.builder(
                DiagnosticCode.E_REFERENCE_BEFORE_INITIALIZATION,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                reference.span())
            .field("name", reference.name())
            .field("declarationLine", Integer.toString(binding.nameSpan().start().line()))
            .field("declarationColumn", Integer.toString(binding.nameSpan().start().column()))
            .field("initializationOrder", Integer.toString(order))
            .expected(self ? "初期化済みの名前" : "先に初期化される名前")
            .actual(self ? "未初期化の" + binding.name() : order + "番目の大域宣言")
            .fix(self ? "別の初期値を使用してください" : "宣言順を入れ替えてください")
            .relatedLocation(
                new RelatedLocation(
                    program.sourcePath(), binding.nameSpan().start(), binding.spelling()))
            .build());
    valid = false;
  }

  private void reportBeforeDeclaration(
      SourceSpan span, String name, String access, Binding binding, boolean assignment) {
    diagnostics.add(
        Diagnostic.builder(
                DiagnosticCode.E_REFERENCE_BEFORE_DECLARATION,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                span)
            .field("name", name)
            .field("access", access)
            .field("declarationLine", Integer.toString(binding.nameSpan().start().line()))
            .field("declarationColumn", Integer.toString(binding.nameSpan().start().column()))
            .expected(assignment ? "宣言後の代入" : "宣言後の参照")
            .actual(assignment ? "宣言前の代入" : "宣言前の参照")
            .fix("宣言をこの" + (assignment ? "代入" : "参照") + "より前へ移動してください")
            .relatedLocation(
                new RelatedLocation(
                    program.sourcePath(), binding.nameSpan().start(), binding.spelling()))
            .build());
    valid = false;
  }

  private void reportOutOfScope(SourceSpan span, String name, Binding binding) {
    LexicalScope declarationScope =
        scopes.stream()
            .filter(scope -> scope.id().equals(binding.scopeId()))
            .findFirst()
            .orElseThrow();
    String scopeName = scopeSourceName(declarationScope.kind());
    diagnostics.add(
        Diagnostic.builder(
                DiagnosticCode.E_BINDING_OUT_OF_SCOPE,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                span)
            .field("name", name)
            .field("scope", scopeName)
            .field("declarationLine", Integer.toString(binding.nameSpan().start().line()))
            .field("declarationColumn", Integer.toString(binding.nameSpan().start().column()))
            .expected("可視な名前")
            .actual(scopeName.replace("条件分岐の", "") + "だけの局所名")
            .fix("参照を宣言と同じスコープへ移動してください")
            .relatedLocation(
                new RelatedLocation(
                    program.sourcePath(), binding.nameSpan().start(), binding.spelling()))
            .build());
    valid = false;
  }

  private void reportAssignToConstant(Assignment assignment, Binding binding) {
    ValueDeclaration declaration =
        infoByDeclaration.entrySet().stream()
            .filter(entry -> bindingByDeclaration.get(entry.getKey()) == binding)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    diagnostics.add(
        Diagnostic.builder(
                DiagnosticCode.E_ASSIGN_TO_CONSTANT,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                assignment.targetSpan())
            .field("name", binding.name())
            .field("type", simpleInitializerType(declaration))
            .field("declarationLine", Integer.toString(binding.nameSpan().start().line()))
            .field("declarationColumn", Integer.toString(binding.nameSpan().start().column()))
            .expected("変数")
            .actual("定数 " + binding.spelling())
            .fix("宣言を変数にするか代入を削除してください")
            .relatedLocation(
                new RelatedLocation(
                    program.sourcePath(), binding.nameSpan().start(), binding.spelling()))
            .build());
    valid = false;
  }

  private void reportTargetNotVariable(
      Assignment assignment, String targetKind, SourceSpan declarationSpan, String spelling) {
    var builder =
        Diagnostic.builder(
                DiagnosticCode.E_ASSIGNMENT_TARGET_NOT_VARIABLE,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                assignment.targetSpan())
            .field("name", assignment.targetName())
            .field("targetKind", targetKind)
            .expected("変数")
            .actual(targetKind + " " + spelling)
            .fix("変数名を指定してください");
    if (declarationSpan != null) {
      builder.relatedLocation(
          new RelatedLocation(program.sourcePath(), declarationSpan.start(), spelling));
    }
    diagnostics.add(builder.build());
    valid = false;
  }

  private void reportUndefinedAssignmentTarget(Assignment assignment) {
    diagnostics.add(
        Diagnostic.builder(
                DiagnosticCode.E_UNDEFINED_ASSIGNMENT_TARGET,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                assignment.targetSpan())
            .field("name", assignment.targetName())
            .expected("宣言済み変数")
            .actual(assignment.targetLexeme())
            .fix("変数を宣言してください")
            .build());
    valid = false;
  }

  private void reportLimit(
      DiagnosticCode code, ValueDeclaration declaration, String name, int limit, int observed) {
    diagnostics.add(
        Diagnostic.builder(
                code,
                Severity.ERROR,
                DiagnosticStage.NAME,
                program.sourcePath(),
                declaration.nameSpan())
            .limit(name, limit, observed)
            .build());
    valid = false;
  }

  private static String simpleInitializerType(ValueDeclaration declaration) {
    return declaration.initializer().stream()
        .filter(Literal.class::isInstance)
        .map(Literal.class::cast)
        .findFirst()
        .map(
            literal ->
                switch (literal.kind()) {
                  case INTEGER -> "整数";
                  case BOOLEAN -> "真偽";
                  case CHARACTER -> "文字";
                  case STRING -> "文字列";
                  case DECIMAL -> "小数";
                  case REGEX -> "正規表現";
                })
        .orElse("未推論");
  }

  private static String scopeSourceName(LexicalScopeKind kind) {
    return switch (kind) {
      case GLOBAL -> "大域";
      case WORD_BODY -> "単語本体";
      case CONDITIONAL_TRUE -> "条件分岐の真側";
      case CONDITIONAL_FALSE -> "条件分岐の偽側";
      case SHORT_CIRCUIT_RIGHT -> "短絡評価の右辺";
      case COUNTED_LOOP_BODY -> "回数ループ本体";
      case CONDITION_LOOP_CONDITION -> "条件ループの条件計算部";
      case CONDITION_LOOP_BODY -> "条件ループ本体";
      case ARRAY_LOOP_BODY -> "配列反復本体";
    };
  }

  record Result(
      NameResolution resolution,
      Map<ValueDeclaration, BindingId> bindingIdsByDeclaration,
      boolean valid) {}

  private static final class DeclarationInfo {
    private final ValueDeclaration declaration;
    private final ScopeFrame scope;
    private final BindingStorage storage;
    private final OptionalInt globalInitializationOrder;
    private Binding binding;

    private DeclarationInfo(
        ValueDeclaration declaration,
        ScopeFrame scope,
        BindingStorage storage,
        OptionalInt globalInitializationOrder) {
      this.declaration = declaration;
      this.scope = scope;
      this.storage = storage;
      this.globalInitializationOrder = globalInitializationOrder;
    }
  }

  private static final class ScopeFrame {
    private final LexicalScope scope;
    private final ScopeFrame parent;
    private final Map<String, DeclarationInfo> directDeclarations = new LinkedHashMap<>();

    private ScopeFrame(LexicalScope scope, ScopeFrame parent) {
      this.scope = scope;
      this.parent = parent;
    }
  }

  private static final class WordCounters {
    private int localBindings;
  }
}
