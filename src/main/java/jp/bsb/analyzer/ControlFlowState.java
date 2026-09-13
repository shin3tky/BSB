package jp.bsb.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * ある構文要素を解析した後に残る、通常経路と制御移行経路の集合です。
 *
 * <p>通常経路は高々1本です。分岐の合流時に通常経路のスタックを統一するためです。一方、複数の枝に書かれた「戻る」などは、後でそれぞれを検査できるよう一覧として保持します。
 */
public final class ControlFlowState {
  private static final ControlFlowState UNREACHABLE = new ControlFlowState(null, List.of());

  private final ControlPath fallthrough;
  private final List<ControlPath> transfers;

  private ControlFlowState(ControlPath fallthrough, List<ControlPath> transfers) {
    if (fallthrough != null && !fallthrough.canFallThrough()) {
      throw new IllegalArgumentException("fallthrough path must have FALLTHROUGH kind");
    }
    if (transfers.stream().anyMatch(ControlPath::canFallThrough)) {
      throw new IllegalArgumentException("transfer paths must not have FALLTHROUGH kind");
    }
    this.fallthrough = fallthrough;
    this.transfers = List.copyOf(transfers);
  }

  /**
   * 指定スタックで次の文へ到達できる初期状態を作ります。
   *
   * @param stack 通常経路の抽象スタック
   * @param origin 通常経路の由来位置
   * @return 到達可能な初期状態
   */
  public static ControlFlowState reachable(AbstractStack stack, SourceSpan origin) {
    return new ControlFlowState(ControlPath.fallthrough(stack, origin), List.of());
  }

  /**
   * どの経路からも次の文へ到達しない状態を返します。
   *
   * @return 共有可能な到達不能状態
   */
  public static ControlFlowState unreachable() {
    return UNREACHABLE;
  }

  static ControlFlowState joined(ControlPath fallthrough, List<ControlPath> transfers) {
    return fallthrough == null && transfers.isEmpty()
        ? UNREACHABLE
        : new ControlFlowState(fallthrough, transfers);
  }

  /**
   * 次の文へ到達する通常経路を返します。
   *
   * @return 通常経路。到達不能なら空
   */
  public Optional<ControlPath> fallthrough() {
    return Optional.ofNullable(fallthrough);
  }

  /**
   * 復帰・脱出・継続によって通常の流れを離れた経路を返します。
   *
   * @return 不変の制御移行経路一覧
   */
  public List<ControlPath> transfers() {
    return transfers;
  }

  /**
   * 次の文へ到達できるかを返します。
   *
   * @return 通常経路があればtrue
   */
  public boolean isReachable() {
    return fallthrough != null;
  }

  /**
   * 現在の通常経路を、復帰・脱出・継続のいずれかへ変換します。
   *
   * <p>変換後は通常経路がなくなるため、直後の文は到達不能です。解析器はそこで警告を1件生成し、派生する型エラーを抑止できます。
   *
   * @param kind 制御移行の種類
   * @param origin 制御移行語のソース範囲
   * @return 制御移行後の状態
   * @throws IllegalArgumentException 通常経路を表す種類を指定した場合
   * @throws IllegalStateException すでに到達不能な場合
   */
  public ControlFlowState transfer(ControlPathKind kind, SourceSpan origin) {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(origin, "origin");
    if (kind == ControlPathKind.FALLTHROUGH) {
      throw new IllegalArgumentException("a transfer kind is required");
    }
    if (fallthrough == null) {
      throw new IllegalStateException("unreachable state has no path to transfer");
    }
    var result = new ArrayList<>(transfers);
    result.add(new ControlPath(kind, fallthrough.stack(), origin));
    return new ControlFlowState(null, result);
  }
}
