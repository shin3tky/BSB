package jp.bsb.analyzer;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 1本の制御経路が構文要素を出る時の状態です。
 *
 * @param kind 経路が次に向かう場所
 * @param stack その出口での抽象スタック
 * @param origin この経路を生じさせた構文の範囲
 */
public record ControlPath(ControlPathKind kind, AbstractStack stack, SourceSpan origin) {
  /** 経路の種類、抽象スタック、由来位置が存在することを検査します。 */
  public ControlPath {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(stack, "stack");
    Objects.requireNonNull(origin, "origin");
  }

  /**
   * 通常どおり次の文へ進む経路を作ります。
   *
   * @param stack 出口の抽象スタック
   * @param origin 経路の由来位置
   * @return 通常経路
   */
  public static ControlPath fallthrough(AbstractStack stack, SourceSpan origin) {
    return new ControlPath(ControlPathKind.FALLTHROUGH, stack, origin);
  }

  /**
   * この経路が次の文へ到達できるかを返します。
   *
   * @return 通常経路ならtrue
   */
  public boolean canFallThrough() {
    return kind == ControlPathKind.FALLTHROUGH;
  }
}
