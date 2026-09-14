package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import jp.bsb.frontend.ast.Comment;
import jp.bsb.frontend.ast.ConditionLoop;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.ControlTransfer;
import jp.bsb.frontend.ast.CountedLoop;
import jp.bsb.frontend.ast.ShortCircuitEvaluation;
import jp.bsb.frontend.ast.ShortCircuitOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ControlFlowParserTest {
  @Test
  void buildsNestedShortCircuitEvaluationBlocks() {
    String source =
        "メインとは （--）\n"
            + "    はい または\n"
            + "        いいえ かつ\n"
            + "            はい\n"
            + "        つぎに\n"
            + "    つぎに\n"
            + "    真偽を捨てる\n"
            + "こと。\n";

    var parsed = ParserTestSupport.parseText("short-circuit.bsb", source);

    assertTrue(parsed.parseResult().successful(), parsed.diagnostics().diagnostics().toString());
    var outer =
        assertInstanceOf(
            ShortCircuitEvaluation.class,
            parsed.parseResult().programForAnalysis().definitions().getFirst().body().get(1));
    var inner = assertInstanceOf(ShortCircuitEvaluation.class, outer.rightBody().get(1));
    assertEquals(ShortCircuitOperator.OR, outer.operator());
    assertEquals(ShortCircuitOperator.AND, inner.operator());
    assertEquals(2, outer.openingSpan().start().line());
    assertEquals(6, outer.endSpan().start().line());
  }

  @ParameterizedTest
  @MethodSource("syntaxValidControlFlowSources")
  void parsesEveryTextBackedControlFlowSourceWithoutSyntaxErrors(String sourceName)
      throws IOException {
    var parsed = ParserTestSupport.parseControlFlowResource(sourceName);

    assertTrue(parsed.parseResult().successful(), sourceName + parsed.diagnostics().diagnostics());
  }

  @Test
  void buildsNestedConditionalBranchesWithAllMarkerSpans() throws IOException {
    var parsed = ParserTestSupport.parseControlFlowResource("FLOW-N005.bsb");
    var outer =
        assertInstanceOf(
            Conditional.class,
            parsed.parseResult().programForAnalysis().definitions().getFirst().body().get(1));
    var inner = assertInstanceOf(Conditional.class, outer.trueBody().get(1));

    assertTrue(outer.hasElse());
    assertTrue(inner.hasElse());
    assertEquals(2, outer.openingSpan().start().line());
    assertEquals(8, outer.elseSpan().orElseThrow().start().line());
    assertEquals(10, outer.endSpan().start().line());
    assertEquals(2, outer.span().start().line());
    assertEquals(10, outer.span().end().line());
  }

  @Test
  void separatesConditionCalculationAndLoopBody() throws IOException {
    var parsed = ParserTestSupport.parseControlFlowResource("FLOW-N012.bsb");
    var loop =
        assertInstanceOf(
            ConditionLoop.class,
            parsed.parseResult().programForAnalysis().definitions().getFirst().body().getFirst());
    var transfer = assertInstanceOf(ControlTransfer.class, loop.body().get(3));

    assertEquals(1, loop.conditionBody().size());
    assertEquals(4, loop.body().size());
    assertEquals(4, loop.separatorSpan().start().line());
    assertEquals(7, loop.endSpan().start().line());
    assertEquals(ControlTransfer.Kind.BREAK, transfer.kind());
  }

  @Test
  void representsDifferentControlKindsInsideOneAnother() throws IOException {
    var parsed = ParserTestSupport.parseControlFlowResource("FLOW-N019.bsb");
    var outerLoop =
        assertInstanceOf(
            CountedLoop.class,
            parsed.parseResult().programForAnalysis().definitions().getFirst().body().get(1));
    var conditional = assertInstanceOf(Conditional.class, outerLoop.body().get(1));
    var innerLoop = assertInstanceOf(CountedLoop.class, conditional.trueBody().get(1));

    // 「文字列」「を」「一行表示する」の3要素を、内側ループも失わず保持します。
    assertEquals(3, innerLoop.body().size());
  }

  @Test
  void representsEveryControlTransferWithoutTurningItIntoAWordCall() throws IOException {
    var breakSource = ParserTestSupport.parseControlFlowResource("FLOW-N012.bsb");
    var continueSource = ParserTestSupport.parseControlFlowResource("FLOW-N013.bsb");
    var returnSource = ParserTestSupport.parseControlFlowResource("FLOW-N015.bsb");

    var breakLoop =
        assertInstanceOf(
            ConditionLoop.class,
            breakSource
                .parseResult()
                .programForAnalysis()
                .definitions()
                .getFirst()
                .body()
                .getFirst());
    var continueLoop =
        assertInstanceOf(
            CountedLoop.class,
            continueSource
                .parseResult()
                .programForAnalysis()
                .definitions()
                .getFirst()
                .body()
                .get(1));
    var returned =
        assertInstanceOf(
            ControlTransfer.class,
            returnSource
                .parseResult()
                .programForAnalysis()
                .definitions()
                .getFirst()
                .body()
                .getFirst());

    assertEquals(
        List.of(
            ControlTransfer.Kind.BREAK, ControlTransfer.Kind.CONTINUE, ControlTransfer.Kind.RETURN),
        List.of(
            assertInstanceOf(ControlTransfer.class, breakLoop.body().get(3)).kind(),
            assertInstanceOf(ControlTransfer.class, continueLoop.body().getFirst()).kind(),
            returned.kind()));
  }

  @Test
  void keepsCommentsInsideEmptyBranchesAndLoops() throws IOException {
    var parsed = ParserTestSupport.parseControlFlowResource("FLOW-N020.bsb");
    var body = parsed.parseResult().programForAnalysis().definitions().getFirst().body();
    var conditional = assertInstanceOf(Conditional.class, body.get(1));
    var loop = assertInstanceOf(CountedLoop.class, body.get(3));

    assertInstanceOf(Comment.class, conditional.trueBody().getFirst());
    assertInstanceOf(Comment.class, conditional.falseBody().getFirst());
    assertInstanceOf(Comment.class, loop.body().getFirst());
  }

  @Test
  void acceptsExactly256NestedControls() {
    var parsed = ParserTestSupport.parseText("深さ256.bsb", nestedConditionals(256));

    assertTrue(parsed.parseResult().successful(), parsed.diagnostics().diagnostics().toString());
    assertEquals(256, conditionalDepth(parsed));
  }

  private static int conditionalDepth(ParserTestSupport.ParsedSource parsed) {
    var body = parsed.parseResult().programForAnalysis().definitions().getFirst().body();
    int depth = 0;
    while (body.size() == 2 && body.get(1) instanceof Conditional conditional) {
      depth++;
      body = conditional.trueBody();
    }
    return depth;
  }

  static String nestedConditionals(int depth) {
    var source = new StringBuilder("メインとは （--）\n");
    source.append("    はい ならば\n".repeat(depth));
    source.append("    つぎに\n".repeat(depth));
    return source.append("こと。\n").toString();
  }

  private static Stream<String> syntaxValidControlFlowSources() {
    return Stream.concat(
        java.util.stream.IntStream.rangeClosed(1, 20)
            .mapToObj(number -> "FLOW-N%03d.bsb".formatted(number)),
        java.util.stream.IntStream.rangeClosed(10, 26)
            .mapToObj(number -> "FLOW-F%03d.bsb".formatted(number)));
  }
}
