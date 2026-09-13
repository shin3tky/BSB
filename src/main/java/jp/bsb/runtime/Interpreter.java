package jp.bsb.runtime;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.ArrayLoopNext;
import jp.bsb.ir.ArrayLoopStart;
import jp.bsb.ir.BranchIfFalse;
import jp.bsb.ir.BuildArray;
import jp.bsb.ir.Call;
import jp.bsb.ir.CountedLoopNext;
import jp.bsb.ir.CountedLoopStart;
import jp.bsb.ir.InitializeGlobal;
import jp.bsb.ir.InitializeLocal;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.Jump;
import jp.bsb.ir.LoadGlobal;
import jp.bsb.ir.LoadLocal;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.StoreGlobal;
import jp.bsb.ir.StoreLocal;

/**
 * 制御フローと大域・局所保存領域を、命令位置を直接更新しながら実行するスタックマシンです。
 *
 * <p>【コンピュータ科学の観点：反復型仮想機械】利用者単語をJavaメソッドとして再帰呼出しせず、命令位置を持つ {@link Frame}
 * を独自リストへ積みます。これにより、BSBの呼出し上限をJavaのスタック容量と無関係に、正確に1,024へ制御できます。
 */
public final class Interpreter {
  /** 状態を持たないインタープリタを作ります。 */
  public Interpreter() {}

