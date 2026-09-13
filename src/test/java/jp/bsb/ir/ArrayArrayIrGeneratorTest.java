package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.binding.NameResolution;
import jp.bsb.binding.WordName;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.runtime.StringValue;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 配列の配列リテラルと配列反復を、具体型と確定済み飛び先を持つIRへ変換できることを検証します。 */
class ArrayArrayIrGeneratorTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ARRAY-N001.bsb", "ARRAY-N002.bsb", "ARRAY-N003.bsb", "ARRAY-N004.bsb", "ARRAY-N005.bsb",
        "ARRAY-N006.bsb", "ARRAY-N007.bsb", "ARRAY-N008.bsb", "ARRAY-N009.bsb", "ARRAY-N010.bsb",
        "ARRAY-N011.bsb", "ARRAY-N012.bsb", "ARRAY-N013.bsb", "ARRAY-N014.bsb", "ARRAY-N015.bsb",
        "ARRAY-N016.bsb", "ARRAY-N017.bsb", "ARRAY-N018.bsb", "ARRAY-N019.bsb", "ARRAY-N020.bsb",
        "ARRAY-N021.bsb"
      })
  void generatesEveryNormalArraySource(String sourceName) throws IOException {
    IrProgram program = generateSource(sourceName);

    assertTrue(program.instructionCount() > 0, sourceName);
    assertTrue(
        allInstructions(program).stream().allMatch(instruction -> instruction.span() != null),
        sourceName);
  }

  @Test
  void lowersTheNormativeChapterToTheSpecifiedArrayInstructionsAndTargets() throws IOException {
    IrProgram program = generateChapter();

    assertEquals(30, program.instructionCount());
    IrWord initializer = program.globalInitializer().orElseThrow();
    BuildArray points = assertInstanceOf(BuildArray.class, initializer.instructions().get(5));
    assertEquals(ScalarType.INTEGER, points.elementType());
    assertEquals(5, points.elementCount());
    assertEquals(1, points.span().start().line());
    assertEquals(8, points.span().start().column());

    List<IrInstruction> main = program.mainWord().instructions();
    ArrayLoopStart firstStart = assertInstanceOf(ArrayLoopStart.class, main.get(1));
    ArrayLoopNext firstNext = assertInstanceOf(ArrayLoopNext.class, main.get(7));
    assertEquals(8, firstStart.exitTargetIndex());
    assertEquals(2, firstNext.bodyTargetIndex());
    assertEquals(6, firstStart.span().start().line());
    assertEquals(10, firstStart.span().start().column());
    assertEquals(9, firstNext.span().start().line());
    assertEquals(5, firstNext.span().start().column());

    ArrayLoopStart secondStart = assertInstanceOf(ArrayLoopStart.class, main.get(14));
    ArrayLoopNext secondNext = assertInstanceOf(ArrayLoopNext.class, main.get(16));
    assertEquals(17, secondStart.exitTargetIndex());
    assertEquals(15, secondNext.bodyTargetIndex());
  }

  @Test
  void emitsElementExpressionsOnceInSourceOrderBeforeBuildArray() throws IOException {
    IrWord initializer = generateSource("ARRAY-N004.bsb").globalInitializer().orElseThrow();

    assertEquals(
        List.of(
            "PushConst",
            "InitializeGlobal",
            "LoadGlobal",
            "PushConst",
            "PushConst",
            "Call:足す",
            "BuildArray",
            "InitializeGlobal",
            "Return"),
        initializer.instructions().stream().map(IrInstruction::opcode).toList());
    BuildArray build = assertInstanceOf(BuildArray.class, initializer.instructions().get(6));
    assertEquals(ScalarType.INTEGER, build.elementType());
    assertEquals(2, build.elementCount());
  }

  @Test
  void lowersAllTypedEmptyArraysWithoutRuntimeTypeNameLookup() throws IOException {
    IrWord main = generateSource("ARRAY-N003.bsb").mainWord();
    List<BuildArray> emptyArrays =
        main.instructions().stream()
            .filter(BuildArray.class::isInstance)
            .map(BuildArray.class::cast)
            .toList();

    assertEquals(
        List.of(ScalarType.INTEGER, ScalarType.BOOLEAN, ScalarType.CHARACTER, ScalarType.STRING),
        emptyArrays.stream().map(BuildArray::elementType).toList());
    assertTrue(emptyArrays.stream().allMatch(array -> array.elementCount() == 0));
    assertTrue(
        main.instructions().stream()
            .filter(Call.class::isInstance)
            .map(Call.class::cast)
            .noneMatch(call -> call.targetName().startsWith("空の")));
  }

  @Test
  void givesAnEmptyArrayLoopADirectExitPastItsStaticallyGeneratedBody() throws IOException {
    List<IrInstruction> instructions = generateSource("ARRAY-N014.bsb").mainWord().instructions();

    assertEquals(
        List.of(
            "BuildArray",
            "ArrayLoopStart",
            "Call:一行表示する",
            "ArrayLoopNext",
            "PushConst",
            "Call:一行表示する",
            "Return"),
        instructions.stream().map(IrInstruction::opcode).toList());
    ArrayLoopStart start = assertInstanceOf(ArrayLoopStart.class, instructions.get(1));
    ArrayLoopNext next = assertInstanceOf(ArrayLoopNext.class, instructions.get(3));
    assertEquals(4, start.exitTargetIndex());
    assertEquals(2, next.bodyTargetIndex());
  }

  @Test
  void resolvesArrayContinueAndBreakAndEncodesStateDiscard() throws IOException {
    IrWord continued = generateSource("ARRAY-N015.bsb").mainWord();
    ArrayLoopNext continueNext =
        continued.instructions().stream()
            .filter(ArrayLoopNext.class::isInstance)
            .map(ArrayLoopNext.class::cast)
            .findFirst()
            .orElseThrow();
    Jump continueJump =
        continued.instructions().stream()
            .filter(Jump.class::isInstance)
            .map(Jump.class::cast)
            .filter(jump -> jump.span().start().line() == 8)
            .findFirst()
            .orElseThrow();
    assertEquals(continued.instructions().indexOf(continueNext), continueJump.targetIndex());
    assertEquals(0, continueJump.arrayLoopStatesToDiscard());

    IrWord broken = generateSource("ARRAY-N016.bsb").mainWord();
    ArrayLoopStart breakStart =
        broken.instructions().stream()
            .filter(ArrayLoopStart.class::isInstance)
            .map(ArrayLoopStart.class::cast)
            .findFirst()
            .orElseThrow();
    Jump breakJump =
        broken.instructions().stream()
            .filter(Jump.class::isInstance)
            .map(Jump.class::cast)
            .filter(jump -> jump.span().start().line() == 8)
            .findFirst()
            .orElseThrow();
    assertEquals(breakStart.exitTargetIndex(), breakJump.targetIndex());
    assertEquals(1, breakJump.arrayLoopStatesToDiscard());
    assertEquals(0, breakJump.countedLoopStatesToDiscard());
  }

  @Test
  void omitsAnUnreachableArrayAndItsElementInstructions() throws IOException {
    IrProgram program = generateSource("ARRAY-F033.bsb");

    assertTrue(allInstructions(program).stream().noneMatch(BuildArray.class::isInstance));
    assertTrue(allInstructions(program).stream().noneMatch(ArrayLoopStart.class::isInstance));
    assertTrue(allInstructions(program).stream().noneMatch(ArrayLoopNext.class::isInstance));
  }

  @Test
  void rejectsMalformedBuildArrayAndArrayLoopIrBeforeExecution() {
    assertThrows(
        IllegalArgumentException.class, () -> new BuildArray(ScalarType.INTEGER, -1, SPAN));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BuildArray(ScalarType.INTEGER, ArrayLimits.MAX_LENGTH + 1, SPAN));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            program(
                new IrWord(
                    new SymbolId(0),
                    "メイン",
                    List.of(new BuildArray(ScalarType.INTEGER, 1, SPAN), new Return(SPAN)),
                    List.of(),
                    new IrStackEffect(List.of(), List.of()))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            program(
                new IrWord(
                    new SymbolId(0),
                    "メイン",
                    List.of(
                        new PushConst(new StringValue("違う"), SPAN),
                        new BuildArray(ScalarType.INTEGER, 1, SPAN),
                        new Return(SPAN)),
                    List.of(),
                    new IrStackEffect(List.of(), List.of(ValueType.arrayOf(ValueType.INTEGER))))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrWord(
                new SymbolId(0), "メイン", List.of(new ArrayLoopNext(0, SPAN), new Return(SPAN))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrWord(
                new SymbolId(0),
                "メイン",
                List.of(
                    new ArrayLoopStart(2, SPAN), new ArrayLoopNext(0, SPAN), new Return(SPAN))));
  }

  @Test
  void requiresBreakToDiscardItsActiveArrayLoopState() {
    SymbolId mainId = new SymbolId(0);
    SymbolId displayId = new SymbolId(1);
    IrStackEffect displayEffect = new IrStackEffect(List.of(ValueType.INTEGER), List.of());
    List<IrInstruction> invalid =
        List.of(
            new ArrayLoopStart(4, SPAN),
            new Call(displayId, "一行表示する", List.of(), displayEffect, SPAN),
            new Jump(4, 0, 0, SPAN),
            new ArrayLoopNext(1, SPAN),
            new Return(SPAN));
    List<IrInstruction> valid =
        List.of(
            new ArrayLoopStart(4, SPAN),
            new Call(displayId, "一行表示する", List.of(), displayEffect, SPAN),
            new Jump(4, 0, 1, SPAN),
            new ArrayLoopNext(1, SPAN),
            new Return(SPAN));
    IrStackEffect mainEffect =
        new IrStackEffect(List.of(ValueType.arrayOf(ValueType.INTEGER)), List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> program(new IrWord(mainId, "メイン", invalid, List.of(), mainEffect), displayId));
    IrProgram accepted =
        program(new IrWord(mainId, "メイン", valid, List.of(), mainEffect), displayId);
    assertEquals(5, accepted.instructionCount());
  }

  @Test
  void rejectsAConcreteCallEffectThatContradictsTheArrayBuiltinRule() {
    SymbolId mainId = new SymbolId(0);
    SymbolId lengthId = new SymbolId(1);
    ValueType integers = ValueType.arrayOf(ValueType.INTEGER);
    IrStackEffect falseLengthEffect =
        new IrStackEffect(List.of(integers), List.of(ValueType.STRING));
    IrWord main =
        new IrWord(
            mainId,
            "メイン",
            List.of(
                new Call(lengthId, "配列の長さ", List.of(), falseLengthEffect, SPAN), new Return(SPAN)),
            List.of(),
            falseLengthEffect);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrProgram(
                "synthetic.bsb",
                mainId,
                Map.of(mainId, main),
                Map.of(lengthId, BuiltinDictionary.find("配列の長さ").orElseThrow()),
                main.instructions().size()));
  }

  @Test
  void countsBuildArrayAtTheExistingIrInstructionBoundary() {
    IrGenerationResult accepted =
        new IrGenerator().generate(syntheticEmptyArrayProgram(IrGenerator.MAX_INSTRUCTIONS - 1));
    IrGenerationResult rejected =
        new IrGenerator().generate(syntheticEmptyArrayProgram(IrGenerator.MAX_INSTRUCTIONS));

    assertTrue(accepted.successful());
    assertEquals(IrGenerator.MAX_INSTRUCTIONS, accepted.programForExecution().instructionCount());
    assertEquals(
        IrGenerator.MAX_INSTRUCTIONS - 1L,
        accepted.programForExecution().mainWord().instructions().stream()
            .filter(BuildArray.class::isInstance)
            .count());
    assertFalse(rejected.successful());
    assertEquals(DiagnosticCode.E_IR_LIMIT, rejected.diagnostics().getFirst().code());
    assertEquals("250001", rejected.diagnostics().getFirst().observed().orElseThrow());
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

  private static IrProgram program(IrWord main) {
    return new IrProgram(
        "synthetic.bsb",
        main.symbolId(),
        Map.of(main.symbolId(), main),
        Map.of(),
        main.instructions().size());
  }

  private static IrProgram program(IrWord main, SymbolId displayId) {
    return new IrProgram(
        "synthetic.bsb",
        main.symbolId(),
        Map.of(main.symbolId(), main),
        Map.of(displayId, BuiltinDictionary.find("一行表示する").orElseThrow()),
        main.instructions().size());
  }

  private static IrProgram generateChapter() throws IOException {
    String resource = "/conformance/arrays/chapter/arrays-chapter.bsb";
    try (var input = ArrayArrayIrGeneratorTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      var analysis = new SourceChecker().check("arrays-chapter.bsb", input.readAllBytes());
      assertTrue(analysis.successful(), analysis.diagnostics().toString());
      return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    }
  }

  private static IrProgram generateSource(String sourceName) throws IOException {
    String resource = "/conformance/arrays/sources/" + sourceName;
    try (var input = ArrayArrayIrGeneratorTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      var analysis = new SourceChecker().check(sourceName, input.readAllBytes());
      assertTrue(analysis.successful(), analysis.diagnostics().toString());
      return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    }
  }

  private static List<IrInstruction> allInstructions(IrProgram program) {
    var result = new java.util.ArrayList<IrInstruction>();
    program.globalInitializer().ifPresent(word -> result.addAll(word.instructions()));
    program.userWords().values().forEach(word -> result.addAll(word.instructions()));
    return List.copyOf(result);
  }
}
