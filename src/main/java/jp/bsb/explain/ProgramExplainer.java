package jp.bsb.explain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.binding.Binding;
import jp.bsb.binding.LexicalScope;
import jp.bsb.binding.ResolvedBindingUse;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.ArrayLoop;
import jp.bsb.frontend.ast.Assignment;
import jp.bsb.frontend.ast.BodyElement;
import jp.bsb.frontend.ast.ConditionLoop;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.CountedLoop;
import jp.bsb.frontend.ast.LogicalConnectionDeclaration;
import jp.bsb.frontend.ast.ShortCircuitEvaluation;
import jp.bsb.frontend.ast.TopLevelElement;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.frontend.ast.WorkspaceDeclaration;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.BuiltinTypeRule;
import jp.bsb.stdlib.BuiltinWord;

/** 検査済みプログラムから単語、能力、副作用、スコープ、束縛の静的説明を作ります。 */
public final class ProgramExplainer {
  private static final List<String> CAPABILITY_ORDER =
      List.of(
          BuiltinDictionary.CONSOLE_INPUT,
          BuiltinDictionary.CONSOLE_OUTPUT,
          BuiltinDictionary.CONSOLE_ERROR,
          BuiltinDictionary.PROCESS_EXIT,
          BuiltinDictionary.PROCESS_ARGUMENTS,
          BuiltinDictionary.PROGRAM_IDENTITY,
          BuiltinDictionary.TIME_SLEEP,
          BuiltinDictionary.TIME_MONOTONIC,
          BuiltinDictionary.TIME_WALL,
          BuiltinDictionary.CONNECTION_RESOLVE,
          BuiltinDictionary.HTTP_SEND,
          BuiltinDictionary.WORKSPACE_RESOLVE,
          BuiltinDictionary.FILE_READ,
          BuiltinDictionary.FILE_WRITE);
  private static final Map<String, Integer> CAPABILITY_BITS = capabilityBits();

  /** 状態を持たない説明器を作ります。 */
  public ProgramExplainer() {}

  /** 静的検査に成功したプログラムを説明します。 */
  public ProgramExplanation explain(AnalyzedProgram program) {
    Objects.requireNonNull(program, "program");
    List<WordDefinition> definitions = definitions(program);
    var wordIndex = new LinkedHashMap<String, Integer>();
    for (int index = 0; index < definitions.size(); index++) {
      if (wordIndex.put(definitions.get(index).name(), index) != null) {
        throw new IllegalStateException("duplicate analyzed word");
      }
    }

    WordFacts[] facts = new WordFacts[definitions.size()];
    var connectionIndex = new IdentityHashMap<LogicalConnectionDeclaration, Integer>();
    List<LogicalConnectionDeclaration> connectionDeclarations =
        program.logicalConnectionResolution().declarations();
    for (int index = 0; index < connectionDeclarations.size(); index++) {
      connectionIndex.put(connectionDeclarations.get(index), index);
    }
    var workspaceIndex = new IdentityHashMap<WorkspaceDeclaration, Integer>();
    List<WorkspaceDeclaration> workspaceDeclarations = program.workspaceResolution().declarations();
    for (int index = 0; index < workspaceDeclarations.size(); index++) {
      workspaceIndex.put(workspaceDeclarations.get(index), index);
    }
    for (int index = 0; index < definitions.size(); index++) {
      facts[index] =
          collectFacts(program, definitions.get(index), wordIndex, connectionIndex, workspaceIndex);
    }
    propagate(facts);

    boolean[] reachable = reachableFromMain(facts, wordIndex);
    List<ProgramExplanation.UserWordEntry> userWords =
        userWords(program, definitions, facts, reachable);
    ProgramExplanation.Summary summary = summary(definitions, facts, reachable);
    return new ProgramExplanation(
        summary,
        builtinWords(),
        userWords,
        scopes(program),
        bindings(program),
        parameterizedCapabilities(program, definitions, facts, reachable, wordIndex));
  }

