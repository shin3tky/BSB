package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.conformance.ControlFlowConformanceData;
import jp.bsb.conformance.ControlFlowConformanceData.CheckedProperties;
import jp.bsb.conformance.ControlFlowConformanceData.ResourceSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.ir.IrGenerator;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** R3生成定義から境界入力を再現し、上限ちょうどと1単位超過を検証します。 */
class ControlFlowResourceConformanceTest {
  private static final String SOURCE_PATH = "ControlFlow-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @TestFactory
  List<DynamicTest> everyResourceBoundaryMatchesTheNormativeData() throws Exception {
    List<ResourceSpec> resources = ControlFlowConformanceData.loadResources();
    var tests = new ArrayList<DynamicTest>();
    for (ResourceSpec spec : resources) {
      String label = spec.id() + '/' + spec.target() + '/' + spec.variant();
      tests.add(DynamicTest.dynamicTest(label, () -> compare(label, spec)));
    }
    assertEquals(8, tests.size(), "two boundary rows are required for each R3 case");
    return List.copyOf(tests);
  }

  private static void compare(String label, ResourceSpec spec) throws Exception {
    CheckedProperties properties = ControlFlowConformanceData.loadGeneratedResource(spec.id());
    consumeGeneratorDefinition(properties, spec);
    Observation actual = execute(spec);
    properties.assertFullyConsumed();

    assertEquals(spec.exit(), actual.exit(), label + ": expected/actual exit code");
    assertEquals(spec.code(), actual.code(), label + ": expected/actual diagnostic code");
    assertEquals(spec.limit(), actual.limit(), label + ": expected/actual limit");
    assertEquals(spec.observed(), actual.observed(), label + ": expected/actual observed");
    assertEquals(spec.outcome(), actual.outcome(), label + ": expected/actual outcome");
  }

  private static Observation execute(ResourceSpec spec) throws Exception {
    return switch (spec.id()) {
      case "FLOW-R001" -> syntaxDepthBoundary(spec);
      case "FLOW-R002" -> controlIrBoundary(spec);
      case "FLOW-R003" -> executedControlBoundary(spec);
      case "FLOW-R004" -> repeatCountBoundary(spec);
      default -> throw new IllegalArgumentException("unknown resource case: " + spec.id());
    };
  }

  private static Observation syntaxDepthBoundary(ResourceSpec spec) {
    String source =
        ControlFlowConformanceData.nestedConditionalSource(Math.toIntExact(spec.variant()));
    var result = new SourceChecker().check(SOURCE_PATH, source.getBytes(StandardCharsets.UTF_8));
    if (result.diagnostics().isEmpty()) {
      return Observation.accepted(spec.limit(), spec.observed());
    }
    return diagnosticObservation(result.exitCode(), result.diagnostics().getFirst());
  }

  private static Observation controlIrBoundary(ResourceSpec spec) {
    // Conditional 1個が制御IR 1命令になり、最後に暗黙のReturnが1命令加わる。
    int conditionalCount = Math.toIntExact(spec.variant() - 1);
    var result = new IrGenerator().generate(syntheticControlProgram(conditionalCount));
    if (result.successful()) {
      assertEquals(spec.variant(), result.programForExecution().instructionCount());
      return Observation.accepted(spec.limit(), spec.observed());
    }
    return diagnosticObservation(8, result.diagnostics().getFirst());
  }

  private static Observation executedControlBoundary(ResourceSpec spec) throws Exception {
    // 1000万回の巨大ループを実時間で待たず、同じ実行予算部品を境界直前の状態から検査する。
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L, spec.variant() - 1, 0L);
    try {
      budget.beforeInstruction(SPAN);
      assertEquals(spec.variant(), budget.executed());
      return Observation.accepted(spec.limit(), budget.executed());
    } catch (RuntimeFailure failure) {
      return diagnosticObservation(10, failure.diagnostic());
    }
  }

  private static Observation repeatCountBoundary(ResourceSpec spec) {
    String source = "メインとは （--）\n    " + spec.variant() + " 回だけ\n    繰り返す\nこと。\n";
    var result =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));
    if (result.successful()) {
      return Observation.accepted(spec.limit(), spec.observed());
    }
    Diagnostic diagnostic = result.diagnostics().getFirst();
    return new Observation(
        result.exitCode(),
        diagnostic.code().name(),
        0,
        Long.parseLong(diagnostic.fields().get("count")),
        "error");
  }

  private static void consumeGeneratorDefinition(CheckedProperties properties, ResourceSpec spec) {
    String generator = properties.require("generator");
    long[] variants = longCsv(properties.require("variants"));
    assertTrue(
        Arrays.stream(variants).anyMatch(variant -> variant == spec.variant()),
        spec.id() + ": TSV variant is absent from generated properties");
    switch (spec.id()) {
      case "FLOW-R001" -> {
        assertEquals("nested-control", generator);
        assertEquals("conditional-no-else", properties.require("control"));
        assertEquals(256, Long.parseLong(properties.require("accepted.depth")));
        assertEquals(257, Long.parseLong(properties.require("rejected.depth")));
      }
      case "FLOW-R002" -> {
        assertEquals("control-ir", generator);
        assertEquals("Jump", properties.require("opcode"));
        assertEquals(250_000, Long.parseLong(properties.require("accepted.instructions")));
        assertEquals(250_001, Long.parseLong(properties.require("rejected.instructions")));
      }
      case "FLOW-R003" -> {
        assertEquals("loop-execution", generator);
        assertEquals("Jump", properties.require("opcode"));
        assertEquals(10_000_000, Long.parseLong(properties.require("accepted.executions")));
        assertEquals(10_000_001, Long.parseLong(properties.require("rejected.executions")));
      }
      case "FLOW-R004" -> {
        assertEquals("counted-loop-source", generator);
        assertEquals(0, Long.parseLong(properties.require("accepted.count")));
        assertEquals(-1, Long.parseLong(properties.require("rejected.count")));
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

  private static AnalyzedProgram syntheticControlProgram(int conditionalCount) {
    var conditional = new Conditional(SPAN, List.of(), Optional.empty(), List.of(), SPAN, SPAN);
    var effect = new StackEffect(List.of(), List.of(), SPAN);
    var definition =
        new WordDefinition(
            "メイン",
            "メイン",
            SPAN,
            effect,
            Collections.nCopies(conditionalCount, conditional),
            SPAN,
            SPAN);
    var syntax = new Program(SOURCE_PATH, List.of(definition), SPAN);
    return new AnalyzedProgram(syntax, Map.of("メイン", new WordSignature(List.of(), List.of())));
  }

  private record Observation(int exit, String code, long limit, long observed, String outcome) {
    private static Observation accepted(long limit, long observed) {
      return new Observation(0, null, limit, observed, "accepted");
    }
  }
}
