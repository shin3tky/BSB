package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.conformance.NumericConformanceData;
import jp.bsb.conformance.NumericConformanceData.CheckedProperties;
import jp.bsb.conformance.NumericConformanceData.ResourceSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.Call;
import jp.bsb.ir.IrGenerator;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrStackEffect;
import jp.bsb.ir.IrWord;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** R6生成定義から15個の数値・トレース・IR境界を再現し、規範資源表と照合します。 */
class NumericResourceConformanceTest {
  private static final String SOURCE_PATH = "numerics-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @TestFactory
  List<DynamicTest> everyResourceBoundaryMatchesTheNormativeData() throws Exception {
    List<ResourceSpec> resources = NumericConformanceData.loadResources();
    var tests = new ArrayList<DynamicTest>();
    for (ResourceSpec spec : resources) {
      String label = spec.id() + '/' + spec.target() + '/' + spec.variant();
      tests.add(DynamicTest.dynamicTest(label, () -> compare(label, spec)));
    }
    assertEquals(15, tests.size(), "resources.tsv must contain every R6 boundary row");
    return List.copyOf(tests);
  }

  private static void compare(String label, ResourceSpec spec) throws Exception {
    CheckedProperties properties = NumericConformanceData.loadGeneratedResource(spec.id());
    consumeGeneratorDefinition(properties, spec);
    Observation actual = execute(spec);
    properties.assertFullyConsumed();

    assertEquals(spec.outcome(), actual.outcome(), label + ": outcome");
    assertEquals(spec.exit(), actual.exit(), label + ": exit code");
    assertEquals(spec.code(), actual.code(), label + ": diagnostic code");
    assertEquals(spec.limit(), actual.limit(), label + ": limit");
    assertEquals(spec.observed(), actual.observed(), label + ": observed");
    assertEquals(actual, execute(spec), label + ": deterministic generated observation");
  }

  private static Observation execute(ResourceSpec spec) throws Exception {
    return switch (spec.id()) {
      case "NUM-R001" -> decimalPrecision(spec);
      case "NUM-R002" -> decimalScale(spec);
      case "NUM-R003" -> divisionPrecision(spec);
      case "NUM-R004" -> decimalToInteger(spec);
      case "NUM-R005" -> traceValue(spec);
      case "NUM-R006" -> numericIr(spec);
      default -> throw new IllegalArgumentException("unknown resource case: " + spec.id());
    };
  }

  private static Observation decimalPrecision(ResourceSpec spec) throws Exception {
    int observed = Integer.parseInt(spec.variant());
    int inputPrecision = observed - 1;
    DecimalValue input =
        new DecimalValue(BigInteger.TEN.pow(inputPrecision - 1).add(BigInteger.ONE), 0);
    var stack = stack(input, new DecimalValue(BigInteger.valueOf(99), 0));
    try {
      executeBuiltin("掛ける", stack);
      return new Observation("accepted", 0, null, "65536", Integer.toString(observed));
    } catch (RuntimeFailure failure) {
      return diagnosticObservation("error", failure.diagnostic());
    }
  }

  private static Observation decimalScale(ResourceSpec spec) throws Exception {
    int observed = Integer.parseInt(spec.variant());
    var stack =
        stack(
            new DecimalValue(BigInteger.ONE, 32_768),
            new DecimalValue(BigInteger.ONE, observed - 32_768));
    try {
      executeBuiltin("掛ける", stack);
      return new Observation("accepted", 0, null, "65536", Integer.toString(observed));
    } catch (RuntimeFailure failure) {
      return diagnosticObservation("error", failure.diagnostic());
    }
  }

  private static Observation divisionPrecision(ResourceSpec spec) throws Exception {
    BigInteger precision = new BigInteger(spec.variant());
    var stack =
        stack(integer(1), integer(3), new IntegerValue(precision), RoundingModeValue.NEAREST_EVEN);
    try {
      executeBuiltin("精度指定で割る", stack);
      return new Observation("accepted", 0, null, "1..4096", spec.variant());
    } catch (RuntimeFailure failure) {
      Diagnostic diagnostic = failure.diagnostic();
      return new Observation(
          "error",
          10,
          diagnostic.code().name(),
          diagnostic.fields().get("minimum") + ".." + diagnostic.fields().get("maximum"),
          diagnostic.fields().get("precision"));
    }
  }

  private static Observation decimalToInteger(ResourceSpec spec) throws Exception {
    int observed = Integer.parseInt(spec.variant());
    var stack = stack(new DecimalValue(BigInteger.ONE, -(observed - 1)));
    try {
      executeBuiltin("整数に変換する", stack);
      return new Observation("accepted", 0, null, "65536", Integer.toString(observed));
    } catch (RuntimeFailure failure) {
      return diagnosticObservation("error", failure.diagnostic());
    }
  }

