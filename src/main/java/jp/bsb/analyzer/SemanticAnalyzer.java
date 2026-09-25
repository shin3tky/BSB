package jp.bsb.analyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jp.bsb.binding.Binding;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingStorage;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.binding.DeclaredName;
import jp.bsb.binding.NameResolution;
import jp.bsb.binding.WordName;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.UnicodeRules;
import jp.bsb.frontend.ast.ArrayElement;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.ArrayLoop;
import jp.bsb.frontend.ast.Assignment;
import jp.bsb.frontend.ast.BodyElement;
import jp.bsb.frontend.ast.Comment;
import jp.bsb.frontend.ast.ConditionLoop;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.ControlTransfer;
import jp.bsb.frontend.ast.CountedLoop;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LogicalConnectionDeclaration;
import jp.bsb.frontend.ast.Particle;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.Propagation;
import jp.bsb.frontend.ast.ShortCircuitEvaluation;
import jp.bsb.frontend.ast.TopLevelElement;
import jp.bsb.frontend.ast.TypeReference;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.frontend.ast.WorkspaceDeclaration;
import jp.bsb.numeric.DecimalLexemeAnalyzer;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Invalid;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Valid;
import jp.bsb.numeric.DecimalLimits;
import jp.bsb.regex.Re2RegexCompiler;
import jp.bsb.regex.RegexCompilationResult;
import jp.bsb.regex.RegexLimits;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.BuiltinWord;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import jp.bsb.stdlib.ValueTypeTraits;

/**
 * ホスト入出力までの制御フロー、値束縛、配列、数値、文字列、正規表現、入出力・実行環境語を含む名前解決、型検査、スタック効果検査、静的警告を実行します。
 *
 * <p>【コンピュータ科学の観点：多段階の意味解析】 先に全定義名と宣言シグネチャを集めることで、後に書かれた単語や再帰する単語も本体の走査前に参照できます。
 * 本体では実際の値を計算せず、型だけを積んだ「抽象スタック」を動かします。この抽象実行により、プログラムを実行する前に入力不足と型不一致を検出できます。
 */
public final class SemanticAnalyzer {
  /** 状態を持たない静的解析器を生成します。 */
  public SemanticAnalyzer() {}

  /**
   * 構文的に有効なプログラムを静的検査します。
   *
   * @param program 構文解析に成功したAST
   * @return 診断と、成功時だけ存在するIR生成用プログラム
   */
  public AnalysisResult analyze(Program program) {
    Objects.requireNonNull(program, "program");
    var state = new State(program);
    state.validateDecimalLiterals();
    state.collectDefinitions();
    state.validateMain();
    state.identifyReachability();
    boolean bindingsValid = state.resolveBindings();
    state.reportConfusableNames();
    if (!bindingsValid) {
      return state.result();
    }
    state.validateDeclaredTypes();
    state.resolveBodies();
    state.inferBindingTypes();
    state.checkWordBodies();
    return state.result();
  }

  /** 1回の解析に固有な記号表、解決結果、診断を保持します。 */
  private static final class State {
    private static final Re2RegexCompiler REGEX_COMPILER = new Re2RegexCompiler();
    private final Program program;
    private final DiagnosticCollector diagnostics = new DiagnosticCollector();
    private final Map<String, WordDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ValueDeclaration> globalValueDeclarations = new LinkedHashMap<>();
    private final Map<String, LogicalConnectionDeclaration> logicalConnections =
        new LinkedHashMap<>();
    private final Map<String, WorkspaceDeclaration> workspaces = new LinkedHashMap<>();
    private final Map<String, GlobalNameSite> globalNameSites = new LinkedHashMap<>();
    private final Map<WordDefinition, WordSignature> signatures = new IdentityHashMap<>();
    private final Map<WordCall, CallTarget> resolvedCalls = new IdentityHashMap<>();
    private final Map<ArrayLiteral, ValueType> arrayLiteralTypes = new IdentityHashMap<>();
    private final Map<WordCall, WordSignature> callSignatures = new IdentityHashMap<>();
    private final List<LogicalConnectionResolution.Use> logicalConnectionUses = new ArrayList<>();
    private final List<WorkspaceResolution.Use> workspaceUses = new ArrayList<>();
    private final Set<WordDefinition> invalidDefinitions = identitySet();
    private final Set<WordDefinition> bodiesWithResolutionErrors = identitySet();
    private final Set<BodyElement> unreachableElements = identitySet();
    private final Set<Literal> validatedRegexLiterals = identitySet();
    private final Set<Literal> invalidRegexLiterals = identitySet();
    private final Set<String> nonReturningWords = new LinkedHashSet<>();
    private final boolean bindingSyntaxPresent;
    private NameResolution nameResolution = NameResolution.empty();
    private Map<ValueDeclaration, BindingId> bindingIdsByDeclaration = Map.of();
    private final Map<BindingId, BindingTypeState> bindingTypeStates = new LinkedHashMap<>();
    private boolean globalBindingNameErrors;
    private boolean logicalConnectionLimitExceeded;
    private int logicalConnectionCount;
    private boolean workspaceLimitExceeded;
    private int workspaceCount;

    private State(Program program) {
      this.program = program;
      bindingSyntaxPresent = containsBindingSyntax(program);
    }

    /** 全ソース小数を値生成前に有限幅で検査し、通常の型検査より先に資源診断を確定します。 */
    private void validateDecimalLiterals() {
      for (TopLevelElement element : program.elements()) {
        if (element instanceof ValueDeclaration declaration) {
          validateDecimalLiterals(declaration.initializer());
        } else if (element instanceof WordDefinition definition) {
          validateDecimalLiterals(definition.body());
        }
      }
    }

    private void validateDecimalLiterals(List<BodyElement> body) {
      for (BodyElement element : body) {
        if (element instanceof Literal literal) {
          validateDecimalLiteral(literal);
        } else if (element instanceof ArrayLiteral array) {
          array.elements().forEach(item -> validateDecimalLiterals(item.body()));
        } else if (element instanceof ValueDeclaration declaration) {
          validateDecimalLiterals(declaration.initializer());
        } else if (element instanceof Conditional conditional) {
          validateDecimalLiterals(conditional.trueBody());
          validateDecimalLiterals(conditional.falseBody());
        } else if (element instanceof ShortCircuitEvaluation evaluation) {
          validateDecimalLiterals(evaluation.rightBody());
        } else if (element instanceof CountedLoop loop) {
          validateDecimalLiterals(loop.body());
        } else if (element instanceof ConditionLoop loop) {
          validateDecimalLiterals(loop.conditionBody());
          validateDecimalLiterals(loop.body());
        } else if (element instanceof ArrayLoop loop) {
          validateDecimalLiterals(loop.body());
        }
      }
    }

    private void validateDecimalLiteral(Literal literal) {
      if (literal.kind() != jp.bsb.frontend.ast.LiteralKind.DECIMAL) {
        return;
      }
      DecimalLexemeAnalyzer.Result result =
          DecimalLexemeAnalyzer.analyze(literal.value(), DecimalLimits.ABSOLUTE_SCALE);
      if (result instanceof Invalid invalid) {
        throw new IllegalStateException(
            "invalid decimal literal reached semantic analysis: " + invalid.reason().stableId());
      }
      Valid valid = (Valid) result;
      if (absoluteScale(valid.rawScale()) > DecimalLimits.ABSOLUTE_SCALE) {
        reportDecimalScaleLimit(literal, "rawScale", valid.rawScale());
      } else if (absoluteScale(valid.normalizedScale()) > DecimalLimits.ABSOLUTE_SCALE) {
        reportDecimalScaleLimit(literal, "normalizedScale", valid.normalizedScale());
      }
    }