  private static List<WordDefinition> definitions(AnalyzedProgram program) {
    var result = new ArrayList<WordDefinition>();
    for (TopLevelElement element : program.syntax().elements()) {
      if (element instanceof WordDefinition definition) {
        result.add(definition);
      }
    }
    if (result.size() != program.userWordSignatures().size()) {
      throw new IllegalStateException("analyzed word signatures are incomplete");
    }
    return List.copyOf(result);
  }

  private static WordFacts collectFacts(
      AnalyzedProgram program,
      WordDefinition definition,
      Map<String, Integer> wordIndex,
      Map<LogicalConnectionDeclaration, Integer> connectionIndex,
      Map<WorkspaceDeclaration, Integer> workspaceIndex) {
    var userCallees = new LinkedHashSet<Integer>();
    var builtinCallees = new LinkedHashSet<String>();
    long directCapabilities = 0;
    long directEffects = 0;
    var directConnections = emptyConnectionOperations(connectionIndex.size());
    var directWorkspaces = emptyConnectionOperations(workspaceIndex.size());
    var pending = new ArrayDeque<BodyElement>();
    pushReverse(pending, definition.body());
    while (!pending.isEmpty()) {
      BodyElement element = pending.removeLast();
      if (!program.isReachable(element)) {
        continue;
      }
      if (element instanceof WordCall call) {
        if (program.findCallSignature(call).isEmpty()) {
          throw new IllegalStateException(
              "a reachable call has no analyzed signature: " + call.name());
        }
        var builtin = BuiltinDictionary.find(call.name());
        if (builtin.isPresent()) {
          BuiltinWord word = builtin.orElseThrow();
          builtinCallees.add(word.canonicalName());
          directCapabilities |= mask(word.capabilities());
          directEffects |= mask(word.sideEffects());
          program
              .logicalConnectionResolution()
              .resolve(call)
              .ifPresent(
                  use -> {
                    Integer target = connectionIndex.get(use.declaration());
                    if (target == null) {
                      throw new IllegalStateException(
                          "a logical connection use has no explained declaration");
                    }
                    directConnections.get(target).add("resolve");
                    use.httpMethod().ifPresent(directConnections.get(target)::add);
                  });
          program
              .workspaceResolution()
              .resolve(call)
              .ifPresent(
                  use -> {
                    Integer target = workspaceIndex.get(use.declaration());
                    if (target == null) {
                      throw new IllegalStateException(
                          "a workspace use has no explained declaration");
                    }
                    directWorkspaces.get(target).add("resolve");
                    directWorkspaces.get(target).add(use.operation());
                  });
        } else {
          Integer target = wordIndex.get(call.name());
          if (target == null) {
            throw new IllegalStateException(
                "a reachable call has no resolved target: " + call.name());
          }
          userCallees.add(target);
        }
      } else if (element instanceof ArrayLiteral array) {
        for (int index = array.elements().size() - 1; index >= 0; index--) {
          pushReverse(pending, array.elements().get(index).body());
        }
      } else if (element instanceof Conditional conditional) {
        pushReverse(pending, conditional.falseBody());
        pushReverse(pending, conditional.trueBody());
      } else if (element instanceof ShortCircuitEvaluation evaluation) {
        pushReverse(pending, evaluation.rightBody());
      } else if (element instanceof CountedLoop loop) {
        pushReverse(pending, loop.body());
      } else if (element instanceof ConditionLoop loop) {
        pushReverse(pending, loop.body());
        pushReverse(pending, loop.conditionBody());
      } else if (element instanceof ArrayLoop loop) {
        pushReverse(pending, loop.body());
      }
    }
    return new WordFacts(
        userCallees.stream().mapToInt(Integer::intValue).toArray(),
        Set.copyOf(builtinCallees),
        directCapabilities,
        directEffects,
        directConnections,
        directWorkspaces);
  }

  private static void pushReverse(ArrayDeque<BodyElement> pending, List<BodyElement> body) {
    for (BodyElement element : body) {
      pending.addLast(element);
    }
  }

