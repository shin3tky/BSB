package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.binding.NameResolution;
import jp.bsb.binding.WordName;
import jp.bsb.conformance.ArrayConformanceData;
import jp.bsb.conformance.ArrayConformanceData.CheckedProperties;
import jp.bsb.conformance.ArrayConformanceData.ResourceSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.ir.BuildArray;
import jp.bsb.ir.IrGenerationResult;
import jp.bsb.ir.IrGenerator;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** R5生成定義から15個の配列境界入力を再現し、規範資源表と照合します。 */
class ArrayResourceConformanceTest {
  private static final String SOURCE_PATH = "arrays-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @TestFactory
  List<DynamicTest> everyResourceBoundaryMatchesTheNormativeData() throws Exception {
    List<ResourceSpec> resources = ArrayConformanceData.loadResources();
    var tests = new ArrayList<DynamicTest>();
    for (ResourceSpec spec : resources) {
      String label = spec.id() + '/' + spec.target() + '/' + spec.variant();
      tests.add(DynamicTest.dynamicTest(label, () -> compare(label, spec)));
    }
    assertEquals(15, tests.size(), "resources.tsv must contain every R5 boundary row");
    return List.copyOf(tests);
  }

  private static void compare(String label, ResourceSpec spec) throws Exception {
    CheckedProperties properties = ArrayConformanceData.loadGeneratedResource(spec.id());
    consumeGeneratorDefinition(properties, spec);
    Observation actual = execute(spec);
    properties.assertFullyConsumed();

    assertEquals(spec.exit(), actual.exit(), label + ": exit code");
    assertEquals(spec.code(), actual.code(), label + ": diagnostic code");
    assertEquals(spec.limit(), actual.limit(), label + ": limit");
    assertEquals(spec.observed(), actual.observed(), label + ": observed");
    assertEquals(spec.outcome(), actual.outcome(), label + ": outcome");
    assertEquals(actual, execute(spec), label + ": deterministic generated observation");
  }

  private static Observation execute(ResourceSpec spec) throws Exception {
    return switch (spec.id()) {
      case "ARRAY-R001" -> arrayLiteralBoundary(spec);
      case "ARRAY-R002" -> dynamicLengthBoundary(spec);
      case "ARRAY-R003" -> constructionBoundary(spec);
      case "ARRAY-R004" -> operationBoundary(spec);
      case "ARRAY-R005" -> arrayIrBoundary(spec);
      case "ARRAY-R006" -> traceValueBoundary(spec);
      default -> throw new IllegalArgumentException("unknown resource case: " + spec.id());
    };
  }

  private static Observation arrayLiteralBoundary(ResourceSpec spec) {
    int count = Math.toIntExact(spec.variant());
    String source = arrayLiteralSource(count);
    assertEquals(
        source, arrayLiteralSource(count), "array literal generator must be deterministic");
    var result = new SourceChecker().check(SOURCE_PATH, source.getBytes(StandardCharsets.UTF_8));
    if (result.successful()) {
      return Observation.accepted(spec.limit(), count);
    }
    return diagnosticObservation(result.exitCode(), result.diagnostics().getFirst());
  }

  private static Observation dynamicLengthBoundary(ResourceSpec spec) throws RuntimeFailure {
    int resultLength = Math.toIntExact(spec.variant());
    ArrayValue original = integers(resultLength - 1);
    var stack = stack(original, integer(1));
    try {
      executeBuiltin("配列の末尾へ追加する", stack, new ExecutionBudget(SOURCE_PATH, () -> 0L));
      assertEquals(resultLength, ((ArrayValue) stack.getFirst()).size());
      return Observation.accepted(spec.limit(), resultLength);
    } catch (RuntimeFailure failure) {
      assertEquals(List.of(original, integer(1)), stack, "failed append must be atomic");
      return diagnosticObservation(10, failure.diagnostic());
    }
  }

