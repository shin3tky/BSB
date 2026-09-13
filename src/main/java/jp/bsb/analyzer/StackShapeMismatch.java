package jp.bsb.analyzer;

import java.util.Objects;

/**
 * 2本の到達可能経路を合流できなかった理由と、比較した両経路を保持します。
 *
 * <p>両経路を残すことで、構造化診断の主位置に実際の経路、関連位置に基準経路を設定できます。
 *
 * @param kind 個数または型のどちらが異なったか
 * @param expectedPathIndex 基準にした入力経路の添字
 * @param expectedPath 基準にした経路とスタック
 * @param actualPathIndex 一致しなかった入力経路の添字
 * @param actualPath 一致しなかった経路とスタック
 * @param stackIndex 型不一致が最初に現れた底からの添字。個数不一致なら {@code -1}
 */
public record StackShapeMismatch(
    Kind kind,
    int expectedPathIndex,
    ControlPath expectedPath,
    int actualPathIndex,
    ControlPath actualPath,
    int stackIndex) {
  /** 経路添字と不一致種類が互いに矛盾しないことを検査します。 */
  public StackShapeMismatch {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(expectedPath, "expectedPath");
    Objects.requireNonNull(actualPath, "actualPath");
    if (expectedPathIndex < 0 || actualPathIndex < 0) {
      throw new IllegalArgumentException("path indexes must not be negative");
    }
    if (!expectedPath.canFallThrough() || !actualPath.canFallThrough()) {
      throw new IllegalArgumentException("only fallthrough paths can be joined");
    }
    if ((kind == Kind.HEIGHT && stackIndex != -1) || (kind == Kind.TYPE && stackIndex < 0)) {
      throw new IllegalArgumentException("stack index does not match mismatch kind");
    }
  }

  /** 合流不一致の分類です。 */
  public enum Kind {
    /** スタックへ積まれた値の個数が異なります。 */
    HEIGHT,

    /** 個数は同じですが、対応する位置の型が異なります。 */
    TYPE
  }
}