  /**
   * IRプログラムを注入された環境で実行します。
   *
   * @param program 検査・生成済みIR
   * @param context 出力、時計、トレースの実行環境
   * @return 成功または実行時診断
   */
  public ExecutionResult execute(IrProgram program, ExecutionContext context) {
    var dataStack = new ArrayList<RuntimeValue>();
    var callStack = new ArrayList<Frame>();
    List<IrWord> startupWords = program.startupWords();
    int startupWordIndex = 0;
    callStack.add(new Frame(startupWords.getFirst()));
    var globalValues = new StorageArea(program.globalSlots(), BindingStorage.GLOBAL);
    var output = BoundedOutput.standard(program.sourcePath(), context.environment());
    var budget = new ExecutionBudget(program.sourcePath(), context.clock());
    var builtins = new BuiltinExecutor(program.sourcePath(), output, budget, context.environment());
    boolean tracing = context.trace().enabled();
    long sequence = 0;

    try {
      while (!callStack.isEmpty()) {
        Frame frame = callStack.getLast();
        IrInstruction instruction = frame.nextInstruction();
        budget.beforeInstruction(instruction.span());

        List<RuntimeValue> dataBefore = tracing ? List.copyOf(dataStack) : List.of();
        List<ControlTraceState> controlBefore = tracing ? controlSnapshot(callStack) : List.of();
        int depthBefore = callStack.size();
        String currentWord = frame.word.name();
        frame.advance();
        byte[] instructionOutput = new byte[0];
        String instructionEffect = "";
        Optional<String> controlTarget = Optional.empty();
        Optional<Boolean> branchTaken = Optional.empty();
        Optional<StorageTraceState> storageTrace = Optional.empty();
        OptionalInt programExitCode = OptionalInt.empty();
        boolean startNextStartupWord = false;

        if (instruction instanceof PushConst push) {
          push(dataStack, push, program.sourcePath(), currentWord);
        } else if (instruction instanceof BuildArray build) {
          int firstElement = dataStack.size() - build.elementCount();
          if (firstElement < 0) {
            throw new IllegalStateException("BuildArray has insufficient runtime stack input");
          }
          if (build.elementCount() == 0) {
            requirePushCapacity(dataStack, build.span(), program.sourcePath(), currentWord);
          }
          List<RuntimeValue> elements =
              List.copyOf(dataStack.subList(firstElement, dataStack.size()));
          long logicalLeafCount = ArrayNestedLimit.measure(build.elementType(), elements);
          ArrayNestedLimit.requireAllowed(
              program.sourcePath(), build.span(), "literal", build.elementType(), logicalLeafCount);
          budget.beforeArrayWork(
              build.elementCount(), build.elementCount(), build.span(), "literal");
          ArrayValue value = new ArrayValue(build.elementType(), elements, logicalLeafCount);
          dataStack.subList(firstElement, dataStack.size()).clear();
          dataStack.add(value);
        } else if (instruction instanceof InitializeGlobal initialize) {
          RuntimeValue value = storageInput(dataStack, initialize.opcode());
          globalValues.initializeGlobal(initialize.slot(), value);
          dataStack.removeLast();
          storageTrace =
              Optional.of(
                  new StorageTraceState(initialize.slot(), Optional.empty(), Optional.of(value)));
        } else if (instruction instanceof InitializeLocal initialize) {
          RuntimeValue value = storageInput(dataStack, initialize.opcode());
          frame.localValues.initializeLocal(initialize.slot(), value);
          dataStack.removeLast();
          storageTrace =
              Optional.of(
                  new StorageTraceState(initialize.slot(), Optional.empty(), Optional.of(value)));
        } else if (instruction instanceof LoadGlobal load) {
          RuntimeValue value = globalValues.load(load.slot());
          pushValue(dataStack, value, load.span(), program.sourcePath(), currentWord);
          storageTrace =
              Optional.of(
                  new StorageTraceState(load.slot(), Optional.of(value), Optional.of(value)));
        } else if (instruction instanceof LoadLocal load) {
          RuntimeValue value = frame.localValues.load(load.slot());
          pushValue(dataStack, value, load.span(), program.sourcePath(), currentWord);
          storageTrace =
              Optional.of(
                  new StorageTraceState(load.slot(), Optional.of(value), Optional.of(value)));
        } else if (instruction instanceof StoreGlobal store) {
          RuntimeValue value = storageInput(dataStack, store.opcode());
          RuntimeValue previous = globalValues.store(store.slot(), value);
          dataStack.removeLast();
          storageTrace =
              Optional.of(
                  new StorageTraceState(store.slot(), Optional.of(previous), Optional.of(value)));
        } else if (instruction instanceof StoreLocal store) {
          RuntimeValue value = storageInput(dataStack, store.opcode());
          RuntimeValue previous = frame.localValues.store(store.slot(), value);
          dataStack.removeLast();
          storageTrace =
              Optional.of(
                  new StorageTraceState(store.slot(), Optional.of(previous), Optional.of(value)));
        } else if (instruction instanceof Call call) {
          var builtin = program.findBuiltinWord(call.symbolId());
          if (builtin.isPresent()) {
            try {
              instructionOutput =
                  builtins.execute(
                      builtin.get(),
                      dataStack,
                      call.span(),
                      call.stackEffect(),
                      call.logicalConnection(),
                      call.workspace());
            } catch (ProgramTermination termination) {
              programExitCode = OptionalInt.of(termination.exitCode());
              callStack.clear();
            } catch (RuntimeFailure failure) {
              emitFailedCapabilityTrace(
                  context,
                  tracing,
                  ++sequence,
                  currentWord,
                  call,
                  dataBefore,
                  dataStack,
                  depthBefore,
                  callStack,
                  controlBefore,
                  builtins.effect());
              throw failure;
            } catch (RuntimeException failure) {
              emitFailedCapabilityTrace(
                  context,
                  tracing,
                  ++sequence,
                  currentWord,
                  call,
                  dataBefore,
                  dataStack,
                  depthBefore,
                  callStack,
                  controlBefore,
                  builtins.effect());
              throw failure;
            }
            instructionEffect = builtins.effect();
          } else {
            IrWord targetWord =
                program
                    .findUserWord(call.symbolId())
                    .orElseThrow(
                        () -> new IllegalStateException("unknown symbol: " + call.symbolId()));
            if (callStack.size() >= RuntimeLimits.CALL_STACK_FRAMES) {
              throw callStackLimit(program, callStack, call, targetWord.name());
            }
            callStack.add(new Frame(targetWord));
          }
        } else if (instruction instanceof Jump jump) {
          frame.discardLoopStates(
              jump.countedLoopStatesToDiscard(), jump.arrayLoopStatesToDiscard());
          frame.jumpTo(jump.targetIndex());
          if (tracing) {
            controlTarget = Optional.of(formatTarget(currentWord, jump.targetIndex()));
            branchTaken = Optional.of(true);
          }
        } else if (instruction instanceof BranchIfFalse branch) {
          boolean taken = !((BooleanValue) dataStack.removeLast()).value();
          if (taken) {
            frame.jumpTo(branch.targetIndex());
          }
          if (tracing) {
            controlTarget = Optional.of(formatTarget(currentWord, branch.targetIndex()));
            branchTaken = Optional.of(taken);
          }
        } else if (instruction instanceof CountedLoopStart start) {
          BigInteger count = ((IntegerValue) dataStack.getLast()).value();
          if (count.signum() < 0) {
            throw negativeRepeatCount(program, start, count);
          }
          dataStack.removeLast();
          boolean taken = count.signum() == 0;
          if (taken) {
            frame.jumpTo(start.exitTargetIndex());
          } else {
            frame.startCountedLoop(count);
          }
          if (tracing) {
            controlTarget = Optional.of(formatTarget(currentWord, start.exitTargetIndex()));
            branchTaken = Optional.of(taken);
          }
        } else if (instruction instanceof CountedLoopNext next) {
          boolean taken = frame.advanceCountedLoop();
          if (taken) {
            frame.jumpTo(next.bodyTargetIndex());
          }
          if (tracing) {
            controlTarget = Optional.of(formatTarget(currentWord, next.bodyTargetIndex()));
            branchTaken = Optional.of(taken);
          }
        } else if (instruction instanceof ArrayLoopStart start) {
          ArrayValue array = (ArrayValue) dataStack.getLast();
          boolean taken = array.size() == 0;
          if (taken) {
            dataStack.removeLast();
            frame.jumpTo(start.exitTargetIndex());
          } else {
            budget.beforeArrayWork(0, 1, start.span(), "iterate");
            dataStack.removeLast();
            frame.startArrayLoop(array);
            dataStack.add(array.get(0));
          }
          if (tracing) {
            controlTarget = Optional.of(formatTarget(currentWord, start.exitTargetIndex()));
            branchTaken = Optional.of(taken);
          }
        } else if (instruction instanceof ArrayLoopNext next) {
          boolean taken = frame.hasNextArrayElement();
          if (taken) {
            budget.beforeArrayWork(0, 1, next.span(), "iterate");
            dataStack.add(frame.advanceArrayLoop());
            frame.jumpTo(next.bodyTargetIndex());
          } else {
            frame.finishArrayLoop();
          }
          if (tracing) {
            controlTarget = Optional.of(formatTarget(currentWord, next.bodyTargetIndex()));
            branchTaken = Optional.of(taken);
          }
        } else if (instruction instanceof Return) {
          callStack.removeLast();
          startNextStartupWord = callStack.isEmpty() && startupWordIndex + 1 < startupWords.size();
        } else {
          throw new IllegalStateException("unknown IR instruction: " + instruction);
        }

        if (tracing) {
          context
              .trace()
              .accept(
                  new TraceEvent(
                      ++sequence,
                      currentWord,
                      instruction.opcode(),
                      instruction.span().start().line(),
                      instruction.span().start().column(),
                      dataBefore,
                      dataStack,
                      depthBefore,
                      callStack.size(),
                      instructionOutput,
                      instruction instanceof Call call ? call.particles() : List.of(),
                      controlBefore,
                      controlSnapshot(callStack),
                      controlTarget,
                      branchTaken,
                      storageTrace,
                      instructionEffect));
        }
        if (programExitCode.isPresent()) {
          return new ExecutionResult(
              java.util.Optional.empty(),
              dataStack,
              budget.executed(),
              output.writtenBytes(),
              globalValues.snapshot(),
              budget.arrayConstructionUnits(),
              budget.arrayElementOperationUnits(),
              budget.regexWorkUnits(),
              budget.jsonConstructionUnits(),
              budget.jsonWorkUnits(),
              budget.byteSequenceConstructionBytes(),
              budget.byteSequenceWorkBytes(),
              budget.httpMetadataConstructionBytes(),
              budget.httpSendCalls(),
              budget.httpRequestAttemptBytes(),
              budget.httpResponseReceivedBytes(),
              budget.fileOperations(),
              budget.fileReadBytes(),
              budget.fileWriteAttemptBytes(),
              budget.delimitedTextWorkUnits(),
              ExecutionTermination.programExit(programExitCode.getAsInt()),
              builtins.errorOutputBytes());
        }
        if (startNextStartupWord) {
          callStack.add(new Frame(startupWords.get(++startupWordIndex)));
        }
      }
      return new ExecutionResult(
          java.util.Optional.empty(),
          dataStack,
          budget.executed(),
          output.writtenBytes(),
          globalValues.snapshot(),
          budget.arrayConstructionUnits(),
          budget.arrayElementOperationUnits(),
          budget.regexWorkUnits(),
          budget.jsonConstructionUnits(),
          budget.jsonWorkUnits(),
          budget.byteSequenceConstructionBytes(),
          budget.byteSequenceWorkBytes(),
          budget.httpMetadataConstructionBytes(),
          budget.httpSendCalls(),
          budget.httpRequestAttemptBytes(),
          budget.httpResponseReceivedBytes(),
          budget.fileOperations(),
          budget.fileReadBytes(),
          budget.fileWriteAttemptBytes(),
          budget.delimitedTextWorkUnits(),
          ExecutionTermination.completed(),
          builtins.errorOutputBytes());
    } catch (RuntimeFailure failure) {
      return new ExecutionResult(
          java.util.Optional.of(withRuntimeContext(failure.diagnostic(), dataStack, callStack)),
          dataStack,
          budget.executed(),
          output.writtenBytes(),
          globalValues.snapshot(),
          budget.arrayConstructionUnits(),
          budget.arrayElementOperationUnits(),
          budget.regexWorkUnits(),
          budget.jsonConstructionUnits(),
          budget.jsonWorkUnits(),
          budget.byteSequenceConstructionBytes(),
          budget.byteSequenceWorkBytes(),
          budget.httpMetadataConstructionBytes(),
          budget.httpSendCalls(),
          budget.httpRequestAttemptBytes(),
          budget.httpResponseReceivedBytes(),
          budget.fileOperations(),
          budget.fileReadBytes(),
          budget.fileWriteAttemptBytes(),
          budget.delimitedTextWorkUnits(),
          ExecutionTermination.diagnosticFailure(),
          builtins.errorOutputBytes());
    }
  }