  private static Observation constructionBoundary(ResourceSpec spec) throws RuntimeFailure {
    long observed = spec.variant();
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, observed - 1, 0);
    ArrayValue original = integers(0);
    var stack = stack(original, integer(1));
    try {
      executeBuiltin("配列の末尾へ追加する", stack, budget);
      assertEquals(observed, budget.arrayConstructionUnits());
      return Observation.accepted(spec.limit(), observed);
    } catch (RuntimeFailure failure) {
      assertEquals(List.of(original, integer(1)), stack, "failed construction must be atomic");
      return diagnosticObservation(10, failure.diagnostic());
    }
  }

  private static Observation operationBoundary(ResourceSpec spec) throws RuntimeFailure {
    long observed = spec.variant();
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, 0, observed - 1);
    ArrayValue original = integers(1);
    var stack = stack(original, integer(0));
    try {
      executeBuiltin("配列から取り出す", stack, budget);
      assertEquals(observed, budget.arrayElementOperationUnits());
      return Observation.accepted(spec.limit(), observed);
    } catch (RuntimeFailure failure) {
      assertEquals(List.of(original, integer(0)), stack, "failed operation must be atomic");
      return diagnosticObservation(10, failure.diagnostic());
    }
  }

  private static Observation arrayIrBoundary(ResourceSpec spec) {
    int totalInstructions = Math.toIntExact(spec.variant());
    int arrayInstructions = totalInstructions - 1;
    IrGenerationResult result =
        new IrGenerator().generate(syntheticEmptyArrayProgram(arrayInstructions));
    if (result.successful()) {
      assertEquals(totalInstructions, result.programForExecution().instructionCount());
      assertEquals(
          arrayInstructions,
          result.programForExecution().mainWord().instructions().stream()
              .filter(BuildArray.class::isInstance)
              .count());
      return Observation.accepted(spec.limit(), totalInstructions);
    }
    return diagnosticObservation(8, result.diagnostics().getFirst());
  }

  private static Observation traceValueBoundary(ResourceSpec spec) {
    String rendered;
    if (spec.target().equals("traceArrayElements")) {
      int count = Math.toIntExact(spec.variant());
      var elements = new ArrayList<RuntimeValue>(count);
      for (int index = 0; index < count; index++) {
        elements.add(new StringValue("値" + index));
      }
      rendered =
          TraceValueFormatter.format(
              new ArrayValue(ScalarType.STRING, elements), TraceValuePolicy.arrays());
      assertEquals(spec.outcome().equals("truncated"), rendered.contains("…(+1)"));
    } else if (spec.target().equals("traceElementCodePoints")) {
      int count = Math.toIntExact(spec.variant());
      String value = "😀".repeat(count);
      rendered =
          TraceValueFormatter.format(
              new ArrayValue(ScalarType.STRING, List.of(new StringValue(value))),
              TraceValuePolicy.arrays());
      assertEquals(spec.outcome().equals("truncated"), rendered.contains("…」"));
    } else if (spec.target().equals("traceDisclosure")) {
      rendered =
          TraceValueFormatter.format(
              new ArrayValue(ScalarType.STRING, List.of(new StringValue("秘密"))), ignored -> false);
      assertEquals("配列<文字列>:<redacted>", rendered);
    } else {
      throw new IllegalArgumentException("unknown trace target: " + spec.target());
    }
    assertFalse(rendered.contains("\n"), "trace value must remain on one physical line");
    return new Observation(0, null, spec.limit(), spec.observed(), spec.outcome());
  }

  private static void consumeGeneratorDefinition(CheckedProperties properties, ResourceSpec spec) {
    String generator = properties.require("generator");
    switch (spec.id()) {
      case "ARRAY-R001" -> {
        assertEquals("array-literal", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals("整数", properties.require("element.type"));
        assertEquals(65_536, Long.parseLong(properties.require("accepted.elements")));
        assertEquals(65_537, Long.parseLong(properties.require("rejected.elements")));
        assertTrue(Boolean.parseBoolean(properties.require("required.main")));
      }
      case "ARRAY-R002" -> {
        assertEquals("array-append", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals("整数", properties.require("element.type"));
        assertEquals(65_535, Long.parseLong(properties.require("accepted.initial.length")));
        assertEquals(65_536, Long.parseLong(properties.require("accepted.result.length")));
        assertEquals(65_536, Long.parseLong(properties.require("rejected.initial.length")));
        assertEquals(65_537, Long.parseLong(properties.require("rejected.result.length")));
        assertEquals("synthetic-value", properties.require("input.kind"));
      }
      case "ARRAY-R003" -> {
        assertEquals("array-construction", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals(999_999, Long.parseLong(properties.require("accepted.used.before")));
        assertEquals(1, Long.parseLong(properties.require("accepted.requested")));
        assertEquals(1_000_000, Long.parseLong(properties.require("rejected.used.before")));
        assertEquals(1, Long.parseLong(properties.require("rejected.requested")));
        assertEquals("append", properties.require("operation"));
      }
      case "ARRAY-R004" -> {
        assertEquals("array-operations", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals(9_999_999, Long.parseLong(properties.require("accepted.used.before")));
        assertEquals(1, Long.parseLong(properties.require("accepted.requested")));
        assertEquals(10_000_000, Long.parseLong(properties.require("rejected.used.before")));
        assertEquals(1, Long.parseLong(properties.require("rejected.requested")));
        assertEquals("get", properties.require("operation"));
      }
      case "ARRAY-R005" -> {
        assertEquals("array-ir", generator);
        assertEquals("BuildArray", properties.require("opcode"));
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals(249_999, Long.parseLong(properties.require("accepted.array.instructions")));
        assertEquals(250_000, Long.parseLong(properties.require("accepted.total.instructions")));
        assertEquals(250_000, Long.parseLong(properties.require("rejected.array.instructions")));
        assertEquals(250_001, Long.parseLong(properties.require("rejected.total.instructions")));
        assertEquals("Return", properties.require("terminator.opcode"));
      }
      case "ARRAY-R006" -> {
        assertEquals("array-trace-value", generator);
        assertEquals("文字列", properties.require("element.type"));
        assertVariant(properties.require("element.variants"), 8);
        assertVariant(properties.require("element.variants"), 9);
        assertVariant(properties.require("codePoint.variants"), 16);
        assertVariant(properties.require("codePoint.variants"), 17);
        assertEquals(8, Long.parseLong(properties.require("visible.elements")));
        assertEquals(16, Long.parseLong(properties.require("visible.codePoints.per.element")));
        assertEquals("…(+%d)", properties.require("omission.format"));
        assertEquals("配列<文字列>:<redacted>", properties.require("redacted.format"));
      }
      default -> throw new IllegalArgumentException("unknown resource case: " + spec.id());
    }
  }

  private static void assertVariant(String variants, long expected) {
    assertTrue(
        Arrays.stream(variants.split(",", -1))
            .mapToLong(Long::parseLong)
            .anyMatch(v -> v == expected),
        "generated properties must declare variant " + expected);
  }

  private static void executeBuiltin(
      String builtinName, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    var output = new BoundedOutput(SOURCE_PATH, new MemoryOutputSink());
    new BuiltinExecutor(SOURCE_PATH, output, budget)
        .execute(BuiltinDictionary.find(builtinName).orElseThrow(), stack, SPAN);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static ArrayValue integers(int count) {
    return new ArrayValue(
        ScalarType.INTEGER, Collections.nCopies(count, new IntegerValue(BigInteger.ZERO)));
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

  private static AnalyzedProgram syntheticEmptyArrayProgram(int arrayCount) {
    WordCall empty = new WordCall("空の整数配列", "空の整数配列", SPAN);
    var definition =
        new WordDefinition(
            "メイン",
            "メイン",
            SPAN,
            new StackEffect(List.of(), List.of(), SPAN),
            Collections.nCopies(arrayCount, empty),
            SPAN,
            SPAN);
    var syntax = new Program("synthetic-array-ir.bsb", List.of(definition), SPAN);
    ValueType arrayType = ValueType.arrayOf(ValueType.INTEGER);
    List<ValueType> outputs = Collections.nCopies(arrayCount, arrayType);
    WordSignature wordEffect = new WordSignature(List.of(), outputs);
    var callEffects = new IdentityHashMap<WordCall, WordSignature>();
    callEffects.put(empty, new WordSignature(List.of(), List.of(arrayType)));
    return new AnalyzedProgram(
        syntax,
        Map.of("メイン", wordEffect),
        java.util.Set.of(),
        NameResolution.wordsOnly(List.of(new WordName("メイン", "メイン", SPAN))),
        Map.of(),
        callEffects,
        true);
  }

  private static Observation diagnosticObservation(int exit, Diagnostic diagnostic) {
    return new Observation(
        exit,
        diagnostic.code().name(),
        Long.parseLong(diagnostic.limit().orElseThrow()),
        Long.parseLong(diagnostic.observed().orElseThrow()),
        "error");
  }

  private record Observation(int exit, String code, long limit, long observed, String outcome) {
    private static Observation accepted(long limit, long observed) {
      return new Observation(0, null, limit, observed, "accepted");
    }
  }
}
