package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ControlFlowJoinerTest {
  @Test
  void joinsReachableBranchesWithTheSameStackShape() {
    ControlFlowState thenState = reachableAt(1, ValueType.INTEGER, ValueType.BOOLEAN);
    ControlFlowState elseState = reachableAt(2, ValueType.INTEGER, ValueType.BOOLEAN);

    var joined =
        assertInstanceOf(
            ControlFlowJoinResult.Joined.class,
            ControlFlowJoiner.join(List.of(thenState, elseState)));

    assertEquals(
        List.of(ValueType.INTEGER, ValueType.BOOLEAN),
        joined.state().fallthrough().orElseThrow().stack().types());
  }

  @Test
  void ignoresAnUnreachableBranchWhenChoosingTheNextStack() {
    ControlFlowState returned =
        reachableAt(1, ValueType.INTEGER).transfer(ControlPathKind.RETURNED, spanAt(3));
    ControlFlowState fallthrough = reachableAt(2, ValueType.STRING);

    var joined =
        assertInstanceOf(
            ControlFlowJoinResult.Joined.class,
            ControlFlowJoiner.join(List.of(returned, fallthrough)));

    assertEquals(
        List.of(ValueType.STRING), joined.state().fallthrough().orElseThrow().stack().types());
    assertEquals(List.of(ControlPathKind.RETURNED), kinds(joined.state()));
  }

  @Test
  void keepsAllTransfersWhenEveryBranchIsUnreachable() {
    ControlFlowState returned =
        reachableAt(1, ValueType.INTEGER).transfer(ControlPathKind.RETURNED, spanAt(3));
    ControlFlowState broken =
        reachableAt(2, ValueType.INTEGER).transfer(ControlPathKind.BROKEN, spanAt(4));

    var joined =
        assertInstanceOf(
            ControlFlowJoinResult.Joined.class, ControlFlowJoiner.join(List.of(returned, broken)));

    assertFalse(joined.state().isReachable());
    assertEquals(List.of(ControlPathKind.RETURNED, ControlPathKind.BROKEN), kinds(joined.state()));
  }

  @Test
  void reportsBothPathsForAStackHeightMismatch() {
    ControlFlowState shorter = reachableAt(1, ValueType.INTEGER);
    ControlFlowState taller = reachableAt(2, ValueType.INTEGER, ValueType.BOOLEAN);

    var failure =
        assertInstanceOf(
            ControlFlowJoinResult.Mismatch.class, ControlFlowJoiner.join(List.of(shorter, taller)));
    StackShapeMismatch mismatch = failure.mismatch();

    assertEquals(StackShapeMismatch.Kind.HEIGHT, mismatch.kind());
    assertEquals(shorter.fallthrough().orElseThrow(), mismatch.expectedPath());
    assertEquals(taller.fallthrough().orElseThrow(), mismatch.actualPath());
    assertEquals(-1, mismatch.stackIndex());
  }

  @Test
  void reportsTheFirstDifferentTypeAndBothPaths() {
    ControlFlowState expected = reachableAt(1, ValueType.INTEGER, ValueType.BOOLEAN);
    ControlFlowState actual = reachableAt(2, ValueType.INTEGER, ValueType.STRING);

    var failure =
        assertInstanceOf(
            ControlFlowJoinResult.Mismatch.class,
            ControlFlowJoiner.join(List.of(expected, actual)));
    StackShapeMismatch mismatch = failure.mismatch();

    assertEquals(StackShapeMismatch.Kind.TYPE, mismatch.kind());
    assertEquals(1, mismatch.stackIndex());
    assertEquals(expected.fallthrough().orElseThrow(), mismatch.expectedPath());
    assertEquals(actual.fallthrough().orElseThrow(), mismatch.actualPath());
  }

  @Test
  void transferMakesFollowingCodeUnreachableAndKeepsItsStack() {
    ControlFlowState entry = reachableAt(1, ValueType.INTEGER);

    ControlFlowState transferred = entry.transfer(ControlPathKind.CONTINUED, spanAt(5));

    assertFalse(transferred.isReachable());
    assertEquals(List.of(ControlPathKind.CONTINUED), kinds(transferred));
    assertEquals(
        entry.fallthrough().orElseThrow().stack(), transferred.transfers().getFirst().stack());
  }

  @Test
  void distinguishesBranchAndLoopFrames() {
    AbstractStack entry = AbstractStack.empty();

    var branch = new ControlFrame(ControlFrame.Kind.BRANCH, entry, spanAt(1));
    var countedLoop = new ControlFrame(ControlFrame.Kind.COUNTED_LOOP, entry, spanAt(2));
    var conditionalLoop = new ControlFrame(ControlFrame.Kind.CONDITIONAL_LOOP, entry, spanAt(3));

    assertFalse(branch.isLoop());
    assertTrue(countedLoop.isLoop());
    assertTrue(conditionalLoop.isLoop());
  }

  private static ControlFlowState reachableAt(long offset, ValueType... types) {
    SourceSpan span = spanAt(offset);
    return ControlFlowState.reachable(AbstractStack.ofTypes(List.of(types), span), span);
  }

  private static List<ControlPathKind> kinds(ControlFlowState state) {
    return state.transfers().stream().map(ControlPath::kind).toList();
  }

  private static SourceSpan spanAt(long offset) {
    var position = new SourcePosition(offset, 1, Math.toIntExact(offset) + 1);
    return new SourceSpan(position, position);
  }
}