  private static Diagnostic withRuntimeContext(
      Diagnostic original, List<RuntimeValue> dataStack, List<Frame> callStack) {
    var builder =
        Diagnostic.builder(
            original.code(),
            original.severity(),
            original.stage(),
            original.sourcePath(),
            original.location());
    original.fields().forEach(builder::field);
    if (!original.fields().containsKey("currentWord")) {
      String currentWord = callStack.isEmpty() ? "メイン終了後" : callStack.getLast().word.name();
      builder.field("currentWord", currentWord);
    }
    builder.field("dataStack", safeDataStack(original, dataStack));
    builder.field("callStack", safeCallStack(callStack));
    original.expected().ifPresent(builder::expected);
    original.actual().ifPresent(builder::actual);
    original.relatedLocations().forEach(builder::relatedLocation);
    original.fixes().forEach(builder::fix);
    if (original.limitName().isPresent()) {
      builder.limit(
          original.limitName().orElseThrow(),
          original.limit().orElseThrow(),
          original.observed().orElseThrow());
    }
    return builder.build();
  }

  private static void emitFailedCapabilityTrace(
      ExecutionContext context,
      boolean tracing,
      long sequence,
      String currentWord,
      Call call,
      List<RuntimeValue> dataBefore,
      List<RuntimeValue> dataStack,
      int depthBefore,
      List<Frame> callStack,
      List<ControlTraceState> controlBefore,
      String effect) {
    if (!tracing || effect.isEmpty()) {
      return;
    }
    context
        .trace()
        .accept(
            new TraceEvent(
                sequence,
                currentWord,
                call.opcode(),
                call.span().start().line(),
                call.span().start().column(),
                dataBefore,
                dataStack,
                depthBefore,
                callStack.size(),
                new byte[0],
                call.particles(),
                controlBefore,
                controlSnapshot(callStack),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                effect));
  }

