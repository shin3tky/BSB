package jp.bsb.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.BuiltinWord;
import jp.bsb.stdlib.ValueType;
import jp.bsb.stdlib.ValueTypeTraits;

/** 解析器を経ずに合成されたIRも、実行前に具体型スタックと配列反復状態で検証します。 */
final class IrVerifier {
  private IrVerifier() {}

  static void validate(
      Map<SymbolId, IrWord> userWords,
      Map<SymbolId, BuiltinWord> builtinWords,
      Optional<IrWord> globalInitializer) {
    validateCallTargets(userWords, builtinWords, globalInitializer);
    globalInitializer.ifPresent(IrVerifier::validateWord);
    userWords.values().forEach(IrVerifier::validateWord);
  }

  private static void validateCallTargets(
      Map<SymbolId, IrWord> userWords,
      Map<SymbolId, BuiltinWord> builtinWords,
      Optional<IrWord> globalInitializer) {
    var words = new ArrayList<IrWord>(userWords.values());
    globalInitializer.ifPresent(words::add);
    for (IrWord word : words) {
      for (IrInstruction instruction : word.instructions()) {
        if (!(instruction instanceof Call call)) {
          continue;
        }
        IrWord userTarget = userWords.get(call.symbolId());
        BuiltinWord builtinTarget = builtinWords.get(call.symbolId());
        if (userTarget == null && builtinTarget == null) {
          throw new IllegalArgumentException("Call refers to an unknown symbol");
        }
        if (userTarget != null
            && call.stackEffect().isPresent()
            && userTarget.stackEffect().isPresent()
            && !call.stackEffect().equals(userTarget.stackEffect())) {
          throw new IllegalArgumentException("Call effect differs from its user-word target");
        }
        if (builtinTarget != null
            && call.stackEffect().isPresent()
            && !matchesBuiltinRule(builtinTarget, call.stackEffect().orElseThrow())) {
          throw new IllegalArgumentException("Call effect differs from its builtin target");
        }
        boolean logicalConnectionCheck =
            builtinTarget != null
                && builtinTarget.operation()
                    == jp.bsb.stdlib.BuiltinOperation.LOGICAL_CONNECTION_CHECK;
        boolean httpSend =
            builtinTarget != null
                && builtinTarget.operation() == jp.bsb.stdlib.BuiltinOperation.HTTP_SEND;
        boolean fileRead =
            builtinTarget != null
                && builtinTarget.operation() == jp.bsb.stdlib.BuiltinOperation.FILE_READ;
        boolean fileWrite =
            builtinTarget != null
                && builtinTarget.operation() == jp.bsb.stdlib.BuiltinOperation.FILE_WRITE;
        if ((logicalConnectionCheck || httpSend) != call.logicalConnection().isPresent()) {
          throw new IllegalArgumentException(
              "only logical connection checks may carry a logical connection reference");
        }
        if (logicalConnectionCheck
            && call.stackEffect()
                .filter(effect -> effect.inputTypes().isEmpty() && effect.outputTypes().isEmpty())
                .isEmpty()) {
          throw new IllegalArgumentException(
              "a logical connection check must have the empty effect");
        }
        if (call.logicalConnection().isPresent()
            && call.logicalConnection().orElseThrow().httpMethod().isPresent() != httpSend) {
          throw new IllegalArgumentException(
              "HTTP method presence differs from the builtin target");
        }
        if ((fileRead || fileWrite) != call.workspace().isPresent()) {
          throw new IllegalArgumentException(
              "only file operations may carry a workspace reference");
        }
        if (call.workspace().isPresent()) {
          String expectedOperation = fileRead ? "read" : "write";
          if (!call.workspace().orElseThrow().operation().equals(expectedOperation)) {
            throw new IllegalArgumentException("workspace operation differs from builtin target");
          }
        }
      }
    }
  }

