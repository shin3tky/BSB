package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.SemanticAnalyzer;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.binding.Binding;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.binding.BindingUseKind;
import jp.bsb.binding.LexicalScope;
import jp.bsb.binding.LexicalScopeKind;
import jp.bsb.binding.NameResolution;
import jp.bsb.binding.ResolvedBindingUse;
import jp.bsb.binding.ScopeId;
import jp.bsb.binding.WordName;
import jp.bsb.conformance.BindingConformanceData;
import jp.bsb.conformance.BindingConformanceData.CheckedProperties;
import jp.bsb.conformance.BindingConformanceData.ResourceSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.BodyElement;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LiteralKind;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.TopLevelElement;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.ir.InitializeGlobal;
import jp.bsb.ir.IrGenerationResult;
import jp.bsb.ir.IrGenerator;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrStorageSlot;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.LoadGlobal;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.StorageInstruction;
import jp.bsb.ir.SymbolId;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** R4生成定義から境界入力を再現し、上限ちょうどと1単位超過を検証します。 */
class BindingResourceConformanceTest {
  private static final String SOURCE_PATH = "bindings-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @TestFactory
  List<DynamicTest> everyResourceBoundaryMatchesTheNormativeData() throws Exception {
    List<ResourceSpec> resources = BindingConformanceData.loadResources();
    var tests = new ArrayList<DynamicTest>();
    for (ResourceSpec spec : resources) {
      String label = spec.id() + '/' + spec.target() + '/' + spec.variant();
      tests.add(DynamicTest.dynamicTest(label, () -> compare(label, spec)));
    }
    assertEquals(10, tests.size(), "two boundary rows are required for each R4 case");
    return List.copyOf(tests);
  }

  private static void compare(String label, ResourceSpec spec) throws Exception {
    CheckedProperties properties = BindingConformanceData.loadGeneratedResource(spec.id());
    consumeGeneratorDefinition(properties, spec);
    Observation actual = execute(spec);
    properties.assertFullyConsumed();

    assertEquals(spec.exit(), actual.exit(), label + ": expected/actual exit code");
    assertEquals(spec.code(), actual.code(), label + ": expected/actual diagnostic code");
    assertEquals(spec.limit(), actual.limit(), label + ": expected/actual limit");
    assertEquals(spec.observed(), actual.observed(), label + ": expected/actual observed");
    assertEquals(spec.outcome(), actual.outcome(), label + ": expected/actual outcome");
  }

  private static Observation execute(ResourceSpec spec) {
    return switch (spec.id()) {
      case "BIND-R001" -> topLevelBindingBoundary(spec);
      case "BIND-R002" -> localBindingBoundary(spec);
      case "BIND-R003" -> programBindingBoundary(spec);
      case "BIND-R004" -> storageIrBoundary(spec);
      case "BIND-R005" -> loadStackBoundary(spec);
      default -> throw new IllegalArgumentException("unknown resource case: " + spec.id());
    };
  }

  private static Observation topLevelBindingBoundary(ResourceSpec spec) {
    int count = Math.toIntExact(spec.variant());
    var source = new StringBuilder(count * 18);
    for (int index = 0; index < count; index++) {
      source.append("大域").append(index).append("は 定数 0。\n");
    }
    source.append("\nメインとは （--）\nこと。\n");
    var result =
        new SourceChecker().check(SOURCE_PATH, source.toString().getBytes(StandardCharsets.UTF_8));
    if (result.diagnostics().isEmpty()) {
      assertEquals(count, result.programForIrGeneration().nameResolution().bindings().size());
      return Observation.accepted(spec.limit(), count);
    }
    return diagnosticObservation(result.exitCode(), result.diagnostics().getFirst());
  }

  private static Observation localBindingBoundary(ResourceSpec spec) {
    int count = Math.toIntExact(spec.variant());
    var source = new StringBuilder(count * 20).append("メインとは （--）\n");
    for (int index = 0; index < count; index++) {
      source.append("    局所").append(index).append("は 定数 0。\n");
    }
    source.append("こと。\n");
    var result =
        new SourceChecker().check(SOURCE_PATH, source.toString().getBytes(StandardCharsets.UTF_8));
    if (result.diagnostics().isEmpty()) {
      assertEquals(count, result.programForIrGeneration().nameResolution().bindings().size());
      return Observation.accepted(spec.limit(), count);
    }
    return diagnosticObservation(result.exitCode(), result.diagnostics().getFirst());
  }

  private static Observation programBindingBoundary(ResourceSpec spec) {
    int count = Math.toIntExact(spec.variant());
    var result = new SemanticAnalyzer().analyze(syntheticBindingProgram(count));
    if (result.diagnostics().isEmpty()) {
      assertEquals(count, result.programForIrGeneration().nameResolution().bindings().size());
      return Observation.accepted(spec.limit(), count);
    }
    return diagnosticObservation(result.exitCode(), result.diagnostics().getFirst());
  }

