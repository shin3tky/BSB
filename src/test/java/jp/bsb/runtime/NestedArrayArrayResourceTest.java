package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.BuildArray;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrStackEffect;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.SymbolId;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

/** NARRAY-R001〜NARRAY-R004の境界、共有行の計数、失敗原子性を対象部品へ直接渡して検証します。 */
class NestedArrayArrayResourceTest {
  private static final String SOURCE_PATH = "NestedArray-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final ArrayType INTEGER_ROW_TYPE = new ArrayType(ScalarType.INTEGER);

  @Test
  void acceptsExactlyOneMillionLogicalLeavesAndCountsSharedRowsPerOccurrence() {
    ArrayValue sharedRow = row(1_000);
    ArrayValue matrix = matrix(Collections.nCopies(1_000, sharedRow));

    assertEquals(ArrayLimits.MAX_NESTED_LEAF_ELEMENTS, matrix.logicalLeafCount());
    assertSame(sharedRow, matrix.get(0));
    assertSame(sharedRow, matrix.get(999));
  }

  @Test
  void appendRejectsTheNextLogicalLeafWithoutChangingInputsOrBudgets() {
    ArrayValue maximum = matrix(Collections.nCopies(1_000, row(1_000)));
    ArrayValue extra = row(1);
    var stack = stack(maximum, extra);
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("配列の末尾へ追加する", stack, budget));