  private static boolean matchesBuiltinRule(BuiltinWord word, IrStackEffect effect) {
    List<ValueType> inputs = effect.inputTypes();
    List<ValueType> outputs = effect.outputTypes();
    return switch (word.typeRule()) {
      case FIXED ->
          inputs.equals(concreteTypes(word.inputTypeNames()))
              && outputs.equals(concreteTypes(word.outputTypeNames()));
      case DISPLAYABLE ->
          inputs.size() == 1
              && ValueTypeTraits.isDisplayable(inputs.getFirst())
              && outputs.isEmpty();
      case SAME_TYPE_PAIR ->
          inputs.size() == 2
              && inputs.get(0).equals(inputs.get(1))
              && ValueTypeTraits.isEqualityComparable(inputs.getFirst())
              && outputs.equals(List.of(ValueType.BOOLEAN));
      case SAME_NUMERIC_TYPE -> matchesSameNumericRule(word, inputs, outputs);
      case INDEPENDENT_NUMERIC_INPUTS -> matchesIndependentNumericRule(word, inputs, outputs);
      case ARRAY_LENGTH ->
          inputs.size() == 1
              && inputs.getFirst() instanceof ArrayType
              && outputs.equals(List.of(ValueType.INTEGER));
      case ARRAY_GET ->
          inputs.size() == 2
              && inputs.getFirst() instanceof ArrayType array
              && inputs.get(1).equals(ValueType.INTEGER)
              && outputs.equals(List.of(array.elementType()));
      case ARRAY_SLICE ->
          inputs.size() == 3
              && inputs.getFirst() instanceof ArrayType array
              && inputs.get(1).equals(ValueType.INTEGER)
              && inputs.get(2).equals(ValueType.INTEGER)
              && outputs.equals(List.of(array));
      case ARRAY_REPLACE ->
          inputs.size() == 3
              && inputs.getFirst() instanceof ArrayType array
              && inputs.get(1).equals(ValueType.INTEGER)
              && inputs.get(2).equals(array.elementType())
              && outputs.equals(List.of(array));
      case ARRAY_APPEND ->
          inputs.size() == 2
              && inputs.getFirst() instanceof ArrayType array
              && inputs.get(1).equals(array.elementType())
              && outputs.equals(List.of(array));
      case OPTIONAL_WRAP ->
          inputs.size() == 1 && outputs.equals(List.of(ValueType.optionalOf(inputs.getFirst())));
      case OPTIONAL_PREDICATE ->
          inputs.size() == 1
              && inputs.getFirst().isOptional()
              && outputs.equals(List.of(inputs.getFirst(), ValueType.BOOLEAN));
      case OPTIONAL_UNWRAP ->
          inputs.size() == 1
              && inputs.getFirst().isOptional()
              && outputs.equals(List.of(inputs.getFirst().optionalElementType().orElseThrow()));
      case OPTIONAL_DROP ->
          inputs.size() == 1 && inputs.getFirst().isOptional() && outputs.isEmpty();
      case RESULT_SUCCESS_WRAP ->
          inputs.size() == 1
              && outputs.size() == 1
              && outputs.getFirst().isResult()
              && outputs.getFirst().resultSuccessType().orElseThrow().equals(inputs.getFirst());
      case RESULT_FAILURE_WRAP ->
          inputs.size() == 1
              && outputs.size() == 1
              && outputs.getFirst().isResult()
              && outputs.getFirst().resultFailureType().orElseThrow().equals(inputs.getFirst());
      case RESULT_PREDICATE ->
          inputs.size() == 1
              && inputs.getFirst().isResult()
              && outputs.equals(List.of(inputs.getFirst(), ValueType.BOOLEAN));
      case RESULT_SUCCESS_UNWRAP ->
          inputs.size() == 1
              && inputs.getFirst().isResult()
              && outputs.equals(List.of(inputs.getFirst().resultSuccessType().orElseThrow()));
      case RESULT_FAILURE_UNWRAP ->
          inputs.size() == 1
              && inputs.getFirst().isResult()
              && outputs.equals(List.of(inputs.getFirst().resultFailureType().orElseThrow()));
      case RESULT_DROP -> inputs.size() == 1 && inputs.getFirst().isResult() && outputs.isEmpty();
    };
  }

  private static boolean matchesSameNumericRule(
      BuiltinWord word, List<ValueType> inputs, List<ValueType> outputs) {
    if (inputs.size() != word.inputTypeNames().size() || inputs.isEmpty()) {
      return false;
    }
    ValueType numericType = inputs.getFirst();
    if (!isNumeric(numericType) || inputs.stream().anyMatch(type -> !type.equals(numericType))) {
      return false;
    }
    List<ValueType> expectedOutputs =
        word.outputTypeNames().stream()
            .map(name -> name.equals("N") ? numericType : concreteType(name))
            .toList();
    return outputs.equals(expectedOutputs);
  }

