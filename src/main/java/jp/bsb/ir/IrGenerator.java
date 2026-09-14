package jp.bsb.ir;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.binding.Binding;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.ArrayLoop;
import jp.bsb.frontend.ast.Assignment;
import jp.bsb.frontend.ast.AstNode;
import jp.bsb.frontend.ast.BodyElement;
import jp.bsb.frontend.ast.Comment;
import jp.bsb.frontend.ast.ConditionLoop;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.ControlTransfer;
import jp.bsb.frontend.ast.CountedLoop;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.Particle;
import jp.bsb.frontend.ast.ShortCircuitEvaluation;
import jp.bsb.frontend.ast.ShortCircuitOperator;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.numeric.DecimalLexemeAnalyzer;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Valid;
import jp.bsb.numeric.DecimalLimits;
import jp.bsb.regex.Re2RegexCompiler;
import jp.bsb.regex.RegexCompilationResult;
import jp.bsb.runtime.BooleanValue;
import jp.bsb.runtime.CharacterValue;
import jp.bsb.runtime.DecimalValue;
import jp.bsb.runtime.IntegerValue;
import jp.bsb.runtime.RegexValue;
import jp.bsb.runtime.RoundingModeValue;
import jp.bsb.runtime.RuntimeValue;
import jp.bsb.runtime.StringValue;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.BuiltinOperation;
import jp.bsb.stdlib.ValueType;

/** 検査済みASTを、名前と保存スロットを解決済みのIR命令へ変換します。 */
public final class IrGenerator {
  private static final Re2RegexCompiler REGEX_COMPILER = new Re2RegexCompiler();

  /** 専用大域初期化単語を含むIR命令数の上限です。 */
  public static final int MAX_INSTRUCTIONS = 250_000;

  /** 状態を持たないIR生成器を作ります。 */
  public IrGenerator() {}

  /**
   * 検査済みプログラムからIRを生成します。
   *
   * <p>【コンピュータ科学の観点：ラベル解決】前方分岐を読んだ時点では、偽側やループ出口が何番目の命令になるか未確定です。生成中だけ名前のないラベルを置き、単語全体を並べた後で整数の命令位置へ変換します。
   * 実行時にはラベル探索が残りません。
   *
   * <p>【コンピュータ科学の観点：防御的な資源管理】字句トークン上限とは別に、到達可能な命令と必要な制御命令を追加前に1個ずつ数えます。
   * 250,001個目のIR命令オブジェクトを作る前に停止します。
   *
   * @param analyzed 名前・型・スタック効果を検査済みのプログラム
   * @return IRまたはE_IR_LIMIT診断
   */
  public IrGenerationResult generate(AnalyzedProgram analyzed) {
    var symbolsByName = new LinkedHashMap<String, SymbolId>();
    int nextId = 0;
    for (var definition : analyzed.syntax().definitions()) {
      symbolsByName.put(definition.name(), new SymbolId(nextId++));
    }

    var builtinsById = new LinkedHashMap<SymbolId, jp.bsb.stdlib.BuiltinWord>();
    for (var builtin : BuiltinDictionary.words()) {
      var id = new SymbolId(nextId++);
      symbolsByName.put(builtin.canonicalName(), id);
      builtinsById.put(id, builtin);
      builtin.aliases().forEach(alias -> symbolsByName.put(alias, id));
    }

    SlotLayout slots = createSlotLayout(analyzed);
    boolean verifyTypes = analyzed.irTypeMetadataComplete();
    var words = new LinkedHashMap<SymbolId, IrWord>();
    var counter = new InstructionCounter(analyzed.syntax().sourcePath());
    Optional<IrWord> globalInitializer = Optional.empty();
    if (!slots.globalSlots().isEmpty()) {
      var builder = new WordBuilder("<大域初期化>", counter);
      for (ValueDeclaration declaration : analyzed.syntax().declarations()) {
        IrStorageSlot slot = slots.slotForDeclaration(declaration);
        if (!emitBody(
                declaration.initializer(),
                builder,
                symbolsByName,
                analyzed,
                slots,
                new ArrayList<LoopTarget>())
            || !builder.add(
                declaration.kindSpan(), () -> new InitializeGlobal(slot, declaration.kindSpan()))) {
          return failure(builder.diagnostic());
        }
      }
      ValueDeclaration lastDeclaration = analyzed.syntax().declarations().getLast();
      if (!builder.add(lastDeclaration.endSpan(), () -> new Return(lastDeclaration.endSpan()))) {
        return failure(builder.diagnostic());
      }
      globalInitializer =
          Optional.of(
              createWord(
                  new SymbolId(nextId++),
                  "<大域初期化>",
                  builder.resolve(),
                  List.of(),
                  new IrStackEffect(List.of(), List.of()),
                  verifyTypes));
    }

    for (var definition : analyzed.syntax().definitions()) {
      var builder = new WordBuilder(definition.name(), counter);
      if (!emitBody(
              definition.body(),
              builder,
              symbolsByName,
              analyzed,
              slots,
              new ArrayList<LoopTarget>())
          || !builder.add(definition.endSpan(), () -> new Return(definition.endSpan()))) {
        return failure(builder.diagnostic());
      }

      SymbolId id = symbolsByName.get(definition.name());
      words.put(
          id,
          createWord(
              id,
              definition.name(),
              builder.resolve(),
              slots.localSlotsFor(definition.name()),
              toIrEffect(analyzed.findUserWord(definition.name()).orElseThrow()),
              verifyTypes));
    }

    SymbolId main = symbolsByName.get("メイン");
    if (main == null) {
      throw new IllegalStateException("analyzed program has no main word");
    }
    return new IrGenerationResult(
        java.util.Optional.of(
            new IrProgram(
                analyzed.syntax().sourcePath(),
                main,
                words,
                builtinsById,
                counter.count,
                globalInitializer,
                slots.globalSlots())),
        List.of());
  }

