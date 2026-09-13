package jp.bsb.analyzer;

import java.util.Objects;

/** 分岐などの制御経路を合流した結果です。 */
public sealed interface ControlFlowJoinResult
    permits ControlFlowJoinResult.Joined, ControlFlowJoinResult.Mismatch {
  /**
   * 合流できた状態です。
   *
   * @param state 合流後の制御フロー状態
   */
  record Joined(ControlFlowState state) implements ControlFlowJoinResult {
    /** 合流後の状態が存在することを検査します。 */
    public Joined {
      Objects.requireNonNull(state, "state");
    }
  }

  /**
   * 到達可能な出口スタックが一致しなかった結果です。
   *
   * @param mismatch 合流できなかった経路と理由
   */
  record Mismatch(StackShapeMismatch mismatch) implements ControlFlowJoinResult {
    /** 不一致情報が存在することを検査します。 */
    public Mismatch {
      Objects.requireNonNull(mismatch, "mismatch");
    }
  }
}