  private static void propagate(WordFacts[] facts) {
    var parents = new ArrayList<List<Integer>>(facts.length);
    for (int index = 0; index < facts.length; index++) {
      parents.add(new ArrayList<>());
    }
    for (int caller = 0; caller < facts.length; caller++) {
      for (int callee : facts[caller].userCallees) {
        parents.get(callee).add(caller);
      }
    }
    var pending = new ArrayDeque<Integer>();
    boolean[] queued = new boolean[facts.length];
    for (int index = 0; index < facts.length; index++) {
      pending.add(index);
      queued[index] = true;
    }
    while (!pending.isEmpty()) {
      int word = pending.removeFirst();
      queued[word] = false;
      long capabilities = facts[word].directCapabilities;
      long effects = facts[word].directEffects;
      var connections = copyConnectionOperations(facts[word].directConnections);
      var workspaces = copyConnectionOperations(facts[word].directWorkspaces);
      for (int callee : facts[word].userCallees) {
        capabilities |= facts[callee].capabilities;
        effects |= facts[callee].effects;
        mergeConnectionOperations(connections, facts[callee].connections);
        mergeConnectionOperations(workspaces, facts[callee].workspaces);
      }
      if (capabilities == facts[word].capabilities
          && effects == facts[word].effects
          && connections.equals(facts[word].connections)
          && workspaces.equals(facts[word].workspaces)) {
        continue;
      }
      facts[word].capabilities = capabilities;
      facts[word].effects = effects;
      facts[word].connections = connections;
      facts[word].workspaces = workspaces;
      for (int parent : parents.get(word)) {
        if (!queued[parent]) {
          pending.addLast(parent);
          queued[parent] = true;
        }
      }
    }
  }

  private static boolean[] reachableFromMain(WordFacts[] facts, Map<String, Integer> wordIndex) {
    Integer main = wordIndex.get("メイン");
    if (main == null) {
      throw new IllegalStateException("an analyzed program has no main word");
    }
    boolean[] reachable = new boolean[facts.length];
    var pending = new ArrayDeque<Integer>();
    pending.add(main);
    reachable[main] = true;
    while (!pending.isEmpty()) {
      int word = pending.removeFirst();
      for (int callee : facts[word].userCallees) {
        if (!reachable[callee]) {
          reachable[callee] = true;
          pending.addLast(callee);
        }
      }
    }
    return reachable;
  }

  private static List<ProgramExplanation.UserWordEntry> userWords(
      AnalyzedProgram program,
      List<WordDefinition> definitions,
      WordFacts[] facts,
      boolean[] reachable) {
    var result = new ArrayList<ProgramExplanation.UserWordEntry>(definitions.size());
    for (int index = 0; index < definitions.size(); index++) {
      WordDefinition definition = definitions.get(index);
      WordSignature signature =
          program
              .findUserWord(definition.name())
              .orElseThrow(() -> new IllegalStateException("missing word signature"));
      WordFacts word = facts[index];
      result.add(
          new ProgramExplanation.UserWordEntry(
              definition.name(),
              definition.lexeme(),
              definition.nameSpan(),
              stackEffect(signature, !program.nonReturningWords().contains(definition.name())),
              reachable[index],
              names(word.directCapabilities),
              names(word.capabilities),
              names(word.directEffects),
              names(word.effects)));
    }
    return List.copyOf(result);
  }

  private static ProgramExplanation.Summary summary(
      List<WordDefinition> definitions, WordFacts[] facts, boolean[] reachable) {
    var reachableUsers = new ArrayList<String>();
    var reachableBuiltins = new LinkedHashSet<String>();
    long capabilities = 0;
    long effects = 0;
    for (int index = 0; index < facts.length; index++) {
      if (!reachable[index]) {
        continue;
      }
      reachableUsers.add(definitions.get(index).name());
      reachableBuiltins.addAll(facts[index].builtinCallees);
      capabilities |= facts[index].capabilities;
      effects |= facts[index].effects;
    }
    List<String> orderedBuiltins =
        BuiltinDictionary.words().stream()
            .map(BuiltinWord::canonicalName)
            .filter(reachableBuiltins::contains)
            .toList();
    return new ProgramExplanation.Summary(
        "メイン", reachableUsers, orderedBuiltins, names(capabilities), names(effects));
  }