  /** 束縛ID順を安定な配置順とし、大域と単語ごとの局所番号空間へ分けます。 */
  private static SlotLayout createSlotLayout(AnalyzedProgram analyzed) {
    var globalSlots = new ArrayList<IrStorageSlot>();
    var localSlotsByWord = new LinkedHashMap<String, List<IrStorageSlot>>();
    var slotsByBindingId = new LinkedHashMap<BindingId, IrStorageSlot>();
    var slotsByDeclarationSpan = new LinkedHashMap<SourceSpan, IrStorageSlot>();

    for (Binding binding : analyzed.nameResolution().bindings()) {
      var valueType =
          binding
              .typeState()
              .type()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "an analyzed binding has no inferred type: " + binding.id()));
      Optional<String> ownerWord =
          binding.storage() == BindingStorage.GLOBAL
              ? Optional.empty()
              : analyzed
                  .nameResolution()
                  .findScope(binding.scopeId())
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "an analyzed binding has no lexical scope: " + binding.id()))
                  .ownerWord();
      int slotIndex;
      if (binding.storage() == BindingStorage.GLOBAL) {
        slotIndex = globalSlots.size();
      } else {
        slotIndex = localSlotsByWord.getOrDefault(ownerWord.orElseThrow(), List.of()).size();
      }
      var slot =
          new IrStorageSlot(
              binding.id(),
              binding.name(),
              binding.kind(),
              binding.storage(),
              valueType,
              slotIndex,
              ownerWord);
      slotsByBindingId.put(binding.id(), slot);
      if (slotsByDeclarationSpan.put(binding.declarationSpan(), slot) != null) {
        throw new IllegalStateException("two analyzed bindings share one declaration span");
      }
      if (binding.storage() == BindingStorage.GLOBAL) {
        globalSlots.add(slot);
      } else {
        localSlotsByWord
            .computeIfAbsent(ownerWord.orElseThrow(), ignored -> new ArrayList<>())
            .add(slot);
      }
    }
    return new SlotLayout(globalSlots, localSlotsByWord, slotsByBindingId, slotsByDeclarationSpan);
  }

  private static IrInstruction loadInstruction(IrStorageSlot slot, SourceSpan span) {
    return slot.storage() == BindingStorage.GLOBAL
        ? new LoadGlobal(slot, span)
        : new LoadLocal(slot, span);
  }

  private static IrInstruction storeInstruction(IrStorageSlot slot, SourceSpan span) {
    return slot.storage() == BindingStorage.GLOBAL
        ? new StoreGlobal(slot, span)
        : new StoreLocal(slot, span);
  }

  private static boolean emitBody(
      List<BodyElement> body,
      WordBuilder builder,
      Map<String, SymbolId> symbolsByName,
      AnalyzedProgram analyzed,
      SlotLayout slots,
      ArrayList<LoopTarget> loops) {
    var pendingParticles = new ArrayList<ParticleSource>();
    for (BodyElement element : body) {
      if (!analyzed.isReachable(element)) {
        pendingParticles.clear();
        continue;
      }
      if (element instanceof Comment) {
        continue;
      }
      if (element instanceof Particle particle) {
        pendingParticles.add(new ParticleSource(particle.name(), particle.span()));
        continue;
      }
      if (element instanceof Literal literal) {
        if (!builder.add(
            literal.span(), () -> new PushConst(toRuntimeValue(literal), literal.span()))) {
          return false;
        }
        continue;
      }
      if (element instanceof ValueReference reference) {
        IrStorageSlot slot = slots.slotForUse(reference, analyzed);
        if (!builder.add(reference.span(), () -> loadInstruction(slot, reference.span()))) {
          return false;
        }
        continue;
      }
      if (element instanceof ArrayLiteral array) {
        if (!emitArrayLiteral(array, builder, symbolsByName, analyzed, slots, loops)) {
          return false;
        }
        continue;
      }
      if (element instanceof WordCall call) {
        var builtin = BuiltinDictionary.find(call.name()).orElse(null);
        if (builtin != null && builtin.operation() == BuiltinOperation.EMPTY_ARRAY) {
          ValueType elementType =
              analyzed
                  .findCallSignature(call)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "an analyzed empty-array value has no concrete effect"))
                  .outputTypes()
                  .getFirst()
                  .arrayElementType()
                  .orElseThrow();
          if (!builder.add(call.span(), () -> new BuildArray(elementType, 0, call.span()))) {
            return false;
          }
          pendingParticles.clear();
          continue;
        }
        if (builtin != null && builtin.operation() == BuiltinOperation.ROUNDING_MODE_VALUE) {
          RoundingModeValue value =
              RoundingModeValue.fromSourceName(call.name())
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "an analyzed rounding-mode value has no runtime value"));
          if (!builder.add(call.span(), () -> new PushConst(value, call.span()))) {
            return false;
          }
          pendingParticles.clear();
          continue;
        }
        SymbolId target = symbolsByName.get(call.name());
        if (target == null) {
          throw new IllegalStateException("analyzed call has no symbol: " + call.name());
        }
        List<ParticleSource> particles = List.copyOf(pendingParticles);
        Optional<IrStackEffect> stackEffect =
            analyzed.findCallSignature(call).map(IrGenerator::toIrEffect);
        Optional<LogicalConnectionReference> logicalConnection =
            analyzed
                .logicalConnectionResolution()
                .resolve(call)
                .map(
                    use ->
                        new LogicalConnectionReference(
                            use.declaration().name(),
                            use.operation(),
                            use.declaration().nameSpan(),
                            call.logicalConnectionArgument().orElseThrow().span(),
                            use.httpMethod()));
        Optional<WorkspaceReference> workspace =
            analyzed
                .workspaceResolution()
                .resolve(call)
                .map(
                    use ->
                        new WorkspaceReference(
                            use.declaration().name(),
                            use.operation(),
                            use.declaration().nameSpan(),
                            call.workspaceArgument().orElseThrow().span()));
        if (analyzed.irTypeMetadataComplete() && stackEffect.isEmpty()) {
          throw new IllegalStateException(
              "an analyzed call has no concrete stack effect: " + call.name());
        }
        if (!builder.add(
            call.span(),
            () ->
                stackEffect
                    .map(
                        effect ->
                            new Call(
                                target,
                                call.name(),
                                particles,
                                Optional.of(effect),
                                call.span(),
                                analyzed.callReturnsNormally(call),
                                logicalConnection,
                                workspace))
                    .orElseGet(() -> new Call(target, call.name(), particles, call.span())))) {
          return false;
        }
        pendingParticles.clear();
        continue;
      }

      // 制御境界を越えた助詞は、別の実行経路にある呼出しへ関連づけません。
      pendingParticles.clear();
      if (element instanceof ValueDeclaration declaration) {
        IrStorageSlot slot = slots.slotForDeclaration(declaration);
        if (!emitBody(declaration.initializer(), builder, symbolsByName, analyzed, slots, loops)
            || !builder.add(
                declaration.kindSpan(), () -> new InitializeLocal(slot, declaration.kindSpan()))) {
          return false;
        }
        continue;
      }
      if (element instanceof Assignment assignment) {
        IrStorageSlot slot = slots.slotForUse(assignment, analyzed);
        if (!builder.add(
            assignment.keywordSpan(), () -> storeInstruction(slot, assignment.keywordSpan()))) {
          return false;
        }
        continue;
      }
      boolean emitted =
          switch (element) {
            case Conditional conditional ->
                emitConditional(conditional, builder, symbolsByName, analyzed, slots, loops);
            case ShortCircuitEvaluation evaluation ->
                emitShortCircuit(evaluation, builder, symbolsByName, analyzed, slots, loops);
            case CountedLoop loop ->
                emitCountedLoop(loop, builder, symbolsByName, analyzed, slots, loops);
            case ConditionLoop loop ->
                emitConditionLoop(loop, builder, symbolsByName, analyzed, slots, loops);
            case ArrayLoop loop ->
                emitArrayLoop(loop, builder, symbolsByName, analyzed, slots, loops);
            case ControlTransfer transfer -> emitTransfer(transfer, builder, loops);
            default -> throw new IllegalStateException("unknown analyzed body element: " + element);
          };
      if (!emitted) {
        return false;
      }
    }
    return true;
  }

  private static boolean emitArrayLiteral(
      ArrayLiteral array,
      WordBuilder builder,
      Map<String, SymbolId> symbolsByName,
      AnalyzedProgram analyzed,
      SlotLayout slots,
      ArrayList<LoopTarget> loops) {
    for (var element : array.elements()) {
      if (!emitBody(element.body(), builder, symbolsByName, analyzed, slots, loops)) {
        return false;
      }
    }
    ValueType elementType =
        analyzed
            .findArrayLiteralType(array)
            .orElseThrow(
                () -> new IllegalStateException("an analyzed array literal has no concrete type"))
            .arrayElementType()
            .orElseThrow();
    return builder.add(
        array.openingSpan(),
        () -> new BuildArray(elementType, array.elements().size(), array.openingSpan()));
  }

  private static boolean emitConditional(
      Conditional conditional,
      WordBuilder builder,
      Map<String, SymbolId> symbolsByName,
      AnalyzedProgram analyzed,
      SlotLayout slots,
      ArrayList<LoopTarget> loops) {
    Label falseStart = builder.newLabel();
    if (!builder.add(
        conditional.openingSpan(),
        () -> new BranchIfFalse(falseStart.instructionIndex(), conditional.openingSpan()))) {
      return false;
    }
    if (!emitBody(conditional.trueBody(), builder, symbolsByName, analyzed, slots, loops)) {
      return false;
    }

    if (!conditional.hasElse()) {
      builder.mark(falseStart);
      return true;
    }

    Label end = builder.newLabel();
    SourceSpan elseSpan = conditional.elseSpan().orElseThrow();
    if (!builder.add(elseSpan, () -> new Jump(end.instructionIndex(), 0, elseSpan))) {
      return false;
    }
    builder.mark(falseStart);
    if (!emitBody(conditional.falseBody(), builder, symbolsByName, analyzed, slots, loops)) {
      return false;
    }
    builder.mark(end);
    return true;
  }

  /** 左辺を分岐命令で消費し、必要な場合だけ右辺本体へ進むIRを生成します。 */
  private static boolean emitShortCircuit(
      ShortCircuitEvaluation evaluation,
      WordBuilder builder,
      Map<String, SymbolId> symbolsByName,
      AnalyzedProgram analyzed,
      SlotLayout slots,
      ArrayList<LoopTarget> loops) {
    Label rightOrShortCircuit = builder.newLabel();
    Label end = builder.newLabel();
    if (!builder.add(
        evaluation.openingSpan(),
        () ->
            new BranchIfFalse(rightOrShortCircuit.instructionIndex(), evaluation.openingSpan()))) {
      return false;
    }

    if (evaluation.operator() == ShortCircuitOperator.OR) {
      if (!builder.add(
              evaluation.openingSpan(),
              () -> new PushConst(new BooleanValue(true), evaluation.openingSpan()))
          || !builder.add(
              evaluation.endSpan(),
              () -> new Jump(end.instructionIndex(), 0, evaluation.endSpan()))) {
        return false;
      }
      builder.mark(rightOrShortCircuit);
      if (!emitBody(evaluation.rightBody(), builder, symbolsByName, analyzed, slots, loops)) {
        return false;
      }
    } else {
      if (!emitBody(evaluation.rightBody(), builder, symbolsByName, analyzed, slots, loops)
          || !builder.add(
              evaluation.endSpan(),
              () -> new Jump(end.instructionIndex(), 0, evaluation.endSpan()))) {
        return false;
      }
      builder.mark(rightOrShortCircuit);
      if (!builder.add(
          evaluation.openingSpan(),
          () -> new PushConst(new BooleanValue(false), evaluation.openingSpan()))) {
        return false;
      }
    }
    builder.mark(end);
    return true;
  }

  private static boolean emitCountedLoop(
      CountedLoop loop,
      WordBuilder builder,
      Map<String, SymbolId> symbolsByName,
      AnalyzedProgram analyzed,
      SlotLayout slots,
      ArrayList<LoopTarget> loops) {
    Label bodyStart = builder.newLabel();
    Label next = builder.newLabel();
    Label exit = builder.newLabel();
    if (!builder.add(
        loop.openingSpan(),
        () -> new CountedLoopStart(exit.instructionIndex(), loop.openingSpan()))) {
      return false;
    }
    builder.mark(bodyStart);

    loops.add(new LoopTarget(exit, next, LoopKind.COUNTED));
    boolean bodyEmitted = emitBody(loop.body(), builder, symbolsByName, analyzed, slots, loops);
    loops.removeLast();
    if (!bodyEmitted) {
      return false;
    }

    builder.mark(next);
    if (!builder.add(
        loop.endSpan(), () -> new CountedLoopNext(bodyStart.instructionIndex(), loop.endSpan()))) {
      return false;
    }
    builder.mark(exit);
    return true;
  }

  private static boolean emitConditionLoop(
      ConditionLoop loop,
      WordBuilder builder,
      Map<String, SymbolId> symbolsByName,
      AnalyzedProgram analyzed,
      SlotLayout slots,
      ArrayList<LoopTarget> loops) {
    Label conditionStart = builder.newLabel();
    Label exit = builder.newLabel();
    builder.mark(conditionStart);
    loops.add(new LoopTarget(exit, conditionStart, LoopKind.CONDITIONAL));

    if (!emitBody(loop.conditionBody(), builder, symbolsByName, analyzed, slots, loops)
        || !builder.add(
            loop.separatorSpan(),
            () -> new BranchIfFalse(exit.instructionIndex(), loop.separatorSpan()))
        || !emitBody(loop.body(), builder, symbolsByName, analyzed, slots, loops)
        || !builder.add(
            loop.endSpan(), () -> new Jump(conditionStart.instructionIndex(), 0, loop.endSpan()))) {
      loops.removeLast();
      return false;
    }
    loops.removeLast();
    builder.mark(exit);
    return true;
  }

  private static boolean emitArrayLoop(
      ArrayLoop loop,
      WordBuilder builder,
      Map<String, SymbolId> symbolsByName,
      AnalyzedProgram analyzed,
      SlotLayout slots,
      ArrayList<LoopTarget> loops) {
    Label bodyStart = builder.newLabel();
    Label next = builder.newLabel();
    Label exit = builder.newLabel();
    if (!builder.add(
        loop.openingSpan(),
        () -> new ArrayLoopStart(exit.instructionIndex(), loop.openingSpan()))) {
      return false;
    }
    builder.mark(bodyStart);

    loops.add(new LoopTarget(exit, next, LoopKind.ARRAY));
    boolean bodyEmitted = emitBody(loop.body(), builder, symbolsByName, analyzed, slots, loops);
    loops.removeLast();
    if (!bodyEmitted) {
      return false;
    }

    builder.mark(next);
    if (!builder.add(
        loop.endSpan(), () -> new ArrayLoopNext(bodyStart.instructionIndex(), loop.endSpan()))) {
      return false;
    }
    builder.mark(exit);
    return true;
  }

  private static boolean emitTransfer(
      ControlTransfer transfer, WordBuilder builder, ArrayList<LoopTarget> loops) {
    if (transfer.kind() == ControlTransfer.Kind.RETURN) {
      return builder.add(transfer.span(), () -> new Return(transfer.span()));
    }
    if (loops.isEmpty()) {
      throw new IllegalStateException("analyzed loop transfer has no target: " + transfer.lexeme());
    }

    LoopTarget loop = loops.getLast();
    if (transfer.kind() == ControlTransfer.Kind.BREAK) {
      int countedDiscard = loop.kind() == LoopKind.COUNTED ? 1 : 0;
      int arrayDiscard = loop.kind() == LoopKind.ARRAY ? 1 : 0;
      return builder.add(
          transfer.span(),
          () ->
              new Jump(
                  loop.breakTarget().instructionIndex(),
                  countedDiscard,
                  arrayDiscard,
                  transfer.span()));
    }
    return builder.add(
        transfer.span(),
        () -> new Jump(loop.continueTarget().instructionIndex(), 0, transfer.span()));
  }

  private static IrStackEffect toIrEffect(jp.bsb.analyzer.WordSignature signature) {
    return new IrStackEffect(signature.inputTypes(), signature.outputTypes());
  }

  private static IrWord createWord(
      SymbolId symbolId,
      String name,
      List<IrInstruction> instructions,
      List<IrStorageSlot> localSlots,
      IrStackEffect stackEffect,
      boolean verifyTypes) {
    return verifyTypes
        ? new IrWord(symbolId, name, instructions, localSlots, stackEffect)
        : new IrWord(symbolId, name, instructions, localSlots);
  }

  private static RuntimeValue toRuntimeValue(Literal literal) {
    return switch (literal.kind()) {
      case INTEGER -> new IntegerValue(new BigInteger(literal.value()));
      case BOOLEAN -> new BooleanValue(literal.value().equals("はい"));
      case CHARACTER -> new CharacterValue(literal.value());
      case STRING -> new StringValue(literal.value());
      case DECIMAL -> decimalLiteralValue(literal);
      case REGEX -> compileRegexLiteral(literal);
    };
  }

  /** 検査済みの係数と正規化後スケールだけから小数値を構築します。 */
  private static DecimalValue decimalLiteralValue(Literal literal) {
    DecimalLexemeAnalyzer.Result result =
        DecimalLexemeAnalyzer.analyze(literal.value(), DecimalLimits.ABSOLUTE_SCALE);
    if (!(result instanceof Valid valid)
        || absoluteScale(valid.rawScale()) > DecimalLimits.ABSOLUTE_SCALE
        || absoluteScale(valid.normalizedScale()) > DecimalLimits.ABSOLUTE_SCALE) {
      throw new IllegalStateException("an unvalidated decimal literal reached IR generation");
    }
    BigInteger coefficient = new BigInteger(valid.normalizedCoefficientDigits());
    if (valid.negative() && !valid.zero()) {
      coefficient = coefficient.negate();
    }
    return new DecimalValue(coefficient, Math.toIntExact(valid.normalizedScale()));
  }

  private static long absoluteScale(long scale) {
    return scale < 0 ? -scale : scale;
  }

  private static RegexValue compileRegexLiteral(Literal literal) {
    String flags = literal.regexMetadata().orElseThrow().canonicalFlags();
    return switch (REGEX_COMPILER.compile(literal.value(), flags)) {
      case RegexCompilationResult.Success success ->
          new RegexValue(literal.value(), flags, success.program());
      case RegexCompilationResult.Failure failure ->
          throw new IllegalStateException(
              "an invalid regex literal reached IR generation: " + failure.code());
    };
  }

  /** 1回のIR生成で共有する、束縛と実行スロットの決定的な対応です。 */
  private record SlotLayout(
      List<IrStorageSlot> globalSlots,
      Map<String, List<IrStorageSlot>> localSlotsByWord,
      Map<BindingId, IrStorageSlot> slotsByBindingId,
      Map<SourceSpan, IrStorageSlot> slotsByDeclarationSpan) {
    private SlotLayout {
      globalSlots = List.copyOf(globalSlots);
      var immutableLocals = new LinkedHashMap<String, List<IrStorageSlot>>();
      localSlotsByWord.forEach((word, slots) -> immutableLocals.put(word, List.copyOf(slots)));
      localSlotsByWord = java.util.Collections.unmodifiableMap(immutableLocals);
      slotsByBindingId = Map.copyOf(slotsByBindingId);
      slotsByDeclarationSpan = Map.copyOf(slotsByDeclarationSpan);
    }

    private List<IrStorageSlot> localSlotsFor(String word) {
      return localSlotsByWord.getOrDefault(word, List.of());
    }

    private IrStorageSlot slotForDeclaration(ValueDeclaration declaration) {
      IrStorageSlot slot = slotsByDeclarationSpan.get(declaration.span());
      if (slot == null) {
        throw new IllegalStateException(
            "an analyzed declaration has no storage slot: " + declaration.name());
      }
      return slot;
    }

    private IrStorageSlot slotForUse(AstNode node, AnalyzedProgram analyzed) {
      Binding binding =
          analyzed
              .nameResolution()
              .resolve(node)
              .orElseThrow(() -> new IllegalStateException("an analyzed value use is unresolved"));
      IrStorageSlot slot = slotsByBindingId.get(binding.id());
      if (slot == null) {
        throw new IllegalStateException("an analyzed binding has no storage slot: " + binding.id());
      }
      return slot;
    }
  }

  private static IrGenerationResult failure(Diagnostic diagnostic) {
    if (diagnostic == null) {
      throw new IllegalStateException("IR emission stopped without a diagnostic");
    }
    return new IrGenerationResult(java.util.Optional.empty(), List.of(diagnostic));
  }

  /** 構文上のループ1個について、脱出先と継続先を最も内側優先で保持します。 */
  private record LoopTarget(Label breakTarget, Label continueTarget, LoopKind kind) {}

  private enum LoopKind {
    COUNTED,
    CONDITIONAL,
    ARRAY
  }

  /** 生成中だけ使うラベルです。ラベル自体はIR命令数へ含めません。 */
  private static final class Label {
    private int instructionIndex = -1;

    private int instructionIndex() {
      if (instructionIndex < 0) {
        throw new IllegalStateException("unresolved IR label");
      }
      return instructionIndex;
    }
  }

  /** ラベル確定後に不変IR命令を作る処理です。 */
  @FunctionalInterface
  private interface PendingInstruction {
    IrInstruction resolve();
  }

  /** 1単語分のラベル、未解決命令、資源診断をまとめます。 */
  private static final class WordBuilder {
    private final String word;
    private final InstructionCounter counter;
    private final ArrayList<PendingInstruction> instructions = new ArrayList<>();
    private Diagnostic diagnostic;

    private WordBuilder(String word, InstructionCounter counter) {
      this.word = word;
      this.counter = counter;
    }

    private Label newLabel() {
      return new Label();
    }

    private void mark(Label label) {
      if (label.instructionIndex >= 0) {
        throw new IllegalStateException("IR label was marked twice");
      }
      label.instructionIndex = instructions.size();
    }

    private boolean add(SourceSpan span, PendingInstruction instruction) {
      diagnostic = counter.beforeAdd(span, word);
      if (diagnostic != null) {
        return false;
      }
      instructions.add(instruction);
      return true;
    }

    private Diagnostic diagnostic() {
      return diagnostic;
    }

    private List<IrInstruction> resolve() {
      return instructions.stream().map(PendingInstruction::resolve).toList();
    }
  }

  private static final class InstructionCounter {
    private final String sourcePath;
    private int count;

    private InstructionCounter(String sourcePath) {
      this.sourcePath = sourcePath;
    }

    private Diagnostic beforeAdd(SourceSpan span, String word) {
      long next = (long) count + 1;
      if (next > MAX_INSTRUCTIONS) {
        return Diagnostic.builder(
                DiagnosticCode.E_IR_LIMIT, Severity.ERROR, DiagnosticStage.IR, sourcePath, span)
            .field("word", word)
            .limit("irInstructions", MAX_INSTRUCTIONS, next)
            .expected(MAX_INSTRUCTIONS + "命令以下")
            .actual(next + "命令目")
            .fix("単語本体または定義数を減らしてください。")
            .build();
      }
      count++;
      return null;
    }
  }
}
