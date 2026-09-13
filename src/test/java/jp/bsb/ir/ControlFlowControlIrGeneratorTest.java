package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 制御フローの制御構文が、単語内の確定した命令位置へ変換されることを検証します。 */
class ControlFlowControlIrGeneratorTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void matchesTheNormativeChapterInstructionNumbers() throws IOException {
    IrProgram program = generateChapter();

    assertEquals(15, program.instructionCount());
    List<IrInstruction> display = word(program, "分岐表示").instructions();
    BranchIfFalse branch = assertInstanceOf(BranchIfFalse.class, display.get(0));
    Jump skipElse = assertInstanceOf(Jump.class, display.get(3));
    assertEquals(4, branch.targetIndex());
    assertEquals(2, branch.span().start().line());
    assertEquals(5, branch.span().start().column());
    assertEquals(6, skipElse.targetIndex());
    assertEquals(0, skipElse.countedLoopStatesToDiscard());
    assertEquals(4, skipElse.span().start().line());

    List<IrInstruction> main = program.mainWord().instructions();
    CountedLoopStart start = assertInstanceOf(CountedLoopStart.class, main.get(1));
    CountedLoopNext next = assertInstanceOf(CountedLoopNext.class, main.get(4));
    assertEquals(5, start.exitTargetIndex());
    assertEquals(2, next.bodyTargetIndex());
    assertEquals(10, start.span().start().line());
    assertEquals(12, next.span().start().line());
  }

  @Test
  void lowersElseFreeConditionalToOneForwardBranch() throws IOException {
    List<IrInstruction> instructions = generateSource("FLOW-N001.bsb").mainWord().instructions();

    BranchIfFalse branch = assertInstanceOf(BranchIfFalse.class, instructions.get(1));
    assertEquals(4, branch.targetIndex());
    assertInstanceOf(Return.class, instructions.get(branch.targetIndex()));
    assertEquals(5, instructions.size());
  }

  @Test
  void lowersConditionLoopBreakAndBackEdge() throws IOException {
    List<IrInstruction> instructions = generateSource("FLOW-N012.bsb").mainWord().instructions();

    BranchIfFalse condition = assertInstanceOf(BranchIfFalse.class, instructions.get(1));
    Jump breakJump = assertInstanceOf(Jump.class, instructions.get(4));
    Jump backEdge = assertInstanceOf(Jump.class, instructions.get(5));
    assertEquals(6, condition.targetIndex());
    assertEquals(6, breakJump.targetIndex());
    assertEquals(0, breakJump.countedLoopStatesToDiscard());
    assertEquals(0, backEdge.targetIndex());
  }

  @Test
  void sendsCountedContinueToNextAndBreakToExit() throws IOException {
    List<IrInstruction> continued = generateSource("FLOW-N013.bsb").mainWord().instructions();
    CountedLoopStart continueStart = assertInstanceOf(CountedLoopStart.class, continued.get(1));
    Jump continueJump = assertInstanceOf(Jump.class, continued.get(2));
    CountedLoopNext continueNext = assertInstanceOf(CountedLoopNext.class, continued.get(3));
    assertEquals(4, continueStart.exitTargetIndex());
    assertEquals(3, continueJump.targetIndex());
    assertEquals(0, continueJump.countedLoopStatesToDiscard());
    assertEquals(2, continueNext.bodyTargetIndex());

    List<IrInstruction> broken = generateSource("FLOW-N014.bsb").mainWord().instructions();
    Jump breakJump = assertInstanceOf(Jump.class, broken.get(4));
    assertEquals(6, breakJump.targetIndex());
    assertEquals(1, breakJump.countedLoopStatesToDiscard());
  }

  @Test
  void resolvesNestedLoopsToTheirLexicallyInnermostPositions() throws IOException {
    List<IrInstruction> instructions = generateSource("FLOW-N019.bsb").mainWord().instructions();

    CountedLoopStart outerStart = assertInstanceOf(CountedLoopStart.class, instructions.get(1));
    BranchIfFalse branch = assertInstanceOf(BranchIfFalse.class, instructions.get(3));
    CountedLoopStart innerStart = assertInstanceOf(CountedLoopStart.class, instructions.get(5));
    CountedLoopNext innerNext = assertInstanceOf(CountedLoopNext.class, instructions.get(8));
    CountedLoopNext outerNext = assertInstanceOf(CountedLoopNext.class, instructions.get(9));
    assertEquals(10, outerStart.exitTargetIndex());
    assertEquals(9, branch.targetIndex());
    assertEquals(9, innerStart.exitTargetIndex());
    assertEquals(6, innerNext.bodyTargetIndex());
    assertEquals(2, outerNext.bodyTargetIndex());
  }

  @Test
  void lowersEarlyReturnAndOmitsItsUnreachableTail() throws IOException {
    IrProgram program = generateSource("FLOW-F026.bsb");
    List<IrInstruction> early = word(program, "早く戻る").instructions();

    assertEquals(2, early.size());
    Return explicit = assertInstanceOf(Return.class, early.get(0));
    assertInstanceOf(Return.class, early.get(1));
    assertEquals(2, explicit.span().start().line());
  }

  @ParameterizedTest
  @MethodSource("outOfWordTargets")
  void rejectsEveryControlTargetOutsideItsOwningWord(IrInstruction control) {
    var invalid = List.of(control, new Return(SPAN));

    assertThrows(IllegalArgumentException.class, () -> new IrWord(new SymbolId(0), "不正", invalid));
  }

  @Test
  void producesTheSameInstructionsAndTargetsForTheSameAnalyzedAst() throws IOException {
    byte[] source = resourceBytes("sources/FLOW-N019.bsb");
    var analysis = new SourceChecker().check("FLOW-N019.bsb", source);

    IrProgram first =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    IrProgram second =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();

    assertEquals(first, second);
    assertTrue(
        first.userWords().values().stream()
            .flatMap(word -> word.instructions().stream())
            .allMatch(instruction -> instruction.span() != null));
  }

  private static Stream<Arguments> outOfWordTargets() {
    return Stream.of(
        Arguments.of(new Jump(2, 0, SPAN)),
        Arguments.of(new BranchIfFalse(2, SPAN)),
        Arguments.of(new CountedLoopStart(2, SPAN)),
        Arguments.of(new CountedLoopNext(2, SPAN)));
  }

  private static IrProgram generateChapter() throws IOException {
    byte[] source = resourceBytes("chapter/control-flow-chapter.bsb");
    var analysis = new SourceChecker().check("control-flow-chapter.bsb", source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
  }

  private static IrProgram generateSource(String sourceName) throws IOException {
    byte[] source = resourceBytes("sources/" + sourceName);
    var analysis = new SourceChecker().check(sourceName, source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
  }

  private static IrWord word(IrProgram program, String name) {
    return program.userWords().values().stream()
        .filter(word -> word.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = "/conformance/control-flow/" + relativePath;
    try (var input = ControlFlowControlIrGeneratorTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