  private static String safeValue(RuntimeValue value) {
    return safeValue(value, false);
  }

  static String safeDataStack(Diagnostic diagnostic, List<RuntimeValue> dataStack) {
    return java.util.stream.IntStream.range(0, dataStack.size())
        .mapToObj(
            index ->
                safeValue(
                    dataStack.get(index),
                    isSensitiveOperationInput(diagnostic, index, dataStack.size())))
        .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
  }

  private static String safeValue(RuntimeValue value, boolean forceRedaction) {
    if (forceRedaction
        || containsSensitiveType(value.type())
        || value instanceof JsonRuntimeValue
        || value instanceof ArrayValue array
            && array.elementType() == jp.bsb.stdlib.ScalarType.JSON) {
      return value.type().sourceName() + ":<redacted>";
    }
    String escaped =
        value
            .displayText()
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    int end =
        escaped.offsetByCodePoints(0, Math.min(32, escaped.codePointCount(0, escaped.length())));
    String shortened = escaped.substring(0, end);
    if (end < escaped.length()) {
      shortened += "…";
    }
    return value.type().sourceName() + ":" + shortened;
  }

  private static boolean isSensitiveOperationInput(
      Diagnostic diagnostic, int index, int stackSize) {
    String word = diagnostic.fields().getOrDefault("word", "");
    if (word.equals("ファイルへ書く")) {
      return index >= stackSize - 2;
    }
    if (index != stackSize - 1) return false;
    return word.equals("JSONを解析して結果を返す")
        || word.equals("CSVを表として解析する")
        || word.equals("TSVを表として解析する")
        || word.equals("表をCSVに変換する")
        || word.equals("表をTSVに変換する")
        || word.equals("文字列をUTF8バイト列に変換する")
        || word.equals("Base64文字列をバイト列に変換して結果を返す")
        || word.equals("ファイルを読む");
  }