  private static boolean matchesIndependentNumericRule(
      BuiltinWord word, List<ValueType> inputs, List<ValueType> outputs) {
    if (inputs.size() != word.inputTypeNames().size()) {
      return false;
    }
    for (int index = 0; index < inputs.size(); index++) {
      String required = word.inputTypeNames().get(index);
      ValueType actual = inputs.get(index);
      if (required.equals("数値")) {
        if (!isNumeric(actual)) {
          return false;
        }
      } else if (!actual.equals(concreteType(required))) {
        return false;
      }
    }
    return outputs.equals(concreteTypes(word.outputTypeNames()));
  }

  private static boolean isNumeric(ValueType type) {
    return type.equals(ValueType.INTEGER) || type.equals(ValueType.DECIMAL);
  }

  private static ValueType concreteType(String name) {
    return ValueType.fromSourceName(name)
        .orElseThrow(() -> new IllegalArgumentException("a builtin has an unknown concrete type"));
  }

  private static List<ValueType> concreteTypes(List<String> names) {
    return names.stream().map(name -> concreteType(name)).toList();
  }

  /** 旧来の効果なし合成IRは互換維持のため制御検証だけとし、生成済みIRは全型経路を検証します。 */
  private static void validateWord(IrWord word) {
    if (word.stackEffect().isEmpty()) {
      return;
    }
    List<IrInstruction> instructions = word.instructions();
    var incoming = new VerificationState[instructions.size()];
    incoming[0] =
        new VerificationState(
            TypeStack.from(word.stackEffect().orElseThrow().inputTypes()), List.of());
    var work = new ArrayDeque<Integer>();
    work.add(0);

    while (!work.isEmpty()) {
      int index = work.removeFirst();
      VerificationState state = incoming[index];
      for (Successor successor :
          execute(index, instructions.get(index), state, instructions.size(), word)) {
        VerificationState previous = incoming[successor.index()];
        if (previous == null) {
          incoming[successor.index()] = successor.state();
          work.add(successor.index());
        } else if (!previous.equals(successor.state())) {
          throw new IllegalArgumentException(
              "IR paths must merge with identical data and array-loop states");
        }
      }
    }
  }