  private static List<ProgramExplanation.BuiltinWordEntry> builtinWords() {
    return BuiltinDictionary.words().stream()
        .map(
            word ->
                new ProgramExplanation.BuiltinWordEntry(
                    word.canonicalName(),
                    List.copyOf(word.aliases()),
                    word.description(),
                    stackEffect(word),
                    typeRule(word.typeRule()),
                    names(mask(word.capabilities())),
                    names(mask(word.sideEffects())),
                    word.featureGroup(),
                    word.example()))
        .toList();
  }

  private static List<ProgramExplanation.ScopeEntry> scopes(AnalyzedProgram program) {
    var builder = new ScopeExplanationBuilder(program);
    List<ProgramExplanation.ScopeEntry> result = builder.build();
    List<LexicalScope> analyzedScopes = program.nameResolution().scopes();
    if (!analyzedScopes.isEmpty()) {
      List<ProgramExplanation.ScopeEntry> analyzed =
          analyzedScopes.stream()
              .map(
                  scope ->
                      new ProgramExplanation.ScopeEntry(
                          scope.id().toString(),
                          scopeKind(scope),
                          scope.parentId().map(Object::toString),
                          scope.ownerWord(),
                          scope.span()))
              .toList();
      if (!analyzed.equals(result)) {
        throw new IllegalStateException("analyzed scopes do not match syntax scopes");
      }
    }
    return result;
  }

  private static List<ProgramExplanation.BindingEntry> bindings(AnalyzedProgram program) {
    var result = new ArrayList<ProgramExplanation.BindingEntry>();
    for (Binding binding : program.nameResolution().bindings()) {
      String type =
          binding
              .typeState()
              .type()
              .orElseThrow(() -> new IllegalStateException("an explained binding has no type"))
              .sourceName();
      List<ProgramExplanation.BindingUseEntry> uses =
          program.nameResolution().usesOf(binding.id()).stream()
              .map(ProgramExplainer::bindingUse)
              .toList();
      result.add(
          new ProgramExplanation.BindingEntry(
              binding.id().displayName(),
              binding.name(),
              binding.spelling(),
              binding.kind().reportName(),
              binding.storage().reportName(),
              binding.scopeId().toString(),
              type,
              binding.globalInitializationOrder(),
              binding.nameSpan(),
              uses));
    }
    return List.copyOf(result);
  }