  private static boolean containsSensitiveType(jp.bsb.stdlib.ValueType root) {
    var work = new java.util.ArrayDeque<jp.bsb.stdlib.ValueType>();
    work.push(root);
    while (!work.isEmpty()) {
      jp.bsb.stdlib.ValueType type = work.pop();
      if (type instanceof jp.bsb.stdlib.ResultType
          || type.equals(jp.bsb.stdlib.ValueType.JSON_PARSE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.BYTE_SEQUENCE)
          || type.equals(jp.bsb.stdlib.ValueType.UTF8_DECODE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.BASE64_DECODE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.FILE_READ_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.FILE_WRITE_FAILURE)
          || type.equals(jp.bsb.stdlib.ValueType.DELIMITED_TEXT_PARSE_FAILURE)) {
        return true;
      }
      if (type instanceof jp.bsb.stdlib.OptionalType optional) {
        work.push(optional.elementType());
      }
    }
    return false;
  }

  private static String safeCallStack(List<Frame> callStack) {
    int first = Math.max(0, callStack.size() - 16);
    String suffix =
        callStack.subList(first, callStack.size()).stream()
            .map(frame -> frame.word.name())
            .collect(java.util.stream.Collectors.joining(" → "));
    return first == 0 ? suffix : "… → " + suffix;
  }