  private static List<Successor> execute(
      int index,
      IrInstruction instruction,
      VerificationState state,
      int instructionCount,
      IrWord word) {
    int next = index + 1;
    if (instruction instanceof PushConst push) {
      return fallthrough(next, instructionCount, state.push(push.value().type()));
    }
    if (instruction instanceof BuildArray build) {
      VerificationState result = state.buildArray(build);
      return fallthrough(next, instructionCount, result);
    }
    if (instruction instanceof LoadGlobal load) {
      return fallthrough(next, instructionCount, state.push(load.slot().valueType()));
    }
    if (instruction instanceof LoadLocal load) {
      return fallthrough(next, instructionCount, state.push(load.slot().valueType()));
    }
    if (instruction instanceof InitializeGlobal initialize) {
      return fallthrough(
          next,
          instructionCount,
          state.popExpected(initialize.slot().valueType(), initialize.opcode()));
    }
    if (instruction instanceof InitializeLocal initialize) {
      return fallthrough(
          next,
          instructionCount,
          state.popExpected(initialize.slot().valueType(), initialize.opcode()));
    }
    if (instruction instanceof StoreGlobal store) {
      return fallthrough(
          next, instructionCount, state.popExpected(store.slot().valueType(), store.opcode()));
    }
    if (instruction instanceof StoreLocal store) {
      return fallthrough(
          next, instructionCount, state.popExpected(store.slot().valueType(), store.opcode()));
    }
    if (instruction instanceof Call call) {
      IrStackEffect effect =
          call.stackEffect()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "a call in a typed IR word requires a concrete effect"));
      VerificationState result = state.apply(effect, call.opcode());
      return call.returnsNormally() ? fallthrough(next, instructionCount, result) : List.of();
    }
    if (instruction instanceof BranchIfFalse branch) {
      VerificationState result = state.popExpected(ValueType.BOOLEAN, branch.opcode());
      return branches(branch.targetIndex(), next, instructionCount, result, result);
    }
    if (instruction instanceof CountedLoopStart start) {
      VerificationState result = state.popExpected(ValueType.INTEGER, start.opcode());
      return branches(start.exitTargetIndex(), next, instructionCount, result, result);
    }
    if (instruction instanceof CountedLoopNext loopNext) {
      return branches(loopNext.bodyTargetIndex(), next, instructionCount, state, state);
    }
    if (instruction instanceof ArrayLoopStart start) {
      return state.startArrayLoop(index, start, next, instructionCount);
    }
    if (instruction instanceof ArrayLoopNext loopNext) {
      return state.advanceArrayLoop(loopNext, next, instructionCount);
    }
    if (instruction instanceof Jump jump) {
      return List.of(
          new Successor(
              jump.targetIndex(), state.discardArrayLoops(jump.arrayLoopStatesToDiscard())));
    }
    if (instruction instanceof PropagateOrReturn propagation) {
      if (state.dataStack().isEmpty()) {
        throw new IllegalArgumentException(
            propagation.opcode() + " has insufficient IR stack input");
      }
      ValueType input = state.dataStack().top();
      ValueType normalType;
      if (propagation.kind() == jp.bsb.frontend.ast.Propagation.Kind.OPTIONAL) {
        if (!input.isOptional()) {
          throw new IllegalArgumentException("optional propagation requires an optional IR input");
        }
        normalType = input.optionalElementType().orElseThrow();
      } else {
        if (!input.isResult()) {
          throw new IllegalArgumentException("result propagation requires a result IR input");
        }
        if (!input
            .resultFailureType()
            .orElseThrow()
            .equals(propagation.returnType().resultFailureType().orElseThrow())) {
          throw new IllegalArgumentException("result propagation failure types differ");
        }
        normalType = input.resultSuccessType().orElseThrow();
      }
      TypeStack prefix = state.dataStack().pop();
      TypeStack returned = prefix.push(propagation.returnType());
      if (!returned.matches(word.stackEffect().orElseThrow().outputTypes())) {
        throw new IllegalArgumentException(
            "propagation return stack differs from its IR word effect");
      }
      VerificationState normal = new VerificationState(prefix.push(normalType), state.arrayLoops());
      return fallthrough(next, instructionCount, normal);
    }
    if (instruction instanceof Return) {
      List<ValueType> expected = word.stackEffect().orElseThrow().outputTypes();
      if (!state.dataStack().matches(expected)) {
        throw new IllegalArgumentException("Return stack differs from its IR word effect");
      }
      return List.of();
    }
    throw new IllegalArgumentException("unknown IR instruction during verification");
  }

  private static List<Successor> fallthrough(
      int next, int instructionCount, VerificationState state) {
    if (next >= instructionCount) {
      throw new IllegalArgumentException("an IR path falls past the end of its word");
    }
    return List.of(new Successor(next, state));
  }

  private static List<Successor> branches(
      int target,
      int next,
      int instructionCount,
      VerificationState targetState,
      VerificationState nextState) {
    if (next >= instructionCount || target == next) {
      return List.of(new Successor(target, targetState));
    }
    return List.of(new Successor(target, targetState), new Successor(next, nextState));
  }

  private record Successor(int index, VerificationState state) {}

  private record ArrayLoopMarker(int startIndex, ValueType elementType, TypeStack baseStack) {}

  private record VerificationState(TypeStack dataStack, List<ArrayLoopMarker> arrayLoops) {
    private VerificationState {
      arrayLoops = List.copyOf(arrayLoops);
    }

    private VerificationState push(ValueType type) {
      return new VerificationState(dataStack.push(type), arrayLoops);
    }

    private VerificationState popExpected(ValueType expected, String opcode) {
      if (dataStack.isEmpty()) {
        throw new IllegalArgumentException(opcode + " has insufficient IR stack input");
      }
      ValueType actual = dataStack.top();
      if (!actual.equals(expected)) {
        throw new IllegalArgumentException(opcode + " has an invalid IR stack input type");
      }
      return new VerificationState(dataStack.pop(), arrayLoops);
    }

    private VerificationState apply(IrStackEffect effect, String opcode) {
      int inputCount = effect.inputTypes().size();
      if (dataStack.size() < inputCount) {
        throw new IllegalArgumentException(opcode + " has insufficient IR stack input");
      }
      if (!dataStack.hasSuffix(effect.inputTypes())) {
        throw new IllegalArgumentException(opcode + " has invalid concrete IR input types");
      }
      TypeStack result = dataStack.pop(inputCount);
      for (ValueType output : effect.outputTypes()) {
        result = result.push(output);
      }
      return new VerificationState(result, arrayLoops);
    }

    private VerificationState buildArray(BuildArray build) {
      int count = build.elementCount();
      if (dataStack.size() < count) {
        throw new IllegalArgumentException("BuildArray has insufficient IR stack input");
      }
      TypeStack remaining = dataStack;
      for (int index = 0; index < count; index++) {
        if (!remaining.top().equals(build.elementType())) {
          throw new IllegalArgumentException("BuildArray element type differs from its IR input");
        }
        remaining = remaining.pop();
      }
      return new VerificationState(remaining.push(new ArrayType(build.elementType())), arrayLoops);
    }

    private List<Successor> startArrayLoop(
        int index, ArrayLoopStart start, int next, int instructionCount) {
      if (dataStack.isEmpty() || !(dataStack.top() instanceof ArrayType arrayType)) {
        throw new IllegalArgumentException("ArrayLoopStart requires an array IR input");
      }
      TypeStack base = dataStack.pop();
      var bodyLoops = new ArrayList<>(arrayLoops);
      bodyLoops.add(new ArrayLoopMarker(index, arrayType.elementType(), base));
      VerificationState exitState = new VerificationState(base, arrayLoops);
      VerificationState bodyState =
          new VerificationState(base.push(arrayType.elementType()), bodyLoops);
      return branches(start.exitTargetIndex(), next, instructionCount, exitState, bodyState);
    }

    private List<Successor> advanceArrayLoop(
        ArrayLoopNext nextInstruction, int next, int instructionCount) {
      if (arrayLoops.isEmpty()) {
        throw new IllegalArgumentException("ArrayLoopNext has no active IR loop state");
      }
      ArrayLoopMarker marker = arrayLoops.getLast();
      if (nextInstruction.bodyTargetIndex() != marker.startIndex() + 1) {
        throw new IllegalArgumentException("ArrayLoopNext targets a different IR loop body");
      }
      if (!dataStack.equals(marker.baseStack())) {
        throw new IllegalArgumentException("ArrayLoopNext requires its array-loop base stack");
      }
      VerificationState bodyState = push(marker.elementType());
      VerificationState exitState = discardArrayLoops(1);
      return branches(
          nextInstruction.bodyTargetIndex(), next, instructionCount, bodyState, exitState);
    }

    private VerificationState discardArrayLoops(int count) {
      if (count > arrayLoops.size()) {
        throw new IllegalArgumentException("Jump discards more array-loop states than are active");
      }
      return new VerificationState(dataStack, arrayLoops.subList(0, arrayLoops.size() - count));
    }
  }

  /** 底側を共有し、push/popで既存経路を複製しない検証専用の永続スタックです。 */
  private static final class TypeStack {
    private static final TypeStack EMPTY = new TypeStack(null, 0);

    private final Node top;
    private final int size;

    private TypeStack(Node top, int size) {
      this.top = top;
      this.size = size;
    }

    private static TypeStack from(List<ValueType> types) {
      TypeStack result = EMPTY;
      for (ValueType type : types) {
        result = result.push(type);
      }
      return result;
    }

    private TypeStack push(ValueType type) {
      return new TypeStack(new Node(type, top), size + 1);
    }

    private TypeStack pop() {
      if (top == null) {
        throw new IllegalStateException("cannot pop an empty type stack");
      }
      return top.previous() == null ? EMPTY : new TypeStack(top.previous(), size - 1);
    }

    private TypeStack pop(int count) {
      TypeStack result = this;
      for (int index = 0; index < count; index++) {
        result = result.pop();
      }
      return result;
    }

    private ValueType top() {
      if (top == null) {
        throw new IllegalStateException("an empty type stack has no top");
      }
      return top.type();
    }

    private int size() {
      return size;
    }

    private boolean isEmpty() {
      return size == 0;
    }

    private boolean hasSuffix(List<ValueType> suffix) {
      if (suffix.size() > size) {
        return false;
      }
      Node current = top;
      for (int index = suffix.size() - 1; index >= 0; index--) {
        if (!current.type().equals(suffix.get(index))) {
          return false;
        }
        current = current.previous();
      }
      return true;
    }

    private boolean matches(List<ValueType> types) {
      return size == types.size() && hasSuffix(types);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof TypeStack stack) || size != stack.size) {
        return false;
      }
      Node left = top;
      Node right = stack.top;
      while (left != right) {
        if (left == null || right == null || !left.type().equals(right.type())) {
          return false;
        }
        left = left.previous();
        right = right.previous();
      }
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      for (Node current = top; current != null; current = current.previous()) {
        result = 31 * result + current.type().hashCode();
      }
      return result;
    }

    private record Node(ValueType type, Node previous) {}
  }
}