  private static Observation storageIrBoundary(ResourceSpec spec) {
    int totalInstructions = Math.toIntExact(spec.variant());
    int loadCount = totalInstructions - 2;
    IrGenerationResult result = new IrGenerator().generate(syntheticStorageProgram(loadCount));
    if (result.successful()) {
      IrProgram program = result.programForExecution();
      assertEquals(totalInstructions, program.instructionCount());
      long storageInstructions =
          program.mainWord().instructions().stream()
              .filter(StorageInstruction.class::isInstance)
              .count();
      assertEquals(totalInstructions - 1L, storageInstructions);
      return Observation.accepted(spec.limit(), totalInstructions);
    }
    return diagnosticObservation(8, result.diagnostics().getFirst());
  }

  private static Observation loadStackBoundary(ResourceSpec spec) {
    int loadCount = Math.toIntExact(spec.variant());
    ExecutionResult result =
        new Interpreter()
            .execute(
                loadProgram(loadCount),
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));
    if (result.successful()) {
      assertEquals(loadCount, result.finalDataStack().size());
      return Observation.accepted(spec.limit(), loadCount);
    }
    return diagnosticObservation(result.exitCode(), result.diagnostic().orElseThrow());
  }

  private static void consumeGeneratorDefinition(CheckedProperties properties, ResourceSpec spec) {
    String generator = properties.require("generator");
    long[] variants = longCsv(properties.require("variants"));
    assertTrue(
        Arrays.stream(variants).anyMatch(variant -> variant == spec.variant()),
        spec.id() + ": TSV variant is absent from generated properties");
    switch (spec.id()) {
      case "BIND-R001" -> {
        assertEquals("top-level-bindings", generator);
        assertEquals(10_000, Long.parseLong(properties.require("accepted.bindings")));
        assertEquals(10_001, Long.parseLong(properties.require("rejected.bindings")));
        assertTrue(Boolean.parseBoolean(properties.require("required.main")));
      }
      case "BIND-R002" -> {
        assertEquals("local-bindings", generator);
        assertEquals(1_024, Long.parseLong(properties.require("accepted.bindings")));
        assertEquals(1_025, Long.parseLong(properties.require("rejected.bindings")));
        assertEquals("constant", properties.require("binding.kind"));
      }
      case "BIND-R003" -> {
        assertEquals("program-bindings", generator);
        assertEquals(65_536, Long.parseLong(properties.require("accepted.bindings")));
        assertEquals(65_537, Long.parseLong(properties.require("rejected.bindings")));
        assertEquals("synthetic-ast", properties.require("input.kind"));
      }
      case "BIND-R004" -> {
        assertEquals("storage-ir", generator);
        assertEquals("LoadGlobal", properties.require("opcode"));
        assertEquals(249_999, Long.parseLong(properties.require("accepted.storage.instructions")));
        assertEquals(250_000, Long.parseLong(properties.require("accepted.total.instructions")));
        assertEquals(250_000, Long.parseLong(properties.require("rejected.storage.instructions")));
        assertEquals(250_001, Long.parseLong(properties.require("rejected.total.instructions")));
        assertEquals("Return", properties.require("terminator.opcode"));
      }
      case "BIND-R005" -> {
        assertEquals("load-stack", generator);
        assertEquals("LoadGlobal", properties.require("opcode"));
        assertEquals(65_536, Long.parseLong(properties.require("accepted.values")));
        assertEquals(65_537, Long.parseLong(properties.require("rejected.values")));
      }
      default -> throw new IllegalArgumentException("unknown resource case: " + spec.id());
    }
  }

  private static long[] longCsv(String value) {
    return Arrays.stream(value.split(",", -1)).mapToLong(Long::parseLong).toArray();
  }

  private static Observation diagnosticObservation(int exit, Diagnostic diagnostic) {
    return new Observation(
        exit,
        diagnostic.code().name(),
        Long.parseLong(diagnostic.limit().orElseThrow()),
        Long.parseLong(diagnostic.observed().orElseThrow()),
        "error");
  }

  private static Program syntheticBindingProgram(int bindingCount) {
    int fullWords = bindingCount / 1_024;
    int remainder = bindingCount % 1_024;
    int wordCount = fullWords + (remainder == 0 ? 0 : 1);
    var elements = new ArrayList<TopLevelElement>(wordCount);
    var effect = new StackEffect(List.of(), List.of(), SPAN);
    var initializer = new Literal(LiteralKind.INTEGER, "0", "0", SPAN);
    int bindingIndex = 0;
    for (int wordIndex = 0; wordIndex < wordCount; wordIndex++) {
      int wordBindings = wordIndex < fullWords ? 1_024 : remainder;
      var body = new ArrayList<BodyElement>(wordBindings);
      for (int localIndex = 0; localIndex < wordBindings; localIndex++) {
        String name = "値" + bindingIndex;
        long declarationOffset = 2L + bindingIndex++ * 3L;
        SourceSpan nameSpan = span(declarationOffset, declarationOffset + 1);
        SourceSpan declarationSpan = span(declarationOffset, declarationOffset + 2);
        body.add(
            new ValueDeclaration(
                name,
                name,
                nameSpan,
                nameSpan,
                BindingKind.CONSTANT,
                nameSpan,
                List.of(initializer),
                declarationSpan,
                declarationSpan));
      }
      String wordName = wordIndex == 0 ? "メイン" : "語" + wordIndex;
      elements.add(new WordDefinition(wordName, wordName, SPAN, effect, body, SPAN, SPAN));
    }
    return new Program(SOURCE_PATH, elements, SPAN);
  }

  private static AnalyzedProgram syntheticStorageProgram(int loadCount) {
    SourceSpan programSpan = span(0, 100);
    SourceSpan wordNameSpan = span(1, 2);
    SourceSpan declarationNameSpan = span(10, 11);
    SourceSpan declarationSpan = span(10, 20);
    SourceSpan referenceSpan = span(30, 31);
    var reference = new ValueReference("値", "値", referenceSpan);
    var declaration =
        new ValueDeclaration(
            "値",
            "値",
            declarationNameSpan,
            span(11, 12),
            BindingKind.VARIABLE,
            span(13, 14),
            List.of(),
            span(19, 20),
            declarationSpan);
    var effect = new StackEffect(List.of(), List.of(), span(3, 4));
    var main =
        new WordDefinition(
            "メイン",
            "メイン",
            wordNameSpan,
            effect,
            bodyWithRepeatedReference(declaration, reference, loadCount),
            span(90, 91),
            span(1, 91));
    var syntax = new Program(SOURCE_PATH, List.of(main), programSpan);

    var globalScope = LexicalScope.global(new ScopeId(1), programSpan);
    var wordScope =
        new LexicalScope(
            new ScopeId(2),
            LexicalScopeKind.WORD_BODY,
            Optional.of(globalScope.id()),
            Optional.of("メイン"),
            main.span());
    var binding =
        new Binding(
            new BindingId(1),
            "値",
            "値",
            BindingKind.VARIABLE,
            BindingStorage.LOCAL,
            wordScope.id(),
            BindingTypeState.inferred(ValueType.INTEGER),
            OptionalInt.empty(),
            declarationNameSpan,
            declarationSpan);
    var resolution =
        new NameResolution(
            List.of(new WordName("メイン", "メイン", wordNameSpan), binding),
            List.of(globalScope, wordScope),
            List.of(new ResolvedBindingUse(reference, "値", BindingUseKind.READ, binding.id())));
    return new AnalyzedProgram(
        syntax,
        Map.of("メイン", new WordSignature(List.of(), List.of())),
        java.util.Set.of(),
        resolution);
  }

  private static List<BodyElement> bodyWithRepeatedReference(
      ValueDeclaration declaration, ValueReference reference, int loadCount) {
    var body = new ArrayList<BodyElement>(loadCount + 1);
    body.add(declaration);
    body.addAll(Collections.nCopies(loadCount, reference));
    return List.copyOf(body);
  }

  private static IrProgram loadProgram(int loadCount) {
    var global =
        new IrStorageSlot(
            new BindingId(1),
            "値",
            BindingKind.CONSTANT,
            BindingStorage.GLOBAL,
            ValueType.INTEGER,
            0,
            Optional.empty());
    var mainSymbol = new SymbolId(0);
    var initializer =
        new IrWord(
            new SymbolId(1),
            "<大域初期化>",
            List.of(
                new PushConst(new IntegerValue(BigInteger.ONE), SPAN),
                new InitializeGlobal(global, SPAN),
                new Return(SPAN)));
    var instructions = new ArrayList<IrInstruction>(loadCount + 1);
    for (int index = 0; index < loadCount; index++) {
      instructions.add(new LoadGlobal(global, SPAN));
    }
    instructions.add(new Return(SPAN));
    var main = new IrWord(mainSymbol, "メイン", instructions);
    return new IrProgram(
        SOURCE_PATH,
        mainSymbol,
        new LinkedHashMap<>(Map.of(mainSymbol, main)),
        Map.of(),
        initializer.instructions().size() + instructions.size(),
        Optional.of(initializer),
        List.of(global));
  }

  private static SourceSpan span(long start, long end) {
    return new SourceSpan(
        new SourcePosition(start, 1, Math.toIntExact(start + 1)),
        new SourcePosition(end, 1, Math.toIntExact(end + 1)));
  }

  private record Observation(int exit, String code, long limit, long observed, String outcome) {
    private static Observation accepted(long limit, long observed) {
      return new Observation(0, null, limit, observed, "accepted");
    }
  }
}
