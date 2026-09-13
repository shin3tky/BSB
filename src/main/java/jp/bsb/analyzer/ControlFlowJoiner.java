package jp.bsb.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 分岐などから出る複数の制御状態を、後続処理が使う1状態へ合流します。 */
public final class ControlFlowJoiner {
  private ControlFlowJoiner() {}

  /**
   * 到達可能な通常経路のスタックを比較し、制御移行経路を失わずに統合します。
   *
   * <p>到達不能な枝には後続のスタックがないので、スタック比較から除外します。例えば片方の枝が必ず「戻る」なら、もう片方の枝だけが次の文へ到達します。
   *
   * @param states 合流する各枝の状態
   * @return 成功状態、または最初に見つかったスタック不一致
   */
  public static ControlFlowJoinResult join(List<ControlFlowState> states) {
    Objects.requireNonNull(states, "states");
    var transfers = new ArrayList<ControlPath>();
    ControlPath expected = null;
    int expectedIndex = -1;

    for (int pathIndex = 0; pathIndex < states.size(); pathIndex++) {
      ControlFlowState state = Objects.requireNonNull(states.get(pathIndex), "state");
      transfers.addAll(state.transfers());
      var current = state.fallthrough();
      if (current.isEmpty()) {
        continue;
      }
      ControlPath actual = current.orElseThrow();
      if (expected == null) {
        expected = actual;
        expectedIndex = pathIndex;
        continue;
      }

      AbstractStack expectedStack = expected.stack();
      AbstractStack actualStack = actual.stack();
      if (expectedStack.size() != actualStack.size()) {
        return new ControlFlowJoinResult.Mismatch(
            new StackShapeMismatch(
                StackShapeMismatch.Kind.HEIGHT, expectedIndex, expected, pathIndex, actual, -1));
      }
      var firstTypeMismatch = expectedStack.firstTypeMismatch(actualStack);
      if (firstTypeMismatch.isPresent()) {
        return new ControlFlowJoinResult.Mismatch(
            new StackShapeMismatch(
                StackShapeMismatch.Kind.TYPE,
                expectedIndex,
                expected,
                pathIndex,
                actual,
                firstTypeMismatch.orElseThrow()));
      }
    }

    return new ControlFlowJoinResult.Joined(ControlFlowState.joined(expected, transfers));
  }
}
