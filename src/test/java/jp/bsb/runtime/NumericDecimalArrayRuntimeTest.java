package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

class NumericDecimalArrayRuntimeTest {
  private static final String SOURCE_PATH = "decimal-array.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void runsAllNormativeDecimalArrayCases() throws Exception {
    assertRun("NUM-N014", "【1.5、2.0、3.25】\n0\n");
    assertRun("NUM-N015", "2.0\n【1.5、9.0、3.25】\n【1.5、2.0、3.25、4.0】\n1.5\n2.0\n");
    assertRun("NUM-N016", "2.5\n");
    assertRun("NUM-N017", "【1.0、2.0】\n");
  }

  @Test
  void reportsBothNormativeDecimalArrayTypeFailures() throws Exception {
    assertStaticFailure(
        "NUM-F005",
        DiagnosticCode.E_ARRAY_ELEMENT_TYPE_MISMATCH,
        Map.of("elementIndex", "2", "expectedType", "整数", "actualType", "小数"),
        "整数",
        "小数",
        "各整数を明示的に小数へ変換するか要素型を揃えてください");
    assertStaticFailure(
        "NUM-F006",
        DiagnosticCode.E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED,
        Map.of(
            "actualType",
            "丸め方法",
            "allowedTypes",
            "整数,真偽,文字,文字列,小数,JSON",
            "plannedFeature",
            "配列要素の対象外"),
        "配列要素にできる型",
        "丸め方法",
        "丸め方法を個別の値として使用してください");
  }

  @Test
  void decimalArrayOperationsPreserveTheOriginalAndUseNumericEquality() {
    ArrayValue original = decimals("1.50", "2.00", "3.250");
    ArrayValue replaced = original.replaced(1, decimal("9.0"));
    ArrayValue appended = original.appended(decimal("4.0"));
    ArrayValue equalByValue = decimals("1.5", "2.0", "3.25");

    assertEquals("【1.5、2.0、3.25】", original.displayText());
    assertEquals("【1.5、9.0、3.25】", replaced.displayText());
    assertEquals("【1.5、2.0、3.25、4.0】", appended.displayText());
    assertEquals(original, equalByValue);
    assertNotSame(original, replaced);
    assertNotSame(original, appended);
  }

  @Test
  void decimalArrayBuiltinsUseTheExistingConstructionAndElementBudgets() throws Exception {
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    ArrayValue original = decimals("1.5", "2.0");

    execute("配列から取り出す", stack(original, integer(0)), budget);
    execute("配列の要素を置き換える", stack(original, integer(1), decimal("9.0")), budget);
    execute("配列の末尾へ追加する", stack(original, decimal("3.0")), budget);
    execute("等しい", stack(original, decimals("1.50", "2.00")), budget);

    assertEquals(2, budget.arrayConstructionUnits());
    assertEquals(5, budget.arrayElementOperationUnits());
  }

  private static void assertStaticFailure(
      String caseId,
      DiagnosticCode code,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix)
      throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(caseId, output);
    Diagnostic diagnostic = result.diagnostics().getFirst();

    assertEquals(8, result.exitCode(), caseId);
    assertEquals(code, diagnostic.code(), caseId);
    fields.forEach(
        (name, value) -> assertEquals(value, diagnostic.fields().get(name), caseId + ": " + name));
    assertEquals(expected, diagnostic.expected().orElseThrow(), caseId);
    assertEquals(actual, diagnostic.actual().orElseThrow(), caseId);
    assertEquals(List.of(fix), diagnostic.fixes(), caseId);
    assertEquals("", output.utf8Text(), caseId);
  }

  private static void assertRun(String caseId, String expectedOutput) throws Exception {
    var output = new MemoryOutputSink();
    ProgramRunResult result = run(caseId, output);

    assertTrue(result.successful(), caseId + ": " + result.diagnostics());
    assertEquals(expectedOutput, output.utf8Text(), caseId);
    assertTrue(result.finalDataStack().isEmpty(), caseId);
  }

  private static ProgramRunResult run(String caseId, MemoryOutputSink output) throws Exception {
    return new ProgramRunner()
        .run(
            caseId + ".bsb",
            numericsSource(caseId),
            new ExecutionContext(output, () -> 0L, TraceSink.none()));
  }

  private static byte[] numericsSource(String caseId) throws Exception {
    String resource = "/conformance/numerics/sources/" + caseId + ".bsb";
    try (var input = NumericDecimalArrayRuntimeTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    var output = new BoundedOutput(SOURCE_PATH, new MemoryOutputSink());
    new BuiltinExecutor(SOURCE_PATH, output, budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static ArrayValue decimals(String... values) {
    return new ArrayValue(
        ScalarType.DECIMAL,
        java.util.Arrays.stream(values).map(value -> (RuntimeValue) decimal(value)).toList());
  }

  private static DecimalValue decimal(String value) {
    return new DecimalValue(new BigDecimal(value));
  }

  private static IntegerValue integer(int value) {
    return new IntegerValue(BigInteger.valueOf(value));
  }
}