  private static ProgramExplanation.ParameterizedCapabilities parameterizedCapabilities(
      AnalyzedProgram program,
      List<WordDefinition> definitions,
      WordFacts[] facts,
      boolean[] reachable,
      Map<String, Integer> wordIndex) {
    List<LogicalConnectionDeclaration> declarations =
        program.logicalConnectionResolution().declarations();
    var declarationEntries =
        new ArrayList<ProgramExplanation.ConnectionDeclarationEntry>(declarations.size());
    for (LogicalConnectionDeclaration declaration : declarations) {
      List<ProgramExplanation.ConnectionUseEntry> uses =
          program.logicalConnectionResolution().uses().stream()
              .filter(use -> use.declaration() == declaration)
              .map(
                  use -> {
                    Integer owner = wordIndex.get(use.ownerWord());
                    if (owner == null) {
                      throw new IllegalStateException("a logical connection use has no owner word");
                    }
                    return new ProgramExplanation.ConnectionUseEntry(
                        use.ownerWord(),
                        use.httpMethod().orElse(use.operation()),
                        reachable[owner],
                        use.call().span());
                  })
              .toList();
      declarationEntries.add(
          new ProgramExplanation.ConnectionDeclarationEntry(
              declaration.name(), declaration.lexeme(), declaration.nameSpan(), uses));
    }
    List<WorkspaceDeclaration> workspaceDeclarations = program.workspaceResolution().declarations();
    var workspaceDeclarationEntries =
        new ArrayList<ProgramExplanation.WorkspaceDeclarationEntry>(workspaceDeclarations.size());
    for (WorkspaceDeclaration declaration : workspaceDeclarations) {
      List<ProgramExplanation.ConnectionUseEntry> uses =
          program.workspaceResolution().uses().stream()
              .filter(use -> use.declaration() == declaration)
              .map(
                  use -> {
                    Integer owner = wordIndex.get(use.ownerWord());
                    if (owner == null) {
                      throw new IllegalStateException("a workspace use has no owner word");
                    }
                    return new ProgramExplanation.ConnectionUseEntry(
                        use.ownerWord(), use.operation(), reachable[owner], use.call().span());
                  })
              .toList();
      workspaceDeclarationEntries.add(
          new ProgramExplanation.WorkspaceDeclarationEntry(
              declaration.name(), declaration.lexeme(), declaration.nameSpan(), uses));
    }

    var userEntries =
        new ArrayList<ProgramExplanation.ParameterizedUserWordEntry>(definitions.size());
    for (int index = 0; index < definitions.size(); index++) {
      userEntries.add(
          new ProgramExplanation.ParameterizedUserWordEntry(
              definitions.get(index).name(),
              requirements(
                  facts[index].directConnections,
                  declarations,
                  facts[index].directWorkspaces,
                  workspaceDeclarations),
              requirements(
                  facts[index].connections,
                  declarations,
                  facts[index].workspaces,
                  workspaceDeclarations)));
    }
    Integer main = wordIndex.get("メイン");
    if (main == null) {
      throw new IllegalStateException("an analyzed program has no main word");
    }
    return new ProgramExplanation.ParameterizedCapabilities(
        1,
        declarationEntries,
        workspaceDeclarationEntries,
        requirements(
            facts[main].connections, declarations, facts[main].workspaces, workspaceDeclarations),
        userEntries);
  }

  private static List<ProgramExplanation.ResourceRequirementEntry> requirements(
      List<LinkedHashSet<String>> operations,
      List<LogicalConnectionDeclaration> declarations,
      List<LinkedHashSet<String>> workspaceOperations,
      List<WorkspaceDeclaration> workspaceDeclarations) {
    var result = new ArrayList<ProgramExplanation.ResourceRequirementEntry>();
    for (int index = 0; index < operations.size(); index++) {
      if (!operations.get(index).isEmpty()) {
        result.add(
            new ProgramExplanation.ResourceRequirementEntry(
                "logicalConnection",
                declarations.get(index).name(),
                List.copyOf(operations.get(index))));
      }
    }
    for (int index = 0; index < workspaceOperations.size(); index++) {
      if (!workspaceOperations.get(index).isEmpty()) {
        result.add(
            new ProgramExplanation.ResourceRequirementEntry(
                "workspace",
                workspaceDeclarations.get(index).name(),
                List.copyOf(workspaceOperations.get(index))));
      }
    }
    return List.copyOf(result);
  }

  private static List<LinkedHashSet<String>> emptyConnectionOperations(int size) {
    var result = new ArrayList<LinkedHashSet<String>>(size);
    for (int index = 0; index < size; index++) result.add(new LinkedHashSet<>());
    return result;
  }

  private static List<LinkedHashSet<String>> copyConnectionOperations(
      List<LinkedHashSet<String>> source) {
    var result = new ArrayList<LinkedHashSet<String>>(source.size());
    source.forEach(operations -> result.add(new LinkedHashSet<>(operations)));
    return result;
  }

  private static void mergeConnectionOperations(
      List<LinkedHashSet<String>> target, List<LinkedHashSet<String>> source) {
    for (int index = 0; index < target.size(); index++) target.get(index).addAll(source.get(index));
  }