    private void reportDecimalScaleLimit(Literal literal, String metric, long scale) {
      long observed = absoluteScale(scale);
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_DECIMAL_SCALE_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  literal.span())
              .field("word", "小数リテラル")
              .field("metric", metric)
              .limit("decimalScale", DecimalLimits.ABSOLUTE_SCALE, observed)
              .expected("スケール絶対値" + DecimalLimits.ABSOLUTE_SCALE + "以下")
              .actual(Long.toString(observed))
              .fix("指数の絶対値を小さくしてください")
              .build());
    }

    private static long absoluteScale(long scale) {
      return scale < 0 ? -scale : scale;
    }

    /** 予約名と重複を検査しながら、最初の有効な定義を記号表へ登録します。 */
    private void collectDefinitions() {
      for (TopLevelElement element : program.elements()) {
        if (element instanceof WordDefinition definition) {
          collectWordDefinition(definition);
        } else if (element instanceof ValueDeclaration declaration) {
          collectGlobalValueDeclaration(declaration);
        } else if (element instanceof LogicalConnectionDeclaration declaration) {
          collectLogicalConnectionDeclaration(declaration);
        } else if (element instanceof WorkspaceDeclaration declaration) {
          collectWorkspaceDeclaration(declaration);
        }
      }
    }

    private void collectWordDefinition(WordDefinition definition) {
      if (LanguageNames.reservedForDefinition(definition.name())) {
        reportReservedName(definition);
        invalidDefinitions.add(definition);
        return;
      }
      GlobalNameSite previous = globalNameSites.get(definition.name());
      if (previous != null) {
        reportDuplicateName(definition.lexeme(), definition.nameSpan(), previous);
        invalidDefinitions.add(definition);
        if (previous.declaration() instanceof ValueDeclaration) {
          globalBindingNameErrors = true;
        }
        return;
      }
      definitions.put(definition.name(), definition);
      globalNameSites.put(
          definition.name(),
          new GlobalNameSite(definition.lexeme(), definition.nameSpan(), definition));
    }

    private void collectGlobalValueDeclaration(ValueDeclaration declaration) {
      if (LanguageNames.reservedForValue(declaration.name())) {
        reportReservedName(declaration);
        globalBindingNameErrors = true;
        return;
      }
      GlobalNameSite previous = globalNameSites.get(declaration.name());
      if (previous != null) {
        reportDuplicateName(declaration.lexeme(), declaration.nameSpan(), previous);
        globalBindingNameErrors = true;
        return;
      }
      globalValueDeclarations.put(declaration.name(), declaration);
      globalNameSites.put(
          declaration.name(),
          new GlobalNameSite(declaration.lexeme(), declaration.nameSpan(), declaration));
    }

    private void collectLogicalConnectionDeclaration(LogicalConnectionDeclaration declaration) {
      logicalConnectionCount++;
      if (logicalConnectionCount > 10_000) {
        if (!logicalConnectionLimitExceeded) {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_LOGICAL_CONNECTION_LIMIT,
                      Severity.ERROR,
                      DiagnosticStage.NAME,
                      program.sourcePath(),
                      declaration.kindSpan())
                  .limit("logicalConnections", 10_000, logicalConnectionCount)
                  .field("limit", "10000")
                  .field("observed", Integer.toString(logicalConnectionCount))
                  .expected("10000個以下")
                  .actual(logicalConnectionCount + "個")
                  .build());
        }
        logicalConnectionLimitExceeded = true;
        return;
      }
      if (LanguageNames.reservedForValue(declaration.name())) {
        reportReservedName(declaration);
        return;
      }
      GlobalNameSite previous = globalNameSites.get(declaration.name());
      if (previous != null) {
        reportDuplicateName(declaration.lexeme(), declaration.nameSpan(), previous);
        return;
      }
      logicalConnections.put(declaration.name(), declaration);
      globalNameSites.put(
          declaration.name(),
          new GlobalNameSite(declaration.lexeme(), declaration.nameSpan(), declaration));
    }

    private void collectWorkspaceDeclaration(WorkspaceDeclaration declaration) {
      workspaceCount++;
      if (workspaceCount > 10_000) {
        if (!workspaceLimitExceeded) {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_WORKSPACE_DECLARATION_LIMIT,
                      Severity.ERROR,
                      DiagnosticStage.NAME,
                      program.sourcePath(),
                      declaration.kindSpan())
                  .limit("workspaces", 10_000, workspaceCount)
                  .field("limit", "10000")
                  .field("observed", Integer.toString(workspaceCount))
                  .expected("10000個以下")
                  .actual(workspaceCount + "個")
                  .build());
        }
        workspaceLimitExceeded = true;
        return;
      }
      if (LanguageNames.reservedForValue(declaration.name())) {
        reportReservedName(declaration);
        return;
      }
      GlobalNameSite previous = globalNameSites.get(declaration.name());
      if (previous != null) {
        reportDuplicateName(declaration.lexeme(), declaration.nameSpan(), previous);
        return;
      }
      workspaces.put(declaration.name(), declaration);
      globalNameSites.put(
          declaration.name(),
          new GlobalNameSite(declaration.lexeme(), declaration.nameSpan(), declaration));
    }

    private void validateMain() {
      WordDefinition main = definitions.get(LanguageNames.MAIN);
      if (main == null) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_MISSING_MAIN,
                    Severity.ERROR,
                    DiagnosticStage.NAME,
                    program.sourcePath(),
                    program.span().end())
                .expected("メインとは （--）")
                .actual("EOF")
                .fix("メインを追加してください")
                .build());
        return;
      }

      if (!main.stackEffect().inputTypes().isEmpty()
          || !main.stackEffect().outputTypes().isEmpty()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_INVALID_MAIN_EFFECT,
                    Severity.ERROR,
                    DiagnosticStage.NAME,
                    program.sourcePath(),
                    main.nameSpan())
                .expected("（--）")
                .actual(formatDeclaredEffect(main))
                .fix("メインの効果を（--）にしてください")
                .build());
        invalidDefinitions.add(main);
      }
    }

    /** 全宣言を本体より先に型へ変換し、前方参照と再帰で使えるシグネチャ表を作ります。 */
    private void validateDeclaredTypes() {
      for (WordDefinition definition : definitions.values()) {
        var diagnosedNames = new LinkedHashSet<String>();
        List<ValueType> inputs =
            resolveTypeList(definition.stackEffect().inputTypes(), diagnosedNames);
        List<ValueType> outputs =
            resolveTypeList(definition.stackEffect().outputTypes(), diagnosedNames);
        if (inputs != null && outputs != null) {
          signatures.put(definition, new WordSignature(inputs, outputs));
        } else {
          invalidDefinitions.add(definition);
        }
      }
    }

    private List<ValueType> resolveTypeList(
        List<TypeReference> references, Set<String> diagnosedNames) {
      var result = new ArrayList<ValueType>();
      boolean valid = true;
      for (TypeReference reference : references) {
        ValueType type = resolveTypeReference(reference, diagnosedNames);
        if (type == null) {
          valid = false;
        } else {
          result.add(type);
        }
      }
      return valid ? List.copyOf(result) : null;
    }

    /** 1個の型参照を構造に沿って解決し、主因となる最内側の診断だけを返します。 */
    private ValueType resolveTypeReference(TypeReference reference, Set<String> diagnosedNames) {
      if (reference.name().equals("配列") && !reference.isArray()) {
        if (diagnosedNames.add(reference.name())) {
          reportArrayElementTypeRequired(reference);
        }
        return null;
      }
      if (reference.name().equals("任意") && !reference.isOptional()) {
        if (diagnosedNames.add(reference.name())) {
          reportOptionalElementTypeRequired(reference);
        }
        return null;
      }
      if (reference.name().equals("結果") && !reference.isResult()) {
        if (diagnosedNames.add(reference.name())) {
          reportResultTypeArgumentsRequired(reference);
        }
        return null;
      }
      if (reference.isArray()) {
        return resolveArrayType(reference, diagnosedNames);
      }
      if (reference.isOptional()) {
        ValueType elementType =
            resolveTypeReference(reference.typeArgument().orElseThrow(), diagnosedNames);
        return elementType == null ? null : ValueType.optionalOf(elementType);
      }
      if (reference.isResult()) {
        ValueType successType =
            resolveTypeReference(reference.typeArgument().orElseThrow(), new LinkedHashSet<>());
        ValueType failureType =
            resolveTypeReference(
                reference.secondTypeArgument().orElseThrow(), new LinkedHashSet<>());
        return successType == null || failureType == null
            ? null
            : ValueType.resultOf(successType, failureType);
      }

      var scalarType = ScalarType.fromSourceName(reference.name());
      if (scalarType.isPresent()) {
        return scalarType.orElseThrow();
      }
      if (diagnosedNames.add(reference.name())) {
        String plannedFeature = LanguageNames.FUTURE_FEATURES.get(reference.name());
        if (plannedFeature != null) {
          reportFeatureNotAvailable(
              reference.span(), reference.name(), reference.lexeme(), plannedFeature);
        } else if (LanguageNames.TYPE_CONSTRAINTS.contains(reference.name())) {
          reportTypeConstraintNotAllowed(reference);
        } else {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_UNKNOWN_TYPE,
                      Severity.ERROR,
                      DiagnosticStage.TYPE_AND_STACK,
                      program.sourcePath(),
                      reference.span())
                  .field("typeName", reference.name())
                  .expected("整数・真偽・文字・文字列")
                  .actual(reference.lexeme())
                  .fix("言語コアの型へ変更してください")
                  .build());
        }
      }
      return null;
    }

    /** ラッパー越しの配列構築子も数え、最大2次元の配列だけを解決します。 */
    private ValueType resolveArrayType(TypeReference reference, Set<String> diagnosedNames) {
      TypeReference elementReference = reference.typeArgument().orElseThrow();
      ValueType elementType =
          resolveTypeReference(
              elementReference,
              elementReference.isResult() || elementReference.isArray()
                  ? new LinkedHashSet<>()
                  : diagnosedNames);
      if (elementType != null) {
        if (elementType.isArrayElementType()) {
          return ValueType.arrayOf(elementType);
        }
        if (diagnosedNames.add(reference.name())) {
          if (ValueType.arrayConstructorDepth(elementType) >= 2) {
            reportNestedArrayType(elementReference);
          } else if (elementType instanceof ScalarType scalarType) {
            reportArrayElementTypeNotAllowed(elementReference, scalarType);
          } else {
            reportArrayElementTypeNotAllowed(elementReference, elementType);
          }
        }
        return null;
      }

      // 内側の型解決が主因を診断済みなので、外側の派生診断を追加しません。
      return null;
    }

    /**
     * 型や実値を使わず、無条件の制御移行だけから到達不能範囲を先に確定します。
     *
     * <p>【コンピュータ科学の観点：構造的到達可能性】名前解決より前に「絶対に実行されない範囲」を見つけると、その中の未定義名などを派生エラーとして報告せずに済みます。
     * 真偽リテラルや回数の値は見ないため、実行結果を推測する最適化とは異なります。
     */
    private void identifyReachability() {
      computeNonReturningWords();
      for (WordDefinition definition : definitions.values()) {
        if (!invalidDefinitions.contains(definition)) {
          identifyBodyReachability(definition.body());
        }
      }
    }

    /** 呼出し先をまたいで通常出口を持たない単語を単調な固定点として求めます。 */
    private void computeNonReturningWords() {
      boolean changed;
      do {
        changed = false;
        for (WordDefinition definition : definitions.values()) {
          if (!invalidDefinitions.contains(definition)
              && !nonReturningWords.contains(definition.name())
              && !bodyReturnsNormally(definition.body())) {
            nonReturningWords.add(definition.name());
            changed = true;
          }
        }
      } while (changed);
    }

    private boolean bodyReturnsNormally(List<BodyElement> body) {
      ReturnSummary summary = summarizeReturns(body);
      return summary.fallsThrough() || summary.returnsFromWord();
    }

    private ReturnSummary summarizeReturns(List<BodyElement> body) {
      boolean returnsFromWord = false;
      for (BodyElement element : body) {
        ReturnSummary elementSummary = summarizeReturns(element);
        returnsFromWord |= elementSummary.returnsFromWord();
        if (!elementSummary.fallsThrough()) {
          return new ReturnSummary(false, returnsFromWord);
        }
      }
      return new ReturnSummary(true, returnsFromWord);
    }

    private ReturnSummary summarizeReturns(BodyElement element) {
      if (element instanceof ControlTransfer transfer) {
        return new ReturnSummary(false, transfer.kind() == ControlTransfer.Kind.RETURN);
      }
      if (element instanceof Propagation) {
        return new ReturnSummary(true, true);
      }
      if (element instanceof WordCall call) {
        var builtin = BuiltinDictionary.find(call.name());
        boolean returns =
            builtin
                .map(BuiltinWord::returnsNormally)
                .orElse(!nonReturningWords.contains(call.name()));
        return new ReturnSummary(returns, false);
      }
      if (element instanceof Conditional conditional) {
        ReturnSummary trueSummary = summarizeReturns(conditional.trueBody());
        ReturnSummary falseSummary =
            conditional.hasElse()
                ? summarizeReturns(conditional.falseBody())
                : new ReturnSummary(true, false);
        return new ReturnSummary(
            trueSummary.fallsThrough() || falseSummary.fallsThrough(),
            trueSummary.returnsFromWord() || falseSummary.returnsFromWord());
      }
      if (element instanceof ShortCircuitEvaluation evaluation) {
        ReturnSummary rightSummary = summarizeReturns(evaluation.rightBody());
        return new ReturnSummary(true, rightSummary.returnsFromWord());
      }
      if (element instanceof CountedLoop loop) {
        ReturnSummary bodySummary = summarizeReturns(loop.body());
        return new ReturnSummary(true, bodySummary.returnsFromWord());
      }
      if (element instanceof ArrayLoop loop) {
        ReturnSummary bodySummary = summarizeReturns(loop.body());
        return new ReturnSummary(true, bodySummary.returnsFromWord());
      }
      if (element instanceof ConditionLoop loop) {
        ReturnSummary conditionSummary = summarizeReturns(loop.conditionBody());
        if (!conditionSummary.fallsThrough()) {
          return conditionSummary;
        }
        ReturnSummary bodySummary = summarizeReturns(loop.body());
        return new ReturnSummary(
            true, conditionSummary.returnsFromWord() || bodySummary.returnsFromWord());
      }
      return new ReturnSummary(true, false);
    }

    private boolean resolveBindings() {
      if (!bindingSyntaxPresent) {
        nameResolution =
            NameResolution.wordsOnly(
                definitions.values().stream()
                    .map(
                        definition ->
                            new WordName(
                                definition.name(), definition.lexeme(), definition.nameSpan()))
                    .toList());
        return true;
      }
      BindingResolver.Result result =
          BindingResolver.resolve(
              program,
              definitions,
              globalValueDeclarations,
              logicalConnections,
              workspaces,
              unreachableElements,
              diagnostics);
      nameResolution = result.resolution();
      bindingIdsByDeclaration = result.bindingIdsByDeclaration();
      return result.valid() && !globalBindingNameErrors;
    }

    private StructuralFlow identifyBodyReachability(List<BodyElement> body) {
      boolean canFallThrough = true;
      boolean canReturn = false;
      boolean canBreak = false;
      boolean canContinue = false;
      BodyElement cause = null;

      for (int index = 0; index < body.size(); index++) {
        BodyElement element = body.get(index);
        if (!canFallThrough) {
          markUnreachableRange(body, index, cause);
          break;
        }

        StructuralFlow elementFlow = identifyElementReachability(element);
        canReturn |= elementFlow.canReturn();
        canBreak |= elementFlow.canBreak();
        canContinue |= elementFlow.canContinue();
        canFallThrough = elementFlow.canFallThrough();
        if (!canFallThrough) {
          cause = elementFlow.cause();
        }
      }
      return new StructuralFlow(canFallThrough, canReturn, canBreak, canContinue, cause);
    }

    private StructuralFlow identifyElementReachability(BodyElement element) {
      if (element instanceof ControlTransfer transfer) {
        return switch (transfer.kind()) {
          case RETURN -> StructuralFlow.returned(transfer);
          case BREAK -> StructuralFlow.broken(transfer);
          case CONTINUE -> StructuralFlow.continued(transfer);
        };
      }
      if (element instanceof Propagation) {
        return new StructuralFlow(true, true, false, false, null);
      }
      if (element instanceof WordCall call
          && (BuiltinDictionary.find(call.name()).map(word -> !word.returnsNormally()).orElse(false)
              || nonReturningWords.contains(call.name()))) {
        return StructuralFlow.terminated(call);
      }
      if (element instanceof Conditional conditional) {
        StructuralFlow trueFlow = identifyBodyReachability(conditional.trueBody());
        StructuralFlow falseFlow =
            conditional.hasElse()
                ? identifyBodyReachability(conditional.falseBody())
                : StructuralFlow.fallthrough();
        boolean fallsThrough = trueFlow.canFallThrough() || falseFlow.canFallThrough();
        return new StructuralFlow(
            fallsThrough,
            trueFlow.canReturn() || falseFlow.canReturn(),
            trueFlow.canBreak() || falseFlow.canBreak(),
            trueFlow.canContinue() || falseFlow.canContinue(),
            fallsThrough ? null : earlierCause(trueFlow.cause(), falseFlow.cause()));
      }
      if (element instanceof ShortCircuitEvaluation evaluation) {
        StructuralFlow rightFlow = identifyBodyReachability(evaluation.rightBody());
        return new StructuralFlow(
            true, rightFlow.canReturn(), rightFlow.canBreak(), rightFlow.canContinue(), null);
      }
      if (element instanceof CountedLoop loop) {
        StructuralFlow bodyFlow = identifyBodyReachability(loop.body());
        // 回数ループには0回経路が必ずあるため、ループ後は構造上到達可能です。
        return new StructuralFlow(true, bodyFlow.canReturn(), false, false, null);
      }
      if (element instanceof ConditionLoop loop) {
        StructuralFlow conditionFlow = identifyBodyReachability(loop.conditionBody());
        StructuralFlow bodyFlow;
        if (conditionFlow.canFallThrough()) {
          bodyFlow = identifyBodyReachability(loop.body());
        } else {
          markUnreachableRange(loop.body(), 0, conditionFlow.cause());
          bodyFlow = StructuralFlow.unreachable(conditionFlow.cause());
        }
        boolean fallsThrough =
            conditionFlow.canFallThrough() || conditionFlow.canBreak() || bodyFlow.canBreak();
        return new StructuralFlow(
            fallsThrough,
            conditionFlow.canReturn() || bodyFlow.canReturn(),
            false,
            false,
            fallsThrough ? null : earlierCause(conditionFlow.cause(), bodyFlow.cause()));
      }
      if (element instanceof ArrayLoop loop) {
        StructuralFlow bodyFlow = identifyBodyReachability(loop.body());
        // 配列反復には空配列の0回経路があるため、ループ後は構造上到達可能です。
        return new StructuralFlow(true, bodyFlow.canReturn(), false, false, null);
      }
      return StructuralFlow.fallthrough();
    }

    /** 到達不能な最大連続範囲を記録し、最初の実行要素だけへ警告を付けます。 */
    private void markUnreachableRange(List<BodyElement> body, int startIndex, BodyElement cause) {
      BodyElement firstExecutable = null;
      String textFeatureOperation = null;
      for (int index = startIndex; index < body.size(); index++) {
        BodyElement element = body.get(index);
        markUnreachableTree(element);
        if (firstExecutable == null && isExecutable(element)) {
          firstExecutable = element;
        }
        if (textFeatureOperation == null
            && element instanceof WordCall call
            && BuiltinDictionary.find(call.name())
                .filter(word -> word.featureGroup().equals("TEXT"))
                .isPresent()) {
          textFeatureOperation = call.lexeme();
        }
      }
      if (firstExecutable != null && cause != null) {
        reportUnreachable(
            firstExecutable,
            textFeatureOperation == null ? elementText(firstExecutable) : textFeatureOperation,
            cause);
      }
    }

    private void markUnreachableTree(BodyElement element) {
      unreachableElements.add(element);
      if (element instanceof ArrayLiteral array) {
        array.elements().stream()
            .flatMap(arrayElement -> arrayElement.body().stream())
            .forEach(this::markUnreachableTree);
      } else if (element instanceof ValueDeclaration declaration) {
        declaration.initializer().forEach(this::markUnreachableTree);
      } else if (element instanceof Conditional conditional) {
        conditional.trueBody().forEach(this::markUnreachableTree);
        conditional.falseBody().forEach(this::markUnreachableTree);
      } else if (element instanceof ShortCircuitEvaluation evaluation) {
        evaluation.rightBody().forEach(this::markUnreachableTree);
      } else if (element instanceof CountedLoop loop) {
        loop.body().forEach(this::markUnreachableTree);
      } else if (element instanceof ConditionLoop loop) {
        loop.conditionBody().forEach(this::markUnreachableTree);
        loop.body().forEach(this::markUnreachableTree);
      } else if (element instanceof ArrayLoop loop) {
        loop.body().forEach(this::markUnreachableTree);
      }
    }

    private void reportUnreachable(
        BodyElement element, String unreachableDescription, BodyElement cause) {
      String causeText = elementText(cause);
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.W_UNREACHABLE_CODE,
                  Severity.WARNING,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  element.span())
              .field("cause", causeText)
              .field("causeLine", Integer.toString(cause.span().start().line()))
              .field("causeColumn", Integer.toString(cause.span().start().column()))
              .expected("到達可能な処理")
              .actual(unreachableDescription)
              .fix("この処理を削除するか" + causeText + "前へ移動してください")
              .relatedLocation(
                  new RelatedLocation(program.sourcePath(), cause.span().start(), causeText))
              .build());
    }

    private static boolean isExecutable(BodyElement element) {
      return !(element instanceof Comment) && !(element instanceof Particle);
    }

    private static String elementText(BodyElement element) {
      return switch (element) {
        case Assignment ignored -> "入れる";
        case ArrayLiteral ignored -> "配列";
        case ArrayLoop ignored -> "各要素について";
        case Literal literal -> literal.lexeme();
        case ValueDeclaration declaration -> declaration.lexeme();
        case ValueReference reference -> reference.lexeme();
        case WordCall call -> call.lexeme();
        case ControlTransfer transfer -> transfer.lexeme();
        case Propagation propagation -> propagation.lexeme();
        case Conditional ignored -> "ならば";
        case ShortCircuitEvaluation evaluation -> evaluation.operator().sourceName();
        case CountedLoop ignored -> "回だけ";
        case ConditionLoop ignored -> "ここから";
        case Comment comment -> comment.text();
        case Particle particle -> particle.lexeme();
      };
    }

    private static BodyElement earlierCause(BodyElement first, BodyElement second) {
      if (first == null) {
        return second;
      }
      if (second == null || first.span().utf8Offset() <= second.span().utf8Offset()) {
        return first;
      }
      return second;
    }

    /** 呼出し候補を分類し、助詞位置も本体ごとに検査します。 */
    private void resolveBodies() {
      for (WordDefinition definition : definitions.values()) {
        if (!invalidDefinitions.contains(definition)) {
          resolveBodyNames(definition);
        }
        checkParticles(definition);
      }
    }

    private void resolveBodyNames(WordDefinition definition) {
      resolveBodyNames(definition, definition.body());
    }

    /** 制御構文の子本体もたどり、構文木の深さに関係なく同じ名前解決規則を適用します。 */
    private void resolveBodyNames(WordDefinition definition, List<BodyElement> body) {
      for (BodyElement element : body) {
        if (unreachableElements.contains(element)) {
          continue;
        }
        if (element instanceof ArrayLiteral array) {
          for (ArrayElement arrayElement : array.elements()) {
            resolveBodyNames(definition, arrayElement.body());
          }
          continue;
        }

        if (element instanceof Conditional conditional) {
          resolveBodyNames(definition, conditional.trueBody());
          resolveBodyNames(definition, conditional.falseBody());
          continue;
        }
        if (element instanceof ShortCircuitEvaluation evaluation) {
          resolveBodyNames(definition, evaluation.rightBody());
          continue;
        }
        if (element instanceof CountedLoop loop) {
          resolveBodyNames(definition, loop.body());
          continue;
        }
        if (element instanceof ConditionLoop loop) {
          resolveBodyNames(definition, loop.conditionBody());
          resolveBodyNames(definition, loop.body());
          continue;
        }
        if (element instanceof ArrayLoop loop) {
          resolveBodyNames(definition, loop.body());
          continue;
        }
        if (!(element instanceof WordCall call)) {
          continue;
        }

        if (call.logicalConnectionArgument().isPresent()) {
          if (logicalConnectionLimitExceeded) {
            bodiesWithResolutionErrors.add(definition);
            continue;
          }
          String connectionName = call.logicalConnectionArgument().orElseThrow().name();
          LogicalConnectionDeclaration connection = logicalConnections.get(connectionName);
          if (connection == null) {
            reportUndeclaredLogicalConnection(call, connectionName);
            bodiesWithResolutionErrors.add(definition);
            continue;
          }
          logicalConnectionUses.add(
              new LogicalConnectionResolution.Use(
                  call,
                  connection,
                  definition.name(),
                  "resolve",
                  call.logicalConnectionArgument()
                      .orElseThrow()
                      .httpMethod()
                      .map(jp.bsb.frontend.ast.HttpMethodArgument::value)));
        } else if (call.workspaceArgument().isPresent()) {
          if (workspaceLimitExceeded) {
            bodiesWithResolutionErrors.add(definition);
            continue;
          }
          String workspaceName = call.workspaceArgument().orElseThrow().name();
          WorkspaceDeclaration workspace = workspaces.get(workspaceName);
          if (workspace == null) {
            reportUndeclaredWorkspace(call, workspaceName);
            bodiesWithResolutionErrors.add(definition);
            continue;
          }
          String operation = call.name().equals("ファイルを読む") ? "read" : "write";
          workspaceUses.add(
              new WorkspaceResolution.Use(call, workspace, definition.name(), operation));
        } else if (logicalConnections.containsKey(call.name())) {
          reportLogicalConnectionReferenceNotAllowed(call);
          bodiesWithResolutionErrors.add(definition);
          continue;
        } else if (workspaces.containsKey(call.name())) {
          reportWorkspaceReferenceNotAllowed(call);
          bodiesWithResolutionErrors.add(definition);
          continue;
        }

        var builtin = BuiltinDictionary.find(call.name());
        if (builtin.isPresent()) {
          resolvedCalls.put(call, new BuiltinTarget(builtin.orElseThrow()));
          continue;
        }

        String plannedFeature = LanguageNames.FUTURE_FEATURES.get(call.name());
        if (plannedFeature != null) {
          reportFeatureNotAvailable(call.span(), call.name(), call.lexeme(), plannedFeature);
          bodiesWithResolutionErrors.add(definition);
          continue;
        }

        var nonCallable = LanguageNames.currentNonCallableKind(call.name());
        if (nonCallable.isPresent()) {
          reportNameNotCallable(call, nonCallable.orElseThrow());
          bodiesWithResolutionErrors.add(definition);
          continue;
        }

        WordDefinition target = definitions.get(call.name());
        if (target != null) {
          WordSignature signature = signatures.get(target);
          if (signature != null && !invalidDefinitions.contains(target)) {
            resolvedCalls.put(
                call, new UserTarget(signature, !nonReturningWords.contains(target.name())));
          } else {
            // 呼出し先の宣言自体に診断済みの問題があるため、派生診断は追加しない。
            bodiesWithResolutionErrors.add(definition);
          }
          continue;
        }

        reportUndefinedWord(call);
        bodiesWithResolutionErrors.add(definition);
      }
    }

    /** 大域を初期化順、局所をソース順に検査し、全束縛の型状態を確定します。 */
    private void inferBindingTypes() {
      if (nameResolution.bindings().isEmpty()) {
        return;
      }

      var declarationsById = new LinkedHashMap<BindingId, ValueDeclaration>();
      bindingIdsByDeclaration.forEach((declaration, id) -> declarationsById.put(id, declaration));
      List<Binding> bindings = nameResolution.bindings();
      bindings.stream()
          .filter(binding -> binding.storage() == BindingStorage.GLOBAL)
          .forEach(binding -> inferBindingType(binding, declarationsById.get(binding.id())));
      bindings.stream()
          .filter(binding -> binding.storage() == BindingStorage.LOCAL)
          .forEach(binding -> inferBindingType(binding, declarationsById.get(binding.id())));
      publishBindingTypes();
    }

    private void inferBindingType(Binding binding, ValueDeclaration declaration) {
      if (declaration == null) {
        throw new IllegalStateException("binding has no declaration AST: " + binding.id());
      }
      bindingTypeStates.put(binding.id(), inferInitializerType(declaration));
    }

    /** 周囲と分離した空スタックから初期値を抽象実行し、出口の唯一の型を返します。 */
    private BindingTypeState inferInitializerType(ValueDeclaration declaration) {
      AbstractStack stack = AbstractStack.empty();
      boolean valid = true;
      for (BodyElement element : declaration.initializer()) {
        if (element instanceof Literal literal) {
          if (!validateRegexLiteral(literal)) {
            valid = false;
            continue;
          }
          stack = stack.push(literalType(literal), literal.span());
          continue;
        }
        if (element instanceof ArrayLiteral array) {
          ValueType arrayType = checkArrayLiteral(array);
          if (arrayType == null) {
            valid = false;
          } else {
            stack = stack.push(arrayType, array.span());
          }
          continue;
        }
        if (element instanceof ValueReference reference) {
          Binding binding = nameResolution.resolve(reference).orElseThrow();
          BindingTypeState typeState = bindingTypeState(binding);
          if (typeState.status() == BindingTypeState.Status.INFERRED) {
            stack = stack.push(typeState.type().orElseThrow(), reference.span());
          } else {
            valid = false;
          }
          continue;
        }
        if (element instanceof WordCall call) {
          if (!validateLogicalConnectionUseOutsideWordBody(call)) {
            valid = false;
            continue;
          }
          WordDefinition userWord = definitions.get(call.name());
          if (userWord != null) {
            reportInitializerCallNotAllowed(call, userWord);
            valid = false;
            continue;
          }
          BuiltinWord builtin = BuiltinDictionary.find(call.name()).orElse(null);
          if (builtin != null) {
            if (!builtin.sideEffects().isEmpty()) {
              reportInitializerCallNotAllowed(call, builtin);
              valid = false;
              continue;
            }
            AbstractStack next = applyBuiltin(call, builtin, stack);
            if (next == null) {
              valid = false;
              break;
            }
            stack = next;
            continue;
          }
          String plannedFeature = LanguageNames.FUTURE_FEATURES.get(call.name());
          if (plannedFeature != null) {
            reportFeatureNotAvailable(call.span(), call.name(), call.lexeme(), plannedFeature);
          } else {
            var nonCallable = LanguageNames.currentNonCallableKind(call.name());
            if (nonCallable.isPresent()) {
              reportNameNotCallable(call, nonCallable.orElseThrow());
            } else {
              reportUndefinedWord(call);
            }
          }
          valid = false;
        }
      }

      if (!valid) {
        return BindingTypeState.diagnosed();
      }
      if (stack.isEmpty()) {
        reportInitializerValueMissing(declaration, stack);
        return BindingTypeState.diagnosed();
      }
      if (stack.size() != 1) {
        reportInitializerValueCount(declaration, stack);
        return BindingTypeState.diagnosed();
      }
      return BindingTypeState.inferred(stack.top().orElseThrow().type());
    }

    private BindingTypeState bindingTypeState(Binding binding) {
      return bindingTypeStates.getOrDefault(binding.id(), binding.typeState());
    }

    /** 各要素を周囲から分離した空スタックで検査し、同じ許可要素型を持つ配列型を確定します。 */
    private ValueType checkArrayLiteral(ArrayLiteral array) {
      if (array.elements().isEmpty()) {
        reportEmptyArrayTypeRequired(array);
        return null;
      }
      if (array.elements().size() > ArrayLimits.MAX_LENGTH) {
        reportArrayLengthLimit(array);
        return null;
      }

      ArrayElementCheck first = null;
      for (int index = 0; index < array.elements().size(); index++) {
        ArrayElement element = array.elements().get(index);
        ArrayElementCheck current = checkArrayElement(array, element, index + 1);
        if (current == null) {
          return null;
        }
        if (!current.type().isArrayElementType()) {
          if (current.type() instanceof ArrayType) {
            reportNestedArrayElement(current);
          } else {
            reportArrayLiteralElementTypeNotAllowed(array, current.type());
          }
          return null;
        }
        if (first == null) {
          first = current;
        } else if (!first.type().equals(current.type())) {
          reportArrayElementTypeMismatch(element, index + 1, first, current);
          return null;
        }
      }

      ValueType type = ValueType.arrayOf(first.type());
      arrayLiteralTypes.put(array, type);
      array.elements().forEach(element -> checkParticles(element.body()));
      return type;
    }

    private void reportArrayLiteralElementTypeNotAllowed(ArrayLiteral array, ValueType type) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  array.openingSpan())
              .field("actualType", type.sourceName())
              .field("allowedTypes", "整数,真偽,文字,文字列,小数,JSON")
              .expected("配列要素にできる型")
              .actual(type.sourceName())
              .fix(type.sourceName() + "を個別に処理してください")
              .build());
    }

    private void reportArrayLengthLimit(ArrayLiteral array) {
      long observed = (long) ArrayLimits.MAX_LENGTH + 1;
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_LENGTH_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  array.elements().get(ArrayLimits.MAX_LENGTH).span())
              .field("operation", "literal")
              .limit("arrayLength", ArrayLimits.MAX_LENGTH, observed)
              .expected(ArrayLimits.MAX_LENGTH + "要素以下")
              .actual(observed + "要素")
              .fix("配列リテラルの要素数を減らしてください")
              .build());
    }

    /** 1要素式だけを抽象実行し、出口が正確に1値ならその型と由来を返します。 */
    private ArrayElementCheck checkArrayElement(
        ArrayLiteral array, ArrayElement element, int elementIndex) {
      AbstractStack stack = AbstractStack.empty();
      boolean valid = true;
      for (BodyElement bodyElement : element.body()) {
        if (bodyElement instanceof Literal literal) {
          if (!validateRegexLiteral(literal)) {
            valid = false;
            continue;
          }
          stack = stack.push(literalType(literal), literal.span());
          continue;
        }
        if (bodyElement instanceof ArrayLiteral nested) {
          ValueType nestedType = checkArrayLiteral(nested);
          if (nestedType == null) {
            valid = false;
          } else {
            stack = stack.push(nestedType, nested.span());
          }
          continue;
        }
        if (bodyElement instanceof ValueReference reference) {
          Binding binding = nameResolution.resolve(reference).orElseThrow();
          BindingTypeState typeState = bindingTypeState(binding);
          if (typeState.status() == BindingTypeState.Status.INFERRED) {
            stack = stack.push(typeState.type().orElseThrow(), reference.span());
          } else {
            valid = false;
          }
          continue;
        }
        if (bodyElement instanceof WordCall call) {
          if (!validateLogicalConnectionUseOutsideWordBody(call)) {
            valid = false;
            continue;
          }
          WordDefinition userWord = definitions.get(call.name());
          if (userWord != null) {
            reportArrayElementCallNotAllowed(call, userWord, elementIndex);
            valid = false;
            continue;
          }
          BuiltinWord builtin = BuiltinDictionary.find(call.name()).orElse(null);
          if (builtin != null) {
            if (!builtin.sideEffects().isEmpty()
                && builtin.operation() != jp.bsb.stdlib.BuiltinOperation.READ_LINE
                && builtin.operation() != jp.bsb.stdlib.BuiltinOperation.WALL_TIME) {
              reportArrayElementCallNotAllowed(call, builtin, elementIndex);
              valid = false;
              continue;
            }
            AbstractStack next = applyBuiltin(call, builtin, stack);
            if (next == null) {
              valid = false;
              break;
            }
            stack = next;
            continue;
          }
          String plannedFeature = LanguageNames.FUTURE_FEATURES.get(call.name());
          if (plannedFeature != null) {
            reportFeatureNotAvailable(call.span(), call.name(), call.lexeme(), plannedFeature);
          } else {
            var nonCallable = LanguageNames.currentNonCallableKind(call.name());
            if (nonCallable.isPresent()) {
              reportNameNotCallable(call, nonCallable.orElseThrow());
            } else {
              reportUndefinedWord(call);
            }
          }
          valid = false;
        }
      }

      if (!valid) {
        return null;
      }
      SourceSpan endSpan = arrayElementEndSpan(array, element);
      if (stack.isEmpty()) {
        reportArrayElementValueMissing(array, endSpan, elementIndex, stack);
        return null;
      }
      if (stack.size() != 1) {
        reportArrayElementValueCount(array, endSpan, elementIndex, stack);
        return null;
      }
      AbstractStack.Slot value = stack.top().orElseThrow();
      return new ArrayElementCheck(
          value.type(), value.origin(), arrayElementValueText(element, value.origin()));
    }

    /** 初期値・配列要素でも接続名を値にせず、静的引数だけを宣言表へ解決します。 */
    private boolean validateLogicalConnectionUseOutsideWordBody(WordCall call) {
      if (logicalConnectionLimitExceeded) {
        return false;
      }
      if (call.logicalConnectionArgument().isPresent()) {
        String connection = call.logicalConnectionArgument().orElseThrow().name();
        if (!logicalConnections.containsKey(connection)) {
          reportUndeclaredLogicalConnection(call, connection);
          return false;
        }
        return true;
      }
      if (logicalConnections.containsKey(call.name())) {
        reportLogicalConnectionReferenceNotAllowed(call);
        return false;
      }
      if (workspaceLimitExceeded) return false;
      if (call.workspaceArgument().isPresent()) {
        String workspace = call.workspaceArgument().orElseThrow().name();
        if (!workspaces.containsKey(workspace)) {
          reportUndeclaredWorkspace(call, workspace);
          return false;
        }
        return true;
      }
      if (workspaces.containsKey(call.name())) {
        reportWorkspaceReferenceNotAllowed(call);
        return false;
      }
      return true;
    }

    private static SourceSpan arrayElementEndSpan(ArrayLiteral array, ArrayElement element) {
      if (array.elements().getLast() == element) {
        return array.endSpan();
      }
      return new SourceSpan(element.span().end(), element.span().end());
    }

    private static String arrayElementValueText(ArrayElement element, SourceSpan origin) {
      for (int index = element.body().size() - 1; index >= 0; index--) {
        BodyElement bodyElement = element.body().get(index);
        if (bodyElement.span().equals(origin)) {
          return elementText(bodyElement);
        }
      }
      return "配列要素";
    }

    private void publishBindingTypes() {
      var declarations = new ArrayList<DeclaredName>(nameResolution.declarations().size());
      for (DeclaredName declaration : nameResolution.declarations()) {
        if (declaration instanceof Binding binding) {
          BindingTypeState typeState = bindingTypeStates.get(binding.id());
          if (typeState == null) {
            throw new IllegalStateException("binding type was not checked: " + binding.id());
          }
          declarations.add(withTypeState(binding, typeState));
        } else {
          declarations.add(declaration);
        }
      }
      nameResolution =
          new NameResolution(declarations, nameResolution.scopes(), nameResolution.uses());
    }

    private static Binding withTypeState(Binding binding, BindingTypeState typeState) {
      return new Binding(
          binding.id(),
          binding.name(),
          binding.spelling(),
          binding.kind(),
          binding.storage(),
          binding.scopeId(),
          typeState,
          binding.globalInitializationOrder(),
          binding.nameSpan(),
          binding.declarationSpan());
    }

    private void reportInitializerValueMissing(ValueDeclaration declaration, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INITIALIZER_VALUE_MISSING,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  declaration.endSpan())
              .field("name", declaration.name())
              .field("actualStack", formatStackField(stack))
              .expected("[1値]")
              .actual(formatStack(stack))
              .fix("初期値を1個追加してください")
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(), declaration.nameSpan().start(), declaration.lexeme()))
              .build());
    }

    private void reportInitializerValueCount(ValueDeclaration declaration, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INITIALIZER_VALUE_COUNT,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  declaration.endSpan())
              .field("name", declaration.name())
              .field("actualCount", Integer.toString(stack.size()))
              .field("actualStack", formatStackField(stack))
              .expected("[1値]")
              .actual(formatStack(stack))
              .fix("余分な値を残さないでください")
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(), declaration.nameSpan().start(), declaration.lexeme()))
              .build());
    }

    private void reportInitializerCallNotAllowed(WordCall call, WordDefinition definition) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INITIALIZER_CALL_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .field("callKind", "利用者定義単語")
              .expected("初期値で許可された組み込み単語")
              .actual("利用者定義単語 " + call.lexeme())
              .fix("リテラル、名前参照、または許可された組み込み単語を使ってください")
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(), definition.nameSpan().start(), definition.lexeme()))
              .build());
    }

    private void reportInitializerCallNotAllowed(WordCall call, BuiltinWord builtin) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INITIALIZER_CALL_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .field("callKind", "副作用を持つ組み込み単語")
              .expected("副作用のない初期値")
              .actual(call.lexeme())
              .fix("出力は宣言の後へ移動してください")
              .build());
    }

    private void reportArrayElementTypeRequired(TypeReference reference) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_TYPE_REQUIRED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  reference.span())
              .field("context", "スタック効果")
              .field("allowedTypes", "整数,真偽,文字,文字列")
              .expected("配列<要素型>")
              .actual(reference.lexeme())
              .fix("具体的な要素型を追加してください")
              .build());
    }

    private void reportOptionalElementTypeRequired(TypeReference reference) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_OPTIONAL_ELEMENT_TYPE_REQUIRED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  reference.span())
              .field("typeConstructor", "任意")
              .field("context", "スタック効果")
              .expected("任意<具体型>")
              .actual(reference.lexeme())
              .fix("任意<JSON>のように具体型を追加してください")
              .build());
    }

    private void reportResultTypeArgumentsRequired(TypeReference reference) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_RESULT_TYPE_ARGUMENTS_REQUIRED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  reference.span())
              .field("typeConstructor", "結果")
              .field("owner", "結果")
              .field("context", "スタック効果")
              .expected("結果<具体型,具体型>")
              .actual(reference.lexeme())
              .fix("結果<整数,文字列>のように成功型と失敗型を指定してください")
              .build());
    }

    private void reportArrayElementTypeNotAllowed(
        TypeReference elementReference, ScalarType elementType) {
      boolean regex = elementType == ScalarType.REGEX;
      boolean jsonParseFailure = elementType == ScalarType.JSON_PARSE_FAILURE;
      boolean delimitedTextParseFailure = elementType == ScalarType.DELIMITED_TEXT_PARSE_FAILURE;
      boolean byteSequence = elementType == ScalarType.BYTE_SEQUENCE;
      boolean decodeFailure =
          elementType == ScalarType.UTF8_DECODE_FAILURE
              || elementType == ScalarType.BASE64_DECODE_FAILURE;
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  elementReference.span())
              .field("actualType", elementType.sourceName())
              .field("allowedTypes", "整数,真偽,文字,文字列,小数,JSON")
              .field(
                  "plannedFeature",
                  jsonParseFailure || delimitedTextParseFailure || byteSequence || decodeFailure
                      ? "対象外"
                      : "配列要素の対象外")
              .expected("配列要素にできる型")
              .actual(elementReference.lexeme())
              .fix(
                  jsonParseFailure
                      ? "JSON解析失敗を配列の外で個別に処理してください"
                      : delimitedTextParseFailure
                          ? "区切りテキスト解析失敗を配列の外で個別に処理してください"
                          : byteSequence
                              ? "バイト列を配列の外で個別に処理してください"
                              : decodeFailure
                                  ? "復号失敗を配列の外で個別に処理してください"
                                  : regex ? "正規表現を個別の値として使用してください" : "丸め方法を個別の値として使用してください")
              .build());
    }

    private void reportArrayElementTypeNotAllowed(
        TypeReference elementReference, ValueType elementType) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  elementReference.span())
              .field("actualType", elementType.sourceName())
              .field("allowedTypes", "整数,真偽,文字,文字列,小数,JSON")
              .expected("配列要素にできる型")
              .actual(elementReference.lexeme())
              .fix(elementType.isResult() ? "結果値を配列の外で個別に処理してください" : "任意値を配列の外で個別に処理してください")
              .build());
    }

    private void reportNestedArrayType(TypeReference innerType) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_NESTED_ARRAY_NOT_AVAILABLE,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  innerType.span())
              .field("context", "配列型")
              .field("maximumDimensions", "2")
              .field("actualDimensions", "3")
              .expected("最大2次元の配列型")
              .actual(innerType.name())
              .fix("配列型の入れ子を2段までにしてください")
              .build());
    }

    private void reportEmptyArrayTypeRequired(ArrayLiteral array) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EMPTY_ARRAY_TYPE_REQUIRED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  array.span())
              .field("candidates", "空の整数配列,空の真偽配列,空の文字配列,空の文字列配列")
              .expected("具体的な配列型")
              .actual("【】")
              .fix("型付き空配列値を使用してください")
              .build());
    }

    private void reportArrayElementValueMissing(
        ArrayLiteral array, SourceSpan endSpan, int elementIndex, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_VALUE_MISSING,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  endSpan)
              .field("elementIndex", Integer.toString(elementIndex))
              .field("actualStack", formatStackField(stack))
              .expected("[1値]")
              .actual(formatStack(stack))
              .fix("要素式に値を1個追加してください")
              .relatedLocation(
                  new RelatedLocation(program.sourcePath(), array.openingSpan().start(), "【"))
              .build());
    }

    private void reportArrayElementValueCount(
        ArrayLiteral array, SourceSpan endSpan, int elementIndex, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_VALUE_COUNT,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  endSpan)
              .field("elementIndex", Integer.toString(elementIndex))
              .field("actualCount", Integer.toString(stack.size()))
              .field("actualStack", formatStackField(stack))
              .expected("[1値]")
              .actual(formatStack(stack))
              .fix("要素式に余分な値を残さないでください")
              .relatedLocation(
                  new RelatedLocation(program.sourcePath(), array.openingSpan().start(), "【"))
              .build());
    }

    private void reportArrayElementTypeMismatch(
        ArrayElement element, int elementIndex, ArrayElementCheck first, ArrayElementCheck actual) {
      boolean mixedNumeric = isNumeric(first.type()) && isNumeric(actual.type());
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_TYPE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  element.span())
              .field("elementIndex", Integer.toString(elementIndex))
              .field("expectedType", first.type().sourceName())
              .field("actualType", actual.type().sourceName())
              .expected(first.type().sourceName())
              .actual(actual.type().sourceName())
              .fix(
                  mixedNumeric
                      ? "各整数を明示的に小数へ変換するか要素型を揃えてください"
                      : "すべての要素を" + first.type().sourceName() + "に揃えてください")
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(), first.origin().start(), first.description()))
              .build());
    }

    private void reportNestedArrayElement(ArrayElementCheck element) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_NESTED_ARRAY_NOT_AVAILABLE,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  element.origin())
              .field("context", "配列要素")
              .field("maximumDimensions", "2")
              .field("actualDimensions", "3")
              .expected("最大2次元の配列値")
              .actual(element.type().sourceName())
              .fix("配列リテラルの入れ子を2段までにしてください")
              .build());
    }

    private void reportArrayElementCallNotAllowed(
        WordCall call, WordDefinition definition, int elementIndex) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_CALL_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("elementIndex", Integer.toString(elementIndex))
              .field("word", call.name())
              .field("callKind", "利用者定義単語")
              .expected("副作用のない組み込み単語")
              .actual("利用者定義単語 " + call.lexeme())
              .fix("要素値を配列の外で作ってください")
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(), definition.nameSpan().start(), definition.lexeme()))
              .build());
    }

    private void reportArrayElementCallNotAllowed(
        WordCall call, BuiltinWord builtin, int elementIndex) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_CALL_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("elementIndex", Integer.toString(elementIndex))
              .field("word", call.name())
              .field("callKind", "副作用を持つ組み込み単語")
              .expected("副作用のない要素式")
              .actual(call.lexeme())
              .fix("出力を配列リテラルの外へ移動してください")
              .build());
    }

    /** 宣言入力を初期抽象スタックとして、本体要素と条件分岐の型効果を順に適用します。 */
    private void checkWordBodies() {
      for (WordDefinition definition : definitions.values()) {
        if (invalidDefinitions.contains(definition)
            || bodiesWithResolutionErrors.contains(definition)) {
          continue;
        }
        WordSignature signature = signatures.get(definition);
        if (signature == null) {
          continue;
        }

        AbstractStack initialStack =
            AbstractStack.ofTypes(signature.inputTypes(), definition.nameSpan());
        BodyCheckResult bodyResult =
            checkBody(
                definition,
                signature,
                definition.body(),
                ControlFlowState.reachable(initialStack, definition.nameSpan()),
                List.of());
        if (bodyResult.valid() && bodyResult.state().isReachable()) {
          checkFinalStack(
              definition, signature, bodyResult.state().fallthrough().orElseThrow().stack());
        }
      }
    }

    /** 1本の到達可能経路を要素列に沿って進め、分岐で生じた復帰経路も失わずに返します。 */
    private BodyCheckResult checkBody(
        WordDefinition definition,
        WordSignature signature,
        List<BodyElement> body,
        ControlFlowState entryState,
        List<ControlFrame> frames) {
      ControlFlowState state = entryState;
      for (BodyElement element : body) {
        if (!state.isReachable()) {
          break;
        }

        BodyCheckResult result = checkElement(definition, signature, element, state, frames);
        if (!result.valid()) {
          return result;
        }
        state = result.state();
      }
      return BodyCheckResult.valid(state);
    }

    private BodyCheckResult checkElement(
        WordDefinition definition,
        WordSignature signature,
        BodyElement element,
        ControlFlowState state,
        List<ControlFrame> frames) {
      if (element instanceof Literal literal) {
        if (!validateRegexLiteral(literal)) {
          return BodyCheckResult.invalid(state);
        }
        AbstractStack stack = currentStack(state).push(literalType(literal), literal.span());
        return BodyCheckResult.valid(advance(state, stack, literal.span()));
      }
      if (element instanceof ArrayLiteral array) {
        ValueType arrayType = checkArrayLiteral(array);
        if (arrayType == null) {
          return BodyCheckResult.invalid(state);
        }
        AbstractStack stack = currentStack(state).push(arrayType, array.span());
        return BodyCheckResult.valid(advance(state, stack, array.span()));
      }
      if (element instanceof WordCall call) {
        CallTarget target = resolvedCalls.get(call);
        if (target == null) {
          return BodyCheckResult.invalid(state);
        }
        AbstractStack stack = applyCall(call, target, currentStack(state));
        if (stack == null) {
          return BodyCheckResult.invalid(state);
        }
        return target.returnsNormally()
            ? BodyCheckResult.valid(advance(state, stack, call.span()))
            : BodyCheckResult.valid(state.transfer(ControlPathKind.TERMINATED, call.span()));
      }
      if (element instanceof ValueDeclaration) {
        return BodyCheckResult.valid(state);
      }
      if (element instanceof ValueReference reference) {
        Binding binding = nameResolution.resolve(reference).orElseThrow();
        BindingTypeState typeState = binding.typeState();
        if (typeState.status() != BindingTypeState.Status.INFERRED) {
          return BodyCheckResult.invalid(state);
        }
        AbstractStack stack =
            currentStack(state).push(typeState.type().orElseThrow(), reference.span());
        return BodyCheckResult.valid(advance(state, stack, reference.span()));
      }
      if (element instanceof Assignment assignment) {
        return checkAssignment(assignment, state);
      }
      if (element instanceof Conditional conditional) {
        return checkConditional(definition, signature, conditional, state, frames);
      }
      if (element instanceof ShortCircuitEvaluation evaluation) {
        return checkShortCircuit(definition, signature, evaluation, state, frames);
      }
      if (element instanceof CountedLoop loop) {
        return checkCountedLoop(definition, signature, loop, state, frames);
      }
      if (element instanceof ConditionLoop loop) {
        return checkConditionLoop(definition, signature, loop, state, frames);
      }
      if (element instanceof ArrayLoop loop) {
        return checkArrayLoop(definition, signature, loop, state, frames);
      }
      if (element instanceof ControlTransfer transfer) {
        return checkTransfer(definition, signature, transfer, state, frames);
      }
      if (element instanceof Propagation propagation) {
        return checkPropagation(definition, signature, propagation, state);
      }
      return BodyCheckResult.valid(state);
    }

    /** ラッパーの正常側を通常経路へ、値なし・失敗側を単語復帰経路へ分けます。 */
    private BodyCheckResult checkPropagation(
        WordDefinition definition,
        WordSignature signature,
        Propagation propagation,
        ControlFlowState state) {
      AbstractStack stack = currentStack(state);
      String word = propagation.lexeme();
      String expectedInput = propagation.kind() == Propagation.Kind.OPTIONAL ? "任意<T>" : "結果<T,E>";
      if (stack.isEmpty()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_STACK_UNDERFLOW,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    propagation.span())
                .field("word", word)
                .field("requiredCount", "1")
                .field("actualCount", "0")
                .expected("[" + expectedInput + "]")
                .actual(formatStack(stack))
                .fix(expectedInput + "を先に置いてください")
                .build());
        return BodyCheckResult.invalid(state);
      }

      ValueType input = stack.top().orElseThrow().type();
      boolean inputMatches =
          propagation.kind() == Propagation.Kind.OPTIONAL ? input.isOptional() : input.isResult();
      if (!inputMatches) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_TYPE_MISMATCH,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    propagation.span())
                .field("word", word)
                .field("inputIndex", "1")
                .field("expectedType", expectedInput)
                .field("actualType", input.sourceName())
                .expected(expectedInput)
                .actual(input.sourceName())
                .fix(expectedInput + "を渡してください")
                .build());
        return BodyCheckResult.invalid(state);
      }

      DiagnosticCode contextCode =
          propagation.kind() == Propagation.Kind.OPTIONAL
              ? DiagnosticCode.E_OPTIONAL_PROPAGATION_CONTEXT
              : DiagnosticCode.E_RESULT_PROPAGATION_CONTEXT;
      ValueType output =
          signature.outputTypes().isEmpty() ? null : signature.outputTypes().getLast();
      boolean outputMatches =
          output != null
              && (propagation.kind() == Propagation.Kind.OPTIONAL
                  ? output.isOptional()
                  : output.isResult());
      if (definition.name().equals("メイン") || !outputMatches) {
        diagnostics.add(
            Diagnostic.builder(
                    contextCode,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    propagation.span())
                .field("word", definition.name())
                .field("expectedOutput", expectedInput)
                .expected("利用者単語の末尾出力が" + expectedInput)
                .actual(formatTypes(signature.outputTypes()))
                .fix(expectedInput + "を単語の末尾出力にしてください")
                .relatedLocation(
                    new RelatedLocation(
                        program.sourcePath(), definition.nameSpan().start(), definition.name()))
                .build());
        return BodyCheckResult.invalid(state);
      }

      List<ValueType> expectedPrefix =
          signature.outputTypes().subList(0, signature.outputTypes().size() - 1);
      AbstractStack prefix = stack.removeTop(1);
      boolean failureMatches =
          propagation.kind() != Propagation.Kind.RESULT
              || input
                  .resultFailureType()
                  .orElseThrow()
                  .equals(output.resultFailureType().orElseThrow());
      if (!prefix.types().equals(expectedPrefix) || !failureMatches) {
        DiagnosticCode code =
            propagation.kind() == Propagation.Kind.OPTIONAL
                ? DiagnosticCode.E_OPTIONAL_PROPAGATION_EFFECT_MISMATCH
                : DiagnosticCode.E_RESULT_PROPAGATION_EFFECT_MISMATCH;
        diagnostics.add(
            Diagnostic.builder(
                    code,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    propagation.span())
                .field("word", definition.name())
                .field("expectedPrefix", formatTypes(expectedPrefix))
                .field("actualPrefix", formatStack(prefix))
                .expected(formatTypes(signature.outputTypes()))
                .actual(formatStack(stack))
                .fix("保持する値と失敗型を単語の宣言出力へ合わせてください")
                .relatedLocation(
                    new RelatedLocation(
                        program.sourcePath(), definition.nameSpan().start(), definition.name()))
                .build());
        return BodyCheckResult.invalid(state);
      }

      ValueType normalType =
          propagation.kind() == Propagation.Kind.OPTIONAL
              ? input.optionalElementType().orElseThrow()
              : input.resultSuccessType().orElseThrow();
      AbstractStack normal = prefix.push(normalType, propagation.span());
      AbstractStack returned = AbstractStack.ofTypes(signature.outputTypes(), propagation.span());
      return BodyCheckResult.valid(state.propagate(normal, returned, propagation.span()));
    }

    private BodyCheckResult checkAssignment(Assignment assignment, ControlFlowState state) {
      Binding binding = nameResolution.resolve(assignment).orElseThrow();
      BindingTypeState typeState = binding.typeState();
      if (typeState.status() != BindingTypeState.Status.INFERRED) {
        return BodyCheckResult.invalid(state);
      }
      ValueType expectedType = typeState.type().orElseThrow();
      AbstractStack stack = currentStack(state);
      if (stack.isEmpty()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_ASSIGNMENT_STACK_UNDERFLOW,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    assignment.keywordSpan())
                .field("target", binding.name())
                .field("expectedType", expectedType.sourceName())
                .field("actualStack", formatStackField(stack))
                .expected(formatTypes(List.of(expectedType)))
                .actual(formatStack(stack))
                .fix("代入する" + expectedType.sourceName() + "を先に置いてください")
                .relatedLocation(
                    new RelatedLocation(
                        program.sourcePath(), binding.nameSpan().start(), binding.spelling()))
                .build());
        return BodyCheckResult.invalid(state);
      }

      ValueType actualType = stack.top().orElseThrow().type();
      if (!actualType.equals(expectedType)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_ASSIGNMENT_TYPE_MISMATCH,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    assignment.keywordSpan())
                .field("target", binding.name())
                .field("expectedType", expectedType.sourceName())
                .field("actualType", actualType.sourceName())
                .field("declarationLine", Integer.toString(binding.nameSpan().start().line()))
                .field("declarationColumn", Integer.toString(binding.nameSpan().start().column()))
                .expected(expectedType.sourceName())
                .actual(actualType.sourceName())
                .fix(assignmentTypeFix(expectedType, actualType))
                .relatedLocation(
                    new RelatedLocation(
                        program.sourcePath(), binding.nameSpan().start(), binding.spelling()))
                .build());
        return BodyCheckResult.invalid(state);
      }

      AbstractStack result = stack.removeTop(1);
      return BodyCheckResult.valid(advance(state, result, assignment.keywordSpan()));
    }

    /** 条件を消費した同じ基準スタックから真側と偽側を独立に解析し、出口を合流します。 */
    private BodyCheckResult checkConditional(
        WordDefinition definition,
        WordSignature signature,
        Conditional conditional,
        ControlFlowState entryState,
        List<ControlFrame> frames) {
      AbstractStack conditionStack = currentStack(entryState);
      if (conditionStack.isEmpty()) {
        reportConditionUnderflow(conditional, conditionStack);
        return BodyCheckResult.invalid(entryState);
      }

      ValueType actualType = conditionStack.top().orElseThrow().type();
      if (!actualType.equals(ValueType.BOOLEAN)) {
        reportConditionTypeMismatch(conditional, actualType);
        return BodyCheckResult.invalid(entryState);
      }

      AbstractStack baseStack = conditionStack.removeTop(1);
      BodyCheckResult trueResult =
          checkBody(
              definition,
              signature,
              conditional.trueBody(),
              ControlFlowState.reachable(baseStack, conditional.openingSpan()),
              frames);
      if (!trueResult.valid()) {
        return BodyCheckResult.invalid(entryState);
      }

      SourceSpan falseOrigin = conditional.elseSpan().orElse(conditional.endSpan());
      BodyCheckResult falseResult =
          conditional.hasElse()
              ? checkBody(
                  definition,
                  signature,
                  conditional.falseBody(),
                  ControlFlowState.reachable(baseStack, falseOrigin),
                  frames)
              : BodyCheckResult.valid(ControlFlowState.reachable(baseStack, falseOrigin));
      if (!falseResult.valid()) {
        return BodyCheckResult.invalid(entryState);
      }

      ControlFlowJoinResult joinResult =
          ControlFlowJoiner.join(List.of(trueResult.state(), falseResult.state()));
      if (joinResult instanceof ControlFlowJoinResult.Mismatch) {
        reportBranchMismatch(conditional, baseStack, trueResult.state(), falseResult.state());
        return BodyCheckResult.invalid(entryState);
      }

      ControlFlowState branchState = ((ControlFlowJoinResult.Joined) joinResult).state();
      return BodyCheckResult.valid(prependTransfers(entryState.transfers(), branchState));
    }

    /** 左辺を消費し、選択された場合だけ右辺が同じ基準へ真偽値を1個追加することを検査します。 */
    private BodyCheckResult checkShortCircuit(
        WordDefinition definition,
        WordSignature signature,
        ShortCircuitEvaluation evaluation,
        ControlFlowState entryState,
        List<ControlFrame> frames) {
      AbstractStack leftStack = currentStack(entryState);
      if (leftStack.isEmpty()) {
        reportShortCircuitLeftUnderflow(evaluation, leftStack);
        return BodyCheckResult.invalid(entryState);
      }
      ValueType actualType = leftStack.top().orElseThrow().type();
      if (!actualType.equals(ValueType.BOOLEAN)) {
        reportShortCircuitLeftTypeMismatch(evaluation, actualType);
        return BodyCheckResult.invalid(entryState);
      }

      AbstractStack baseStack = leftStack.removeTop(1);
      BodyCheckResult rightResult =
          checkBody(
              definition,
              signature,
              evaluation.rightBody(),
              ControlFlowState.reachable(baseStack, evaluation.openingSpan()),
              frames);
      if (!rightResult.valid()) {
        return BodyCheckResult.invalid(entryState);
      }
      AbstractStack resultStack = baseStack.push(ValueType.BOOLEAN, evaluation.endSpan());
      if (rightResult.state().isReachable()
          && !currentStack(rightResult.state()).hasSameShape(resultStack)) {
        reportShortCircuitRightMismatch(
            evaluation, baseStack, resultStack, currentStack(rightResult.state()));
        return BodyCheckResult.invalid(entryState);
      }
      ControlFlowState resultState =
          ControlFlowState.joined(
              ControlPath.fallthrough(resultStack, evaluation.endSpan()),
              rightResult.state().transfers());
      return BodyCheckResult.valid(prependTransfers(entryState.transfers(), resultState));
    }

    private void reportShortCircuitLeftUnderflow(
        ShortCircuitEvaluation evaluation, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_SHORT_CIRCUIT_LEFT_UNDERFLOW,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  evaluation.openingSpan())
              .field("operator", evaluation.operator().sourceName())
              .expected("[真偽]")
              .actual(formatStack(stack))
              .fix(evaluation.operator().sourceName() + "の左辺となる真偽値を置いてください")
              .build());
    }

    private void reportShortCircuitLeftTypeMismatch(
        ShortCircuitEvaluation evaluation, ValueType actualType) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_SHORT_CIRCUIT_LEFT_TYPE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  evaluation.openingSpan())
              .field("actualType", actualType.sourceName())
              .field("operator", evaluation.operator().sourceName())
              .expected(ValueType.BOOLEAN.sourceName())
              .actual(actualType.sourceName())
              .fix(evaluation.operator().sourceName() + "の左辺を真偽にしてください")
              .build());
    }

    private void reportShortCircuitRightMismatch(
        ShortCircuitEvaluation evaluation,
        AbstractStack baseStack,
        AbstractStack expectedStack,
        AbstractStack actualStack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_SHORT_CIRCUIT_RIGHT_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  evaluation.endSpan())
              .field("operator", evaluation.operator().sourceName())
              .field("base", formatStackField(baseStack))
              .field("expectedStack", formatStackField(expectedStack))
              .field("actualStack", formatStackField(actualStack))
              .expected(formatStack(expectedStack))
              .actual(formatStack(actualStack))
              .fix("右辺評価ブロックが真偽値を1個だけ残すようにしてください")
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(),
                      evaluation.openingSpan().start(),
                      evaluation.operator().sourceName()))
              .build());
    }

    /** 回数を消費して基準スタックを固定し、0回経路をループ後の通常経路として保持します。 */
    private BodyCheckResult checkCountedLoop(
        WordDefinition definition,
        WordSignature signature,
        CountedLoop loop,
        ControlFlowState entryState,
        List<ControlFrame> frames) {
      AbstractStack countStack = currentStack(entryState);
      if (countStack.isEmpty()) {
        reportRepeatCountUnderflow(loop, countStack);
        return BodyCheckResult.invalid(entryState);
      }
      ValueType actualType = countStack.top().orElseThrow().type();
      if (!actualType.equals(ValueType.INTEGER)) {
        reportRepeatCountTypeMismatch(loop, actualType);
        return BodyCheckResult.invalid(entryState);
      }

      AbstractStack baseStack = countStack.removeTop(1);
      var frame = new ControlFrame(ControlFrame.Kind.COUNTED_LOOP, baseStack, loop.openingSpan());
      BodyCheckResult bodyResult =
          checkBody(
              definition,
              signature,
              loop.body(),
              ControlFlowState.reachable(baseStack, loop.openingSpan()),
              appendFrame(frames, frame));
      if (!bodyResult.valid()
          || !validateLoopPaths(bodyResult.state(), baseStack, loop.endSpan(), frame)) {
        return BodyCheckResult.invalid(entryState);
      }

      ControlFlowState loopState =
          ControlFlowState.joined(
              ControlPath.fallthrough(baseStack, loop.endSpan()),
              returnedPaths(bodyResult.state()));
      return BodyCheckResult.valid(prependTransfers(entryState.transfers(), loopState));
    }

    /** 条件計算部と本体を別々に解析し、偽経路・脱出経路だけをループ後へ接続します。 */
    private BodyCheckResult checkConditionLoop(
        WordDefinition definition,
        WordSignature signature,
        ConditionLoop loop,
        ControlFlowState entryState,
        List<ControlFrame> frames) {
      AbstractStack baseStack = currentStack(entryState);
      var frame =
          new ControlFrame(ControlFrame.Kind.CONDITIONAL_LOOP, baseStack, loop.openingSpan());
      List<ControlFrame> loopFrames = appendFrame(frames, frame);
      BodyCheckResult conditionResult =
          checkBody(
              definition,
              signature,
              loop.conditionBody(),
              ControlFlowState.reachable(baseStack, loop.openingSpan()),
              loopFrames);
      if (!conditionResult.valid()
          || !validateTransferPaths(conditionResult.state(), baseStack, frame)) {
        return BodyCheckResult.invalid(entryState);
      }

      boolean reachesSeparator = conditionResult.state().isReachable();
      boolean hasBreakExit = hasTransfer(conditionResult.state(), ControlPathKind.BROKEN);
      var returned = new ArrayList<>(returnedPaths(conditionResult.state()));
      ControlFlowState bodyState = ControlFlowState.unreachable();

      if (reachesSeparator) {
        AbstractStack actualStack = currentStack(conditionResult.state());
        AbstractStack expectedStack = baseStack.push(ValueType.BOOLEAN, loop.separatorSpan());
        if (!actualStack.hasSameShape(expectedStack)) {
          reportLoopConditionMismatch(loop, baseStack, expectedStack, actualStack);
          return BodyCheckResult.invalid(entryState);
        }

        BodyCheckResult bodyResult =
            checkBody(
                definition,
                signature,
                loop.body(),
                ControlFlowState.reachable(baseStack, loop.separatorSpan()),
                loopFrames);
        if (!bodyResult.valid()
            || !validateLoopPaths(bodyResult.state(), baseStack, loop.endSpan(), frame)) {
          return BodyCheckResult.invalid(entryState);
        }
        bodyState = bodyResult.state();
        hasBreakExit |= hasTransfer(bodyState, ControlPathKind.BROKEN);
        returned.addAll(returnedPaths(bodyState));
      }

      boolean loopExitReachable = reachesSeparator || hasBreakExit;
      ControlPath fallthrough =
          loopExitReachable ? ControlPath.fallthrough(baseStack, loop.endSpan()) : null;
      ControlFlowState loopState = ControlFlowState.joined(fallthrough, returned);
      return BodyCheckResult.valid(prependTransfers(entryState.transfers(), loopState));
    }

    /** 配列を消費し、現在要素だけを加えた本体入口から全出口を基準スタックへ戻します。 */
    private BodyCheckResult checkArrayLoop(
        WordDefinition definition,
        WordSignature signature,
        ArrayLoop loop,
        ControlFlowState entryState,
        List<ControlFrame> frames) {
      AbstractStack inputStack = currentStack(entryState);
      if (inputStack.isEmpty()) {
        reportArrayLoopInputUnderflow(loop, inputStack);
        return BodyCheckResult.invalid(entryState);
      }

      ValueType inputType = inputStack.top().orElseThrow().type();
      if (!inputType.isArray()) {
        reportArrayLoopInputTypeMismatch(loop, inputType, inputStack);
        return BodyCheckResult.invalid(entryState);
      }

      AbstractStack baseStack = inputStack.removeTop(1);
      ValueType elementType = inputType.arrayElementType().orElseThrow();
      AbstractStack bodyEntry = baseStack.push(elementType, loop.openingSpan());
      var frame = new ControlFrame(ControlFrame.Kind.ARRAY_LOOP, baseStack, loop.openingSpan());
      BodyCheckResult bodyResult =
          checkBody(
              definition,
              signature,
              loop.body(),
              ControlFlowState.reachable(bodyEntry, loop.openingSpan()),
              appendFrame(frames, frame));
      if (!bodyResult.valid()
          || !validateArrayLoopPaths(bodyResult.state(), baseStack, loop.endSpan(), frame)) {
        return BodyCheckResult.invalid(entryState);
      }

      // 空配列の0回経路も存在するため、配列の長さにかかわらず通常出口は到達可能です。
      ControlFlowState loopState =
          ControlFlowState.joined(
              ControlPath.fallthrough(baseStack, loop.endSpan()),
              returnedPaths(bodyResult.state()));
      return BodyCheckResult.valid(prependTransfers(entryState.transfers(), loopState));
    }

    private void reportArrayLoopInputUnderflow(ArrayLoop loop, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_LOOP_INPUT_UNDERFLOW,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  loop.openingSpan())
              .field("expectedType", "配列<T>")
              .field("actualStack", formatStackField(stack))
              .expected("[配列<T>]")
              .actual(formatStack(stack))
              .fix("反復する配列を先に置いてください")
              .build());
    }

    private void reportArrayLoopInputTypeMismatch(
        ArrayLoop loop, ValueType actualType, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_LOOP_INPUT_TYPE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  loop.openingSpan())
              .field("expectedType", "配列<T>")
              .field("actualType", actualType.sourceName())
              .field("actualStack", formatStackField(stack))
              .expected("配列<T>")
              .actual(actualType.sourceName())
              .fix("反復する配列を渡してください")
              .build());
    }

    private boolean validateArrayLoopPaths(
        ControlFlowState state,
        AbstractStack baseStack,
        SourceSpan normalEndSpan,
        ControlFrame frame) {
      if (state.isReachable()
          && !validateArrayLoopPath(
              state.fallthrough().orElseThrow(), baseStack, "normal", normalEndSpan, frame)) {
        return false;
      }
      for (ControlPath path : state.transfers()) {
        String pathKind =
            switch (path.kind()) {
              case BROKEN -> "break";
              case CONTINUED -> "continue";
              case RETURNED, TERMINATED, FALLTHROUGH -> null;
            };
        if (pathKind != null
            && !validateArrayLoopPath(path, baseStack, pathKind, path.origin(), frame)) {
          return false;
        }
      }
      return true;
    }

    private boolean validateArrayLoopPath(
        ControlPath path,
        AbstractStack baseStack,
        String pathKind,
        SourceSpan diagnosticSpan,
        ControlFrame frame) {
      if (path.stack().hasSameShape(baseStack)) {
        return true;
      }
      String fix =
          switch (pathKind) {
            case "normal" -> "現在要素を本体で消費してください";
            case "break" -> "打ち切る前に現在要素を消費してください";
            case "continue" -> "続ける前に現在要素を消費してください";
            default -> throw new IllegalArgumentException("unknown array loop path: " + pathKind);
          };
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_LOOP_STACK_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  diagnosticSpan)
              .field("loopKind", "配列")
              .field("path", pathKind)
              .field("base", formatStackField(baseStack))
              .field("actual", formatStackField(path.stack()))
              .expected(formatStack(baseStack))
              .actual(formatStack(path.stack()))
              .fix(fix)
              .relatedLocation(
                  new RelatedLocation(program.sourcePath(), frame.openerSpan().start(), "各要素について"))
              .build());
      return false;
    }

    private BodyCheckResult checkTransfer(
        WordDefinition definition,
        WordSignature signature,
        ControlTransfer transfer,
        ControlFlowState state,
        List<ControlFrame> frames) {
      if (transfer.kind() == ControlTransfer.Kind.RETURN) {
        return checkReturn(definition, signature, transfer, state);
      }

      if (innermostLoop(frames) == null) {
        DiagnosticCode code =
            transfer.kind() == ControlTransfer.Kind.BREAK
                ? DiagnosticCode.E_BREAK_OUTSIDE_LOOP
                : DiagnosticCode.E_CONTINUE_OUTSIDE_LOOP;
        diagnostics.add(
            Diagnostic.builder(
                    code,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    transfer.span())
                .expected("ループ内")
                .actual(transfer.lexeme())
                .fix("ループ内へ移動してください")
                .build());
        return BodyCheckResult.invalid(state);
      }

      ControlPathKind pathKind =
          transfer.kind() == ControlTransfer.Kind.BREAK
              ? ControlPathKind.BROKEN
              : ControlPathKind.CONTINUED;
      return BodyCheckResult.valid(state.transfer(pathKind, transfer.span()));
    }

    private boolean validateLoopPaths(
        ControlFlowState state,
        AbstractStack baseStack,
        SourceSpan normalEndSpan,
        ControlFrame frame) {
      if (state.isReachable()
          && !validateLoopPath(
              state.fallthrough().orElseThrow(), baseStack, "通常終端", normalEndSpan, frame)) {
        return false;
      }
      return validateTransferPaths(state, baseStack, frame);
    }

    private boolean validateTransferPaths(
        ControlFlowState state, AbstractStack baseStack, ControlFrame frame) {
      for (ControlPath path : state.transfers()) {
        String pathKind =
            switch (path.kind()) {
              case BROKEN -> "脱出";
              case CONTINUED -> "継続";
              case RETURNED, TERMINATED, FALLTHROUGH -> null;
            };
        if (pathKind != null
            && !validateLoopPath(path, baseStack, pathKind, path.origin(), frame)) {
          return false;
        }
      }
      return true;
    }

    private boolean validateLoopPath(
        ControlPath path,
        AbstractStack baseStack,
        String pathKind,
        SourceSpan diagnosticSpan,
        ControlFrame frame) {
      if (path.stack().hasSameShape(baseStack)) {
        return true;
      }
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_LOOP_STACK_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  diagnosticSpan)
              .field("pathKind", pathKind)
              .field("base", formatStackField(baseStack))
              .field("actualStack", formatStackField(path.stack()))
              .expected(formatStack(baseStack))
              .actual(formatStack(path.stack()))
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(),
                      frame.openerSpan().start(),
                      frame.kind() == ControlFrame.Kind.COUNTED_LOOP ? "回だけ" : "ここから"))
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(),
                      diagnosticSpan.start(),
                      switch (pathKind) {
                        case "通常終端" -> "繰り返す";
                        case "脱出" -> "打ち切る";
                        case "継続" -> "続ける";
                        default ->
                            throw new IllegalArgumentException("unknown loop path: " + pathKind);
                      }))
              .build());
      return false;
    }

    private void reportRepeatCountUnderflow(CountedLoop loop, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_REPEAT_COUNT_UNDERFLOW,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  loop.openingSpan())
              .expected("[整数]")
              .actual(formatStack(stack))
              .fix("反復回数を置いてください")
              .build());
    }

    private void reportRepeatCountTypeMismatch(CountedLoop loop, ValueType actualType) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_REPEAT_COUNT_TYPE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  loop.openingSpan())
              .field("actualType", actualType.sourceName())
              .expected(ValueType.INTEGER.sourceName())
              .actual(actualType.sourceName())
              .fix("反復回数を整数にしてください")
              .build());
    }

    private void reportLoopConditionMismatch(
        ConditionLoop loop,
        AbstractStack baseStack,
        AbstractStack expectedStack,
        AbstractStack actualStack) {
      String fix;
      if (actualStack.size() <= baseStack.size()) {
        fix = "条件となる真偽値を置いてください";
      } else if (actualStack.top().orElseThrow().type().equals(ValueType.BOOLEAN)) {
        fix = "余分な値を条件計算部に残さないでください";
      } else {
        fix = "条件計算部の最後に真偽値を置いてください";
      }
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_LOOP_CONDITION_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  loop.separatorSpan())
              .field("base", formatStackField(baseStack))
              .field("expectedStack", formatStackField(expectedStack))
              .field("actualStack", formatStackField(actualStack))
              .expected(formatStack(expectedStack))
              .actual(formatStack(actualStack))
              .fix(fix)
              .relatedLocation(
                  new RelatedLocation(program.sourcePath(), loop.openingSpan().start(), "ここから"))
              .build());
    }

    private static List<ControlFrame> appendFrame(List<ControlFrame> frames, ControlFrame frame) {
      var result = new ArrayList<ControlFrame>(frames);
      result.add(frame);
      return List.copyOf(result);
    }

    private static ControlFrame innermostLoop(List<ControlFrame> frames) {
      for (int index = frames.size() - 1; index >= 0; index--) {
        if (frames.get(index).isLoop()) {
          return frames.get(index);
        }
      }
      return null;
    }

    private static boolean hasTransfer(ControlFlowState state, ControlPathKind kind) {
      return state.transfers().stream().anyMatch(path -> path.kind() == kind);
    }

    private static List<ControlPath> returnedPaths(ControlFlowState state) {
      return state.transfers().stream()
          .filter(
              path ->
                  path.kind() == ControlPathKind.RETURNED
                      || path.kind() == ControlPathKind.TERMINATED)
          .toList();
    }

    /** 有効な早期復帰を通常経路から分離し、もう一方の分岐だけが後続へ流れられるようにします。 */
    private BodyCheckResult checkReturn(
        WordDefinition definition,
        WordSignature signature,
        ControlTransfer transfer,
        ControlFlowState state) {
      AbstractStack stack = currentStack(state);
      if (!stack.types().equals(signature.outputTypes())) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_RETURN_EFFECT_MISMATCH,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    transfer.span())
                .field("word", definition.name())
                .expected(formatTypes(signature.outputTypes()))
                .actual(formatStack(stack))
                .fix("戻る前に宣言出力へ合わせてください")
                .relatedLocation(
                    new RelatedLocation(
                        program.sourcePath(), definition.nameSpan().start(), definition.name()))
                .build());
        return BodyCheckResult.invalid(state);
      }
      return BodyCheckResult.valid(state.transfer(ControlPathKind.RETURNED, transfer.span()));
    }

    private void reportConditionUnderflow(Conditional conditional, AbstractStack stack) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_CONDITION_STACK_UNDERFLOW,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  conditional.openingSpan())
              .expected("[真偽]")
              .actual(formatStack(stack))
              .fix("条件となる真偽値を置いてください")
              .build());
    }

    private void reportConditionTypeMismatch(Conditional conditional, ValueType actualType) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_CONDITION_TYPE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  conditional.openingSpan())
              .field("actualType", actualType.sourceName())
              .expected(ValueType.BOOLEAN.sourceName())
              .actual(actualType.sourceName())
              .fix("条件を真偽にしてください")
              .build());
    }

    /** 合流できなかった両出口と基準スタックを、機械可読フィールドと関連位置へ保存します。 */
    private void reportBranchMismatch(
        Conditional conditional,
        AbstractStack baseStack,
        ControlFlowState trueState,
        ControlFlowState falseState) {
      ControlPath truePath = trueState.fallthrough().orElseThrow();
      ControlPath falsePath = falseState.fallthrough().orElseThrow();
      String trueStack = formatStack(truePath.stack());
      String falseStack = formatStack(falsePath.stack());
      var builder =
          Diagnostic.builder(
                  DiagnosticCode.E_BRANCH_STACK_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  conditional.endSpan())
              .field("base", formatStackField(baseStack))
              .field("trueStack", formatStackField(truePath.stack()))
              .field("falseStack", formatStackField(falsePath.stack()));

      if (conditional.hasElse()) {
        builder
            .expected("両側で同じ型列")
            .actual("真側" + trueStack + "、偽側" + falseStack)
            .relatedLocation(
                new RelatedLocation(program.sourcePath(), truePath.origin().start(), "真側"))
            .relatedLocation(
                new RelatedLocation(program.sourcePath(), falsePath.origin().start(), "偽側"));
      } else {
        builder
            .expected("真側" + formatStack(baseStack))
            .actual("真側" + trueStack)
            .relatedLocation(
                new RelatedLocation(program.sourcePath(), conditional.openingSpan().start(), "ならば"))
            .relatedLocation(
                new RelatedLocation(program.sourcePath(), conditional.endSpan().start(), "つぎに"));
      }
      diagnostics.add(builder.build());
    }

    private static AbstractStack currentStack(ControlFlowState state) {
      return state.fallthrough().orElseThrow().stack();
    }

    /** 通常経路だけを新しいスタックへ進め、以前に分離した復帰経路はそのまま保持します。 */
    private static ControlFlowState advance(
        ControlFlowState state, AbstractStack stack, SourceSpan origin) {
      return ControlFlowState.joined(ControlPath.fallthrough(stack, origin), state.transfers());
    }

    /** 現在の分岐より前に生じた制御移行を、分岐内で生じた移行の前へ1回だけ連結します。 */
    private static ControlFlowState prependTransfers(
        List<ControlPath> previousTransfers, ControlFlowState branchState) {
      if (previousTransfers.isEmpty()) {
        return branchState;
      }
      var transfers = new ArrayList<ControlPath>(previousTransfers);
      transfers.addAll(branchState.transfers());
      return ControlFlowState.joined(branchState.fallthrough().orElse(null), transfers);
    }

    private AbstractStack applyCall(WordCall call, CallTarget target, AbstractStack stack) {
      return switch (target) {
        case UserTarget user -> {
          AbstractStack result = applyFixedEffect(call, user.signature(), stack);
          if (result != null) {
            callSignatures.put(call, user.signature());
          }
          yield result;
        }
        case BuiltinTarget builtin -> applyBuiltin(call, builtin.word(), stack);
      };
    }

    private AbstractStack applyBuiltin(WordCall call, BuiltinWord word, AbstractStack stack) {
      AbstractStack result =
          switch (word.typeRule()) {
            case FIXED ->
                applyFixedEffect(
                    call,
                    new WordSignature(
                        concreteTypes(word.inputTypeNames()),
                        concreteTypes(word.outputTypeNames())),
                    stack);
            case DISPLAYABLE -> applyDisplayable(call, stack);
            case SAME_TYPE_PAIR -> applySameTypePair(call, stack);
            case SAME_NUMERIC_TYPE -> applySameNumericType(call, word, stack);
            case INDEPENDENT_NUMERIC_INPUTS -> applyIndependentNumericInputs(call, word, stack);
            case ARRAY_LENGTH -> applyArrayLength(call, stack);
            case ARRAY_GET -> applyArrayGet(call, stack);
            case ARRAY_SLICE -> applyArraySlice(call, stack);
            case ARRAY_REPLACE -> applyArrayReplace(call, stack);
            case ARRAY_APPEND -> applyArrayAppend(call, stack);
            case ARRAY_CONCAT -> applyArrayConcat(call, stack);
            case ARRAY_PREPEND -> applyArrayPrepend(call, stack);
            case ARRAY_REVERSE -> applyArrayReverse(call, stack);
            case ARRAY_CONTAINS -> applyArraySearch(call, stack, ValueType.BOOLEAN);
            case ARRAY_FIND -> applyArraySearch(call, stack, ValueType.INTEGER);
            case ARRAY_IS_EMPTY -> applyArrayIsEmpty(call, stack);
            case ARRAY_EDGE_OPTIONAL -> applyArrayEdgeOptional(call, stack);
            case ARRAY_DELETE_EDGE -> applyArrayDeleteEdge(call, stack);
            case ARRAY_DELETE_RANGE -> applyArrayDeleteRange(call, stack);
            case ARRAY_COUNT -> applyArraySearch(call, stack, ValueType.INTEGER);
            case ARRAY_INSERT -> applyArrayInsert(call, stack);
            case ARRAY_DELETE_AT -> applyArrayDeleteAt(call, stack);
            case ARRAY_FIND_FROM -> applyArrayFindFrom(call, stack);
            case ARRAY_UNIQUE -> applyArrayUnique(call, stack);
            case ARRAY_REPEAT_VALUE -> applyArrayRepeatValue(call, stack);
            case OPTIONAL_WRAP -> applyOptionalWrap(call, stack);
            case OPTIONAL_PREDICATE -> applyOptionalPredicate(call, stack);
            case OPTIONAL_UNWRAP -> applyOptionalUnwrap(call, stack);
            case OPTIONAL_DROP -> applyOptionalDrop(call, stack);
            case RESULT_SUCCESS_WRAP -> applyResultWrap(call, stack, true);
            case RESULT_FAILURE_WRAP -> applyResultWrap(call, stack, false);
            case RESULT_PREDICATE -> applyResultPredicate(call, stack);
            case RESULT_SUCCESS_UNWRAP -> applyResultUnwrap(call, stack, true);
            case RESULT_FAILURE_UNWRAP -> applyResultUnwrap(call, stack, false);
            case RESULT_DROP -> applyResultDrop(call, stack);
          };
      if (result != null) {
        recordCallSignature(
            call, word.inputTypeNames().size(), word.outputTypeNames().size(), stack, result);
      }
      return result;
    }

    private AbstractStack applyOptionalWrap(WordCall call, AbstractStack stack) {
      if (!requireStackDepth(call, List.of("T"), stack)) {
        return null;
      }
      ValueType elementType = stack.slots().getLast().type();
      int observedDepth = ValueType.constructorDepth(elementType) + 1;
      if (observedDepth > ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH) {
        reportTypeDepthLimit(call, "任意", observedDepth);
        return null;
      }
      return stack.removeTop(1).push(ValueType.optionalOf(elementType), call.span());
    }

    private AbstractStack applyResultWrap(WordCall call, AbstractStack stack, boolean success) {
      ValueType resultType = explicitResultType(call);
      if (resultType == null) {
        return null;
      }
      ValueType inputType =
          success
              ? resultType.resultSuccessType().orElseThrow()
              : resultType.resultFailureType().orElseThrow();
      if (!requireStackDepth(call, List.of(inputType.sourceName()), stack)) {
        return null;
      }
      ValueType actual = stack.slots().getLast().type();
      if (!actual.equals(inputType)) {
        reportBuiltinTypeMismatch(
            call,
            1,
            inputType.sourceName(),
            actual,
            inputType.sourceName(),
            actual.sourceName(),
            (success ? "成功型" : "失敗型") + "の値を渡してください");
        return null;
      }
      return stack.removeTop(1).push(resultType, call.span());
    }

    private ValueType explicitResultType(WordCall call) {
      if (call.explicitTypeArguments().isEmpty()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_RESULT_TYPE_ARGUMENTS_REQUIRED,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    call.span())
                .field("typeConstructor", "結果")
                .field("owner", call.name())
                .field("context", "構築呼出し")
                .field("word", call.name())
                .expected(call.name() + "<具体型,具体型>")
                .actual(call.lexeme())
                .fix(call.name() + "<整数,文字列>のように成功型と失敗型を指定してください")
                .build());
        return null;
      }
      ValueType successType =
          resolveTypeReference(call.explicitTypeArguments().get(0), new LinkedHashSet<>());
      ValueType failureType =
          resolveTypeReference(call.explicitTypeArguments().get(1), new LinkedHashSet<>());
      return successType == null || failureType == null
          ? null
          : ValueType.resultOf(successType, failureType);
    }

    private AbstractStack applyResultPredicate(WordCall call, AbstractStack stack) {
      ValueType result = requireResultInput(call, stack);
      return result == null ? null : stack.push(ValueType.BOOLEAN, call.span());
    }

    private AbstractStack applyResultUnwrap(WordCall call, AbstractStack stack, boolean success) {
      ValueType result = requireResultInput(call, stack);
      if (result == null) {
        return null;
      }
      ValueType payload =
          success
              ? result.resultSuccessType().orElseThrow()
              : result.resultFailureType().orElseThrow();
      return stack.removeTop(1).push(payload, call.span());
    }

    private AbstractStack applyResultDrop(WordCall call, AbstractStack stack) {
      return requireResultInput(call, stack) == null ? null : stack.removeTop(1);
    }

    private ValueType requireResultInput(WordCall call, AbstractStack stack) {
      if (!requireStackDepth(call, List.of("結果<T,E>"), stack)) {
        return null;
      }
      ValueType actual = stack.slots().getLast().type();
      if (!actual.isResult()) {
        reportBuiltinTypeMismatch(
            call, 1, "結果<T,E>", actual, "結果<T,E>", actual.sourceName(), "結果値を渡してください");
        return null;
      }
      return actual;
    }

    private void reportTypeDepthLimit(WordCall call, String constructor, int observed) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_TYPE_DEPTH_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("typeConstructor", constructor)
              .field("word", call.name())
              .limit("typeDepth", ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH, observed)
              .expected("型構築子の深さ256以下")
              .actual("型構築子の深さ" + observed)
              .fix("型の入れ子を浅くしてください")
              .build());
    }

    private AbstractStack applyOptionalPredicate(WordCall call, AbstractStack stack) {
      ValueType optionalType = requireOptionalInput(call, stack);
      return optionalType == null ? null : stack.push(ValueType.BOOLEAN, call.span());
    }

    private AbstractStack applyOptionalUnwrap(WordCall call, AbstractStack stack) {
      ValueType optionalType = requireOptionalInput(call, stack);
      return optionalType == null
          ? null
          : stack.removeTop(1).push(optionalType.optionalElementType().orElseThrow(), call.span());
    }

    private AbstractStack applyOptionalDrop(WordCall call, AbstractStack stack) {
      return requireOptionalInput(call, stack) == null ? null : stack.removeTop(1);
    }

    private ValueType requireOptionalInput(WordCall call, AbstractStack stack) {
      if (!requireStackDepth(call, List.of("任意<T>"), stack)) {
        return null;
      }
      ValueType actual = stack.slots().getLast().type();
      if (!actual.isOptional()) {
        reportBuiltinTypeMismatch(
            call, 1, "任意<T>", actual, "任意<T>", actual.sourceName(), "任意値を渡してください");
        return null;
      }
      return actual;
    }

    private void recordCallSignature(
        WordCall call, int inputCount, int outputCount, AbstractStack before, AbstractStack after) {
      int prefixSize = before.size() - inputCount;
      if (prefixSize < 0 || after.size() != prefixSize + outputCount) {
        throw new IllegalStateException("a checked call has an inconsistent stack effect");
      }
      List<ValueType> inputs =
          before.slots().subList(prefixSize, before.size()).stream()
              .map(AbstractStack.Slot::type)
              .toList();
      List<ValueType> outputs =
          after.slots().subList(prefixSize, after.size()).stream()
              .map(AbstractStack.Slot::type)
              .toList();
      WordSignature signature = new WordSignature(inputs, outputs);
      WordSignature previous = callSignatures.put(call, signature);
      if (previous != null && !previous.equals(signature)) {
        throw new IllegalStateException("one call AST acquired two concrete stack effects");
      }
    }

    private AbstractStack applyArrayLength(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>"), stack);
      return arrayType == null ? null : stack.removeTop(1).push(ValueType.INTEGER, call.span());
    }

    private AbstractStack applyArrayGet(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "整数"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 2;
      if (!requireArrayOperationType(
          call, 2, ValueType.INTEGER, stack.slots().get(inputStart + 1).type(), "整数の添字を渡してください")) {
        return null;
      }
      return stack.removeTop(2).push(arrayType.arrayElementType().orElseThrow(), call.span());
    }

    private AbstractStack applyArraySlice(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "整数", "整数"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 3;
      if (!requireArrayOperationType(
              call,
              2,
              ValueType.INTEGER,
              stack.slots().get(inputStart + 1).type(),
              "開始位置を整数にしてください")
          || !requireArrayOperationType(
              call,
              3,
              ValueType.INTEGER,
              stack.slots().get(inputStart + 2).type(),
              "終了位置を整数にしてください")) {
        return null;
      }
      return stack.removeTop(3).push(arrayType, call.span());
    }

    private AbstractStack applyArrayReplace(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "整数", "T"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 3;
      if (!requireArrayOperationType(
          call, 2, ValueType.INTEGER, stack.slots().get(inputStart + 1).type(), "整数の添字を渡してください")) {
        return null;
      }
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      if (!requireArrayOperationType(
          call,
          3,
          elementType,
          stack.slots().get(inputStart + 2).type(),
          "置換値を" + elementType.sourceName() + "にしてください")) {
        return null;
      }
      return stack.removeTop(3).push(arrayType, call.span());
    }

    private AbstractStack applyArrayAppend(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "T"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 2;
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      if (!requireArrayOperationType(
          call,
          2,
          elementType,
          stack.slots().get(inputStart + 1).type(),
          "追加値を" + elementType.sourceName() + "にしてください")) {
        return null;
      }
      return stack.removeTop(2).push(arrayType, call.span());
    }

    private AbstractStack applyArrayConcat(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "配列<T>"), stack);
      if (arrayType == null) {
        return null;
      }
      ValueType secondType = stack.slots().getLast().type();
      if (!requireArrayOperationType(call, 2, arrayType, secondType, "同じ要素型の配列を渡してください")) {
        return null;
      }
      return stack.removeTop(2).push(arrayType, call.span());
    }

    private AbstractStack applyArrayPrepend(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "T"), stack);
      if (arrayType == null) {
        return null;
      }
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      if (!requireArrayOperationType(
          call,
          2,
          elementType,
          stack.slots().getLast().type(),
          "追加値を" + elementType.sourceName() + "にしてください")) {
        return null;
      }
      return stack.removeTop(2).push(arrayType, call.span());
    }

    private AbstractStack applyArrayReverse(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>"), stack);
      return arrayType == null ? null : stack.removeTop(1).push(arrayType, call.span());
    }

    private AbstractStack applyArraySearch(
        WordCall call, AbstractStack stack, ValueType outputType) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "T"), stack);
      if (arrayType == null) {
        return null;
      }
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      ValueType actualElementType = stack.slots().getLast().type();
      if (!requireArrayOperationType(
          call, 2, elementType, actualElementType, "検索値を" + elementType.sourceName() + "にしてください")) {
        return null;
      }
      if (!ValueTypeTraits.isEqualityComparable(elementType)) {
        reportArrayOperationTypeMismatch(call, 2, "等値比較可能", elementType, "比較可能な要素型の配列を使用してください");
        return null;
      }
      return stack.removeTop(2).push(outputType, call.span());
    }

    private AbstractStack applyArrayIsEmpty(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>"), stack);
      return arrayType == null ? null : stack.removeTop(1).push(ValueType.BOOLEAN, call.span());
    }

    private AbstractStack applyArrayEdgeOptional(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>"), stack);
      if (arrayType == null) {
        return null;
      }
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      int observedDepth = ValueType.constructorDepth(elementType) + 1;
      if (observedDepth > ValueType.MAX_TYPE_CONSTRUCTOR_DEPTH) {
        reportTypeDepthLimit(call, "任意", observedDepth);
        return null;
      }
      return stack.removeTop(1).push(ValueType.optionalOf(elementType), call.span());
    }

    private AbstractStack applyArrayDeleteEdge(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>"), stack);
      return arrayType == null ? null : stack.removeTop(1).push(arrayType, call.span());
    }

    private AbstractStack applyArrayDeleteRange(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "整数", "整数"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 3;
      if (!requireArrayOperationType(
              call,
              2,
              ValueType.INTEGER,
              stack.slots().get(inputStart + 1).type(),
              "開始位置を整数にしてください")
          || !requireArrayOperationType(
              call,
              3,
              ValueType.INTEGER,
              stack.slots().get(inputStart + 2).type(),
              "終了位置を整数にしてください")) {
        return null;
      }
      return stack.removeTop(3).push(arrayType, call.span());
    }

    private AbstractStack applyArrayInsert(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "整数", "T"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 3;
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      if (!requireArrayOperationType(
              call,
              2,
              ValueType.INTEGER,
              stack.slots().get(inputStart + 1).type(),
              "挿入位置を整数にしてください")
          || !requireArrayOperationType(
              call,
              3,
              elementType,
              stack.slots().get(inputStart + 2).type(),
              "挿入値を" + elementType.sourceName() + "にしてください")) {
        return null;
      }
      return stack.removeTop(3).push(arrayType, call.span());
    }

    private AbstractStack applyArrayDeleteAt(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "整数"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 2;
      if (!requireArrayOperationType(
          call, 2, ValueType.INTEGER, stack.slots().get(inputStart + 1).type(), "削除位置を整数にしてください")) {
        return null;
      }
      return stack.removeTop(2).push(arrayType, call.span());
    }

    private AbstractStack applyArrayFindFrom(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>", "T", "整数"), stack);
      if (arrayType == null) {
        return null;
      }
      int inputStart = stack.size() - 3;
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      if (!requireArrayOperationType(
              call,
              2,
              elementType,
              stack.slots().get(inputStart + 1).type(),
              "検索値を" + elementType.sourceName() + "にしてください")
          || !requireArrayOperationType(
              call,
              3,
              ValueType.INTEGER,
              stack.slots().get(inputStart + 2).type(),
              "開始位置を整数にしてください")) {
        return null;
      }
      if (!ValueTypeTraits.isEqualityComparable(elementType)) {
        reportArrayOperationTypeMismatch(call, 2, "等値比較可能", elementType, "比較可能な要素型の配列を使用してください");
        return null;
      }
      return stack.removeTop(3).push(ValueType.INTEGER, call.span());
    }

    private AbstractStack applyArrayUnique(WordCall call, AbstractStack stack) {
      ValueType arrayType = requireArrayInputs(call, List.of("配列<T>"), stack);
      if (arrayType == null) {
        return null;
      }
      ValueType elementType = arrayType.arrayElementType().orElseThrow();
      if (!ValueTypeTraits.isEqualityComparable(elementType)) {
        reportArrayOperationTypeMismatch(
            call, 1, "等値比較可能な要素を持つ配列", arrayType, "比較可能な要素型の配列を使用してください");
        return null;
      }
      return stack.removeTop(1).push(arrayType, call.span());
    }

    private AbstractStack applyArrayRepeatValue(WordCall call, AbstractStack stack) {
      if (!requireStackDepth(call, List.of("T", "整数"), stack)) {
        return null;
      }
      int inputStart = stack.size() - 2;
      ValueType elementType = stack.slots().get(inputStart).type();
      if (!elementType.isArrayElementType()) {
        reportArrayOperationTypeMismatch(call, 1, "配列要素型", elementType, "配列に格納できる値を使用してください");
        return null;
      }
      if (!requireArrayOperationType(
          call, 2, ValueType.INTEGER, stack.slots().get(inputStart + 1).type(), "個数を整数にしてください")) {
        return null;
      }
      return stack.removeTop(2).push(ValueType.arrayOf(elementType), call.span());
    }

    /** 入力数と第1入力の配列制約を検査し、具体化した配列型を返します。 */
    private ValueType requireArrayInputs(
        WordCall call, List<String> requiredTypes, AbstractStack stack) {
      if (!requireArrayOperationStackDepth(call, requiredTypes, stack)) {
        return null;
      }
      int inputStart = stack.size() - requiredTypes.size();
      ValueType actualType = stack.slots().get(inputStart).type();
      if (!actualType.isArray()) {
        reportArrayOperationTypeMismatch(call, 1, "配列<T>", actualType, "配列値を渡してください");
        return null;
      }
      return actualType;
    }

    private boolean requireArrayOperationStackDepth(
        WordCall call, List<String> requiredTypes, AbstractStack stack) {
      if (stack.size() >= requiredTypes.size()) {
        return true;
      }
      int missing = requiredTypes.size() - stack.size();
      String fix;
      if (missing != 1) {
        fix = "必要な値を" + missing + "個置いてください";
      } else {
        int missingInput = stack.size() + 1;
        fix =
            switch (call.name()) {
              case "配列から取り出す" -> "整数の添字を追加してください";
              case "配列の一部を取り出す" -> missingInput == 2 ? "開始位置を追加してください" : "終了位置を追加してください";
              case "配列の要素を置き換える" -> missingInput == 2 ? "整数の添字を追加してください" : "置換値を追加してください";
              case "配列の末尾へ追加する" -> "追加値を置いてください";
              case "配列をつなぐ" -> "同じ要素型の配列を追加してください";
              case "配列の先頭へ追加する" -> "追加値を置いてください";
              case "配列に含まれる", "配列から検索する" -> "検索値を置いてください";
              case "配列の一部を削除する" -> missingInput == 2 ? "開始位置を追加してください" : "終了位置を追加してください";
              case "配列の位置へ挿入する" -> missingInput == 2 ? "挿入位置を追加してください" : "挿入値を追加してください";
              case "配列の位置を削除する" -> "削除位置を追加してください";
              case "開始位置から配列を検索する" -> missingInput == 2 ? "検索値を追加してください" : "開始位置を追加してください";
              default -> "配列値を追加してください";
            };
      }
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_STACK_UNDERFLOW,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .field("requiredCount", Integer.toString(requiredTypes.size()))
              .field("actualCount", Integer.toString(stack.size()))
              .expected(formatTypeNames(requiredTypes))
              .actual(formatStack(stack))
              .fix(fix)
              .build());
      return false;
    }

    private boolean requireArrayOperationType(
        WordCall call, int inputIndex, ValueType expectedType, ValueType actualType, String fix) {
      if (expectedType.equals(actualType)) {
        return true;
      }
      reportArrayOperationTypeMismatch(
          call, inputIndex, expectedType.sourceName(), actualType, fix);
      return false;
    }

    private void reportArrayOperationTypeMismatch(
        WordCall call, int inputIndex, String expectedType, ValueType actualType, String fix) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_TYPE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .field("inputIndex", Integer.toString(inputIndex))
              .field("expectedType", expectedType)
              .field("actualType", actualType.sourceName())
              .expected(expectedType)
              .actual(actualType.sourceName())
              .fix(fix)
              .build());
    }

    private AbstractStack applyDisplayable(WordCall call, AbstractStack stack) {
      if (!requireStackDepth(call, List.of("表示可能"), stack)) {
        return null;
      }
      ValueType actual = stack.slots().getLast().type();
      if (!ValueTypeTraits.isDisplayable(actual)) {
        ValueType cause = ValueTypeTraits.firstNonDisplayable(actual).orElseThrow();
        reportBuiltinTypeMismatch(
            call,
            1,
            "表示可能",
            actual,
            "表示可能",
            actual.sourceName(),
            cause.equals(ValueType.INPUT_RESULT)
                ? "入力状態を判定してから行を取り出してください"
                : cause.equals(ValueType.DATE_TIME)
                    ? "日時を文字列に変換してから表示してください"
                    : cause.equals(ValueType.JSON_PARSE_FAILURE)
                        ? "JSON解析失敗の専用取出し語を使用してください"
                        : cause.equals(ValueType.BYTE_SEQUENCE)
                            ? "UTF8またはBase64の文字列へ変換してから表示してください"
                            : cause.equals(ValueType.UTF8_DECODE_FAILURE)
                                    || cause.equals(ValueType.BASE64_DECODE_FAILURE)
                                ? "復号失敗の専用取出し語を使用してください"
                                : "正規表現を照合操作に使用してください");
        return null;
      }
      return stack.removeTop(1);
    }

    private AbstractStack applySameTypePair(WordCall call, AbstractStack stack) {
      if (!requireStackDepth(call, List.of("T", "T"), stack)) {
        return null;
      }
      AbstractStack.Slot first = stack.slots().get(stack.size() - 2);
      AbstractStack.Slot second = stack.slots().getLast();
      boolean firstComparable = ValueTypeTraits.isEqualityComparable(first.type());
      boolean secondComparable = ValueTypeTraits.isEqualityComparable(second.type());
      if (!firstComparable || !secondComparable) {
        ValueType actual = !firstComparable ? first.type() : second.type();
        ValueType cause = ValueTypeTraits.firstNonEqualityComparable(actual).orElseThrow();
        reportBuiltinTypeMismatch(
            call,
            !firstComparable ? 1 : 2,
            "等値比較可能",
            actual,
            "等値比較可能",
            actual.sourceName(),
            cause.equals(ValueType.INPUT_RESULT)
                ? "入力結果の状態判定語を使用してください"
                : cause.equals(ValueType.DATE_TIME)
                    ? "日時を文字列に変換してから比較してください"
                    : cause.equals(ValueType.JSON_PARSE_FAILURE)
                        ? "JSON解析失敗の公開フィールドを取り出して比較してください"
                        : cause.equals(ValueType.UTF8_DECODE_FAILURE)
                                || cause.equals(ValueType.BASE64_DECODE_FAILURE)
                            ? "復号失敗の公開フィールドを取り出して比較してください"
                            : "正規表現を照合操作に使用してください");
        return null;
      }
      if (!first.type().equals(second.type())) {
        reportTypeMismatch(call, 2, first.type(), second.type(), stack);
        return null;
      }
      return stack.removeTop(2).push(ValueType.BOOLEAN, call.span());
    }

    private AbstractStack applySameNumericType(
        WordCall call, BuiltinWord word, AbstractStack stack) {
      if (!requireStackDepth(call, concreteNumericRequirements(word, stack), stack)) {
        return null;
      }
      int inputStart = stack.size() - word.inputTypeNames().size();
      ValueType numericType = null;
      for (int index = 0; index < word.inputTypeNames().size(); index++) {
        if (!word.inputTypeNames().get(index).equals("N")) {
          throw new IllegalStateException("a same-numeric builtin has a non-N input");
        }
        ValueType actual = stack.slots().get(inputStart + index).type();
        if (!isNumeric(actual)) {
          if (Set.of("CORE", "FLOW", "BIND", "ARRAY").contains(word.featureGroup())) {
            List<String> integerInputs =
                word.inputTypeNames().stream()
                    .map(ignored -> ValueType.INTEGER.sourceName())
                    .toList();
            reportBuiltinTypeMismatch(
                call,
                index + 1,
                ValueType.INTEGER.sourceName(),
                actual,
                integerInputs,
                "第" + (index + 1) + "入力を整数にしてください",
                stack);
            return null;
          }
          reportBuiltinTypeMismatch(
              call, index + 1, "整数または小数", actual, word.inputTypeNames(), "数値を渡してください", stack);
          return null;
        }
        if (numericType == null) {
          numericType = actual;
        } else if (!numericType.equals(actual)) {
          ValueType resolvedNumericType = numericType;
          List<String> expectedTypes =
              word.inputTypeNames().stream()
                  .map(name -> name.equals("N") ? resolvedNumericType.sourceName() : name)
                  .toList();
          reportBuiltinTypeMismatch(
              call,
              index + 1,
              numericType.sourceName(),
              actual,
              expectedTypes,
              "両入力を同じ数値型にしてください",
              stack);
          return null;
        }
      }
      if (numericType == null) {
        throw new IllegalStateException("a same-numeric builtin has no N input");
      }
      AbstractStack result = stack.removeTop(word.inputTypeNames().size());
      for (String output : word.outputTypeNames()) {
        ValueType outputType =
            output.equals("N")
                ? numericType
                : ValueType.fromSourceName(output)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "a same-numeric builtin has an unknown output type"));
        result = result.push(outputType, call.span());
      }
      return result;
    }

    private List<String> concreteNumericRequirements(BuiltinWord word, AbstractStack stack) {
      ValueType inferred =
          stack.slots().stream()
              .map(AbstractStack.Slot::type)
              .filter(SemanticAnalyzer::isNumeric)
              .findFirst()
              .orElse(
                  Set.of("CORE", "FLOW", "BIND", "ARRAY").contains(word.featureGroup())
                      ? ValueType.INTEGER
                      : null);
      if (inferred == null) {
        return word.inputTypeNames();
      }
      return word.inputTypeNames().stream()
          .map(name -> name.equals("N") ? inferred.sourceName() : name)
          .toList();
    }

    private AbstractStack applyIndependentNumericInputs(
        WordCall call, BuiltinWord word, AbstractStack stack) {
      if (!requireStackDepth(call, word.inputTypeNames(), stack)) {
        return null;
      }
      int inputStart = stack.size() - word.inputTypeNames().size();
      for (int index = 0; index < word.inputTypeNames().size(); index++) {
        String required = word.inputTypeNames().get(index);
        ValueType actual = stack.slots().get(inputStart + index).type();
        if (required.equals("数値")) {
          if (!isNumeric(actual)) {
            reportBuiltinTypeMismatch(
                call, index + 1, "整数または小数", actual, word.inputTypeNames(), "数値を渡してください", stack);
            return null;
          }
          continue;
        }
        ValueType expected = ValueType.fromSourceName(required).orElseThrow();
        if (!expected.equals(actual)) {
          reportBuiltinTypeMismatch(
              call,
              index + 1,
              expected.sourceName(),
              actual,
              word.inputTypeNames(),
              builtinTypeFix(call, index + 1, expected),
              stack);
          return null;
        }
      }
      AbstractStack result = stack.removeTop(word.inputTypeNames().size());
      for (String output : word.outputTypeNames()) {
        result = result.push(ValueType.fromSourceName(output).orElseThrow(), call.span());
      }
      return result;
    }

    private AbstractStack applyFixedEffect(
        WordCall call, WordSignature signature, AbstractStack stack) {
      if (!requireStackDepth(call, typeNames(signature.inputTypes()), stack)) {
        return null;
      }
      int inputStart = stack.size() - signature.inputTypes().size();
      for (int index = 0; index < signature.inputTypes().size(); index++) {
        ValueType expected = signature.inputTypes().get(index);
        ValueType actual = stack.slots().get(inputStart + index).type();
        if (!expected.equals(actual)) {
          reportTypeMismatch(call, index + 1, expected, actual, stack);
          return null;
        }
      }
      AbstractStack result = stack.removeTop(signature.inputTypes().size());
      for (ValueType output : signature.outputTypes()) {
        result = result.push(output, call.span());
      }
      return result;
    }

    private boolean requireStackDepth(
        WordCall call, List<String> requiredTypes, AbstractStack stack) {
      if (stack.size() >= requiredTypes.size()) {
        return true;
      }
      int missing = requiredTypes.size() - stack.size();
      String fix;
      if (missing == 1) {
        String missingType = requiredTypes.get(stack.size());
        fix = missingType + "をもう1つ置いてください";
      } else {
        fix = "必要な値を" + missing + "個置いてください";
      }
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_STACK_UNDERFLOW,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .field("requiredCount", Integer.toString(requiredTypes.size()))
              .field("actualCount", Integer.toString(stack.size()))
              .expected(formatTypeNames(requiredTypes))
              .actual(formatStack(stack))
              .fix(fix)
              .build());
      return false;
    }

    private void reportTypeMismatch(
        WordCall call,
        int inputIndex,
        ValueType expectedType,
        ValueType actualType,
        AbstractStack stack) {
      List<String> expectedTypes =
          resolvedCalls.get(call) instanceof UserTarget user
              ? typeNames(user.signature().inputTypes())
              : BuiltinDictionary.find(call.name()).orElseThrow().inputTypeNames();
      String expected =
          isSingleInputConversion(call.name())
              ? expectedType.sourceName()
              : formatTypeNames(expectedTypes);
      String actual =
          isSingleInputConversion(call.name()) ? actualType.sourceName() : formatStack(stack);
      reportBuiltinTypeMismatch(
          call,
          inputIndex,
          expectedType.sourceName(),
          actualType,
          expected,
          actual,
          builtinTypeFix(call, inputIndex, expectedType));
    }

    private void reportBuiltinTypeMismatch(
        WordCall call,
        int inputIndex,
        String expectedType,
        ValueType actualType,
        List<String> expectedTypes,
        String fix,
        AbstractStack stack) {
      reportBuiltinTypeMismatch(
          call,
          inputIndex,
          expectedType,
          actualType,
          formatTypeNames(expectedTypes),
          formatStack(stack),
          fix);
    }

    private void reportBuiltinTypeMismatch(
        WordCall call,
        int inputIndex,
        String expectedType,
        ValueType actualType,
        String expected,
        String actual,
        String fix) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_TYPE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .field("inputIndex", Integer.toString(inputIndex))
              .field("expectedType", expectedType)
              .field("actualType", actualType.sourceName())
              .expected(expected)
              .actual(actual)
              .fix(fix)
              .build());
    }

    private void checkFinalStack(
        WordDefinition definition, WordSignature signature, AbstractStack stack) {
      List<ValueType> actualTypes = stack.types();
      if (actualTypes.equals(signature.outputTypes())) {
        return;
      }
      if (definition.name().equals(LanguageNames.MAIN) && !stack.isEmpty()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_MAIN_STACK_NOT_EMPTY,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    stack.slots().getFirst().origin())
                .field("actualCount", Integer.toString(stack.size()))
                .expected("[]")
                .actual(formatStack(stack))
                .fix("値を消費してください")
                .build());
        return;
      }
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_WORD_EFFECT_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  definition.nameSpan())
              .field("word", definition.name())
              .expected(formatTypes(signature.outputTypes()))
              .actual(formatStack(stack))
              .fix("宣言または本体を一致させてください")
              .build());
    }

    /** コメントを除いた隣接関係を使い、1助詞につき高々1件の警告を生成します。 */
    private void checkParticles(WordDefinition definition) {
      checkParticles(definition.body());
    }

    /** 制御語を助詞の隣接候補にせず、各子本体を独立した要素列として再帰的に検査します。 */
    private void checkParticles(List<BodyElement> body) {
      List<BodyElement> significant =
          body.stream()
              .filter(element -> !(element instanceof Comment))
              .filter(element -> !unreachableElements.contains(element))
              .toList();
      for (int index = 0; index < significant.size(); index++) {
        if (!(significant.get(index) instanceof Particle particle)) {
          continue;
        }

        String expected = null;
        String fix = null;
        if (index == 0) {
          expected = "本体先頭以外";
          fix = "先頭の" + particle.lexeme() + "を削除してください";
        } else if (index == significant.size() - 1) {
          expected = "本体末尾以外";
          fix = "末尾の" + particle.lexeme() + "を削除してください";
        } else if (significant.get(index - 1) instanceof Particle) {
          expected = "助詞の連続以外";
          fix = "連続する" + particle.lexeme() + "を削除してください";
        } else if (!isParticleNeighbor(significant.get(index - 1))
            || !isParticleNeighbor(significant.get(index + 1))) {
          expected = "リテラルまたは呼出し可能な名前の間";
          fix = particle.lexeme() + "の前後を確認してください";
        }

        if (expected != null) {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.W_PARTICLE_POSITION,
                      Severity.WARNING,
                      DiagnosticStage.TYPE_AND_STACK,
                      program.sourcePath(),
                      particle.span())
                  .expected(expected)
                  .actual(particle.lexeme())
                  .fix(fix)
                  .build());
        }
      }

      for (BodyElement element : body) {
        if (unreachableElements.contains(element)) {
          continue;
        }
        if (element instanceof Conditional conditional) {
          checkParticles(conditional.trueBody());
          checkParticles(conditional.falseBody());
        } else if (element instanceof ShortCircuitEvaluation evaluation) {
          checkParticles(evaluation.rightBody());
        } else if (element instanceof CountedLoop loop) {
          checkParticles(loop.body());
        } else if (element instanceof ConditionLoop loop) {
          checkParticles(loop.conditionBody());
          checkParticles(loop.body());
        } else if (element instanceof ArrayLoop loop) {
          checkParticles(loop.body());
        }
      }
    }

    private boolean isParticleNeighbor(BodyElement element) {
      return element instanceof Literal
          || element instanceof ArrayLiteral
          || element instanceof ValueReference
          || (element instanceof WordCall call
              && (resolvedCalls.containsKey(call)
                  || BuiltinDictionary.find(call.name()).isPresent()));
    }

    private void reportReservedName(WordDefinition definition) {
      var builder =
          Diagnostic.builder(
                  DiagnosticCode.E_RESERVED_NAME,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  definition.nameSpan())
              .expected("利用者定義名")
              .actual(definition.lexeme())
              .fix("別の名前に変更してください");
      BuiltinDictionary.find(definition.name())
          .ifPresent(
              word ->
                  builder.relatedLocation(
                      RelatedLocation.outsideSource("組み込み単語 " + word.canonicalName())));
      diagnostics.add(builder.build());
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
    }

    private void reportReservedName(LogicalConnectionDeclaration declaration) {
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
    }

    private void reportReservedName(WorkspaceDeclaration declaration) {
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
    }

    private void reportUndeclaredLogicalConnection(WordCall call, String connection) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNDECLARED_LOGICAL_CONNECTION,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  call.logicalConnectionArgument().orElseThrow().span())
              .field("word", call.name())
              .field("connection", connection)
              .expected("宣言済み論理接続")
              .actual(connection)
              .fix(connection + "の論理接続宣言を追加してください")
              .build());
    }

    private void reportLogicalConnectionReferenceNotAllowed(WordCall call) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  call.span())
              .field("connection", call.name())
              .field("context", "valueOrWord")
              .expected("静的接続引数")
              .actual("通常参照")
              .fix("論理接続を確認する<" + call.name() + ">の形で使用してください")
              .build());
    }

    private void reportUndeclaredWorkspace(WordCall call, String workspace) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNDECLARED_WORKSPACE,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  call.workspaceArgument().orElseThrow().span())
              .field("word", call.name())
              .field("workspace", workspace)
              .expected("宣言済み作業領域")
              .actual(workspace)
              .fix(workspace + "の作業領域宣言を追加してください")
              .build());
    }

    private void reportWorkspaceReferenceNotAllowed(WordCall call) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_WORKSPACE_REFERENCE_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  call.span())
              .field("workspace", call.name())
              .field("context", "valueOrWord")
              .expected("静的作業領域引数")
              .actual("通常参照")
              .fix("ファイル語<" + call.name() + ">の形で使用してください")
              .build());
    }

    private void reportDuplicateName(
        String spelling, SourceSpan nameSpan, GlobalNameSite previous) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_DUPLICATE_NAME,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  nameSpan)
              .field("actualName", spelling)
              .field("previousName", previous.spelling())
              .expected("一意な名前")
              .actual(spelling)
              .fix("別の名前に変更してください")
              .relatedLocation(
                  new RelatedLocation(
                      program.sourcePath(), previous.nameSpan().start(), previous.spelling()))
              .build());
    }

    private void reportNameNotCallable(WordCall call, LanguageNames.NonCallableKind kind) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_NAME_NOT_CALLABLE,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .field("kind", kind.fieldValue())
              .expected("呼び出し可能な単語")
              .actual(kind.actualText())
              .fix("単語名を指定してください")
              .build());
    }

    private void reportFeatureNotAvailable(
        SourceSpan span, String feature, String actual, String plannedFeature) {
      String fix = "現在利用できる機能へ変更してください";
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_FEATURE_NOT_AVAILABLE,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  span)
              .field("feature", feature)
              .field("plannedFeature", plannedFeature)
              .expected("言語コアの型")
              .actual(actual)
              .fix(fix)
              .build());
    }

    private void reportTypeConstraintNotAllowed(TypeReference reference) {
      if (reference.name().equals("N") || reference.name().equals("数値")) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_TYPE_CONSTRAINT_NOT_ALLOWED,
                    Severity.ERROR,
                    DiagnosticStage.TYPE_AND_STACK,
                    program.sourcePath(),
                    reference.span())
                .field("type", reference.name())
                .field("context", "利用者定義スタック効果")
                .expected("具体型")
                .actual(reference.lexeme())
                .fix("整数または小数の具体型を書いてください")
                .build());
        return;
      }
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_TYPE_CONSTRAINT_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.TYPE_AND_STACK,
                  program.sourcePath(),
                  reference.span())
              .expected("整数・真偽・文字・文字列")
              .actual(reference.lexeme())
              .fix("具体的な言語コアの型へ変更してください")
              .build());
    }

    private void reportUndefinedWord(WordCall call) {
      var builder =
          Diagnostic.builder(
                  DiagnosticCode.E_UNDEFINED_WORD,
                  Severity.ERROR,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  call.span())
              .field("word", call.name())
              .expected("定義済み単語")
              .actual(call.lexeme());
      var nearest = uniqueNearestCallable(call.name());
      if (nearest.isPresent()) {
        builder.fix(nearest.orElseThrow() + "へ変更してください");
      } else if (looksLikeExponentWithoutMantissa(call.name())) {
        builder.fix("単語を定義するか数値の仮数を書いてください");
      }
      diagnostics.add(builder.build());
    }

    private boolean looksLikeExponentWithoutMantissa(String name) {
      if (name.length() < 2 || (name.charAt(0) != 'e' && name.charAt(0) != 'E')) {
        return false;
      }
      return name.substring(1).chars().allMatch(character -> character >= '0' && character <= '9');
    }

    private java.util.Optional<String> uniqueNearestCallable(String unknown) {
      var candidates = new LinkedHashSet<String>();
      candidates.addAll(BuiltinDictionary.canonicalNames());
      definitions.keySet().stream()
          .filter(name -> !name.equals(LanguageNames.MAIN))
          .forEach(candidates::add);

      int bestDistance = Integer.MAX_VALUE;
      String best = null;
      boolean tied = false;
      for (String candidate : candidates) {
        int distance = graphemeDistance(unknown, candidate);
        if (distance < bestDistance) {
          bestDistance = distance;
          best = candidate;
          tied = false;
        } else if (distance == bestDistance) {
          tied = true;
        }
      }
      return bestDistance <= 2 && !tied
          ? java.util.Optional.ofNullable(best)
          : java.util.Optional.empty();
    }

    private void reportConfusableNames() {
      Map<String, ConfusableName> namesBySkeleton = initialConfusableNames();
      for (DeclaredName declaration : nameResolution.declarations()) {
        reportConfusableName(declaration, namesBySkeleton);
        namesBySkeleton.putIfAbsent(
            UnicodeRules.confusableSkeleton(declaration.name()),
            ConfusableName.fromDeclaration(declaration));
      }
    }

    private void reportConfusableName(
        DeclaredName declaration, Map<String, ConfusableName> namesBySkeleton) {
      String skeleton = UnicodeRules.confusableSkeleton(declaration.name());
      ConfusableName previous = namesBySkeleton.get(skeleton);
      if (previous == null || previous.normalizedName().equals(declaration.name())) {
        return;
      }
      var builder =
          Diagnostic.builder(
                  DiagnosticCode.W_CONFUSABLE_IDENTIFIER,
                  Severity.WARNING,
                  DiagnosticStage.NAME,
                  program.sourcePath(),
                  declaration.nameSpan())
              .field("actualName", declaration.spelling())
              .field("previousName", previous.inputName())
              .field("normalizedName", declaration.name())
              .field("skeleton", skeleton)
              .expected("見分けられる名前")
              .actual(declaration.spelling())
              .fix("見た目の異なる名前へ変更してください");
      if (previous.location() != null) {
        builder.relatedLocation(
            new RelatedLocation(
                program.sourcePath(), previous.location().start(), previous.inputName()));
      }
      diagnostics.add(builder.build());
    }

    private Map<String, ConfusableName> initialConfusableNames() {
      var result = new LinkedHashMap<String, ConfusableName>();
      for (String name : LanguageNames.predefinedConfusableNames()) {
        result.putIfAbsent(
            UnicodeRules.confusableSkeleton(name), new ConfusableName(name, name, null));
      }
      return result;
    }

    private boolean validateRegexLiteral(Literal literal) {
      if (literal.kind() != jp.bsb.frontend.ast.LiteralKind.REGEX) {
        return true;
      }
      if (!validatedRegexLiterals.add(literal)) {
        return !invalidRegexLiterals.contains(literal);
      }
      String flags = literal.regexMetadata().orElseThrow().canonicalFlags();
      RegexCompilationResult result = REGEX_COMPILER.compile(literal.value(), flags);
      if (result instanceof RegexCompilationResult.Success) {
        return true;
      }

      RegexCompilationResult.Failure failure = (RegexCompilationResult.Failure) result;
      invalidRegexLiterals.add(literal);
      var metadata = literal.regexMetadata().orElseThrow();
      int maximumOffset = literal.value().codePointCount(0, literal.value().length());
      int patternOffset = Math.min(failure.patternOffset(), maximumOffset);
      int locationOffset =
          Math.min(
              Integer.parseInt(
                  failure
                      .fields()
                      .getOrDefault("_locationOffset", Integer.toString(patternOffset))),
              maximumOffset);
      var builder =
          Diagnostic.builder(
              failure.code(),
              Severity.ERROR,
              DiagnosticStage.TYPE_AND_STACK,
              program.sourcePath(),
              metadata.sourcePositionAt(locationOffset));
      failure
          .fields()
          .forEach(
              (name, value) -> {
                if (!name.startsWith("_")) {
                  builder.field(name, value);
                }
              });

      String reason = failure.fields().get("reason");
      String construct = failure.fields().get("construct");
      switch (failure.code()) {
        case E_REGEX_UNSUPPORTED_CONSTRUCT -> {
          String token = failure.fields().get("token");
          builder.expected("線形時間部分集合").actual(token);
          if (construct.equals("backreference")) {
            builder.fix("後方参照を使わずパターンを組み直してください");
          } else if (construct.equals("lookahead")) {
            builder.fix("先読みを使わず完全一致または検索を組み合わせてください");
          } else if (construct.equals("lookbehind")) {
            builder.fix("後読みを使わず完全一致または検索を組み合わせてください");
          } else {
            builder.fix("未対応の構文を使わずパターンを組み直してください");
          }
        }
        case E_REGEX_SYNTAX -> {
          if ("quantifierLimit".equals(reason)) {
            builder.expected("量指定子0以上1000以下").actual(literal.value()).fix("回数を1000以下にしてください");
          } else if ("duplicateGroupName".equals(reason)) {
            String name = failure.fields().get("name");
            builder.expected("一意なキャプチャ名").actual(name).fix("2個目のキャプチャ名を変更してください");
            int relatedOffset = Integer.parseInt(failure.fields().get("_relatedOffset"));
            builder.relatedLocation(
                new RelatedLocation(
                    program.sourcePath(),
                    metadata.sourcePositionAt(relatedOffset),
                    failure.fields().get("_relatedLabel")));
          } else if ("invalidGroupName".equals(reason)) {
            builder
                .expected("[A-Za-z][A-Za-z0-9_]{0,63}")
                .actual(failure.fields().get("name"))
                .fix("ASCIIのキャプチャ名を使用してください");
          } else {
            builder
                .expected("規範部分集合の有効な正規表現")
                .actual(literal.value().isEmpty() ? "空パターン" : literal.value())
                .fix("正規表現の構文を確認してください");
          }
        }
        case E_REGEX_PATTERN_LIMIT -> {
          long observed = Long.parseLong(failure.fields().get("patternUtf8Bytes"));
          builder
              .limit("regexPatternUtf8Bytes", RegexLimits.MAX_PATTERN_UTF8_BYTES, observed)
              .expected(RegexLimits.MAX_PATTERN_UTF8_BYTES + " UTF-8バイト以下")
              .actual(observed + " UTF-8バイト")
              .fix("rawパターンを短くしてください");
        }
        case E_REGEX_CAPTURE_LIMIT -> {
          long observed = Long.parseLong(failure.fields().get("regexCaptures"));
          builder
              .limit("regexCaptures", RegexLimits.MAX_CAPTURES, observed)
              .expected(RegexLimits.MAX_CAPTURES + "個以下のキャプチャ")
              .actual(observed + "個目")
              .fix("キャプチャ数を減らしてください");
        }
        case E_REGEX_PROGRAM_LIMIT -> {
          long observed = Long.parseLong(failure.fields().get("programInstructions"));
          builder
              .limit("regexProgramInstructions", RegexLimits.MAX_PROGRAM_INSTRUCTIONS, observed)
              .expected(RegexLimits.MAX_PROGRAM_INSTRUCTIONS + "命令以下")
              .actual(observed + "命令")
              .fix("正規表現を単純にしてください");
        }
        default ->
            throw new IllegalStateException("unexpected regex diagnostic: " + failure.code());
      }
      diagnostics.add(builder.build());
      return false;
    }

    private AnalysisResult result() {
      List<Diagnostic> ordered = diagnostics.diagnostics();
      if (diagnostics.hasErrors()) {
        return AnalysisResult.failure(ordered);
      }
      var signatureTable = new LinkedHashMap<String, WordSignature>();
      definitions.forEach(
          (name, definition) -> signatureTable.put(name, signatures.get(definition)));
      return AnalysisResult.success(
          new AnalyzedProgram(
              program,
              signatureTable,
              unreachableElements,
              nameResolution,
              arrayLiteralTypes,
              callSignatures,
              true,
              nonReturningWords,
              new LogicalConnectionResolution(
                  logicalConnections.values().stream()
                      .sorted(
                          java.util.Comparator.comparingLong(
                              declaration -> declaration.nameSpan().start().utf8Offset()))
                      .toList(),
                  logicalConnectionUses),
              new WorkspaceResolution(
                  workspaces.values().stream()
                      .sorted(
                          java.util.Comparator.comparingLong(
                              declaration -> declaration.nameSpan().start().utf8Offset()))
                      .toList(),
                  workspaceUses)),
          ordered);
    }
  }

  private static List<ValueType> concreteTypes(List<String> names) {
    return names.stream().map(name -> ValueType.fromSourceName(name).orElseThrow()).toList();
  }

  private static ValueType literalType(Literal literal) {
    return switch (literal.kind()) {
      case INTEGER -> ValueType.INTEGER;
      case BOOLEAN -> ValueType.BOOLEAN;
      case CHARACTER -> ValueType.CHARACTER;
      case STRING -> ValueType.STRING;
      case DECIMAL -> ValueType.DECIMAL;
      case REGEX -> ValueType.REGEX;
    };
  }

  private static String formatDeclaredEffect(WordDefinition definition) {
    String inputs =
        String.join(
            " ", definition.stackEffect().inputTypes().stream().map(TypeReference::name).toList());
    String outputs =
        String.join(
            " ", definition.stackEffect().outputTypes().stream().map(TypeReference::name).toList());
    var text = new StringBuilder("（");
    if (!inputs.isEmpty()) {
      text.append(inputs).append(' ');
    }
    text.append("--");
    if (!outputs.isEmpty()) {
      text.append(' ').append(outputs);
    }
    return text.append('）').toString();
  }

  private static String formatStack(AbstractStack stack) {
    return formatTypes(stack.types());
  }

  /** 機械処理用フィールドでは、区切り後の表示用空白を入れず一意な表記にします。 */
  private static String formatStackField(AbstractStack stack) {
    return stack.types().stream()
        .map(ValueType::sourceName)
        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
  }

  private static String formatTypes(List<ValueType> types) {
    return formatTypeNames(typeNames(types));
  }

  private static String assignmentTypeFix(ValueType expectedType, ValueType actualType) {
    if (expectedType.equals(ValueType.INTEGER) && actualType.equals(ValueType.DECIMAL)) {
      return "整数へ変換または丸めてから代入してください";
    }
    return expectedType.arrayElementType().isPresent()
        ? expectedType.arrayElementType().orElseThrow().sourceName() + "配列を代入してください"
        : expectedType.sourceName() + "を代入してください";
  }

  private static boolean isNumeric(ValueType type) {
    return type.equals(ValueType.INTEGER) || type.equals(ValueType.DECIMAL);
  }

  private static boolean isSingleInputConversion(String wordName) {
    return wordName.equals("小数に変換する")
        || wordName.equals("整数に変換する")
        || wordName.equals("大文字に変換する")
        || wordName.equals("小文字に変換する")
        || wordName.equals("入力行である")
        || wordName.equals("入力終端である")
        || wordName.equals("入力キャンセルである")
        || wordName.equals("入力行を取り出す")
        || wordName.equals("入力結果を捨てる")
        || wordName.equals("終了する");
  }

  private static String builtinTypeFix(WordCall call, int inputIndex, ValueType expectedType) {
    return switch (call.name()) {
      case "つなぐ" -> "第" + inputIndex + "入力を明示的に文字列へ変換してください";
      case "大文字に変換する", "小文字に変換する", "大小文字を無視して比較する" -> "文字列を渡してください";
      case "文字列からコードポイントを取り出す" -> inputIndex == 2 ? "0始まりの整数位置を渡してください" : "文字列を渡してください";
      case "正規表現に完全一致する", "正規表現を含む", "正規表現で最初を取り出す", "正規表現で置き換える", "正規表現で分割する" ->
          expectedType.equals(ValueType.REGEX) ? "正規表現リテラルを使用してください" : "文字列を渡してください";
      case "正規表現の名前付き部分を取り出す" ->
          expectedType.equals(ValueType.REGEX) ? "正規表現リテラルを使用してください" : "文字列を渡してください";
      case "割った商", "割った剰余", "割った商と剰余" -> inputIndex == 1 ? "整数の被除数を渡してください" : "整数の除数を渡してください";
      case "小数に変換する" -> "整数値を渡してください";
      case "整数に変換する" -> "小数値を渡してください";
      case "整数に丸める" -> inputIndex == 2 ? "5種類の丸め方法から1つ指定してください" : "小数値を渡してください";
      case "入力行である", "入力終端である", "入力キャンセルである", "入力行を取り出す", "入力結果を捨てる" -> "一行を入力するの結果を渡してください";
      case "精度指定で割る" ->
          inputIndex == 3
              ? "精度を整数で指定してください"
              : inputIndex == 4 ? "5種類の丸め方法から1つ指定してください" : "数値を渡してください";
      default -> "第" + inputIndex + "入力を" + expectedType.sourceName() + "にしてください";
    };
  }

  private static List<String> typeNames(List<ValueType> types) {
    return types.stream().map(ValueType::sourceName).toList();
  }

  private static String formatTypeNames(List<String> types) {
    return types.toString();
  }

  private static int graphemeDistance(String left, String right) {
    List<String> a = graphemes(left);
    List<String> b = graphemes(right);
    int[] previous = new int[b.size() + 1];
    for (int column = 0; column <= b.size(); column++) {
      previous[column] = column;
    }
    for (int row = 1; row <= a.size(); row++) {
      int[] current = new int[b.size() + 1];
      current[0] = row;
      for (int column = 1; column <= b.size(); column++) {
        int substitution = a.get(row - 1).equals(b.get(column - 1)) ? 0 : 1;
        current[column] =
            Math.min(
                Math.min(current[column - 1] + 1, previous[column] + 1),
                previous[column - 1] + substitution);
      }
      previous = current;
    }
    return previous[b.size()];
  }

  private static List<String> graphemes(String value) {
    List<Integer> boundaries = UnicodeRules.graphemeBoundaries(value);
    var result = new ArrayList<String>();
    for (int index = 1; index < boundaries.size(); index++) {
      result.add(value.substring(boundaries.get(index - 1), boundaries.get(index)));
    }
    return List.copyOf(result);
  }

  private static <T> Set<T> identitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private sealed interface CallTarget permits BuiltinTarget, UserTarget {
    boolean returnsNormally();
  }

  private record BuiltinTarget(BuiltinWord word) implements CallTarget {
    @Override
    public boolean returnsNormally() {
      return word.returnsNormally();
    }
  }

  private record UserTarget(WordSignature signature, boolean returnsNormally)
      implements CallTarget {}

  private record ArrayElementCheck(ValueType type, SourceSpan origin, String description) {
    private ArrayElementCheck {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(description, "description");
    }
  }

  private record BodyCheckResult(ControlFlowState state, boolean valid) {
    private BodyCheckResult {
      Objects.requireNonNull(state, "state");
    }

    private static BodyCheckResult valid(ControlFlowState state) {
      return new BodyCheckResult(state, true);
    }

    private static BodyCheckResult invalid(ControlFlowState state) {
      return new BodyCheckResult(state, false);
    }
  }

  private record ReturnSummary(boolean fallsThrough, boolean returnsFromWord) {}

  /** 実値を使わない構造的走査で、要素列から出られる経路の種類を要約します。 */
  private record StructuralFlow(
      boolean canFallThrough,
      boolean canReturn,
      boolean canBreak,
      boolean canContinue,
      BodyElement cause) {
    private static StructuralFlow fallthrough() {
      return new StructuralFlow(true, false, false, false, null);
    }

    private static StructuralFlow returned(ControlTransfer cause) {
      return new StructuralFlow(false, true, false, false, cause);
    }

    private static StructuralFlow broken(ControlTransfer cause) {
      return new StructuralFlow(false, false, true, false, cause);
    }

    private static StructuralFlow continued(ControlTransfer cause) {
      return new StructuralFlow(false, false, false, true, cause);
    }

    private static StructuralFlow terminated(WordCall cause) {
      return new StructuralFlow(false, false, false, false, cause);
    }

    private static StructuralFlow unreachable(BodyElement cause) {
      return new StructuralFlow(false, false, false, false, cause);
    }
  }

  private static boolean containsBindingSyntax(Program program) {
    if (!program.declarations().isEmpty()
        || !program.logicalConnections().isEmpty()
        || !program.workspaces().isEmpty()) {
      return true;
    }
    return program.definitions().stream()
        .anyMatch(definition -> containsBindingSyntax(definition.body()));
  }

  private static boolean containsBindingSyntax(List<BodyElement> body) {
    for (BodyElement element : body) {
      if (element instanceof Assignment
          || element instanceof ValueDeclaration
          || element instanceof ValueReference) {
        return true;
      }
      if (element instanceof Conditional conditional
          && (containsBindingSyntax(conditional.trueBody())
              || containsBindingSyntax(conditional.falseBody()))) {
        return true;
      }
      if (element instanceof ShortCircuitEvaluation evaluation
          && containsBindingSyntax(evaluation.rightBody())) {
        return true;
      }
      if (element instanceof CountedLoop loop && containsBindingSyntax(loop.body())) {
        return true;
      }
      if (element instanceof ConditionLoop loop
          && (containsBindingSyntax(loop.conditionBody()) || containsBindingSyntax(loop.body()))) {
        return true;
      }
      if (element instanceof ArrayLoop loop && containsBindingSyntax(loop.body())) {
        return true;
      }
    }
    return false;
  }

  private record GlobalNameSite(
      String spelling, SourceSpan nameSpan, TopLevelElement declaration) {}

  private record ConfusableName(String inputName, String normalizedName, SourceSpan location) {
    private static ConfusableName fromDeclaration(DeclaredName declaration) {
      return new ConfusableName(declaration.spelling(), declaration.name(), declaration.nameSpan());
    }
  }
}
