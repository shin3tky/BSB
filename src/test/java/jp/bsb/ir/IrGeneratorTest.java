package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LiteralKind;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.WordDefinition;
import org.junit.jupiter.api.Test;

class IrGeneratorTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void preservesCoreOpcodesAndKeepsParticlesOnTheirCall() {
    String source =
        "二倍とは （整数 -- 整数）\n"
            + "    2 と 掛ける\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    21 を 二倍\n"
            + "    表示する\n"
            + "    改行する\n"
            + "こと。\n";
    var analysis = new SourceChecker().check("sample.bsb", source.getBytes(StandardCharsets.UTF_8));

    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();

    assertEquals(8, program.instructionCount());
    var main = program.mainWord().instructions();
    assertInstanceOf(PushConst.class, main.get(0));
    Call userCall = assertInstanceOf(Call.class, main.get(1));
    assertEquals(List.of("を"), userCall.particles().stream().map(ParticleSource::name).toList());
    assertInstanceOf(Return.class, main.getLast());
  }

  @Test
  void emptyWordBodyStillGeneratesOneReturn() {
    String source = "そのままとは （整数 -- 整数）\nこと。\n\n" + "メインとは （--）\n    42 そのまま 一行表示する\nこと。\n";
    var analysis = new SourceChecker().check("empty.bsb", source.getBytes(StandardCharsets.UTF_8));

    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    IrWord empty =
        program.userWords().values().stream()
            .filter(word -> word.name().equals("そのまま"))
            .findFirst()
            .orElseThrow();

    assertEquals(1, empty.instructions().size());
    assertInstanceOf(Return.class, empty.instructions().getFirst());
  }

  @Test
  void acceptsExactly250000GeneratedInstructions() {
    var result =
        new IrGenerator().generate(syntheticControlProgram(IrGenerator.MAX_INSTRUCTIONS - 1));

    assertTrue(result.successful());
    assertEquals(IrGenerator.MAX_INSTRUCTIONS, result.programForExecution().instructionCount());
  }

  @Test
  void rejectsInstruction250001BeforeAddingIt() {
    var result = new IrGenerator().generate(syntheticControlProgram(IrGenerator.MAX_INSTRUCTIONS));

    assertFalse(result.successful());
    assertEquals(DiagnosticCode.E_IR_LIMIT, result.diagnostics().getFirst().code());
    assertEquals("250001", result.diagnostics().getFirst().observed().orElseThrow());
  }

  @Test
  void omitsElementsMarkedUnreachableByStaticAnalysis() {
    var reachable = new Literal(LiteralKind.INTEGER, "1", "1", SPAN);
    var unreachable = new Literal(LiteralKind.INTEGER, "2", "2", SPAN);
    var effect = new StackEffect(List.of(), List.of(), SPAN);
    var definition =
        new WordDefinition("メイン", "メイン", SPAN, effect, List.of(reachable, unreachable), SPAN, SPAN);
    var syntax = new Program("unreachable.bsb", List.of(definition), SPAN);
    var analyzed =
        new AnalyzedProgram(
            syntax, Map.of("メイン", new WordSignature(List.of(), List.of())), Set.of(unreachable));

    IrProgram program = new IrGenerator().generate(analyzed).programForExecution();

    assertEquals(2, program.instructionCount());
    PushConst push =
        assertInstanceOf(PushConst.class, program.mainWord().instructions().getFirst());
    assertEquals("1", push.value().displayText());
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
    var syntax = new Program("synthetic.bsb", List.of(definition), SPAN);
    return new AnalyzedProgram(syntax, Map.of("メイン", new WordSignature(List.of(), List.of())));
  }
}