  private static Observation traceValue(ResourceSpec spec) {
    if (spec.target().equals("traceDisclosure")) {
      DecimalValue value = new DecimalValue(BigInteger.ONE, -29);
      String formatted = TraceValueFormatter.format(value, ignored -> false);
      assertEquals("小数:<redacted>", formatted);
      return new Observation("redacted", 0, null, "0", "0");
    }
    int observed = Integer.parseInt(spec.variant());
    DecimalValue value = new DecimalValue(BigInteger.ONE, -(observed - 3));
    String formatted = TraceValueFormatter.format(value, TraceValuePolicy.numerics());
    String outcome = formatted.endsWith("…") ? "truncated" : "rendered";
    return new Observation(outcome, 0, null, "32", Integer.toString(observed));
  }

  private static Observation numericIr(ResourceSpec spec) {
    IrProgram valid = ir("メインとは （--）\n    1.0 と 2.0 を 足す を 一行表示する\nこと。\n");
    if (spec.variant().equals("matching")) {
      assertTrue(valid.instructionCount() > 0);
      return new Observation("accepted", 0, null, null, null);
    }

    Call add =
        valid.mainWord().instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .filter(call -> call.targetName().equals("足す"))
            .findFirst()
            .orElseThrow();
    var invalidInstructions = new ArrayList<IrInstruction>(valid.mainWord().instructions());
    invalidInstructions.set(
        invalidInstructions.indexOf(add),
        new Call(
            add.symbolId(),
            add.targetName(),
            add.particles(),
            new IrStackEffect(
                List.of(ValueType.INTEGER, ValueType.DECIMAL), List.of(ValueType.DECIMAL)),
            add.span()));
    IrWord invalidMain =
        new IrWord(
            valid.mainSymbol(),
            valid.mainWord().name(),
            invalidInstructions,
            valid.mainWord().localSlots(),
            valid.mainWord().stackEffect().orElseThrow());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrProgram(
                valid.sourcePath(),
                valid.mainSymbol(),
                Map.of(valid.mainSymbol(), invalidMain),
                valid.builtinWords(),
                valid.instructionCount()));
    return new Observation("internal-error", 70, "INTERNAL", null, null);
  }

  private static Observation diagnosticObservation(String outcome, Diagnostic diagnostic) {
    return new Observation(
        outcome,
        10,
        diagnostic.code().name(),
        diagnostic.limit().orElseThrow(),
        diagnostic.observed().orElseThrow());
  }

  private static IrProgram ir(String source) {
    var analysis = new SourceChecker().check(SOURCE_PATH, source.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
  }

  private static void consumeGeneratorDefinition(CheckedProperties properties, ResourceSpec spec) {
    String generator = properties.require("generator");
    switch (spec.id()) {
      case "NUM-R001" -> {
        assertEquals("decimal-result-precision", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals("multiply", properties.require("operation"));
        assertEquals("true", properties.require("preflight.required"));
      }
      case "NUM-R002" -> {
        assertEquals("decimal-result-scale", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals("multiply", properties.require("operation"));
        assertEquals("strip-trailing-zeroes", properties.require("normalization"));
      }
      case "NUM-R003" -> {
        assertEquals("decimal-division-precision", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals("1", properties.require("dividend"));
        assertEquals("3", properties.require("divisor"));
        assertEquals("最近接偶数丸め", properties.require("rounding"));
      }
      case "NUM-R004" -> {
        assertEquals("decimal-to-integer", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals("false", properties.require("fractional"));
        assertEquals("true", properties.require("preflight.required"));
      }
      case "NUM-R005" -> {
        assertEquals("decimal-trace-value", generator);
        assertEquals("32", properties.require("visible.codePoints"));
        assertEquals("小数:<redacted>", properties.require("redacted.format"));
        String variants = properties.require("codePoint.variants");
        if (spec.target().equals("traceDecimalCodePoints")) {
          assertVariant(variants, spec.variant());
        } else {
          assertEquals("traceDisclosure", spec.target());
        }
      }
      case "NUM-R006" -> {
        assertEquals("numeric-ir", generator);
        assertVariant(properties.require("variants"), spec.variant());
        assertEquals("整数,小数,丸め方法,配列<小数>", properties.require("value.types"));
        assertEquals("internal-error", properties.require("mismatch.outcome"));
      }
      default -> throw new IllegalArgumentException("unknown generated resource: " + spec.id());
    }
  }

  private static void assertVariant(String variants, String expected) {
    assertTrue(List.of(variants.split(",", -1)).contains(expected), "missing variant " + expected);
  }

  private static void executeBuiltin(String name, ArrayList<RuntimeValue> stack)
      throws RuntimeFailure {
    var output = new BoundedOutput(SOURCE_PATH, new MemoryOutputSink());
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    new BuiltinExecutor(SOURCE_PATH, output, budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private static IntegerValue integer(long value) {
    return new IntegerValue(BigInteger.valueOf(value));
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private record Observation(
      String outcome, int exit, String code, String limit, String observed) {}
}
