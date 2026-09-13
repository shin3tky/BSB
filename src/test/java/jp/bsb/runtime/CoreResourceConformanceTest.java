package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.conformance.ConformanceData;
import jp.bsb.conformance.ConformanceData.CheckedProperties;
import jp.bsb.conformance.ConformanceData.ResourceSpec;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.Lexer;
import jp.bsb.frontend.Parser;
import jp.bsb.frontend.SourceText;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LiteralKind;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.ir.Call;
import jp.bsb.ir.IrGenerator;
import jp.bsb.ir.IrInstruction;
import jp.bsb.ir.IrProgram;
import jp.bsb.ir.IrWord;
import jp.bsb.ir.PushConst;
import jp.bsb.ir.Return;
import jp.bsb.ir.SymbolId;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** CORE-R001〜R-014の「上限ちょうど」と「上限+1」を規範TSVから実行するハーネスです。 */
class CoreResourceConformanceTest {
  private static final String SOURCE_PATH = "resource-boundary.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @TestFactory
  List<DynamicTest> everyResourceBoundaryMatchesTheNormativeTable() throws Exception {
    var tests = new ArrayList<DynamicTest>();
    for (ResourceSpec spec : ConformanceData.loadResources()) {
      String label = spec.id() + '/' + spec.target() + '/' + spec.variant();
      tests.add(DynamicTest.dynamicTest(label, () -> compare(label, spec)));
    }
    assertEquals(28, tests.size(), "two boundary rows are required for each R case");
    return List.copyOf(tests);
  }

  private static void compare(String label, ResourceSpec spec) throws Exception {
    CheckedProperties properties = ConformanceData.loadGeneratedResource(spec.id());
    Observation actual = execute(spec, properties);
    properties.assertFullyConsumed();

    assertEquals(spec.exit(), actual.exit(), label + ": exit code");
    assertEquals(spec.code(), actual.code(), label + ": diagnostic code");
    assertEquals(spec.limit(), actual.limit(), label + ": limit");
    assertEquals(spec.observed(), actual.observed(), label + ": observed");
    assertEquals(spec.outcome(), actual.outcome(), label + ": outcome classification");
  }

  private static Observation execute(ResourceSpec spec, CheckedProperties properties)
      throws Exception {
    return switch (spec.id()) {
      case "CORE-R001" -> sourceByteBoundary(spec, properties);
      case "CORE-R002" -> identifierBoundary(spec, properties);
      case "CORE-R003" -> tokenBoundary(spec, properties);
      case "CORE-R004" -> definitionBoundary(spec, properties);
      case "CORE-R005" -> diagnosticBoundary(spec, properties);
      case "CORE-R006" -> irBoundary(spec, properties);
      case "CORE-R007" -> stringBoundary(spec, properties);
      case "CORE-R008" -> numberBoundary(spec, properties);
      case "CORE-R009" -> integerResultBoundary(spec, properties);
      case "CORE-R010" -> dataStackBoundary(spec, properties);
      case "CORE-R011" -> callStackBoundary(spec, properties);
      case "CORE-R012" -> instructionBoundary(spec, properties);
      case "CORE-R013" -> elapsedTimeBoundary(spec, properties);
      case "CORE-R014" -> outputBoundary(spec, properties);
      default -> throw new IllegalArgumentException("unknown resource case: " + spec.id());
    };
  }

  private static Observation sourceByteBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("pad-comment-to-byte-length", properties.require("generator"));
    byte[] base = properties.require("base").getBytes(StandardCharsets.UTF_8);
    long[] variants = longCsv(properties.require("variants"));
    consumeCommandExpectations(properties, variants, spec);
    byte[] source = new byte[Math.toIntExact(spec.variant())];
    System.arraycopy(base, 0, source, 0, base.length);
    Arrays.fill(source, base.length, source.length, (byte) 'a');