  private static ProgramExplanation.BindingUseEntry bindingUse(ResolvedBindingUse use) {
    var span =
        use.node() instanceof Assignment assignment ? assignment.targetSpan() : use.node().span();
    return new ProgramExplanation.BindingUseEntry(use.spelling(), use.kind().reportName(), span);
  }

  private static ProgramExplanation.StackEffectEntry stackEffect(BuiltinWord word) {
    return new ProgramExplanation.StackEffectEntry(
        word.inputTypeNames(), word.outputTypeNames(), word.returnsNormally());
  }

  private static ProgramExplanation.StackEffectEntry stackEffect(
      WordSignature signature, boolean returnsNormally) {
    return new ProgramExplanation.StackEffectEntry(
        signature.inputTypes().stream().map(type -> type.sourceName()).toList(),
        signature.outputTypes().stream().map(type -> type.sourceName()).toList(),
        returnsNormally);
  }

  private static String typeRule(BuiltinTypeRule rule) {
    return switch (rule) {
      case FIXED -> "fixed";
      case SAME_TYPE_PAIR -> "sameTypePair";
      case DISPLAYABLE -> "displayable";
      case SAME_NUMERIC_TYPE -> "sameNumericType";
      case INDEPENDENT_NUMERIC_INPUTS -> "independentNumericInputs";
      case ARRAY_LENGTH -> "arrayLength";
      case ARRAY_GET -> "arrayGet";
      case ARRAY_SLICE -> "arraySlice";
      case ARRAY_REPLACE -> "arrayReplace";
      case ARRAY_APPEND -> "arrayAppend";
      case OPTIONAL_WRAP -> "optionalWrap";
      case OPTIONAL_PREDICATE -> "optionalPredicate";
      case OPTIONAL_UNWRAP -> "optionalUnwrap";
      case OPTIONAL_DROP -> "optionalDrop";
      case RESULT_SUCCESS_WRAP -> "resultSuccessWrap";
      case RESULT_FAILURE_WRAP -> "resultFailureWrap";
      case RESULT_PREDICATE -> "resultPredicate";
      case RESULT_SUCCESS_UNWRAP -> "resultSuccessUnwrap";
      case RESULT_FAILURE_UNWRAP -> "resultFailureUnwrap";
      case RESULT_DROP -> "resultDrop";
    };
  }

  private static String scopeKind(LexicalScope scope) {
    return switch (scope.kind()) {
      case GLOBAL -> "global";
      case WORD_BODY -> "wordBody";
      case CONDITIONAL_TRUE -> "conditionalTrue";
      case CONDITIONAL_FALSE -> "conditionalFalse";
      case SHORT_CIRCUIT_RIGHT -> "shortCircuitRight";
      case COUNTED_LOOP_BODY -> "countedLoopBody";
      case CONDITION_LOOP_CONDITION -> "conditionLoopCondition";
      case CONDITION_LOOP_BODY -> "conditionLoopBody";
      case ARRAY_LOOP_BODY -> "arrayLoopBody";
    };
  }

  private static long mask(Set<String> values) {
    long result = 0;
    for (String value : values) {
      Integer bit = CAPABILITY_BITS.get(value);
      if (bit == null) {
        throw new IllegalStateException("unknown capability or effect: " + value);
      }
      result |= 1L << bit;
    }
    return result;
  }

  private static List<String> names(long mask) {
    var result = new ArrayList<String>();
    for (int index = 0; index < CAPABILITY_ORDER.size(); index++) {
      if ((mask & (1L << index)) != 0) {
        result.add(CAPABILITY_ORDER.get(index));
      }
    }
    return List.copyOf(result);
  }

  private static Map<String, Integer> capabilityBits() {
    var result = new LinkedHashMap<String, Integer>();
    for (int index = 0; index < CAPABILITY_ORDER.size(); index++) {
      if (result.put(CAPABILITY_ORDER.get(index), index) != null) {
        throw new ExceptionInInitializerError("duplicate capability");
      }
    }
    return Map.copyOf(result);
  }