  private static void push(
      ArrayList<RuntimeValue> stack, PushConst instruction, String sourcePath, String currentWord)
      throws RuntimeFailure {
    pushValue(stack, instruction.value(), instruction.span(), sourcePath, currentWord);
  }

  private static void pushValue(
      ArrayList<RuntimeValue> stack,
      RuntimeValue value,
      SourceSpan span,
      String sourcePath,
      String currentWord)
      throws RuntimeFailure {
    requirePushCapacity(stack, span, sourcePath, currentWord);
    stack.add(value);
  }

  private static void requirePushCapacity(
      ArrayList<RuntimeValue> stack, SourceSpan span, String sourcePath, String currentWord)
      throws RuntimeFailure {
    if (stack.size() >= RuntimeLimits.DATA_STACK_VALUES) {
      Diagnostic diagnostic =
          Diagnostic.builder(
                  DiagnosticCode.E_DATA_STACK_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", currentWord)
              .limit("dataStackValues", RuntimeLimits.DATA_STACK_VALUES, (long) stack.size() + 1)
              .expected(RuntimeLimits.DATA_STACK_VALUES + "値以下")
              .actual((stack.size() + 1) + "値目")
              .fix("同時に保持する値を減らしてください。")
              .build();
      throw new RuntimeFailure(diagnostic);
    }
  }

  private static RuntimeValue storageInput(ArrayList<RuntimeValue> dataStack, String opcode) {
    if (dataStack.isEmpty()) {
      throw new IllegalStateException(opcode + " requires one data stack value");
    }
    return dataStack.getLast();
  }

  private static RuntimeFailure callStackLimit(
      IrProgram program, ArrayList<Frame> callStack, Call call, String targetName) {
    String history =
        callStack.stream()
            .map(frame -> frame.word.name())
            .collect(java.util.stream.Collectors.joining(" → "));
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_CALL_STACK_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                program.sourcePath(),
                call.span())
            .field("word", targetName)
            .field("callHistory", history)
            .limit("callStackFrames", RuntimeLimits.CALL_STACK_FRAMES, (long) callStack.size() + 1)
            .expected(RuntimeLimits.CALL_STACK_FRAMES + "フレーム以下")
            .actual((callStack.size() + 1) + "フレーム目")
            .fix("再帰呼出しに終了条件があるか確認してください。")
            .build();
    return new RuntimeFailure(diagnostic);
  }