    assertNestedLimit(failure.diagnostic(), "append", 1_000_001L);
    assertEquals(List.of(maximum, extra), stack);
    assertEquals(0, budget.arrayConstructionUnits());
    assertEquals(0, budget.arrayElementOperationUnits());
  }

  @Test
  void replacementUsesOldTotalMinusOldRowPlusNewRowAtomically() {
    ArrayValue maximum = matrix(Collections.nCopies(1_000, row(1_000)));
    ArrayValue replacement = row(1_001);
    IntegerValue index = integer(0);
    var stack = stack(maximum, index, replacement);
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("配列の要素を置き換える", stack, budget));

    assertNestedLimit(failure.diagnostic(), "replace", 1_000_001L);
    assertEquals(List.of(maximum, index, replacement), stack);
    assertEquals(0, budget.arrayConstructionUnits());
    assertEquals(0, budget.arrayElementOperationUnits());
  }

  @Test
  void outerSliceCountsOnlyRowsInTheSelectedHalfOpenRange() throws Exception {
    ArrayValue matrix = matrix(List.of(row(3), row(0), row(2)));
    var stack = stack(matrix, integer(1), integer(3));
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    execute("配列の一部を取り出す", stack, budget);

    ArrayValue result = (ArrayValue) stack.getFirst();
    assertEquals(2, result.size());
    assertEquals(2, result.logicalLeafCount());
    assertEquals(0, budget.arrayConstructionUnits());
    assertEquals(2, budget.arrayElementOperationUnits());
  }

  @Test
  void buildArrayRejectsOverLimitBeforeConsumingRowsOrChargingBudgets() {
    ArrayValue row = row(1_001);
    var instructions = new ArrayList<IrInstruction>();
    for (int index = 0; index < 1_000; index++) {
      instructions.add(new PushConst(row, SPAN));
    }
    instructions.add(new BuildArray(INTEGER_ROW_TYPE, 1_000, SPAN));
    instructions.add(new Return(SPAN));
    var main = new SymbolId(0);
    var word =
        new IrWord(
            main,
            "メイン",
            instructions,
            List.of(),
            new IrStackEffect(List.of(), List.of(new ArrayType(INTEGER_ROW_TYPE))));
    var program =
        new IrProgram(SOURCE_PATH, main, Map.of(main, word), Map.of(), instructions.size());

    ExecutionResult result =
        new Interpreter()
            .execute(
                program, new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));

    assertTrue(result.diagnostic().isPresent());
    assertNestedLimit(result.diagnostic().orElseThrow(), "literal", 1_001_000L);
    assertEquals(1_000, result.finalDataStack().size());
    assertTrue(result.finalDataStack().stream().allMatch(value -> value == row));
    assertEquals(0, result.arrayConstructionUnits());
    assertEquals(0, result.arrayElementOperationUnits());
  }

  @Test
  void buildArrayAcceptsTheExactLogicalLeafLimitAndChargesOnlyOuterReferences() {
    ArrayValue row = row(1_000);
    var instructions = new ArrayList<IrInstruction>();
    for (int index = 0; index < 1_000; index++) {
      instructions.add(new PushConst(row, SPAN));
    }
    instructions.add(new BuildArray(INTEGER_ROW_TYPE, 1_000, SPAN));
    instructions.add(new Return(SPAN));
    var main = new SymbolId(0);
    var word =
        new IrWord(
            main,
            "メイン",
            instructions,
            List.of(),
            new IrStackEffect(List.of(), List.of(new ArrayType(INTEGER_ROW_TYPE))));
    var program =
        new IrProgram(SOURCE_PATH, main, Map.of(main, word), Map.of(), instructions.size());

    ExecutionResult result =
        new Interpreter()
            .execute(
                program, new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));

    assertTrue(result.diagnostic().isEmpty());
    ArrayValue matrix = (ArrayValue) result.finalDataStack().getFirst();
    assertEquals(ArrayLimits.MAX_NESTED_LEAF_ELEMENTS, matrix.logicalLeafCount());
    assertEquals(1_000, result.arrayConstructionUnits());
    assertEquals(1_000, result.arrayElementOperationUnits());
  }

  @Test
  void arrayValueEnforcesTheOuterReferenceBoundaryEvenForEmptyRows() {
    ArrayValue emptyRow = row(0);
    assertEquals(
        ArrayLimits.MAX_LENGTH,
        matrix(Collections.nCopies(ArrayLimits.MAX_LENGTH, emptyRow)).size());

    assertThrows(
        IllegalArgumentException.class,
        () -> matrix(Collections.nCopies(ArrayLimits.MAX_LENGTH + 1, emptyRow)));
  }

  @Test
  void nestedEqualityReservesOuterAndLeafCandidatesBeforeComparing() throws Exception {
    ArrayValue first = matrix(List.of(row(1)));
    ArrayValue second = matrix(List.of(row(1)));
    var acceptedBudget = budget(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 2);
    var acceptedStack = stack(first, second);

    execute("等しい", acceptedStack, acceptedBudget);

    assertEquals(List.of(new BooleanValue(true)), acceptedStack);
    assertEquals(
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, acceptedBudget.arrayElementOperationUnits());

    var rejectedBudget = budget(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 1);
    var rejectedStack = stack(first, second);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("等しい", rejectedStack, rejectedBudget));
    assertEquals(DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT, failure.diagnostic().code());
    assertEquals(List.of(first, second), rejectedStack);
    assertEquals(
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 1, rejectedBudget.arrayElementOperationUnits());
  }

  @Test
  void nestedEqualityRejectsDifferentRowLengthsBeforeReservingWork() throws Exception {
    ArrayValue first = matrix(List.of(row(1)));
    ArrayValue second = matrix(List.of(row(2)));
    var budget = budget(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS);
    var stack = stack(first, second);

    execute("等しい", stack, budget);

    assertEquals(List.of(new BooleanValue(false)), stack);
    assertEquals(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, budget.arrayElementOperationUnits());
  }

  @Test
  void nestedDisplayChecksOutputThenReservesOuterAndLeafCandidatesAtomically() throws Exception {
    ArrayValue value = matrix(List.of(row(1)));
    var acceptedBudget = budget(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 2);
    var acceptedStack = stack(value);
    var acceptedSink = new MemoryOutputSink();
    new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, acceptedSink), acceptedBudget)
        .execute(BuiltinDictionary.find("一行表示する").orElseThrow(), acceptedStack, SPAN);
    assertEquals("【【0】】\n", acceptedSink.utf8Text());
    assertEquals(
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, acceptedBudget.arrayElementOperationUnits());

    var rejectedBudget = budget(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 1);
    var rejectedStack = stack(value);
    var rejectedSink = new MemoryOutputSink();
    RuntimeFailure budgetFailure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                new BuiltinExecutor(
                        SOURCE_PATH, new BoundedOutput(SOURCE_PATH, rejectedSink), rejectedBudget)
                    .execute(BuiltinDictionary.find("一行表示する").orElseThrow(), rejectedStack, SPAN));
    assertEquals(DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT, budgetFailure.diagnostic().code());
    assertEquals(List.of(value), rejectedStack);
    assertEquals("", rejectedSink.utf8Text());
    assertEquals(
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 1, rejectedBudget.arrayElementOperationUnits());

    var outputBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var outputStack = stack(value);
    RuntimeFailure outputFailure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                new BuiltinExecutor(
                        SOURCE_PATH,
                        new BoundedOutput(
                            SOURCE_PATH,
                            new MemoryOutputSink(),
                            RuntimeLimits.OUTPUT_UTF8_BYTES - 1),
                        outputBudget)
                    .execute(BuiltinDictionary.find("一行表示する").orElseThrow(), outputStack, SPAN));
    assertEquals(DiagnosticCode.E_OUTPUT_LIMIT, outputFailure.diagnostic().code());
    assertEquals(List.of(value), outputStack);
    assertEquals(0, outputBudget.arrayElementOperationUnits());
  }

  @Test
  void displayPreflightMeasuresTopLevelTextWithoutArrayLiteralDelimiters() throws Exception {
    var stack = stack(new StringValue("x"));
    var sink = new MemoryOutputSink();
    new BuiltinExecutor(
            SOURCE_PATH,
            new BoundedOutput(SOURCE_PATH, sink, RuntimeLimits.OUTPUT_UTF8_BYTES - 2),
            new ExecutionBudget(SOURCE_PATH, () -> 0L))
        .execute(BuiltinDictionary.find("一行表示する").orElseThrow(), stack, SPAN);

    assertEquals("x\n", sink.utf8Text());
    assertEquals(List.of(), stack);
  }

  @Test
  void nestedJsonLeavesInheritJsonDisplayAndEqualityWork() throws Exception {
    ArrayValue jsonRow =
        new ArrayValue(ScalarType.JSON, List.of(new JsonRuntimeValue(new JsonString("x"))));
    ArrayValue first = new ArrayValue(new ArrayType(ScalarType.JSON), List.of(jsonRow));
    ArrayValue second = new ArrayValue(new ArrayType(ScalarType.JSON), List.of(jsonRow));

    var displayBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var displayStack = stack(first);
    execute("一行表示する", displayStack, displayBudget);
    assertEquals(2, displayBudget.arrayElementOperationUnits());
    assertEquals(4, displayBudget.jsonWorkUnits());

    var equalityBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var equalityStack = stack(first, second);
    execute("等しい", equalityStack, equalityBudget);
    assertEquals(List.of(new BooleanValue(true)), equalityStack);
    assertEquals(2, equalityBudget.arrayElementOperationUnits());
    assertEquals(2, equalityBudget.jsonWorkUnits());
  }

  private static void assertNestedLimit(Diagnostic diagnostic, String operation, long observed) {
    assertEquals(DiagnosticCode.E_ARRAY_NESTED_ELEMENT_LIMIT, diagnostic.code());
    assertEquals(operation, diagnostic.fields().get("operation"));
    assertEquals("arrayNestedLeafElements", diagnostic.limitName().orElseThrow());
    assertEquals("1000000", diagnostic.limit().orElseThrow());
    assertEquals(Long.toString(observed), diagnostic.observed().orElseThrow());
    assertEquals("1000000個以下", diagnostic.expected().orElseThrow());
    assertEquals(observed + "個", diagnostic.actual().orElseThrow());
    assertEquals(List.of("行数または各行の要素数を減らしてください。"), diagnostic.fixes());
  }

  private static ArrayValue matrix(List<ArrayValue> rows) {
    return new ArrayValue(INTEGER_ROW_TYPE, new ArrayList<>(rows));
  }

  private static ArrayValue row(int size) {
    return new ArrayValue(
        ScalarType.INTEGER, Collections.nCopies(size, new IntegerValue(BigInteger.ZERO)));
  }

  private static IntegerValue integer(int value) {
    return new IntegerValue(BigInteger.valueOf(value));
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static void execute(
      String builtinName, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    var output = new BoundedOutput(SOURCE_PATH, new MemoryOutputSink());
    new BuiltinExecutor(SOURCE_PATH, output, budget)
        .execute(BuiltinDictionary.find(builtinName).orElseThrow(), stack, SPAN);
  }

  private static ExecutionBudget budget(long operations) {
    return new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, 0, operations);
  }
}