    var result = new SourceChecker().check(SOURCE_PATH, source);
    return analysisObservation(result.exitCode(), result.diagnostics(), spec);
  }

  private static Observation identifierBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("repeated-identifier", properties.require("generator"));
    int codePoint = Integer.parseInt(properties.require("identifier.codePoint"), 16);
    long[] variants = longCsv(properties.require("variants"));
    String suffix = properties.require("suffix");
    consumeCommandExpectations(properties, variants, spec);
    String identifier =
        new String(Character.toChars(codePoint)).repeat(Math.toIntExact(spec.variant()));
    var result =
        new SourceChecker()
            .check(SOURCE_PATH, (identifier + suffix).getBytes(StandardCharsets.UTF_8));
    return analysisObservation(result.exitCode(), result.diagnostics(), spec);
  }

  private static Observation tokenBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("repeated-token-stream", properties.require("generator"));
    assertEquals("lexer", properties.require("target"));
    String token = properties.require("token");
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);
    String sourceText = (token + ' ').repeat(Math.toIntExact(spec.variant()));
    var diagnostics = new DiagnosticCollector();
    var result = new Lexer().lex(new SourceText(SOURCE_PATH, sourceText, 0), diagnostics);
    if (result.successful()) {
      assertEquals(spec.variant(), result.countedTokenCount());
      return Observation.accepted(spec.variant());
    }
    return diagnosticObservation(9, diagnostics.diagnostics().getFirst(), "error");
  }

  private static Observation definitionBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("empty-word-definitions", properties.require("generator"));
    assertEquals("parser", properties.require("target"));
    String prefix = properties.require("namePrefix");
    assertTrue(booleanValue(properties.require("includeMain")));
    assertTrue(booleanValue(properties.require("definitionCountIncludesMain")));
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);

    int count = Math.toIntExact(spec.variant());
    var source = new StringBuilder(count * 24);
    for (int index = 0; index < count - 1; index++) {
      source.append(prefix).append(index).append("とは （--）\nこと。\n\n");
    }
    source.append("メインとは （--）\nこと。\n");
    var text = new SourceText(SOURCE_PATH, source.toString(), 0);
    var diagnostics = new DiagnosticCollector();
    var lexed = new Lexer().lex(text, diagnostics);
    assertTrue(lexed.successful(), diagnostics.diagnostics().toString());
    var parsed = new Parser().parse(text, lexed, diagnostics);
    if (parsed.successful()) {
      assertEquals(spec.variant(), parsed.partialProgram().definitions().size());
      return Observation.accepted(spec.variant());
    }
    return diagnosticObservation(9, diagnostics.diagnostics().getFirst(), "error");
  }

  private static Observation diagnosticBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("undefined-calls", properties.require("generator"));
    assertEquals("analyzer", properties.require("target"));
    long[] variants = longCsv(properties.require("variants"));
    for (long variant : variants) {
      assertEquals(
          variant, Long.parseLong(properties.require("expect." + variant + ".diagnostic.count")));
      assertEquals(
          DiagnosticCode.E_UNDEFINED_WORD.name(),
          properties.require("expect." + variant + ".diagnostic.1-99"));
      if (variant == 100) {
        assertEquals(
            DiagnosticCode.E_DIAGNOSTIC_LIMIT.name(),
            properties.require("expect.100.diagnostic.100"));
      }
    }
    assertVariantDeclared(variants, spec);
    var source = new StringBuilder("メインとは （--）\n");
    for (int index = 0; index < spec.variant(); index++) {
      source.append("    未定義").append(index).append('\n');
    }
    source.append("こと。\n");
    var result =
        new SourceChecker().check(SOURCE_PATH, source.toString().getBytes(StandardCharsets.UTF_8));
    assertEquals(spec.variant(), result.diagnostics().size());
    Diagnostic selected = result.diagnostics().getLast();
    return new Observation(
        result.exitCode(), selected.code().name(), spec.limit(), spec.observed(), spec.outcome());
  }

  private static Observation irBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("synthetic-ir", properties.require("generator"));
    assertEquals("ir-generator", properties.require("target"));
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);
    properties.require("note");
    var result =
        new IrGenerator().generate(syntheticAnalyzedProgram(Math.toIntExact(spec.variant() - 1)));
    if (result.successful()) {
      assertEquals(spec.variant(), result.programForExecution().instructionCount());
      return Observation.accepted(spec.variant());
    }
    return diagnosticObservation(8, result.diagnostics().getFirst(), "error");
  }

  private static Observation stringBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("repeated-string-value", properties.require("generator"));
    assertEquals("lexer", properties.require("target"));
    int utf8Byte = Integer.parseInt(properties.require("utf8Byte"), 16);
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);
    String value = Character.toString(utf8Byte).repeat(Math.toIntExact(spec.variant()));
    return lexBoundary("「" + value + "」", spec);
  }

  private static Observation numberBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("repeated-number-digit", properties.require("generator"));
    assertEquals("lexer", properties.require("target"));
    String digit = properties.require("digit");
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);
    return lexBoundary(digit.repeat(Math.toIntExact(spec.variant())), spec);
  }

  private static Observation integerResultBoundary(
      ResourceSpec spec, CheckedProperties properties) {
    assertEquals("integer-result-boundary", properties.require("generator"));
    assertEquals("runtime", properties.require("target"));
    assertEquals("1 followed by 4095 zeroes", properties.require("factorA"));
    int factorCount = Integer.parseInt(properties.require("factorA.count"));
    assertEquals("1 followed by 15 zeroes", properties.require("factorB"));
    BigInteger overFactor = new BigInteger(properties.require("overFactor"));
    assertEquals(
        RuntimeLimits.INTEGER_DIGITS, Integer.parseInt(properties.require("expect.exact.digits")));
    assertEquals("accepted", properties.require("expect.exact"));
    assertEquals(DiagnosticCode.E_INTEGER_RESULT_LIMIT.name(), properties.require("expect.over"));

    BigInteger first = BigInteger.TEN.pow(4095).pow(factorCount);
    BigInteger second = BigInteger.TEN.pow(15);
    if (spec.variant() > RuntimeLimits.INTEGER_DIGITS) {
      second = second.multiply(overFactor);
    }
    ExecutionResult result = executeArithmetic(first, second);
    if (result.successful()) {
      int digits = ((IntegerValue) result.finalDataStack().getFirst()).displayText().length();
      return Observation.accepted(digits);
    }
    return diagnosticObservation(10, result.diagnostic().orElseThrow(), "error");
  }

  private static Observation dataStackBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("synthetic-push-ir", properties.require("generator"));
    assertEquals("runtime", properties.require("target"));
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);
    properties.require("note");
    var value = new IntegerValue(BigInteger.ZERO);
    var instructions = new ArrayList<IrInstruction>(Math.toIntExact(spec.variant() + 1));
    for (long index = 0; index < spec.variant(); index++) {
      instructions.add(new PushConst(value, SPAN));
    }
    instructions.add(new Return(SPAN));
    ExecutionResult result = execute(singleWord(instructions));
    return runtimeObservation(result, spec.variant());
  }

  private static Observation callStackBoundary(ResourceSpec spec, CheckedProperties properties) {
    assertEquals("synthetic-call-chain-ir", properties.require("generator"));
    assertEquals("runtime", properties.require("target"));
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);
    properties.require("note");
    ExecutionResult result = execute(callChain(Math.toIntExact(spec.variant())));
    return runtimeObservation(result, spec.variant());
  }

  private static Observation instructionBoundary(ResourceSpec spec, CheckedProperties properties)
      throws Exception {
    assertEquals("synthetic-loop-ir", properties.require("generator"));
    assertEquals("runtime", properties.require("target"));
    long[] variants = longCsv(properties.require("variants"));
    consumeSimpleExpectations(properties, variants, spec);
    properties.require("note");
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L, spec.variant() - 1, 0L);
    try {
      budget.beforeInstruction(SPAN);
      assertEquals(spec.variant(), budget.executed());
      return Observation.accepted(budget.executed());
    } catch (RuntimeFailure failure) {
      return diagnosticObservation(10, failure.diagnostic(), "error");
    }
  }

  private static Observation elapsedTimeBoundary(ResourceSpec spec, CheckedProperties properties)
      throws Exception {
    assertEquals("synthetic-clock-ir", properties.require("generator"));
    assertEquals("runtime", properties.require("target"));
    assertEquals("fake-monotonic", properties.require("clock"));
    long[] variants = longCsv(properties.require("variants.nanos"));
    consumeSimpleExpectations(properties, variants, spec);
    var now = new AtomicLong(spec.variant());
    var budget = new ExecutionBudget(SOURCE_PATH, now::get, 0, 0);
    try {
      budget.beforeInstruction(SPAN);
      return Observation.accepted(spec.variant());
    } catch (RuntimeFailure failure) {
      return diagnosticObservation(10, failure.diagnostic(), "error");
    }
  }

  private static Observation outputBoundary(ResourceSpec spec, CheckedProperties properties)
      throws Exception {
    assertEquals("preloaded-output-sink", properties.require("generator"));
    assertEquals("runtime", properties.require("target"));
    long limit = Long.parseLong(properties.require("limit"));
    long[] variants = longCsv(properties.require("variants.currentPlusNext"));
    consumeSimpleExpectations(properties, variants, spec);
    assertEquals("0", properties.require("expect.over.partialWriteBytes"));
    long initial = limit - 1;
    var sink = new MemoryOutputSink();
    var output = new BoundedOutput(SOURCE_PATH, sink, initial);
    byte[] next = new byte[Math.toIntExact(spec.variant() - initial)];
    try {
      output.write(next, SPAN);
      assertEquals(next.length, sink.bytes().length);
      return Observation.accepted(output.writtenBytes());
    } catch (RuntimeFailure failure) {
      assertEquals(0, sink.bytes().length, "a rejected output call must be atomic");
      return diagnosticObservation(10, failure.diagnostic(), "error");
    }
  }

  private static Observation lexBoundary(String source, ResourceSpec spec) {
    var diagnostics = new DiagnosticCollector();
    var result = new Lexer().lex(new SourceText(SOURCE_PATH, source, 0), diagnostics);
    if (result.successful()) {
      return Observation.accepted(spec.variant());
    }
    return diagnosticObservation(9, diagnostics.diagnostics().getFirst(), "error");
  }

  private static Observation analysisObservation(
      int exit, List<Diagnostic> diagnostics, ResourceSpec spec) {
    if (diagnostics.isEmpty()) {
      return Observation.accepted(spec.variant());
    }
    return diagnosticObservation(exit, diagnostics.getFirst(), "error");
  }

  private static Observation runtimeObservation(ExecutionResult result, long acceptedObserved) {
    if (result.successful()) {
      return Observation.accepted(acceptedObserved);
    }
    return diagnosticObservation(10, result.diagnostic().orElseThrow(), "error");
  }

  private static Observation diagnosticObservation(
      int exit, Diagnostic diagnostic, String outcome) {
    return new Observation(
        exit,
        diagnostic.code().name(),
        Long.parseLong(diagnostic.limit().orElseThrow()),
        Long.parseLong(diagnostic.observed().orElseThrow()),
        outcome);
  }

  private static void consumeCommandExpectations(
      CheckedProperties properties, long[] variants, ResourceSpec spec) {
    for (long variant : variants) {
      assertEquals("check", properties.require("expect." + variant + ".command"));
      String exit = properties.require("expect." + variant + ".exit");
      String diagnostic = properties.optional("expect." + variant + ".diagnostic");
      if (variant == spec.variant()) {
        assertEquals(spec.exit(), Integer.parseInt(exit));
        assertEquals(spec.code(), diagnostic);
      }
    }
    assertVariantDeclared(variants, spec);
  }

  private static void consumeSimpleExpectations(
      CheckedProperties properties, long[] variants, ResourceSpec spec) {
    for (long variant : variants) {
      String expected = properties.require("expect." + variant);
      if (variant == spec.variant()) {
        assertEquals(spec.code() == null ? "accepted" : spec.code(), expected);
      }
    }
    assertVariantDeclared(variants, spec);
  }

  private static void assertVariantDeclared(long[] variants, ResourceSpec spec) {
    assertTrue(
        Arrays.stream(variants).anyMatch(variant -> variant == spec.variant()),
        spec.id() + ": TSV variant is absent from generated properties");
  }

  private static long[] longCsv(String value) {
    return Arrays.stream(value.split(",", -1)).mapToLong(Long::parseLong).toArray();
  }

  private static boolean booleanValue(String value) {
    if (value.equals("true")) {
      return true;
    }
    if (value.equals("false")) {
      return false;
    }
    return fail("invalid boolean: " + value);
  }

  private static AnalyzedProgram syntheticAnalyzedProgram(int literalCount) {
    var literal = new Literal(LiteralKind.INTEGER, "0", "0", SPAN);
    var effect = new StackEffect(List.of(), List.of(), SPAN);
    var definition =
        new WordDefinition(
            "メイン", "メイン", SPAN, effect, Collections.nCopies(literalCount, literal), SPAN, SPAN);
    var syntax = new Program(SOURCE_PATH, List.of(definition), SPAN);
    return new AnalyzedProgram(syntax, Map.of("メイン", new WordSignature(List.of(), List.of())));
  }

  private static ExecutionResult executeArithmetic(BigInteger first, BigInteger second) {
    var builtin = BuiltinDictionary.find("掛ける").orElseThrow();
    var main = new SymbolId(0);
    var multiply = new SymbolId(1);
    var instructions =
        List.<IrInstruction>of(
            new PushConst(new IntegerValue(first), SPAN),
            new PushConst(new IntegerValue(second), SPAN),
            new Call(multiply, "掛ける", List.of(), SPAN),
            new Return(SPAN));
    var program =
        new IrProgram(
            SOURCE_PATH,
            main,
            Map.of(main, new IrWord(main, "メイン", instructions)),
            Map.of(multiply, builtin),
            instructions.size());
    return execute(program);
  }

  private static IrProgram singleWord(List<IrInstruction> instructions) {
    var main = new SymbolId(0);
    return new IrProgram(
        SOURCE_PATH,
        main,
        Map.of(main, new IrWord(main, "メイン", instructions)),
        Map.of(),
        instructions.size());
  }

  private static IrProgram callChain(int frameCount) {
    var words = new LinkedHashMap<SymbolId, IrWord>();
    int instructionCount = 0;
    for (int index = 0; index < frameCount; index++) {
      var id = new SymbolId(index);
      List<IrInstruction> instructions;
      if (index + 1 < frameCount) {
        var target = new SymbolId(index + 1);
        instructions =
            List.of(new Call(target, "語" + (index + 1), List.of(), SPAN), new Return(SPAN));
      } else {
        instructions = List.of(new Return(SPAN));
      }
      instructionCount += instructions.size();
      words.put(id, new IrWord(id, index == 0 ? "メイン" : "語" + index, instructions));
    }
    return new IrProgram(SOURCE_PATH, new SymbolId(0), words, Map.of(), instructionCount);
  }

  private static ExecutionResult execute(IrProgram program) {
    return new Interpreter()
        .execute(program, new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));
  }

  private record Observation(int exit, String code, long limit, long observed, String outcome) {
    private static Observation accepted(long observed) {
      return new Observation(0, null, observed, observed, "accepted");
    }
  }
}