  private static RuntimeFailure negativeRepeatCount(
      IrProgram program, CountedLoopStart instruction, BigInteger count) {
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_NEGATIVE_REPEAT_COUNT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                program.sourcePath(),
                instruction.span())
            .field("count", count.toString())
            .expected("0以上")
            .actual(count.toString())
            .fix("反復回数を0以上にしてください")
            .build();
    return new RuntimeFailure(diagnostic);
  }

  private static String formatTarget(String word, int targetIndex) {
    return word + ":" + (targetIndex + 1);
  }

  /** 呼出し元の停止中ループも含め、外側から内側の順に状態を複製します。 */
  private static List<ControlTraceState> controlSnapshot(List<Frame> callStack) {
    var result = new ArrayList<ControlTraceState>();
    for (Frame frame : callStack) {
      for (LoopState state : frame.loopStates) {
        result.add(state.snapshot());
      }
    }
    return List.copyOf(result);
  }

  /** Java呼出しスタックの代わりに、現在の単語、次の命令位置、ループ状態を保持します。 */
  private static final class Frame {
    private final IrWord word;
    private final StorageArea localValues;
    private final ArrayList<LoopState> loopStates = new ArrayList<>();
    private int instructionIndex;

    private Frame(IrWord word) {
      this.word = word;
      localValues = new StorageArea(word.localSlots(), BindingStorage.LOCAL);
    }

    private IrInstruction nextInstruction() {
      if (instructionIndex >= word.instructions().size()) {
        throw new IllegalStateException("IR word reached its end without Return: " + word.name());
      }
      return word.instructions().get(instructionIndex);
    }

    private void advance() {
      instructionIndex++;
    }

    private void jumpTo(int targetIndex) {
      instructionIndex = targetIndex;
    }

    private void startCountedLoop(BigInteger count) {
      loopStates.add(new CountedLoopState(count, count));
    }

    private boolean advanceCountedLoop() {
      if (loopStates.isEmpty() || !(loopStates.getLast() instanceof CountedLoopState state)) {
        throw new IllegalStateException("CountedLoopNext has no active loop state");
      }
      if (state.remaining().equals(BigInteger.ONE)) {
        loopStates.removeLast();
        return false;
      }
      loopStates.set(
          loopStates.size() - 1,
          new CountedLoopState(state.remaining().subtract(BigInteger.ONE), state.initial()));
      return true;
    }

    private void startArrayLoop(ArrayValue array) {
      if (array.size() == 0) {
        throw new IllegalStateException("an empty array must not create a loop state");
      }
      loopStates.add(new ArrayLoopState(array, 0));
    }

    private boolean hasNextArrayElement() {
      ArrayLoopState state = currentArrayLoop();
      return state.index() + 1 < state.array().size();
    }

    private RuntimeValue advanceArrayLoop() {
      ArrayLoopState state = currentArrayLoop();
      int nextIndex = state.index() + 1;
      if (nextIndex >= state.array().size()) {
        throw new IllegalStateException("ArrayLoopNext has no next element");
      }
      var next = new ArrayLoopState(state.array(), nextIndex);
      loopStates.set(loopStates.size() - 1, next);
      return next.array().get(next.index());
    }

    private void finishArrayLoop() {
      currentArrayLoop();
      loopStates.removeLast();
    }

    private ArrayLoopState currentArrayLoop() {
      if (loopStates.isEmpty() || !(loopStates.getLast() instanceof ArrayLoopState state)) {
        throw new IllegalStateException("ArrayLoopNext has no active array-loop state");
      }
      return state;
    }

    private void discardLoopStates(int countedCount, int arrayCount) {
      int total = countedCount + arrayCount;
      if (total > loopStates.size()) {
        throw new IllegalStateException("Jump discards more loop states than are active");
      }
      int countedObserved = 0;
      int arrayObserved = 0;
      for (int index = loopStates.size() - total; index < loopStates.size(); index++) {
        if (loopStates.get(index) instanceof CountedLoopState) {
          countedObserved++;
        } else {
          arrayObserved++;
        }
      }
      if (countedObserved != countedCount || arrayObserved != arrayCount) {
        throw new IllegalStateException("Jump discards different loop-state kinds than requested");
      }
      for (int index = 0; index < total; index++) {
        loopStates.removeLast();
      }
    }
  }

  /** 実行中だけ更新され、トレースには不変コピーを渡すループ状態です。 */
  private sealed interface LoopState permits ArrayLoopState, CountedLoopState {
    ControlTraceState snapshot();
  }

  private record CountedLoopState(BigInteger remaining, BigInteger initial) implements LoopState {
    @Override
    public CountedLoopTraceState snapshot() {
      return new CountedLoopTraceState(remaining, initial);
    }
  }

  private record ArrayLoopState(ArrayValue array, int index) implements LoopState {
    @Override
    public ArrayLoopTraceState snapshot() {
      return new ArrayLoopTraceState(index, array.size());
    }
  }
}