  private static final class WordFacts {
    private final int[] userCallees;
    private final Set<String> builtinCallees;
    private final long directCapabilities;
    private final long directEffects;
    private final List<LinkedHashSet<String>> directConnections;
    private final List<LinkedHashSet<String>> directWorkspaces;
    private long capabilities;
    private long effects;
    private List<LinkedHashSet<String>> connections;
    private List<LinkedHashSet<String>> workspaces;

    private WordFacts(
        int[] userCallees,
        Set<String> builtinCallees,
        long directCapabilities,
        long directEffects,
        List<LinkedHashSet<String>> directConnections,
        List<LinkedHashSet<String>> directWorkspaces) {
      this.userCallees = Arrays.copyOf(userCallees, userCallees.length);
      this.builtinCallees = Set.copyOf(builtinCallees);
      this.directCapabilities = directCapabilities;
      this.directEffects = directEffects;
      this.directConnections = copyConnectionOperations(directConnections);
      this.directWorkspaces = copyConnectionOperations(directWorkspaces);
      capabilities = directCapabilities;
      effects = directEffects;
      connections = copyConnectionOperations(directConnections);
      workspaces = copyConnectionOperations(directWorkspaces);
    }
  }

  private static final class ScopeExplanationBuilder {
    private final AnalyzedProgram program;
    private final List<ProgramExplanation.ScopeEntry> scopes = new ArrayList<>();

    private ScopeExplanationBuilder(AnalyzedProgram program) {
      this.program = program;
    }

    private List<ProgramExplanation.ScopeEntry> build() {
      String global = add("global", Optional.empty(), Optional.empty(), program.syntax().span());
      for (WordDefinition definition : definitions(program)) {
        String wordBody =
            add("wordBody", Optional.of(global), Optional.of(definition.name()), definition.span());
        addBody(definition.body(), wordBody, definition.name());
      }
      return List.copyOf(scopes);
    }

    private void addBody(List<BodyElement> body, String parent, String ownerWord) {
      for (BodyElement element : body) {
        if (!program.isReachable(element)) {
          continue;
        }
        if (element instanceof Conditional conditional) {
          String trueScope =
              add(
                  "conditionalTrue",
                  Optional.of(parent),
                  Optional.of(ownerWord),
                  conditional.span());
          addBody(conditional.trueBody(), trueScope, ownerWord);
          if (conditional.hasElse()) {
            String falseScope =
                add(
                    "conditionalFalse",
                    Optional.of(parent),
                    Optional.of(ownerWord),
                    conditional.span());
            addBody(conditional.falseBody(), falseScope, ownerWord);
          }
        } else if (element instanceof CountedLoop loop) {
          String loopScope =
              add("countedLoopBody", Optional.of(parent), Optional.of(ownerWord), loop.span());
          addBody(loop.body(), loopScope, ownerWord);
        } else if (element instanceof ConditionLoop loop) {
          String conditionScope =
              add(
                  "conditionLoopCondition",
                  Optional.of(parent),
                  Optional.of(ownerWord),
                  loop.span());
          addBody(loop.conditionBody(), conditionScope, ownerWord);
          String bodyScope =
              add("conditionLoopBody", Optional.of(parent), Optional.of(ownerWord), loop.span());
          addBody(loop.body(), bodyScope, ownerWord);
        } else if (element instanceof ArrayLoop loop) {
          String loopScope =
              add("arrayLoopBody", Optional.of(parent), Optional.of(ownerWord), loop.span());
          addBody(loop.body(), loopScope, ownerWord);
        }
      }
    }

    private String add(
        String kind, Optional<String> parentId, Optional<String> ownerWord, SourceSpan location) {
      String id = "S" + (scopes.size() + 1);
      scopes.add(new ProgramExplanation.ScopeEntry(id, kind, parentId, ownerWord, location));
      return id;
    }
  }
}
