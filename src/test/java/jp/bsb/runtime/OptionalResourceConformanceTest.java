package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.conformance.OptionalConformanceData;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.Call;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.SymbolId;
import jp.bsb.json.JsonLimits;
import jp.bsb.json.JsonMember;
import jp.bsb.json.JsonNull;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class OptionalResourceConformanceTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void optionalR001TypeConstructorDepthAcceptsExactly256AndRejects257() {
    var specs = specs("OPT-R001");
    var exact =
        new SourceChecker()
            .check("OPT-R001-exact.bsb", OptionalConformanceData.generatedInput("typeDepth256"));
    var over =
        new SourceChecker()
            .check("OPT-R001-over.bsb", OptionalConformanceData.generatedInput("typeDepth257"));

    assertTrue(exact.successful(), exact.diagnostics().toString());
    assertEquals(DiagnosticCode.valueOf(specs.get(1).code()), over.diagnostics().getFirst().code());
    assertLimit(specs.get(1), over.diagnostics().getFirst());
  }

  @Test
  void optionalR002NestedValueOperationsStopAt256WithoutJavaRecursion() {
    specs("OPT-R002");
    RuntimeValue value = new IntegerValue(BigInteger.ONE);
    for (int depth = 0; depth < 256; depth++) {
      value = OptionalValue.present(value);
    }
    OptionalValue nested = (OptionalValue) value;
    RuntimeValue second = new IntegerValue(BigInteger.ONE);
    for (int depth = 0; depth < 256; depth++) {
      second = OptionalValue.present(second);
    }

    assertEquals("任意<".repeat(256) + "整数" + ">".repeat(256), nested.type().sourceName());
    assertEquals("ある（".repeat(256) + "1" + "）".repeat(256), nested.displayText());
    assertEquals(nested, second);
    String trace = TraceValueFormatter.format(nested, TraceValuePolicy.optional());
    assertTrue(trace.startsWith(nested.type().sourceName() + ":"));
    assertEquals(256, count(trace, "ある("));
  }

  @Test
  void optionalR003OptionalValuesReachTheDataStackBoundaryAtomically() {
    var specs = specs("OPT-R003");
    var exactRecipe = OptionalConformanceData.loadStackRecipe(specs.getFirst().generatorKey());
    var overRecipe = OptionalConformanceData.loadStackRecipe(specs.get(1).generatorKey());
    ExecutionResult exact = executeOptionalStack(exactRecipe.count(), exactRecipe.lookupFirst());
    ExecutionResult over = executeOptionalStack(overRecipe.count(), overRecipe.lookupFirst());

    assertEquals(0, exact.exitCode(), exact.diagnostic().toString());
    assertEquals(specs.getFirst().observed(), exact.finalDataStack().size());
    assertTrue(exact.finalDataStack().stream().allMatch(OptionalValue.class::isInstance));
    assertEquals(10, over.exitCode());
    assertEquals(
        DiagnosticCode.valueOf(specs.get(1).code()), over.diagnostic().orElseThrow().code());
    assertEquals(specs.getFirst().limit(), over.finalDataStack().size());
    assertLimit(specs.get(1), over.diagnostic().orElseThrow());
  }

  @Test
  void optionalR004GlobalOptionalBindingsAcceptExactly10000() {
    assertBindingBoundary("OPT-R004", "globals10000", "globals10001");
  }

  @Test
  void optionalR005LocalOptionalBindingsAcceptExactly1024() {
    assertBindingBoundary("OPT-R005", "locals1024", "locals1025");
  }

  @Test
  void optionalR006OptionalLookupUsesTheExistingJsonWorkBoundary() throws Exception {
    var specs = specs("OPT-R006");
    var exactBudget = budget(JsonLimits.WORK_UNITS - 1);
    var exactStack = lookupStack(new JsonObject(List.of()), "missing");
    execute("JSONオブジェクトから任意値を取り出す", exactStack, exactBudget);
    assertEquals(JsonLimits.WORK_UNITS, exactBudget.jsonWorkUnits());
    assertEquals(OptionalValue.absent(ValueType.JSON), exactStack.getFirst());

    var overBudget = budget(JsonLimits.WORK_UNITS);
    var overStack = lookupStack(new JsonObject(List.of()), "missing");
    List<RuntimeValue> before = List.copyOf(overStack);
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> execute("JSONオブジェクトから任意値を取り出す", overStack, overBudget));
    assertEquals(before, overStack);
    assertEquals(JsonLimits.WORK_UNITS, overBudget.jsonWorkUnits());
    assertEquals(DiagnosticCode.valueOf(specs.get(1).code()), failure.diagnostic().code());
    assertLimit(specs.get(1), failure.diagnostic());
    assertEquals("objectGetOptional", failure.diagnostic().fields().get("operation"));
    assertOptionalF023(failure.diagnostic());
  }

  @Test
  void optionalR007PresentAndMissingLookupConsumeOneUnitEach() throws Exception {
    var specs = specs("OPT-R007");
    var presentBudget = budget(0);
    var missingBudget = budget(0);
    var presentStack =
        lookupStack(new JsonObject(List.of(new JsonMember("x", new JsonString("value")))), "x");
    var missingStack = lookupStack(new JsonObject(List.of()), "x");

    execute("JSONオブジェクトから任意値を取り出す", presentStack, presentBudget);
    execute("JSONオブジェクトから任意値を取り出す", missingStack, missingBudget);
    assertEquals(specs.getFirst().observed(), presentBudget.jsonWorkUnits());
    assertEquals(specs.get(1).observed(), missingBudget.jsonWorkUnits());
    assertTrue(((OptionalValue) presentStack.getFirst()).isPresent());
    assertFalse(((OptionalValue) missingStack.getFirst()).isPresent());
  }

  @Test
  void optionalR008AllOptionalFailuresPreserveStackOutputAndRejectedBudget() throws Exception {
    specs("OPT-R008");
    var absentStack = new ArrayList<RuntimeValue>(List.of(OptionalValue.absent(ValueType.JSON)));
    assertAtomicFailure("任意から値を取り出す", absentStack, budget(0), 0);

    var kindStack =
        new ArrayList<RuntimeValue>(
            List.of(new JsonRuntimeValue(JsonNull.INSTANCE), new StringValue("x")));
    assertAtomicFailure("JSONオブジェクトから任意値を取り出す", kindStack, budget(0), 0);

    var output = new MemoryOutputSink();
    var displayStack =
        new ArrayList<RuntimeValue>(
            List.of(OptionalValue.present(new JsonRuntimeValue(JsonNull.INSTANCE))));
    List<RuntimeValue> before = List.copyOf(displayStack);
    var full = budget(JsonLimits.WORK_UNITS);
    RuntimeFailure displayFailure =
        assertThrows(
            RuntimeFailure.class,
            () ->
                new BuiltinExecutor("OPT-R008.bsb", new BoundedOutput("OPT-R008.bsb", output), full)
                    .execute(BuiltinDictionary.find("一行表示する").orElseThrow(), displayStack, SPAN));
    assertEquals(DiagnosticCode.E_JSON_WORK_LIMIT, displayFailure.diagnostic().code());
    assertEquals(before, displayStack);
    assertEquals("", output.utf8Text());
    assertEquals(JsonLimits.WORK_UNITS, full.jsonWorkUnits());
  }

  @Test
  void optionalR009TraceRedactsSensitiveOptionalStateAndChangesNoResult() {
    specs("OPT-R009");
    OptionalValue secret =
        OptionalValue.present(new JsonRuntimeValue(new JsonString("credential-secret")));
    OptionalValue visible = OptionalValue.present(new IntegerValue(BigInteger.valueOf(42)));
    TraceEvent event =
        new TraceEvent(
            1,
            "メイン",
            "Call:検査",
            1,
            1,
            List.of(secret, visible),
            List.of(OptionalValue.absent(ValueType.JSON), visible),
            1,
            1,
            new byte[0],
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            "");
    String trace = TraceTsvFormatter.formatOptional(List.of(event));
    assertTrue(trace.contains("任意<整数>:ある(42)"));
    assertTrue(trace.contains("任意<JSON>:<redacted>"));
    assertFalse(trace.contains("credential"));
    assertFalse(trace.contains(":ない"));

    byte[] chapter = resource("chapter/optional-values-chapter.bsb");
    Observation normal = run(chapter, false);
    Observation traced = run(chapter, true);
    assertEquals(normal.result, traced.result);
    assertEquals(normal.output, traced.output);
  }

  @Test
  void optionalR010IntegratedDepthWidthAndLookupFitIn512MiBDeterministically() {
    var specs = specs("OPT-R010");
    assertEquals(512, specs.getFirst().limit());
    assertTrue(Runtime.getRuntime().maxMemory() <= 512L * 1024 * 1024);
    var recipe = OptionalConformanceData.loadStackRecipe(specs.getFirst().generatorKey());
    assertEquals(256, recipe.typeDepth());
    var depth =
        new SourceChecker()
            .check("integrated-depth.bsb", OptionalConformanceData.generatedInput("typeDepth256"));
    assertTrue(depth.successful(), depth.diagnostics().toString());
    ExecutionResult first = executeOptionalStack(recipe.count(), recipe.lookupFirst());
    ExecutionResult second = executeOptionalStack(recipe.count(), recipe.lookupFirst());
    assertEquals(0, first.exitCode(), first.diagnostic().toString());
    assertEquals(65_536, first.finalDataStack().size());
    assertEquals(1, first.jsonWorkUnits());
    assertEquals(first.finalDataStack(), second.finalDataStack());
    assertEquals(first.executedInstructions(), second.executedInstructions());
    assertEquals(first.jsonWorkUnits(), second.jsonWorkUnits());
  }

  private static void assertOptionalF023(Diagnostic actual) throws Exception {
    var expected =
        OptionalConformanceData.loadDiagnostics().stream()
            .filter(spec -> spec.id().equals("OPT-F023"))
            .findFirst()
            .orElseThrow();
    assertEquals(expected.code(), actual.code().name());
    assertEquals(expected.fields(), actual.fields());
    assertEquals(expected.expected(), actual.expected().orElseThrow());
    assertEquals(expected.actual(), actual.actual().orElseThrow());
    assertEquals(expected.fixes(), actual.fixes());
    assertEquals(expected.limitName(), actual.limitName().orElseThrow());
    assertEquals(expected.limit(), actual.limit().orElseThrow());
    assertEquals(expected.observed(), actual.observed().orElseThrow());
  }

  private static void assertBindingBoundary(String id, String exactKey, String overKey) {
    var specs = specs(id);
    var exact =
        new SourceChecker()
            .check(id + "-exact.bsb", OptionalConformanceData.generatedInput(exactKey));
    var over =
        new SourceChecker()
            .check(id + "-over.bsb", OptionalConformanceData.generatedInput(overKey));
    assertTrue(exact.successful(), exact.diagnostics().toString());
    assertEquals(
        specs.getFirst().observed(),
        exact.programForIrGeneration().nameResolution().bindings().size());
    assertEquals(DiagnosticCode.valueOf(specs.get(1).code()), over.diagnostics().getFirst().code());
    assertLimit(specs.get(1), over.diagnostics().getFirst());
  }

  private static void assertAtomicFailure(
      String word, ArrayList<RuntimeValue> stack, ExecutionBudget budget, long expectedWork) {
    List<RuntimeValue> before = List.copyOf(stack);
    assertThrows(RuntimeFailure.class, () -> execute(word, stack, budget));
    assertEquals(before, stack);
    assertEquals(expectedWork, budget.jsonWorkUnits());
  }

  private static void assertLimit(
      OptionalConformanceData.ResourceSpec spec, Diagnostic diagnostic) {
    assertEquals(Long.toString(spec.limit()), diagnostic.limit().orElseThrow());
    assertEquals(Long.toString(spec.observed()), diagnostic.observed().orElseThrow());
  }

  private static ArrayList<RuntimeValue> lookupStack(JsonObject object, String key) {
    return new ArrayList<>(List.of(new JsonRuntimeValue(object), new StringValue(key)));
  }

  private static ExecutionBudget budget(long jsonWork) {
    return new ExecutionBudget("Optional-resource.bsb", () -> 0L, 0, 0, 0, 0, 0, 0, jsonWork);
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws RuntimeFailure {
    new BuiltinExecutor(
            "Optional-resource.bsb",
            new BoundedOutput("Optional-resource.bsb", new MemoryOutputSink()),
            budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }

  private static ExecutionResult executeOptionalStack(int count, boolean lookupFirst) {
    var main = new SymbolId(0);
    var wrap = new SymbolId(1);
    var lookup = new SymbolId(2);
    var instructions = new ArrayList<IrInstruction>(count * 2 + 5);
    var builtins = new LinkedHashMap<SymbolId, jp.bsb.stdlib.BuiltinWord>();
    builtins.put(wrap, BuiltinDictionary.find("任意にする").orElseThrow());
    if (lookupFirst) {
      builtins.put(lookup, BuiltinDictionary.find("JSONオブジェクトから任意値を取り出す").orElseThrow());
      instructions.add(new PushConst(new JsonRuntimeValue(new JsonObject(List.of())), SPAN));
      instructions.add(new PushConst(new StringValue("x"), SPAN));
      instructions.add(new Call(lookup, "JSONオブジェクトから任意値を取り出す", List.of(), SPAN));
    }
    var zero = new IntegerValue(BigInteger.ZERO);
    for (int index = 0; index < count; index++) {
      instructions.add(new PushConst(zero, SPAN));
      instructions.add(new Call(wrap, "任意にする", List.of(), SPAN));
    }
    instructions.add(new Return(SPAN));
    IrProgram program =
        new IrProgram(
            "Optional-stack.bsb",
            main,
            Map.of(main, new IrWord(main, "メイン", instructions)),
            builtins,
            instructions.size());
    return new Interpreter()
        .execute(program, new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));
  }

  private static List<OptionalConformanceData.ResourceSpec> specs(String id) {
    try {
      List<OptionalConformanceData.ResourceSpec> result =
          OptionalConformanceData.loadResources().stream()
              .filter(spec -> spec.id().equals(id))
              .toList();
      assertEquals(2, result.size(), id);
      return result;
    } catch (java.io.IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
  }

  private static byte[] resource(String path) {
    try {
      return OptionalConformanceData.resourceBytes(path);
    } catch (java.io.IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
  }

  private static Observation run(byte[] source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "OPT-R009.bsb",
                source,
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Observation(result, output.utf8Text(), List.copyOf(events));
  }

  private static int count(String text, String needle) {
    int result = 0;
    for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
      result++;
    }
    return result;
  }

  private record Observation(ProgramRunResult result, String output, List<TraceEvent> events) {}
}
