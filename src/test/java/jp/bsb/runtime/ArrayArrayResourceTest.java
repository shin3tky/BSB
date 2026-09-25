package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

/** ARRAY-R001〜ARRAY-R004の受理側と1単位超過側を、対象部品へ直接渡して検証します。 */
class ArrayArrayResourceTest {
  private static final String SOURCE_PATH = "arrays-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void arrayR001Accepts65536LiteralElementsAndRejectsThe65537thStatically() {
    var output = new MemoryOutputSink();
    var accepted =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                arrayLiteralSource(ArrayLimits.MAX_LENGTH).getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));
    var rejected =
        new SourceChecker()
            .check(
                SOURCE_PATH,
                arrayLiteralSource(ArrayLimits.MAX_LENGTH + 1).getBytes(StandardCharsets.UTF_8));

    assertTrue(accepted.successful(), accepted.diagnostics().toString());
    assertEquals("65536\n", output.utf8Text());
    assertEquals(ArrayLimits.MAX_LENGTH, accepted.arrayConstructionUnits());
    assertEquals((long) ArrayLimits.MAX_LENGTH + 1, accepted.arrayElementOperationUnits());
    assertEquals(8, rejected.exitCode());
    assertEquals(DiagnosticCode.E_ARRAY_LENGTH_LIMIT, rejected.diagnostics().getFirst().code());
    assertEquals("65536", rejected.diagnostics().getFirst().limit().orElseThrow());
    assertEquals("65537", rejected.diagnostics().getFirst().observed().orElseThrow());
    assertEquals("literal", rejected.diagnostics().getFirst().fields().get("operation"));
  }

  @Test
  void arrayR002AcceptsDynamicLength65536AndRejects65537WithoutChangingTheStack() throws Exception {
    ArrayValue below = integers(ArrayLimits.MAX_LENGTH - 1);
    var acceptedStack = stack(below, integer(1));
    execute("配列の末尾へ追加する", acceptedStack, new ExecutionBudget(SOURCE_PATH, () -> 0L));
    assertEquals(ArrayLimits.MAX_LENGTH, ((ArrayValue) acceptedStack.getFirst()).size());

    ArrayValue maximum = integers(ArrayLimits.MAX_LENGTH);
    var rejectedStack = stack(maximum, integer(1));
    ExecutionBudget budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("配列の末尾へ追加する", rejectedStack, budget));
    assertEquals(DiagnosticCode.E_ARRAY_LENGTH_LIMIT, failure.diagnostic().code());
    assertEquals("65537", failure.diagnostic().observed().orElseThrow());
    assertEquals(java.util.List.of(maximum, integer(1)), rejectedStack);
    assertEquals(0, budget.arrayConstructionUnits());
    assertEquals(0, budget.arrayElementOperationUnits());
  }

  @Test
  void arrayR003AppliesTheConstructionBoundaryAtomically() throws Exception {
    var acceptedBudget = budget(ArrayLimits.MAX_CONSTRUCTION_UNITS - 1, 0);
    var acceptedStack = stack(integers(0), integer(1));
    execute("配列の末尾へ追加する", acceptedStack, acceptedBudget);
    assertEquals(ArrayLimits.MAX_CONSTRUCTION_UNITS, acceptedBudget.arrayConstructionUnits());

    var rejectedBudget = budget(ArrayLimits.MAX_CONSTRUCTION_UNITS, 0);
    ArrayValue original = integers(0);
    var rejectedStack = stack(original, integer(1));
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> execute("配列の末尾へ追加する", rejectedStack, rejectedBudget));
    assertEquals(DiagnosticCode.E_ARRAY_CONSTRUCTION_LIMIT, failure.diagnostic().code());
    assertEquals("1000001", failure.diagnostic().observed().orElseThrow());
    assertEquals(java.util.List.of(original, integer(1)), rejectedStack);
    assertEquals(0, rejectedBudget.arrayElementOperationUnits());
  }

  @Test
  void arrayR004AppliesTheElementOperationBoundaryAtomically() throws Exception {
    var acceptedBudget = budget(0, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 1);
    var acceptedStack = stack(integers(1), integer(0));
    execute("配列から取り出す", acceptedStack, acceptedBudget);
    assertEquals(
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, acceptedBudget.arrayElementOperationUnits());

    var rejectedBudget = budget(0, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS);
    ArrayValue original = integers(1);
    var rejectedStack = stack(original, integer(0));
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> execute("配列から取り出す", rejectedStack, rejectedBudget));
    assertEquals(DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT, failure.diagnostic().code());
    assertEquals("10000001", failure.diagnostic().observed().orElseThrow());
    assertEquals(java.util.List.of(original, integer(0)), rejectedStack);
  }

  @Test
  void checksBothArrayBudgetsBeforeCommittingEitherCounter() {
    var budget =
        budget(ArrayLimits.MAX_CONSTRUCTION_UNITS - 1, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> budget.beforeArrayWork(1, 1, SPAN, "replace"));

    assertEquals(DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT, failure.diagnostic().code());
    assertEquals(ArrayLimits.MAX_CONSTRUCTION_UNITS - 1, budget.arrayConstructionUnits());
    assertEquals(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS, budget.arrayElementOperationUnits());
  }

  @Test
  void chargesEveryArrayBuiltinAccordingToTheNormativeCostTable() throws Exception {
    var budget = budget(0, 0);

    execute("配列の長さ", stack(integers(3)), budget);
    execute("配列から取り出す", stack(integers(3), integer(0)), budget);
    execute("配列の一部を取り出す", stack(integers(3), integer(0), integer(2)), budget);
    execute("配列の要素を置き換える", stack(integers(3), integer(1), integer(9)), budget);
    execute("配列の末尾へ追加する", stack(integers(2), integer(3)), budget);
    execute("一行表示する", stack(integers(2)), budget);
    execute("等しい", stack(array(1, 2, 3), array(1, 9, 3)), budget);
    execute("配列をつなぐ", stack(array(1), array(2, 3)), budget);
    execute("配列の先頭へ追加する", stack(array(2, 3), integer(1)), budget);
    execute("配列を逆順にする", stack(array(1, 2, 3)), budget);
    execute("配列に含まれる", stack(array(1, 2, 3), integer(2)), budget);
    execute("配列から検索する", stack(array(1, 2, 3), integer(9)), budget);
    execute("配列が空である", stack(integers(0)), budget);
    execute("配列の先頭を任意で取り出す", stack(array(1, 2, 3)), budget);
    execute("配列の末尾を任意で取り出す", stack(array(1, 2, 3)), budget);
    execute("配列の先頭を削除する", stack(array(1, 2, 3)), budget);
    execute("配列の末尾を削除する", stack(array(1, 2, 3)), budget);
    execute("配列の一部を削除する", stack(array(1, 2, 3), integer(1), integer(3)), budget);

    assertEquals(8, budget.arrayConstructionUnits());
    assertEquals(29, budget.arrayElementOperationUnits());
  }

  @Test
  void extendedOperationsRejectLengthAndNestedLeafOverflowWithoutChangingTheStack()
      throws Exception {
    ArrayValue maximum = integers(ArrayLimits.MAX_LENGTH);
    var prependStack = stack(maximum, integer(1));
    RuntimeFailure prependFailure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("配列の先頭へ追加する", prependStack, new ExecutionBudget(SOURCE_PATH, () -> 0L)));
    assertEquals(DiagnosticCode.E_ARRAY_LENGTH_LIMIT, prependFailure.diagnostic().code());
    assertEquals(java.util.List.of(maximum, integer(1)), prependStack);

    var concatStack = stack(maximum, array(1));
    RuntimeFailure concatFailure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("配列をつなぐ", concatStack, new ExecutionBudget(SOURCE_PATH, () -> 0L)));
    assertEquals(DiagnosticCode.E_ARRAY_LENGTH_LIMIT, concatFailure.diagnostic().code());
    assertEquals(java.util.List.of(maximum, array(1)), concatStack);

    ArrayValue row = integers(ArrayLimits.MAX_LENGTH);
    ArrayValue first = rows(row, 15);
    ArrayValue second = rows(row, 1);
    var nestedStack = stack(first, second);
    ExecutionBudget nestedBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    RuntimeFailure nestedFailure =
        assertThrows(RuntimeFailure.class, () -> execute("配列をつなぐ", nestedStack, nestedBudget));
    assertEquals(DiagnosticCode.E_ARRAY_NESTED_ELEMENT_LIMIT, nestedFailure.diagnostic().code());
    assertEquals(java.util.List.of(first, second), nestedStack);
    assertEquals(0, nestedBudget.arrayConstructionUnits());
    assertEquals(0, nestedBudget.arrayElementOperationUnits());
  }

  @Test
  void searchReservesItsWholeWorkBeforeChangingBudgetsOrStack() {
    ArrayValue original = array(1, 2, 3);
    IntegerValue sought = integer(9);
    var rejectedStack = stack(original, sought);
    var budget = budget(0, ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 2);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("配列から検索する", rejectedStack, budget));

    assertEquals(DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT, failure.diagnostic().code());
    assertEquals(java.util.List.of(original, sought), rejectedStack);
    assertEquals(ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - 2, budget.arrayElementOperationUnits());
  }

  @Test
  void deleteRangeRejectsInvalidBoundsBeforeChangingTheStackOrBudget() {
    ArrayValue original = array(1, 2, 3);
    var rejectedStack = stack(original, integer(2), integer(1));
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("配列の一部を削除する", rejectedStack, budget));

    assertEquals(DiagnosticCode.E_ARRAY_RANGE_OUT_OF_BOUNDS, failure.diagnostic().code());
    assertEquals(java.util.List.of(original, integer(2), integer(1)), rejectedStack);
    assertEquals(0, budget.arrayConstructionUnits());
    assertEquals(0, budget.arrayElementOperationUnits());
  }

  private static ExecutionBudget budget(long construction, long operations) {
    return new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, construction, operations);
  }

  private static void execute(
      String builtinName, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    var output = new BoundedOutput(SOURCE_PATH, new MemoryOutputSink());
    new BuiltinExecutor(SOURCE_PATH, output, budget)
        .execute(BuiltinDictionary.find(builtinName).orElseThrow(), stack, SPAN);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(java.util.List.of(values));
  }

  private static ArrayValue integers(int count) {
    return new ArrayValue(
        ScalarType.INTEGER, Collections.nCopies(count, new IntegerValue(BigInteger.ZERO)));
  }

  private static ArrayValue array(int... values) {
    return new ArrayValue(
        ScalarType.INTEGER,
        java.util.Arrays.stream(values).mapToObj(value -> (RuntimeValue) integer(value)).toList());
  }

  private static ArrayValue rows(ArrayValue row, int count) {
    return new ArrayValue(new ArrayType(ScalarType.INTEGER), Collections.nCopies(count, row));
  }

  private static IntegerValue integer(int value) {
    return new IntegerValue(BigInteger.valueOf(value));
  }

  private static String arrayLiteralSource(int elementCount) {
    var source = new StringBuilder(elementCount * 2 + 80).append("メインとは （--）\n    【");
    for (int index = 0; index < elementCount; index++) {
      if (index > 0) {
        source.append('、');
      }
      source.append('0');
    }
    return source.append("】を 配列の長さ を 一行表示する\nこと。\n").toString();
  }
}
